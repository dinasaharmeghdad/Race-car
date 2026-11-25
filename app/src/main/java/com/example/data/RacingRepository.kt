package com.example.data

import android.content.Context
import com.example.R
import com.example.model.CarSetup
import com.example.model.PlayerProfile
import com.example.model.RaceResult
import com.example.model.RacingCatalog
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class RacingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProfile(userId: String): Flow<PlayerProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            PlayerProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeGarage(userId: String): Flow<Map<String, CarSetup>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/garage"
        emitAll(
            db.collection("users").document(uid).collection("garage")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CarSetup::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).associateBy { it.carId }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRecentRaces(userId: String): Flow<List<RaceResult>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid/races"
        emitAll(
            db.collection("users").document(uid).collection("races")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RaceResult::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensurePlayerInitialized(displayName: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            docPath = userRef.path
            val snapshot = userRef.get().await()
            if (!snapshot.exists()) {
                val safeName = displayName.trim().take(40).ifBlank { "راننده اپکس" }
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "credits" to 25000L,
                    "selectedCarId" to "viper_gtr",
                    "totalRaces" to 0L,
                    "totalWins" to 0L,
                    "unlockedCarIds" to listOf("viper_gtr"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(payload).await()
            }

            val starterCar = RacingCatalog.carById("viper_gtr")
            val starterSetupRef = userRef.collection("garage").document(starterCar.id)
            docPath = starterSetupRef.path
            if (!starterSetupRef.get().await().exists()) {
                val defaultSetup = CarSetup.defaultFor(starterCar, uid)
                starterSetupRef.set(carSetupCreateMap(uid, defaultSetup)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun selectCar(carId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            require(RacingCatalog.cars.any { it.id == carId }) { "Unknown carId: $carId" }
            val userRef = db.collection("users").document(uid)
            docPath = userRef.path
            userRef.update(
                mapOf(
                    "selectedCarId" to carId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun unlockCar(carId: String, currentProfile: PlayerProfile): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val spec = RacingCatalog.carById(carId)
            require(!currentProfile.unlockedCarIds.contains(carId)) { "Car already unlocked" }
            require(currentProfile.credits >= spec.price) { "Insufficient credits" }

            val userRef = db.collection("users").document(uid)
            val garageRef = userRef.collection("garage").document(carId)
            docPath = userRef.path

            val newCredits = (currentProfile.credits - spec.price).coerceAtLeast(0L)
            val newUnlocked = (currentProfile.unlockedCarIds + carId).distinct().take(20)

            val batch = db.batch()
            batch.update(
                userRef,
                mapOf(
                    "credits" to newCredits,
                    "selectedCarId" to carId,
                    "unlockedCarIds" to newUnlocked,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(garageRef, carSetupCreateMap(uid, CarSetup.defaultFor(spec, uid)))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCarSetup(setup: CarSetup, upgradeCost: Long = 0L, currentCredits: Long = 0L): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/garage/${setup.carId}"
        return try {
            val uid = requireUserId()
            require(RacingCatalog.cars.any { it.id == setup.carId }) { "Invalid carId" }
            require(setup.engineLevel in 1L..5L) { "Invalid engine level" }
            require(setup.turboLevel in 1L..5L) { "Invalid turbo level" }
            require(setup.tiresLevel in 1L..5L) { "Invalid tires level" }
            require(setup.nitroLevel in 1L..5L) { "Invalid nitro level" }
            if (upgradeCost > 0L) {
                require(currentCredits >= upgradeCost) { "Insufficient credits" }
            }

            val userRef = db.collection("users").document(uid)
            val setupRef = userRef.collection("garage").document(setup.carId)
            docPath = setupRef.path

            val exists = setupRef.get().await().exists()
            val batch = db.batch()
            if (exists) {
                batch.update(
                    setupRef,
                    mapOf(
                        "paintColorHex" to setup.paintColorHex,
                        "paintFinish" to setup.finishEnum.name,
                        "rimStyle" to setup.rimEnum.name,
                        "rimColorHex" to setup.rimColorHex,
                        "spoilerStyle" to setup.spoilerEnum.name,
                        "underglowHex" to setup.underglowHex,
                        "engineLevel" to setup.engineLevel,
                        "turboLevel" to setup.turboLevel,
                        "tiresLevel" to setup.tiresLevel,
                        "nitroLevel" to setup.nitroLevel,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(setupRef, carSetupCreateMap(uid, setup))
            }

            if (upgradeCost > 0L) {
                batch.update(
                    userRef,
                    mapOf(
                        "credits" to (currentCredits - upgradeCost).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordRaceCompletion(
        currentProfile: PlayerProfile,
        trackId: String,
        carId: String,
        position: Int,
        totalTimeMs: Long,
        bestLapMs: Long,
        creditsEarned: Long
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/races"
        return try {
            val uid = requireUserId()
            require(position in 1..4) { "Position must be 1..4" }
            require(totalTimeMs in 1L..3600000L) { "Invalid totalTimeMs" }
            require(bestLapMs in 1L..3600000L) { "Invalid bestLapMs" }

            val userRef = db.collection("users").document(uid)
            val raceRef = userRef.collection("races").document()
            docPath = raceRef.path

            val racePayload = mapOf(
                "userId" to uid,
                "trackId" to trackId,
                "carId" to carId,
                "position" to position.toLong(),
                "totalTimeMs" to totalTimeMs,
                "bestLapMs" to bestLapMs,
                "creditsEarned" to creditsEarned,
                "createdAt" to FieldValue.serverTimestamp()
            )

            val profileUpdates = mutableMapOf<String, Any>(
                "credits" to (currentProfile.credits + creditsEarned).coerceAtMost(100_000_000L),
                "totalRaces" to (currentProfile.totalRaces + 1L),
                "totalWins" to (currentProfile.totalWins + if (position == 1) 1L else 0L),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            when (trackId) {
                "neon_tokyo" -> {
                    val prev = currentProfile.bestLapNeonMs
                    if (prev == null || bestLapMs < prev) profileUpdates["bestLapNeonMs"] = bestLapMs
                }
                "alpine_pass" -> {
                    val prev = currentProfile.bestLapAlpineMs
                    if (prev == null || bestLapMs < prev) profileUpdates["bestLapAlpineMs"] = bestLapMs
                }
                "desert_canyon" -> {
                    val prev = currentProfile.bestLapDesertMs
                    if (prev == null || bestLapMs < prev) profileUpdates["bestLapDesertMs"] = bestLapMs
                }
            }

            val batch = db.batch()
            batch.set(raceRef, racePayload)
            batch.update(userRef, profileUpdates)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private fun carSetupCreateMap(uid: String, setup: CarSetup): Map<String, Any> = mapOf(
        "userId" to uid,
        "carId" to setup.carId,
        "paintColorHex" to setup.paintColorHex,
        "paintFinish" to setup.finishEnum.name,
        "rimStyle" to setup.rimEnum.name,
        "rimColorHex" to setup.rimColorHex,
        "spoilerStyle" to setup.spoilerEnum.name,
        "underglowHex" to setup.underglowHex,
        "engineLevel" to setup.engineLevel,
        "turboLevel" to setup.turboLevel,
        "tiresLevel" to setup.tiresLevel,
        "nitroLevel" to setup.nitroLevel,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
