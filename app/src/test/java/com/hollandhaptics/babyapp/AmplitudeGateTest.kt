package com.hollandhaptics.babyapp

import org.junit.Assert.assertEquals
import org.junit.Test

class AmplitudeGateTest {

    @Test
    fun loud_sample_above_threshold_continues() {
        val gate = AmplitudeGate(threshold = 1000, silenceTicksBeforeStop = 3)
        assertEquals(AmplitudeGate.Decision.CONTINUE, gate.onSample(2000))
    }

    @Test
    fun silence_below_threshold_short_of_limit_continues() {
        val gate = AmplitudeGate(threshold = 1000, silenceTicksBeforeStop = 3)
        assertEquals(AmplitudeGate.Decision.CONTINUE, gate.onSample(10))
        assertEquals(AmplitudeGate.Decision.CONTINUE, gate.onSample(10))
    }

    @Test
    fun silence_only_recording_is_discarded_at_limit() {
        val gate = AmplitudeGate(threshold = 1000, silenceTicksBeforeStop = 3)
        gate.onSample(10)
        gate.onSample(10)
        assertEquals(AmplitudeGate.Decision.STOP_AND_DISCARD, gate.onSample(10))
    }

    @Test
    fun loud_then_silence_is_kept_at_limit() {
        val gate = AmplitudeGate(threshold = 1000, silenceTicksBeforeStop = 3)
        assertEquals(AmplitudeGate.Decision.CONTINUE, gate.onSample(5000))
        gate.onSample(10)
        gate.onSample(10)
        assertEquals(AmplitudeGate.Decision.STOP_AND_KEEP, gate.onSample(10))
    }

    @Test
    fun loud_sample_during_silence_streak_resets_counter() {
        val gate = AmplitudeGate(threshold = 1000, silenceTicksBeforeStop = 3)
        gate.onSample(10)
        gate.onSample(10)
        assertEquals(AmplitudeGate.Decision.CONTINUE, gate.onSample(2000))
        // counter was reset; two more silent ticks should still continue
        assertEquals(AmplitudeGate.Decision.CONTINUE, gate.onSample(10))
        assertEquals(AmplitudeGate.Decision.CONTINUE, gate.onSample(10))
    }

    @Test
    fun reset_clears_recording_and_silence_state() {
        val gate = AmplitudeGate(threshold = 1000, silenceTicksBeforeStop = 2)
        gate.onSample(5000)
        gate.onSample(10)
        gate.reset()
        // After reset, two silent ticks should DISCARD (no recording happened post-reset).
        gate.onSample(10)
        assertEquals(AmplitudeGate.Decision.STOP_AND_DISCARD, gate.onSample(10))
    }
}
