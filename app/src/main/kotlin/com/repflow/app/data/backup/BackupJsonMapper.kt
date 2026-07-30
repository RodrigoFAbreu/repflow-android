package com.repflow.app.data.backup

import com.repflow.app.domain.backup.BackupValidationError
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import com.repflow.app.infrastructure.database.progression.ProgressionRecommendationEntity
import com.repflow.app.infrastructure.database.recovery.FutsalSessionEntity
import com.repflow.app.infrastructure.database.recovery.RecoveryEntryEntity
import com.repflow.app.infrastructure.database.trainingplan.PlannedExerciseEntity
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanEntity
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanVersionEntity
import com.repflow.app.infrastructure.database.workout.WorkoutExerciseEntity
import com.repflow.app.infrastructure.database.workout.WorkoutSessionEntity
import com.repflow.app.infrastructure.database.workout.WorkoutSetEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A full snapshot rendered as plain Room-entity-shaped rows, one list per
 * table - the shape [BackupJsonMapper] actually serializes/parses.
 * [com.repflow.app.data.backup.LocalBackupRepository] converts this to/from
 * a `BackupSnapshot` using each table's existing entity<->domain mapper.
 */
internal data class BackupEntitySnapshot(
    val schemaVersion: Int,
    val exercises: List<ExerciseEntity>,
    val trainingPlans: List<TrainingPlanEntity>,
    val trainingPlanVersions: List<TrainingPlanVersionEntity>,
    val plannedExercises: List<PlannedExerciseEntity>,
    val workoutSessions: List<WorkoutSessionEntity>,
    val workoutExercises: List<WorkoutExerciseEntity>,
    val workoutSets: List<WorkoutSetEntity>,
    val recoveryEntries: List<RecoveryEntryEntity>,
    val futsalSessions: List<FutsalSessionEntity>,
    val progressionRecommendations: List<ProgressionRecommendationEntity>,
)

/**
 * Serializes/parses a [BackupEntitySnapshot] as plain `org.json` text (see
 * the milestone reference's "no new dependency" decision) - never a direct
 * serialization of Room's own row format, per
 * `docs/TECHNICAL_DECISIONS.md`'s "Backup" decision: this is a hand-built,
 * explicit JSON shape versioned by its own `schemaVersion` field, entirely
 * independent of the Room schema version.
 *
 * Every parse failure (malformed JSON, a missing/mistyped field) is
 * reported as [BackupValidationError.Malformed] rather than thrown, so a
 * corrupt backup file never crashes the restore flow.
 */
@Suppress("TooManyFunctions") // one serialize/parse pair per backed-up table by design
internal object BackupJsonMapper {
    fun serialize(snapshot: BackupEntitySnapshot): String {
        val root = JSONObject()
        root.put("schemaVersion", snapshot.schemaVersion)
        root.put("exercises", JSONArray(snapshot.exercises.map(::exerciseToJson)))
        root.put("trainingPlans", JSONArray(snapshot.trainingPlans.map(::trainingPlanToJson)))
        root.put("trainingPlanVersions", JSONArray(snapshot.trainingPlanVersions.map(::trainingPlanVersionToJson)))
        root.put("plannedExercises", JSONArray(snapshot.plannedExercises.map(::plannedExerciseToJson)))
        root.put("workoutSessions", JSONArray(snapshot.workoutSessions.map(::workoutSessionToJson)))
        root.put("workoutExercises", JSONArray(snapshot.workoutExercises.map(::workoutExerciseToJson)))
        root.put("workoutSets", JSONArray(snapshot.workoutSets.map(::workoutSetToJson)))
        root.put("recoveryEntries", JSONArray(snapshot.recoveryEntries.map(::recoveryEntryToJson)))
        root.put("futsalSessions", JSONArray(snapshot.futsalSessions.map(::futsalSessionToJson)))
        root.put("progressionRecommendations", JSONArray(snapshot.progressionRecommendations.map(::progressionRecommendationToJson)))
        return root.toString()
    }

