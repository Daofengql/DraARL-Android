package cn.silverdragon.draarl.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class MorseCodecTest {
    @Test
    fun `encodes and decodes text using word separators`() {
        val encoded = MorseCodec.encode("CQ DE BG7")
        assertEquals("-.-. --.- / -.. . / -... --. --...", encoded)
        assertEquals("CQ DE BG7", MorseCodec.decode(encoded))
    }

    @Test
    fun `normalization matches tone generator`() {
        assertEquals("CQ TEST?", MorseCodec.normalizeText(" cq 中文 test? "))
        assertEquals(MorseCodec.normalizeText("cq test?"), CwToneGenerator.normalize("cq test?"))
    }
}
