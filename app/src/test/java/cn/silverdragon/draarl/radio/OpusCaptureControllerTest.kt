package cn.silverdragon.draarl.radio

import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OpusCaptureControllerTest {
    @Test
    fun `release is idempotent and rejects later capture callbacks`() {
        val packets = AtomicInteger(0)
        val levels = AtomicInteger(0)
        val errors = AtomicInteger(0)
        val controller = OpusCaptureController()

        controller.release()
        controller.release()

        assertFalse(
            controller.start(
                onPacket = { packets.incrementAndGet() },
                onLevel = { levels.incrementAndGet() },
                onError = { errors.incrementAndGet() }
            )
        )
        assertFalse(controller.isCapturing)
        assertEquals(0, packets.get())
        assertEquals(0, levels.get())
        assertEquals(0, errors.get())
    }
}