    fun parse(json: String): DomainResult<BackupEntitySnapshot, BackupValidationError> =
        try {
            val root = JSONObject(json)
            DomainResult.Success(
                BackupEntitySnapshot(
                    schemaVersion = root.getInt("schemaVersion"),
                    exercises = root.getJSONArray("exercises").map(::exerciseFromJson),
                    trainingPlans = root.getJSONArray("trainingPlans").map(::trainingPlanFromJson),
                    trainingPlanVersions = root.getJSONArray("trainingPlanVersions").map(::trainingPlanVersionFromJson),
                    plannedExercises = root.getJSONArray("plannedExercises").map(::plannedExerciseFromJson),
                    workoutSessions = root.getJSONArray("workoutSessions").map(::workoutSessionFromJson),
                    workoutExercises = root.getJSONArray("workoutExercises").map(::workoutExerciseFromJson),
                    workoutSets = root.getJSONArray("workoutSets").map(::workoutSetFromJson),
                    recoveryEntries = root.getJSONArray("recoveryEntries").map(::recoveryEntryFromJson),
                    futsalSessions = root.getJSONArray("futsalSessions").map(::futsalSessionFromJson),
                    progressionRecommendations = root.getJSONArray("progressionRecommendations").map(::progressionRecommendationFromJson),
                ),
            )
        } catch (expected: JSONException) {
            DomainResult.Failure(BackupValidationError.Malformed(expected.message ?: "invalid JSON"))
        }

    private inline fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> = List(length()) { transform(getJSONObject(it)) }

    private fun exerciseToJson(e: ExerciseEntity) =
        JSONObject().apply {
            put("id", e.id)
            put("name", e.name)
            put("nameKey", e.nameKey)
            put("trackingType", e.trackingType)
            put("instructions", e.instructions)
            put("defaultLoadIncrementGrams", e.defaultLoadIncrementGrams)
            put("defaultRestSeconds", e.defaultRestSeconds)
            put("origin", e.origin)
            put("archivedAt", e.archivedAt)
            put("createdAt", e.createdAt)
            put("updatedAt", e.updatedAt)
        }

    private fun exerciseFromJson(o: JSONObject) =
        ExerciseEntity(
            id = o.getString("id"),
            name = o.getString("name"),
            nameKey = o.getString("nameKey"),
            trackingType = o.getString("trackingType"),
            instructions = o.optStringOrNull("instructions"),
            defaultLoadIncrementGrams = o.optLongOrNull("defaultLoadIncrementGrams"),
            defaultRestSeconds = o.optLongOrNull("defaultRestSeconds"),
            origin = o.getString("origin"),
            archivedAt = o.optLongOrNull("archivedAt"),
            createdAt = o.getLong("createdAt"),
            updatedAt = o.getLong("updatedAt"),
        )

    private fun trainingPlanToJson(p: TrainingPlanEntity) =
        JSONObject().apply {
            put("id", p.id)
            put("name", p.name)
            put("nameKey", p.nameKey)
            put("createdAt", p.createdAt)
            put("updatedAt", p.updatedAt)
            put("archivedAt", p.archivedAt)
        }

    private fun trainingPlanFromJson(o: JSONObject) =
        TrainingPlanEntity(
            id = o.getString("id"),
            name = o.getString("name"),
            nameKey = o.getString("nameKey"),
            createdAt = o.getLong("createdAt"),
            updatedAt = o.getLong("updatedAt"),
            // A v1 backup (Milestone 8, CP14) never wrote this key at all - optLongOrNull
            // already treats a missing key the same as an explicit JSON null, so no
            // schemaVersion branching is needed here to stay backward-compatible.
            archivedAt = o.optLongOrNull("archivedAt"),
        )

    private fun trainingPlanVersionToJson(v: TrainingPlanVersionEntity) =
        JSONObject().apply {
            put("id", v.id)
            put("planId", v.planId)
            put("versionNumber", v.versionNumber)
            put("note", v.note)
            put("createdAt", v.createdAt)
        }

    private fun trainingPlanVersionFromJson(o: JSONObject) =
        TrainingPlanVersionEntity(
            id = o.getString("id"),
            planId = o.getString("planId"),
            versionNumber = o.getInt("versionNumber"),
            note = o.optStringOrNull("note"),
            createdAt = o.getLong("createdAt"),
        )

