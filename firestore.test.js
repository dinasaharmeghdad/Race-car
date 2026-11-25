const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_racer_1";
const BOB_UID = "bob_racer_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write user profile, garage, or races", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("garage").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Rogue",
      credits: 25000,
      selectedCarId: "viper_gtr",
      totalRaces: 0,
      totalWins: 0,
      unlockedCarIds: ["viper_gtr"],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated owner: valid profile create, partial update, and garage customization succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Create player profile (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Apex Driver",
      credits: 25000,
      selectedCarId: "viper_gtr",
      totalRaces: 0,
      totalWins: 0,
      unlockedCarIds: ["viper_gtr"],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update on profile
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      credits: 18000,
      selectedCarId: "apex_phantom",
      unlockedCarIds: ["viper_gtr", "apex_phantom"],
      bestLapNeonMs: 42350,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Empty garage subcollection list succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("garage").get()
  );

  // Create CarSetup in garage subcollection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("garage").doc("viper_gtr").set({
      userId: ALICE_UID,
      carId: "viper_gtr",
      paintColorHex: "#FF2E4C",
      paintFinish: "METALLIC",
      rimStyle: "TURBINE_10",
      rimColorHex: "#FFD700",
      spoilerStyle: "GT_WING",
      underglowHex: "#00E5FF",
      engineLevel: 2,
      turboLevel: 1,
      tiresLevel: 3,
      nitroLevel: 2,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update on CarSetup
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("garage").doc("viper_gtr").update({
      paintColorHex: "#00E5FF",
      paintFinish: "PEARL",
      engineLevel: 3,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Create RaceResult
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("races").doc("race_1").set({
      userId: ALICE_UID,
      trackId: "neon_tokyo",
      carId: "viper_gtr",
      position: 1,
      totalTimeMs: 128400,
      bestLapMs: 41200,
      creditsEarned: 6000,
      createdAt: new Date(Date.now() - 500),
    })
  );
});

test("Security bounds: reject cross-user access, ghost fields, future timestamps, and invalid enums", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      credits: 25000,
      selectedCarId: "viper_gtr",
      totalRaces: 0,
      totalWins: 0,
      unlockedCarIds: ["viper_gtr"],
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Cross-user isolation
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("garage").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).update({ credits: 999999 }));

  // Ghost field & @DocumentId `id` rejection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      id: ALICE_UID,
      userId: ALICE_UID,
      displayName: "Alice",
      credits: 25000,
      selectedCarId: "viper_gtr",
      totalRaces: 0,
      totalWins: 0,
      unlockedCarIds: ["viper_gtr"],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Future timestamp rejection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      credits: 25000,
      selectedCarId: "viper_gtr",
      totalRaces: 0,
      totalWins: 0,
      unlockedCarIds: ["viper_gtr"],
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );

  // Invalid CarSetup enum / hex / upgrade level rejection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("garage").doc("viper_gtr").set({
      userId: ALICE_UID,
      carId: "viper_gtr",
      paintColorHex: "NOT_HEX",
      paintFinish: "GLOSS",
      rimStyle: "SPORT_5",
      rimColorHex: "#FFFFFF",
      spoilerStyle: "NONE",
      underglowHex: "NONE",
      engineLevel: 99,
      turboLevel: 1,
      tiresLevel: 1,
      nitroLevel: 1,
      createdAt: created,
      updatedAt: created,
    })
  );
});
