package com.example.peppergptintegration

import android.provider.Contacts.PresenceColumns.AWAY
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.builder.SayBuilder
import com.aldebaran.qi.sdk.`object`.conversation.BodyLanguageOption
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.`object`.human.*
import com.aldebaran.qi.sdk.`object`.humanawareness.EngageHuman
import com.aldebaran.qi.sdk.`object`.humanawareness.HumanAwareness
import com.aldebaran.qi.sdk.builder.EngageHumanBuilder
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

// GazeTrackingManager.kt
class GazeTrackingManager(private val qiContext: QiContext) {

    private var humanAwareness: HumanAwareness? = qiContext.humanAwareness
    private var gazeTrackingJob: Job? = null
    private var sessionStartTime: Long = 0
    private var totalAttentionTime: Long = 0
    private var lastAttentionUpdateTime: Long = 0
    private var isTrackingAttention = false

    private val gazeDataList = mutableListOf<GazeData>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    data class GazeData(
        val timestamp: Long,
        val isLookingAtRobot: Boolean,
        val attentionState: AttentionState?,
        val pleasureState: PleasureState?,
        val excitementState: ExcitementState?,
        val engagementIntentionState: EngagementIntentionState?,
        val smileState: SmileState?,
        val estimatedAge: Int?,
        val estimatedGender: Gender?,
        val distanceFromRobot: Double?,
        val engagementZone: Int,
        val humanAngle: Double
    )

    data class GazeTrackingResult(
        val sessionDuration: Long?,
        val totalAttentionTime: Long?,
        val attentionPercentage: Double?,
        val gazeData: List<GazeData>?,
        val timeInZone1: Long?,
        val timeInZone2: Long?,
        val timeInZone3: Long?,
        val zonePercentages: Map<Int, Double>?,
        val averagePleasure: Double?,
        val averageExcitement: Double?,
        val engagementPercentage: Double?,
        val smilePercentage: Double?,
        val metricsSummary: Map<String, Any>
    )

    fun startGazeTracking() {
        if (qiContext == null) {
            Log.e("GazeTracking", "QiContext is null - cannot start gaze tracking")
            return
        }
        stopGazeTracking()

        sessionStartTime = System.currentTimeMillis()
        totalAttentionTime = 0
        lastAttentionUpdateTime = sessionStartTime
        gazeDataList.clear()
        isTrackingAttention = true

        gazeTrackingJob = scope.launch {
            while (isActive && isTrackingAttention) {
                trackCurrentGaze()
                delay(500)
            }
        }

        Log.d("GazeTracking", "Started gaze tracking")
    }

    fun stopGazeTracking(): GazeTrackingResult {
        isTrackingAttention = false
        gazeTrackingJob?.cancel()

        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        val attentionPercentage = if (sessionDuration > 0) {
            (totalAttentionTime.toDouble() / sessionDuration.toDouble()) * 100.0
        } else {
            0.0
        }

        val zoneAnalytics = calculateZoneAnalytics(gazeDataList)
        val emotionAnalytics = calculateEmotionAnalytics(gazeDataList)

        val result = GazeTrackingResult(
            sessionDuration = sessionDuration,
            totalAttentionTime = totalAttentionTime,
            attentionPercentage = attentionPercentage,
            gazeData = gazeDataList.toList(),
            timeInZone1 = zoneAnalytics["timeInZone1"] as Long,
            timeInZone2 = zoneAnalytics["timeInZone2"] as Long,
            timeInZone3 = zoneAnalytics["timeInZone3"] as Long,
            zonePercentages = zoneAnalytics["zonePercentages"] as Map<Int, Double>,
            averagePleasure = emotionAnalytics["averagePleasure"] as Double,
            averageExcitement = emotionAnalytics["averageExcitement"] as Double,
            engagementPercentage = emotionAnalytics["engagementPercentage"] as Double,
            smilePercentage = emotionAnalytics["smilePercentage"] as Double,
            metricsSummary = mapOf(
                "totalDataPoints" to gazeDataList.size,
                "trackingStartTime" to sessionStartTime,
                "trackingEndTime" to System.currentTimeMillis()
            )
        )

        Log.d("GazeTracking",
            "Stopped gaze tracking. " +
                    "Duration: ${result.sessionDuration}ms, " +
                    "Attention: ${"%.2f".format(result.attentionPercentage)}%, " +
                    "Zone1: ${"%.2f".format(result.zonePercentages?.get(1) ?: null )}%, " +
                    "Zone2: ${"%.2f".format(result.zonePercentages?.get(2) ?: null )}%, " +
                    "Zone3: ${"%.2f".format(result.zonePercentages?.get(3) ?: null )}%")

        return result
    }

    fun isTracking(): Boolean {
        return isTrackingAttention
    }