    private fun plannedExerciseToJson(e: PlannedExerciseEntity) =
        JSONObject().apply {
            put("id", e.id)
            put("versionId", e.versionId)
            put("exerciseId", e.exerciseId)
            put("sortOrder", e.sortOrder)
            put("targetSets", e.targetSets)
            put("targetKind", e.targetKind)
            put("repMin", e.repMin)
            put("repMax", e.repMax)
            put("durationMinSeconds", e.durationMinSeconds)
            put("durationMaxSeconds", e.durationMaxSeconds)
            put("restSeconds", e.restSeconds)
            put("isOptional", e.isOptional)
            put("targetWarmupSets", e.targetWarmupSets)
        }

    private fun plannedExerciseFromJson(o: JSONObject) =
        PlannedExerciseEntity(
            id = o.getString("id"),
            versionId = o.getString("versionId"),
            exerciseId = o.getString("exerciseId"),
            sortOrder = o.getInt("sortOrder"),
            targetSets = o.getInt("targetSets"),
            targetKind = o.getString("targetKind"),
            repMin = o.optIntOrNull("repMin"),
            repMax = o.optIntOrNull("repMax"),
            durationMinSeconds = o.optLongOrNull("durationMinSeconds"),
            durationMaxSeconds = o.optLongOrNull("durationMaxSeconds"),
            restSeconds = o.optLongOrNull("restSeconds"),
            isOptional = o.getBoolean("isOptional"),
            targetWarmupSets = o.optIntOrNull("targetWarmupSets"),
        )

    private fun workoutSessionToJson(s: WorkoutSessionEntity) =
        JSONObject().apply {
            put("id", s.id)
            put("trainingPlanVersionId", s.trainingPlanVersionId)
            put("status", s.status)
            put("startedAt", s.startedAt)
            put("endedAt", s.endedAt)
            put("restTimerEndAtEpochMs", s.restTimerEndAtEpochMs)
            put("restTimerTotalDurationSeconds", s.restTimerTotalDurationSeconds)
            put("invalidatedAt", s.invalidatedAt)
        }

    private fun workoutSessionFromJson(o: JSONObject) =
        WorkoutSessionEntity(
            id = o.getString("id"),
            trainingPlanVersionId = o.optStringOrNull("trainingPlanVersionId"),
            status = o.getString("status"),
            startedAt = o.getLong("startedAt"),
            endedAt = o.optLongOrNull("endedAt"),
            restTimerEndAtEpochMs = o.optLongOrNull("restTimerEndAtEpochMs"),
            restTimerTotalDurationSeconds = o.optIntOrNull("restTimerTotalDurationSeconds"),
            invalidatedAt = o.optLongOrNull("invalidatedAt"),
        )

    private fun workoutExerciseToJson(e: WorkoutExerciseEntity) =
        JSONObject().apply {
            put("id", e.id)
            put("sessionId", e.sessionId)
            put("exerciseId", e.exerciseId)
            put("sortOrder", e.sortOrder)
            put("exerciseNameSnapshot", e.exerciseNameSnapshot)
            put("trackingType", e.trackingType)
            put("plannedExerciseId", e.plannedExerciseId)
        }

    private fun workoutExerciseFromJson(o: JSONObject) =
        WorkoutExerciseEntity(
            id = o.getString("id"),
            sessionId = o.getString("sessionId"),
            exerciseId = o.getString("exerciseId"),
            sortOrder = o.getInt("sortOrder"),
            exerciseNameSnapshot = o.getString("exerciseNameSnapshot"),
            trackingType = o.getString("trackingType"),
            plannedExerciseId = o.optStringOrNull("plannedExerciseId"),
        )

    private fun workoutSetToJson(s: WorkoutSetEntity) =
        JSONObject().apply {
            put("id", s.id)
            put("workoutExerciseId", s.workoutExerciseId)
            put("sortOrder", s.sortOrder)
            put("load", s.load)
            put("reps", s.reps)
            put("durationSeconds", s.durationSeconds)
            put("rpe", s.rpe)
            put("isWarmup", s.isWarmup)
            put("createdAt", s.createdAt)
            put("updatedAt", s.updatedAt)
            put("pain", s.pain)
            put("techniqueQuality", s.techniqueQuality)
        }

