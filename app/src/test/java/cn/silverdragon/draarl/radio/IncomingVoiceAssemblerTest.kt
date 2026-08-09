package cn.silverdragon.draarl.radio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingVoiceAssemblerTest {
    @Test
    fun `active stream plays immediately and pending backlog advances after timeout`() {
        val assembler = assembler()
        val first = assembler.accept(packet(A, FIRST_PACKET, receivedAt = 1_000L))
        val second = assembler.accept(packet(B, SECOND_PACKET, receivedAt = 1_050L))
        assembler.accept(packet(B, THIRD_PACKET, receivedAt = 1_100L))

        assertTrue(first.startsPlaybackSession)
        assertArrayEquals(FIRST_PACKET, (first.actions.single() as IncomingVoiceAction.Play).payload)
        assertFalse(second.startsPlaybackSession)
        assertTrue(second.actions.isEmpty())

        val expired = assembler.expire(now = 1_701L)
        val completed = expired.actions[0] as IncomingVoiceAction.Complete
        val playback = expired.actions[1] as IncomingVoiceAction.Play
        assertEquals(A.identity, completed.voice.source.identity)
        assertArrayEquals(SECOND_PACKET + THIRD_PACKET, playback.payload)
        assertEquals(B.speaker, expired.activeSpeaker)
    }

    @Test
    fun `expired pending streams play backlog before completion`() {
        val assembler = assembler()
        assembler.accept(packet(A, FIRST_PACKET, receivedAt = 1_000L))
        assembler.accept(packet(B, SECOND_PACKET, receivedAt = 1_000L))

        val result = assembler.expire(now = 1_701L)

        assertEquals(3, result.actions.size)
        assertTrue(result.actions[0] is IncomingVoiceAction.Complete)
        assertTrue(result.actions[1] is IncomingVoiceAction.Play)
        assertTrue(result.actions[2] is IncomingVoiceAction.Complete)
        assertNull(result.activeSpeaker)
        assertFalse(assembler.hasStreams())
    }

    @Test
    fun `capacity evicts oldest pending stream before active stream`() {
        val assembler = assembler(maxStreams = 2)
        assembler.accept(packet(A, FIRST_PACKET, receivedAt = 1_000L))
        assembler.accept(packet(B, SECOND_PACKET, receivedAt = 1_050L))

        val result = assembler.accept(packet(C, THIRD_PACKET, receivedAt = 1_100L))

        val evicted = (result.actions.single() as IncomingVoiceAction.Complete).voice
        assertEquals(B.identity, evicted.source.identity)
        assertFalse(result.startsPlaybackSession)
        assertEquals(A.speaker, assembler.activeSpeaker())
        assertEquals(listOf(A.identity, C.identity), assembler.finishAll().map { it.source.identity })
    }

    @Test
    fun `capacity replacement starts new stream when active stream is evicted`() {
        val assembler = assembler(maxStreams = 1)
        assembler.accept(packet(A, FIRST_PACKET, receivedAt = 1_000L))

        val result = assembler.accept(packet(B, SECOND_PACKET, receivedAt = 1_050L))

        assertTrue(result.startsPlaybackSession)
        assertTrue(result.actions[0] is IncomingVoiceAction.Complete)
        assertTrue(result.actions[1] is IncomingVoiceAction.Play)
        assertEquals(B.speaker, assembler.activeSpeaker())
    }

    @Test
    fun `older packet timestamp preserves arrival order without moving deadline backward`() {
        val assembler = assembler()
        assembler.accept(packet(A, FIRST_PACKET, receivedAt = 1_000L))
        assembler.accept(packet(A, SECOND_PACKET, receivedAt = 900L))

        assertTrue(assembler.expire(now = 1_700L).actions.isEmpty())
        val completed = assembler.expire(now = 1_701L).actions.single() as IncomingVoiceAction.Complete

        assertEquals(1_000L, completed.voice.startedAt)
        assertEquals(1_000L, completed.voice.lastPacketAt)
        assertArrayEquals(FIRST_PACKET + SECOND_PACKET, completed.voice.networkPayload)
    }

    @Test
    fun `normalized identity shares one stream and payload limit does not block live playback`() {
        val assembler = assembler(maxPayloadBytes = 3)
        val normalized = A.copy(callsign = " bg7aaa ")
        assembler.accept(packet(A, FIRST_PACKET, receivedAt = 1_000L))

        val result = assembler.accept(packet(normalized, SECOND_PACKET, receivedAt = 1_100L))
        val completed = assembler.finishAll().single()

        assertTrue(result.actions.single() is IncomingVoiceAction.Play)
        assertArrayEquals(FIRST_PACKET, completed.networkPayload)
    }

    private fun assembler(maxStreams: Int = 4, maxPayloadBytes: Int = 1_024): IncomingVoiceAssembler =
        IncomingVoiceAssembler(
            maxStreams = maxStreams,
            maxPayloadBytes = maxPayloadBytes,
            endTimeoutMillis = 700L
        )

    private fun packet(source: IncomingVoiceSource, payload: ByteArray, receivedAt: Long): IncomingVoicePacket =
        IncomingVoicePacket(source, payload, receivedAt)

    private companion object {
        val A = IncomingVoiceSource("alpha", "BG7AAA", 101, 7)
        val B = IncomingVoiceSource("bravo", "BG7BBB", 102, 7)
        val C = IncomingVoiceSource("charlie", "BG7CCC", 103, 7)
        val FIRST_PACKET = byteArrayOf(1, 2)
        val SECOND_PACKET = byteArrayOf(3, 4)
        val THIRD_PACKET = byteArrayOf(5, 6)
    }
}
