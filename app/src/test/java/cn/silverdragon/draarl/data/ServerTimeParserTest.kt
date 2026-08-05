package cn.silverdragon.draarl.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ServerTimeParserTest {
    @Test
    fun `parses rfc3339 timestamps with nanoseconds`() {
        assertEquals(
            ServerTimeParser.parseMillis("2026-08-05T12:00:00.123Z"),
            ServerTimeParser.parseMillis("2026-08-05T12:00:00.123456789Z"),
        )
    }

    @Test
    fun `keeps legacy communication record timestamps compatible`() {
        assertNotNull(ServerTimeParser.parseMillis("2026-08-05 12:00:00"))
        assertNull(ServerTimeParser.parseMillis("not-a-time"))
    }
}