    fun getCurrentMetrics(): GazeTrackingResult? {
        if (!isTrackingAttention) return null

        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        val attentionPercentage = if (sessionDuration > 0) {
            (totalAttentionTime.toDouble() / sessionDuration.toDouble()) * 100.0
        } else {
            0.0
        }

        val zoneAnalytics = calculateZoneAnalytics(gazeDataList)
        val emotionAnalytics = calculateEmotionAnalytics(gazeDataList)

        return GazeTrackingResult(
            sessionDuration = sessionDuration,
            totalAttentionTime = totalAttentionTime,
            attentionPercentage = attentionPercentage,
            gazeData = gazeDataList.toList(),
            timeInZone1 = zoneAnalytics["timeInZone1"] as Long,
            timeInZone2 = zoneAnalytics["timeInZone2"] as Long,
            timeInZone3 = zoneAnalytics["timeInZone3"] as Long,
            zonePercentages = zoneAnalytics["zonePercentages"] as Map<Int, Double>,
            averagePleasure = emotionAnalytics["averagePleasure"] as Double,
            averageExcitement = emotionAnalytics["averageExcitement"] as Double,
            engagementPercentage = emotionAnalytics["engagementPercentage"] as Double,
            smilePercentage = emotionAnalytics["smilePercentage"] as Double,
            metricsSummary = mapOf(
                "totalDataPoints" to gazeDataList.size,
                "trackingStartTime" to sessionStartTime,
                "trackingEndTime" to System.currentTimeMillis()
            )
        )
    }

    fun cleanup() {
        stopGazeTracking()
        scope.cancel()
    }

    private suspend fun trackCurrentGaze() {
        if (qiContext == null) return

        withContext(Dispatchers.IO) {
            try {
                val humansAround = humanAwareness?.async()?.humansAround?.get(2, TimeUnit.SECONDS)
                val robotFrame = qiContext.actuation?.robotFrame()

                humansAround?.firstOrNull()?.let { human ->
                    val currentTime = System.currentTimeMillis()
                    val isLookingAtRobot = isLookingAtRobotOrTablet(human.attention, human, robotFrame)

                    // Update attention time
                    if (isLookingAtRobot) {
                        totalAttentionTime += (currentTime - lastAttentionUpdateTime)
                    }
                    lastAttentionUpdateTime = currentTime

                    // Calculate engagement zone and metrics
                    val engagementZone = robotFrame?.let { zoneOfHuman(human, it) } ?: 3
                    val (distance, angle) = calculateDistanceAndAngle(human, robotFrame)

                    // Store comprehensive gaze data
                    gazeDataList.add(GazeData(
                        timestamp = currentTime,
                        isLookingAtRobot = isLookingAtRobot,
                        attentionState = human.attention,
                        pleasureState = human.emotion.pleasure,
                        excitementState = human.emotion.excitement,
                        engagementIntentionState = human.engagementIntention,
                        smileState = human.facialExpressions.smile,
                        estimatedAge = human.estimatedAge.years,
                        estimatedGender = human.estimatedGender,
                        distanceFromRobot = distance,
                        engagementZone = engagementZone,
                        humanAngle = angle
                    ))

                    Log.v("GazeTracking",
                        "Zone: $engagementZone, " +
                                "Distance: ${"%.2f".format(distance)}m, " +
                                "Angle: ${"%.1f".format(Math.toDegrees(angle))}°, " +
                                "Looking: $isLookingAtRobot")
                }
            } catch (e: Exception) {
                Log.e("GazeTracking", "Error tracking gaze: ${e.message}")
            }
        }
    }

    // Engagement Zone Functions
    private fun zoneOfHuman(human: Human, robotFrame: Frame): Int {
        val headFrame = human.headFrame
        val timedTransform = headFrame.computeTransform(robotFrame)
        val transform = timedTransform.transform
        return if (isInArc(transform, 1.5, Math.PI / 2)) 1
        else if (isInArc(transform, 2.5, Math.PI / 2)) 2
        else 3
    }

    fun isInArc(transform: Transform, radius: Double, angle: Double): Boolean {
        val t = transform.translation
        // We are interested in humans that are somewhat close to us.
        val d = sqrt(t.x.pow(2) + t.y.pow(2))
        return if (d < radius) {
            // We are interested by humans that are somewhat facing us.
            val theta = acos(t.x / d)
            theta < angle
        } else false
    }

    private fun calculateDistanceAndAngle(human: Human, robotFrame: Frame?): Pair<Double, Double> {
        return robotFrame?.let {
            val humanFrame = human.headFrame
            val transform = humanFrame.computeTransform(it).transform
            val translation = transform.translation
            val x = translation.x
            val y = translation.y

            val distance = sqrt(x * x + y * y)
            val angle = atan2(y, x)

            Pair(distance, abs(angle))
        } ?: Pair(0.0, 0.0)
    }

