package cn.silverdragon.draarl.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioCalculationsTest {
    @Test
    fun `converts distance units`() {
        val result = RadioConversions.distance(1.0, DistanceUnit.MILES)
        assertEquals(1.609344, result.kilometers, 1e-6)
        assertEquals(1.0, result.miles, 1e-6)
    }

    @Test
    fun `converts dbm at 50 ohms`() {
        val result = RadioConversions.signal(0.0, SignalUnit.DBM)
        assertEquals(0.001, result.watts, 1e-9)
        assertEquals(223_606.7977, result.microvolts, 0.01)
        assertEquals(106.9897, result.dbuv, 0.001)
    }

    @Test
    fun `calculates feedline output power`() {
        val result = FeedlineCalculator.calculate(20.0, 10.0, 50.0, 0.5)
        assertEquals(2.5, result.totalLossDb, 1e-9)
        assertEquals(28.117, result.outputPowerWatts, 0.001)
        assertEquals(56.234, result.efficiencyPercent, 0.001)
    }

    @Test
    fun `feedline rejects non finite inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            FeedlineCalculator.calculate(Double.POSITIVE_INFINITY, 1.0, 10.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeedlineCalculator.calculate(1.0, Double.NaN, 10.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeedlineCalculator.calculate(1.0, 1.0, Double.POSITIVE_INFINITY, 0.0)
        }
    }

    @Test
    fun `interpolates preset attenuation by frequency`() {
        val preset = FeedlinePresets.items.first { it.name == "LMR-400" }
        val loss = preset.lossDbPer100Meters(200.0)
        assertTrue(loss in 4.3..8.9)
    }

    @Test
    fun `finds mainland amateur band by frequency`() {
        val matches = ChinaAmateurFrequencyPlan.find(433.5)
        assertEquals(1, matches.size)
        assertTrue(matches.single().secondary)
    }

    @Test
    fun `rejects non finite and out of range conversion input`() {
        assertThrows(IllegalArgumentException::class.java) {
            RadioConversions.distance(Double.POSITIVE_INFINITY, DistanceUnit.KILOMETERS)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RadioConversions.signal(301.0, SignalUnit.DBM)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RadioConversions.signal(1.0, SignalUnit.WATTS, impedanceOhms = 0.0)
        }
    }

    @Test
    fun `calculates free space path loss and wavelength`() {
        val result = FreeSpacePathLossCalculator.calculate(distanceKilometers = 10.0, frequencyMHz = 145.0)
        assertEquals(95.68, result.pathLossDb, 0.02)
        assertEquals(2.0675, result.wavelengthMeters, 0.001)
    }

    @Test
    fun `calculates eirp and erp from antenna system`() {
        val result = EffectiveRadiatedPowerCalculator.calculate(
            transmitterPowerWatts = 10.0,
            feedlineLossDb = 1.0,
            antennaGainDb = 3.0,
            gainUnit = AntennaGainUnit.DBI
        )
        assertEquals(42.0, result.eirpDbm, 1e-9)
        assertEquals(15.8489, result.eirpWatts, 0.001)
        assertEquals(39.85, result.erpDbm, 1e-9)
        assertEquals(9.6605, result.erpWatts, 0.001)
    }

    @Test
    fun `converts dBd gain to dBi for effective radiated power`() {
        val result = EffectiveRadiatedPowerCalculator.calculate(1.0, 0.0, 0.0, AntennaGainUnit.DBD)
        assertEquals(2.15, result.antennaGainDbi, 1e-9)
        assertEquals(1.0, result.erpWatts, 1e-9)
    }

    @Test
    fun `calculates antenna lengths with shortening factor`() {
        val result = AntennaLengthCalculator.calculate(
            frequencyMHz = 145.0,
            antennaType = AntennaType.HALF_WAVE_DIPOLE,
            shorteningFactor = 0.95
        )
        assertEquals(2.0675, result.wavelengthMeters, 0.001)
        assertEquals(0.9819, result.totalLengthMeters, 0.002)
        assertEquals(0.4909, result.elementLengthMeters, 0.002)
    }

    @Test
    fun `calculates swr from power and reports reflected power`() {
        val result = SwrCalculator.fromPower(100.0, 4.0)
        assertEquals(1.5, result.swr, 1e-9)
        assertEquals(20.0, result.reflectionPercent, 1e-9)
        assertEquals(4.0, result.reflectedPowerPercent, 1e-9)
        assertEquals(4.0, result.reflectedPowerWatts ?: 0.0, 1e-9)
    }

    @Test
    fun `plans repeater transmit frequency`() {
        val result = RepeaterPlanner.calculate(
            receiveFrequencyMHz = 145.600,
            offsetMHz = 0.600,
            direction = RepeaterOffsetDirection.NEGATIVE,
            tone = "88.5",
            channelName = "本地中继"
        )
        assertEquals(145.0, result.transmitFrequencyMHz, 1e-9)
        assertEquals(-0.6, result.offsetMHz, 1e-9)
        assertEquals("88.5", result.tone)
    }

    @Test
    fun `formats ordinary and extreme radio values`() {
        assertEquals("1.234568", RadioNumberFormatter.format(1.23456789))
        assertEquals("1000000", RadioNumberFormatter.format(1_000_000.0))
        assertEquals("1.2346e+12", RadioNumberFormatter.format(1.23456789e12))
        assertEquals("1.2346e-9", RadioNumberFormatter.format(1.23456789e-9))
        assertEquals("4e-3 dB", RadioNumberFormatter.withUnit(0.004, "dB", maxFractionDigits = 2))
        assertEquals("-", RadioNumberFormatter.format(Double.NaN))
    }
}
