package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioLevelThrottlerTest {
    @Test
    fun `first level is emitted immediately`() {
        val clock = FakeClock()
        val throttler = AudioLevelThrottler(clockMillis = clock::now)

        assertEquals(0.4f, throttler.update(0.4f))
    }

    @Test
    fun `updates inside interval retain the highest peak`() {
        val clock = FakeClock()
        val throttler = AudioLevelThrottler(intervalMillis = 50, minimumChange = 0f, clockMillis = clock::now)

        assertEquals(0.2f, throttler.update(0.2f))
        clock.advance(10)
        assertNull(throttler.update(0.8f))
        clock.advance(10)
        assertNull(throttler.update(0.3f))
        clock.advance(30)

        assertEquals(0.8f, throttler.update(0.4f))
    }

    @Test
    fun `insignificant changes are suppressed after interval`() {
        val clock = FakeClock()
        val throttler = AudioLevelThrottler(intervalMillis = 50, minimumChange = 0.05f, clockMillis = clock::now)

        assertEquals(0.5f, throttler.update(0.5f))
        clock.advance(50)

        assertNull(throttler.update(0.53f))
    }

    @Test
    fun `zero is immediate and duplicate zero is ignored`() {
        val clock = FakeClock()
        val throttler = AudioLevelThrottler(intervalMillis = 50, clockMillis = clock::now)

        assertEquals(0.7f, throttler.update(0.7f))
        clock.advance(1)
        assertEquals(0f, throttler.update(0f))
        assertNull(throttler.update(0f))
    }

    @Test
    fun `reset allows the next level through immediately`() {
        val clock = FakeClock()
        val throttler = AudioLevelThrottler(intervalMillis = 50, clockMillis = clock::now)

        assertEquals(0.5f, throttler.update(0.5f))
        throttler.reset()

        assertEquals(0.5f, throttler.update(0.5f))
    }

    private class FakeClock {
        private var time = 0L

        fun now(): Long = time

        fun advance(millis: Long) {
            time += millis
        }
    }
}
