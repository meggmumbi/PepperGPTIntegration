package com.example.peppergptintegration.robot

import com.example.peppergptintegration.data.AttentionSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Where the participant's attention was, per item and per phase of an item.
 *
 * The session tells this which phase it is in -- prompt, response, feedback --
 * and a 500 ms sampler counts, within the open phase, how often Pepper's
 * human-awareness engine classed the person as attending. The segments are
 * uploaded once, at the end of the session.
 *
 * On a phone there is no engine, so nothing is collected and nothing is sent:
 * a row of zero samples means "the robot saw nobody", which a phone cannot
 * truthfully claim.
 */
class AttentionTracker(private val scope: CoroutineScope) {

    private class Open(
        val itemId: String?,
        val phase: String,
        val revisit: Boolean,
        val startedAt: Long,
    ) {
        var samples = 0
        var attending = 0
        val states = HashMap<String, Int>()
    }

    private val closed = ArrayList<AttentionSegment>()
    private var open: Open? = null
    private var sampler: Job? = null

    /** Close whatever phase was running and start [phase] for [itemId]. */
    @Synchronized
    fun begin(itemId: String?, phase: String, revisit: Boolean) {
        if (!Robot.hasRobot) return
        close()
        open = Open(itemId, phase, revisit, System.currentTimeMillis())
        if (sampler?.isActive != true) {
            sampler = scope.launch {
                while (isActive) {
                    delay(SAMPLE_MS)
                    val sample = Robot.sampleAttention() ?: continue
                    record(sample)
                }
            }
        }
    }

    @Synchronized
    private fun record(sample: Robot.AttentionSample) {
        val segment = open ?: return
        segment.samples++
        if (sample.attending) segment.attending++
        segment.states[sample.state] = (segment.states[sample.state] ?: 0) + 1
    }

    @Synchronized
    private fun close() {
        val segment = open ?: return
        open = null
        closed += AttentionSegment(
            item_id = segment.itemId,
            phase = segment.phase,
            revisit = segment.revisit,
            started_at_ms = segment.startedAt,
            ended_at_ms = System.currentTimeMillis(),
            samples = segment.samples,
            attending_samples = segment.attending,
            states = HashMap(segment.states),
        )
    }

    /** Stop sampling and hand back everything collected. */
    @Synchronized
    fun finish(): List<AttentionSegment> {
        sampler?.cancel()
        sampler = null
        close()
        return ArrayList(closed)
    }

    companion object {
        private const val SAMPLE_MS = 500L
    }
}