    private fun workoutSetFromJson(o: JSONObject) =
        WorkoutSetEntity(
            id = o.getString("id"),
            workoutExerciseId = o.getString("workoutExerciseId"),
            sortOrder = o.getInt("sortOrder"),
            load = o.optDoubleOrNull("load"),
            reps = o.optIntOrNull("reps"),
            durationSeconds = o.optIntOrNull("durationSeconds"),
            rpe = o.optDoubleOrNull("rpe"),
            isWarmup = o.getBoolean("isWarmup"),
            createdAt = o.getLong("createdAt"),
            updatedAt = o.getLong("updatedAt"),
            pain = o.optIntOrNull("pain"),
            techniqueQuality = o.optIntOrNull("techniqueQuality"),
        )

    private fun recoveryEntryToJson(r: RecoveryEntryEntity) =
        JSONObject().apply {
            put("id", r.id)
            put("entryDate", r.entryDate)
            put("sleepQuality", r.sleepQuality)
            put("energy", r.energy)
            put("legDoms", r.legDoms)
            put("heelStiffness", r.heelStiffness)
            put("painWhileWalking", r.painWhileWalking)
            put("heavyLegs", r.heavyLegs)
            put("futsalInPrevious24h", r.futsalInPrevious24h)
            put("futsalExpectedNext24h", r.futsalExpectedNext24h)
            put("notes", r.notes)
            put("createdAt", r.createdAt)
            put("updatedAt", r.updatedAt)
        }

    private fun recoveryEntryFromJson(o: JSONObject) =
        RecoveryEntryEntity(
            id = o.getString("id"),
            entryDate = o.getString("entryDate"),
            sleepQuality = o.getInt("sleepQuality"),
            energy = o.getInt("energy"),
            legDoms = o.getInt("legDoms"),
            heelStiffness = o.getInt("heelStiffness"),
            painWhileWalking = o.getInt("painWhileWalking"),
            heavyLegs = o.getInt("heavyLegs"),
            futsalInPrevious24h = o.getBoolean("futsalInPrevious24h"),
            futsalExpectedNext24h = o.getBoolean("futsalExpectedNext24h"),
            notes = o.optStringOrNull("notes"),
            createdAt = o.getLong("createdAt"),
            updatedAt = o.getLong("updatedAt"),
        )

    private fun futsalSessionToJson(f: FutsalSessionEntity) =
        JSONObject().apply {
            put("id", f.id)
            put("entryDate", f.entryDate)
            put("durationMinutes", f.durationMinutes)
            put("sessionRpe", f.sessionRpe)
            put("createdAt", f.createdAt)
            put("updatedAt", f.updatedAt)
        }

    private fun futsalSessionFromJson(o: JSONObject) =
        FutsalSessionEntity(
            id = o.getString("id"),
            entryDate = o.getString("entryDate"),
            durationMinutes = o.getInt("durationMinutes"),
            sessionRpe = o.getDouble("sessionRpe"),
            createdAt = o.getLong("createdAt"),
            updatedAt = o.getLong("updatedAt"),
        )

    private fun progressionRecommendationToJson(p: ProgressionRecommendationEntity) =
        JSONObject().apply {
            put("id", p.id)
            put("exerciseId", p.exerciseId)
            put("result", p.result)
            put("reasons", p.reasons)
            put("policyVersion", p.policyVersion)
            put("computedAt", p.computedAt)
            put("overrideResult", p.overrideResult)
            put("overrideAt", p.overrideAt)
        }

    private fun progressionRecommendationFromJson(o: JSONObject) =
        ProgressionRecommendationEntity(
            id = o.getString("id"),
            exerciseId = o.getString("exerciseId"),
            result = o.getString("result"),
            reasons = o.getString("reasons"),
            policyVersion = o.getInt("policyVersion"),
            computedAt = o.getLong("computedAt"),
            overrideResult = o.optStringOrNull("overrideResult"),
            overrideAt = o.optLongOrNull("overrideAt"),
        )

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)
}