    private fun isLookingAtRobotOrTablet(attentionState: AttentionState?, human: Human, robotFrame: Frame?): Boolean {
        return when (attentionState) {
            AttentionState.LOOKING_AT_ROBOT -> true

            AttentionState.LOOKING_DOWN,
            AttentionState.LOOKING_DOWN_LEFT,
            AttentionState.LOOKING_DOWN_RIGHT,
            AttentionState.LOOKING_LEFT,
            AttentionState.LOOKING_RIGHT,
            AttentionState.LOOKING_UP,
            AttentionState.LOOKING_UP_LEFT,
            AttentionState.LOOKING_UP_RIGHT -> {
                // For other directions, check if they're generally facing the robot/tablet area
                // This could mean they're looking at the tablet or robot body
                robotFrame?.let { rf ->
                    val humanFrame = human.headFrame
                    humanFrame?.let { hf ->
                        val transform = hf.computeTransform(rf).transform
                        val angle = Math.toDegrees(atan2(transform.translation.y, transform.translation.x))
                        // Allow wider angle for tablet interaction (60 degrees)
                        return@isLookingAtRobotOrTablet abs(angle) < 60.0
                    }
                } ?: false
            }

            AttentionState.UNKNOWN -> {
                // Fallback: check if human is facing robot (within 45 degrees)
                robotFrame?.let { rf ->
                    val humanFrame = human.headFrame
                    humanFrame?.let { hf ->
                        val transform = hf.computeTransform(rf).transform
                        val angle = Math.toDegrees(atan2(transform.translation.y, transform.translation.x))
                        return@isLookingAtRobotOrTablet abs(angle) < 45.0
                    }
                } ?: false
            }

            else -> false
        }
    }

    // Analytics Functions
    private fun calculateZoneAnalytics(gazeData: List<GazeData>): Map<String, Any> {
        if (gazeData.size < 2) {
            return mapOf(
                "timeInZone1" to 0L,
                "timeInZone2" to 0L,
                "timeInZone3" to 0L,
                "zonePercentages" to mapOf(1 to 0.0, 2 to 0.0, 3 to 0.0)
            )
        }

        var timeInZone1 = 0L
        var timeInZone2 = 0L
        var timeInZone3 = 0L

        for (i in 1 until gazeData.size) {
            val current = gazeData[i]
            val previous = gazeData[i - 1]
            val timeDiff = current.timestamp - previous.timestamp

            when (current.engagementZone) {
                1 -> timeInZone1 += timeDiff
                2 -> timeInZone2 += timeDiff
                3 -> timeInZone3 += timeDiff
            }
        }

        val totalTime = timeInZone1 + timeInZone2 + timeInZone3
        val zonePercentages = if (totalTime > 0) {
            mapOf(
                1 to (timeInZone1.toDouble() / totalTime * 100.0),
                2 to (timeInZone2.toDouble() / totalTime * 100.0),
                3 to (timeInZone3.toDouble() / totalTime * 100.0)
            )
        } else {
            mapOf(1 to 0.0, 2 to 0.0, 3 to 0.0)
        }

        return mapOf(
            "timeInZone1" to timeInZone1,
            "timeInZone2" to timeInZone2,
            "timeInZone3" to timeInZone3,
            "zonePercentages" to zonePercentages
        )
    }

    private fun calculateEmotionAnalytics(gazeData: List<GazeData>): Map<String, Double> {
        // Convert emotion states to numerical values for analysis
        val pleasureValues = gazeData.mapNotNull { it.pleasureState?.toScore() }
        val excitementValues = gazeData.mapNotNull { it.excitementState?.toScore() }
        val engagementValues = gazeData.mapNotNull { it.engagementIntentionState?.toScore() }
        val smileValues = gazeData.mapNotNull { it.smileState?.toScore() }

        val averagePleasure = if (pleasureValues.isNotEmpty()) pleasureValues.average() else 0.0
        val averageExcitement = if (excitementValues.isNotEmpty()) excitementValues.average() else 0.0
        val engagementPercentage = if (engagementValues.isNotEmpty()) engagementValues.average() * 100.0 else 0.0
        val smilePercentage = if (smileValues.isNotEmpty()) smileValues.average() * 100.0 else 0.0

        return mapOf(
            "averagePleasure" to averagePleasure,
            "averageExcitement" to averageExcitement,
            "engagementPercentage" to engagementPercentage,
            "smilePercentage" to smilePercentage
        )
    }

// Extension functions to convert states to numerical scores
    private fun PleasureState?.toScore(): Double {
        return when (this) {
            PleasureState.POSITIVE -> 1.0
            PleasureState.NEUTRAL -> 0.5
            PleasureState.NEGATIVE -> 0.0
            PleasureState.UNKNOWN -> 0.5 // Default for unknown
            else -> 0.5
        }
    }

    private fun ExcitementState?.toScore(): Double {
        return when (this) {
            ExcitementState.EXCITED -> 1.0
            ExcitementState.CALM -> 0.25
            ExcitementState.UNKNOWN -> 0.5
            else -> 0.5
        }
    }

    private fun EngagementIntentionState?.toScore(): Double {
        return when (this) {
            EngagementIntentionState.SEEKING_ENGAGEMENT -> 1.0
            EngagementIntentionState.INTERESTED -> 0.75
            EngagementIntentionState.NOT_INTERESTED -> 0.25
            EngagementIntentionState.UNKNOWN -> 0.5
            else -> 0.5
        }
    }

    private fun SmileState?.toScore(): Double {
        return when (this) {
            SmileState.BROADLY_SMILING -> 1.0
            SmileState.SMILING -> 0.75
            SmileState.NOT_SMILING -> 0.0
            SmileState.UNKNOWN -> 0.5
            else -> 0.5
        }
    }

}