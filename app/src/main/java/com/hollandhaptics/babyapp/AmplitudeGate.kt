package com.hollandhaptics.babyapp

/**
 * Decides when to keep, stop, or roll over a recording based on
 * the stream of MediaRecorder.getMaxAmplitude() samples.
 *
 * Pure logic: no Android dependencies, fully unit-testable.
 */
class AmplitudeGate(
    private val threshold: Int,
    private val silenceTicksBeforeStop: Int,
) {

    enum class Decision { CONTINUE, STOP_AND_KEEP, STOP_AND_DISCARD }

    /** True after at least one above-threshold sample in the current recording cycle. */
    var hasRecorded: Boolean = false
        private set
    private var silentTicks: Int = 0

    fun reset() {
        hasRecorded = false
        silentTicks = 0
    }

    fun onSample(amplitude: Int): Decision {
        if (amplitude > threshold) {
            hasRecorded = true
            silentTicks = 0
            return Decision.CONTINUE
        }
        silentTicks++
        if (silentTicks < silenceTicksBeforeStop) {
            return Decision.CONTINUE
        }
        return if (hasRecorded) Decision.STOP_AND_KEEP else Decision.STOP_AND_DISCARD
    }
}
