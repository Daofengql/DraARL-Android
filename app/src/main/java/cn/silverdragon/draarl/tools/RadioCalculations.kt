@file:Suppress("MagicNumber")

package cn.silverdragon.draarl.tools

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

data class DistanceConversion(val kilometers: Double, val miles: Double, val nauticalMiles: Double)

enum class DistanceUnit { KILOMETERS, MILES, NAUTICAL_MILES }

object RadioConversions {
    fun distance(value: Double, unit: DistanceUnit): DistanceConversion {
        require(value.isFinite()) { "距离必须是有限数值" }
        require(value in 0.0..MAX_DISTANCE_INPUT) { "距离需在 0 到 1e12 之间" }
        val kilometers = when (unit) {
            DistanceUnit.KILOMETERS -> value
            DistanceUnit.MILES -> value * KILOMETERS_PER_MILE
            DistanceUnit.NAUTICAL_MILES -> value * KILOMETERS_PER_NAUTICAL_MILE
        }
        return DistanceConversion(
            kilometers = kilometers,
            miles = kilometers / KILOMETERS_PER_MILE,
            nauticalMiles = kilometers / KILOMETERS_PER_NAUTICAL_MILE
        )
    }

    fun signal(value: Double, unit: SignalUnit, impedanceOhms: Double = 50.0): SignalConversion {
        require(value.isFinite()) { "输入值必须是有限数值" }
        require(impedanceOhms.isFinite() && impedanceOhms in MIN_IMPEDANCE..MAX_IMPEDANCE) {
            "阻抗需在 0.1 到 1e6 Ω 之间"
        }
        val watts = when (unit) {
            SignalUnit.WATTS -> value.also {
                require(it in MIN_LINEAR_INPUT..MAX_WATTS) { "功率需在 1e-30 到 1e12 W 之间" }
            }

            SignalUnit.MILLIWATTS -> (value / 1_000.0).also {
                require(value in MIN_LINEAR_INPUT..MAX_MILLIWATTS) { "功率需在 1e-30 到 1e15 mW 之间" }
            }

            SignalUnit.DBM -> {
                require(value in MIN_DBM..MAX_DBM) { "dBm 需在 -300 到 300 之间" }
                10.0.pow(value / 10.0) / 1_000.0
            }

            SignalUnit.MICROVOLTS -> {
                require(value in MIN_LINEAR_INPUT..MAX_MICROVOLTS) { "电压需在 1e-30 到 1e15 µV 之间" }
                (value / 1_000_000.0).pow(2) / impedanceOhms
            }

            SignalUnit.DBUV -> {
                require(value in MIN_DBUV..MAX_DBUV) { "dBµV 需在 -200 到 400 之间" }
                val microvolts = 10.0.pow(value / 20.0)
                (microvolts / 1_000_000.0).pow(2) / impedanceOhms
            }
        }
        require(watts.isFinite() && watts > 0.0) { "换算结果超出可表示范围" }
        val milliwatts = watts * 1_000.0
        val dbm = 10.0 * log10(milliwatts)
        val microvolts = sqrt(watts * impedanceOhms) * 1_000_000.0
        return SignalConversion(
            watts = watts,
            milliwatts = milliwatts,
            dbm = dbm,
            microvolts = microvolts,
            dbuv = 20.0 * log10(microvolts),
            impedanceOhms = impedanceOhms
        )
    }

    private const val KILOMETERS_PER_MILE = 1.609344
    private const val KILOMETERS_PER_NAUTICAL_MILE = 1.852
    private const val MAX_DISTANCE_INPUT = 1e12
    private const val MIN_IMPEDANCE = 0.1
    private const val MAX_IMPEDANCE = 1e6
    private const val MIN_LINEAR_INPUT = 1e-30
    private const val MAX_WATTS = 1e12
    private const val MAX_MILLIWATTS = 1e15
    private const val MAX_MICROVOLTS = 1e15
    private const val MIN_DBM = -300.0
    private const val MAX_DBM = 300.0
    private const val MIN_DBUV = -200.0
    private const val MAX_DBUV = 400.0
}

enum class SignalUnit { WATTS, MILLIWATTS, DBM, MICROVOLTS, DBUV }

data class SignalConversion(
    val watts: Double,
    val milliwatts: Double,
    val dbm: Double,
    val microvolts: Double,
    val dbuv: Double,
    val impedanceOhms: Double
)

data class FeedlineResult(
    val cableLossDb: Double,
    val totalLossDb: Double,
    val outputPowerWatts: Double,
    val efficiencyPercent: Double
)

data class FeedlinePreset(val name: String, val attenuationPoints: List<Pair<Double, Double>>) {
    fun lossDbPer100Meters(frequencyMHz: Double): Double {
        require(frequencyMHz.isFinite() && frequencyMHz > 0.0) { "频率必须是大于 0 的有限数值" }
        val sorted = attenuationPoints.sortedBy { it.first }
        return when {
            frequencyMHz <= sorted.first().first -> sorted.first().second

            frequencyMHz >= sorted.last().first -> sorted.last().second

            else -> {
                val upperIndex = sorted.indexOfFirst { it.first >= frequencyMHz }
                val lower = sorted[upperIndex - 1]
                val upper = sorted[upperIndex]
                val fraction = ln(frequencyMHz / lower.first) / ln(upper.first / lower.first)
                lower.second + (upper.second - lower.second) * fraction
            }
        }
    }
}

object FeedlinePresets {
    val items = listOf(
        FeedlinePreset("RG-58", listOf(10.0 to 3.8, 100.0 to 11.9, 400.0 to 25.0, 1_000.0 to 43.0)),
        FeedlinePreset("RG-213", listOf(10.0 to 2.0, 100.0 to 6.7, 400.0 to 14.4, 1_000.0 to 25.5)),
        FeedlinePreset("LMR-400", listOf(10.0 to 1.3, 100.0 to 4.3, 400.0 to 8.9, 1_000.0 to 14.8)),
        FeedlinePreset("1/2 英寸馈线", listOf(10.0 to 0.7, 100.0 to 2.3, 400.0 to 4.8, 1_000.0 to 8.1))
    )
}

object FeedlineCalculator {
    fun calculate(
        lengthMeters: Double,
        lossDbPer100Meters: Double,
        inputPowerWatts: Double,
        connectorLossDb: Double
    ): FeedlineResult {
        require(lengthMeters.isFinite() && lengthMeters >= 0.0) { "馈线长度必须是非负有限数值" }
        require(lossDbPer100Meters.isFinite() && lossDbPer100Meters >= 0.0) {
            "馈线损耗必须是非负有限数值"
        }
        require(inputPowerWatts.isFinite() && inputPowerWatts > 0.0) {
            "输入功率必须是大于 0 的有限数值"
        }
        require(connectorLossDb.isFinite() && connectorLossDb >= 0.0) {
            "接头损耗必须是非负有限数值"
        }
        val cableLoss = lengthMeters / 100.0 * lossDbPer100Meters
        val totalLoss = cableLoss + connectorLossDb
        val efficiency = 10.0.pow(-totalLoss / 10.0)
        return FeedlineResult(
            cableLossDb = cableLoss,
            totalLossDb = totalLoss,
            outputPowerWatts = inputPowerWatts * efficiency,
            efficiencyPercent = efficiency * 100.0
        )
    }
}

data class FreeSpacePathLossResult(val pathLossDb: Double, val wavelengthMeters: Double)

object FreeSpacePathLossCalculator {
    fun calculate(distanceKilometers: Double, frequencyMHz: Double): FreeSpacePathLossResult {
        require(distanceKilometers.isFinite() && distanceKilometers in MIN_DISTANCE_KM..MAX_DISTANCE_KM) {
            "距离需在 1e-6 到 1e12 km 之间"
        }
        require(frequencyMHz.isFinite() && frequencyMHz in MIN_FREQUENCY_MHZ..MAX_FREQUENCY_MHZ) {
            "频率需在 1e-6 到 1e6 MHz 之间"
        }
        val wavelength = SPEED_OF_LIGHT_METERS_PER_SECOND / (frequencyMHz * HERTZ_PER_MHZ)
        val pathLoss = 20.0 * log10(
            4.0 * PI * distanceKilometers * METERS_PER_KILOMETER / wavelength
        )
        return FreeSpacePathLossResult(pathLossDb = pathLoss, wavelengthMeters = wavelength)
    }

    private const val SPEED_OF_LIGHT_METERS_PER_SECOND = 299_792_458.0
    private const val HERTZ_PER_MHZ = 1_000_000.0
    private const val METERS_PER_KILOMETER = 1_000.0
    private const val MIN_DISTANCE_KM = 1e-6
    private const val MAX_DISTANCE_KM = 1e12
    private const val MIN_FREQUENCY_MHZ = 1e-6
    private const val MAX_FREQUENCY_MHZ = 1e6
}

enum class AntennaGainUnit { DBI, DBD }

data class EffectiveRadiatedPowerResult(
    val transmitterPowerDbm: Double,
    val antennaGainDbi: Double,
    val eirpDbm: Double,
    val eirpWatts: Double,
    val erpDbm: Double,
    val erpWatts: Double
)

object EffectiveRadiatedPowerCalculator {
    fun calculate(
        transmitterPowerWatts: Double,
        feedlineLossDb: Double,
        antennaGainDb: Double,
        gainUnit: AntennaGainUnit
    ): EffectiveRadiatedPowerResult {
        require(
            transmitterPowerWatts.isFinite() && transmitterPowerWatts in MIN_POWER_WATTS..MAX_POWER_WATTS
        ) { "发射功率需在 1e-30 到 1e12 W 之间" }
        require(feedlineLossDb.isFinite() && feedlineLossDb in 0.0..MAX_FEEDLINE_LOSS_DB) {
            "馈线损耗需在 0 到 300 dB 之间"
        }
        require(antennaGainDb.isFinite() && antennaGainDb in MIN_GAIN_DB..MAX_GAIN_DB) {
            "天线增益需在 -100 到 100 dB 之间"
        }
        val transmitterDbm = 10.0 * log10(transmitterPowerWatts * MILLIWATTS_PER_WATT)
        val gainDbi = antennaGainDb + if (gainUnit == AntennaGainUnit.DBD) DBD_TO_DBI else 0.0
        val eirpDbm = transmitterDbm - feedlineLossDb + gainDbi
        val erpDbm = eirpDbm - DBD_TO_DBI
        return EffectiveRadiatedPowerResult(
            transmitterPowerDbm = transmitterDbm,
            antennaGainDbi = gainDbi,
            eirpDbm = eirpDbm,
            eirpWatts = dbmToWatts(eirpDbm),
            erpDbm = erpDbm,
            erpWatts = dbmToWatts(erpDbm)
        )
    }

    private fun dbmToWatts(dbm: Double): Double = 10.0.pow((dbm - DBM_PER_WATT) / DECIBELS_PER_DECADE)

    private const val MIN_POWER_WATTS = 1e-30
    private const val MAX_POWER_WATTS = 1e12
    private const val MAX_FEEDLINE_LOSS_DB = 300.0
    private const val MIN_GAIN_DB = -100.0
    private const val MAX_GAIN_DB = 100.0
    private const val MILLIWATTS_PER_WATT = 1_000.0
    private const val DBM_PER_WATT = 30.0
    private const val DECIBELS_PER_DECADE = 10.0
    private const val DBD_TO_DBI = 2.15
}

enum class AntennaType(val label: String, val wavelengthFraction: Double) {
    QUARTER_WAVE_VERTICAL("1/4 波长垂直", 0.25),
    HALF_WAVE_DIPOLE("1/2 波长偶极", 0.5),
    FIVE_EIGHTHS_VERTICAL("5/8 波长垂直", 0.625),
    J_POLE("J 型天线", 0.75)
}

data class AntennaLengthResult(
    val wavelengthMeters: Double,
    val totalLengthMeters: Double,
    val elementLengthMeters: Double
)

object AntennaLengthCalculator {
    fun calculate(
        frequencyMHz: Double,
        antennaType: AntennaType,
        shorteningFactor: Double = 1.0
    ): AntennaLengthResult {
        require(frequencyMHz.isFinite() && frequencyMHz in MIN_FREQUENCY_MHZ..MAX_FREQUENCY_MHZ) {
            "频率需在 0.1 到 100000 MHz 之间"
        }
        require(shorteningFactor.isFinite() && shorteningFactor in MIN_SHORTENING_FACTOR..MAX_SHORTENING_FACTOR) {
            "缩短系数需在 0.5 到 1.2 之间"
        }
        val wavelength = SPEED_OF_LIGHT_METERS_PER_SECOND / (frequencyMHz * HERTZ_PER_MHZ)
        val total = wavelength * antennaType.wavelengthFraction * shorteningFactor
        val element = if (antennaType == AntennaType.HALF_WAVE_DIPOLE) total / 2.0 else total
        return AntennaLengthResult(wavelength, total, element)
    }

    private const val SPEED_OF_LIGHT_METERS_PER_SECOND = 299_792_458.0
    private const val HERTZ_PER_MHZ = 1_000_000.0
    private const val MIN_FREQUENCY_MHZ = 0.1
    private const val MAX_FREQUENCY_MHZ = 100_000.0
    private const val MIN_SHORTENING_FACTOR = 0.5
    private const val MAX_SHORTENING_FACTOR = 1.2
}

data class SwrResult(
    val swr: Double,
    val reflectionCoefficient: Double,
    val reflectionPercent: Double,
    val returnLossDb: Double,
    val reflectedPowerPercent: Double,
    val forwardPowerWatts: Double?,
    val reflectedPowerWatts: Double?
)

object SwrCalculator {
    fun fromSwr(swr: Double, forwardPowerWatts: Double? = null): SwrResult {
        require(swr.isFinite() && swr >= 1.0) { "SWR 必须是大于等于 1 的有限数值" }
        val coefficient = (swr - 1.0) / (swr + 1.0)
        return result(coefficient, forwardPowerWatts)
    }

    fun fromPower(forwardPowerWatts: Double, reflectedPowerWatts: Double): SwrResult {
        require(forwardPowerWatts.isFinite() && forwardPowerWatts > 0.0) { "前向功率必须大于 0" }
        require(reflectedPowerWatts.isFinite() && reflectedPowerWatts >= 0.0) { "反射功率不能小于 0" }
        require(reflectedPowerWatts <= forwardPowerWatts) { "反射功率不能大于前向功率" }
        return result(sqrt(reflectedPowerWatts / forwardPowerWatts), forwardPowerWatts)
    }

    private fun result(coefficient: Double, forwardPowerWatts: Double?): SwrResult {
        val normalizedForward = forwardPowerWatts?.also {
            require(it.isFinite() && it > 0.0) { "前向功率必须大于 0" }
        }
        val reflectedPower = normalizedForward?.let { it * coefficient * coefficient }
        val returnLoss = if (coefficient == 0.0) Double.POSITIVE_INFINITY else -20.0 * log10(coefficient)
        return SwrResult(
            swr = (1.0 + coefficient) / (1.0 - coefficient).coerceAtLeast(MIN_DENOMINATOR),
            reflectionCoefficient = coefficient,
            reflectionPercent = coefficient * 100.0,
            returnLossDb = returnLoss,
            reflectedPowerPercent = coefficient * coefficient * 100.0,
            forwardPowerWatts = normalizedForward,
            reflectedPowerWatts = reflectedPower
        )
    }

    private const val MIN_DENOMINATOR = 1e-12
}

enum class RepeaterOffsetDirection(val label: String, val multiplier: Double) {
    POSITIVE("+", 1.0),
    NEGATIVE("-", -1.0),
    SIMPLEX("同频", 0.0)
}

data class RepeaterPlanResult(
    val receiveFrequencyMHz: Double,
    val transmitFrequencyMHz: Double,
    val offsetMHz: Double,
    val tone: String,
    val channelName: String
)

object RepeaterPlanner {
    fun calculate(
        receiveFrequencyMHz: Double,
        offsetMHz: Double,
        direction: RepeaterOffsetDirection,
        tone: String,
        channelName: String
    ): RepeaterPlanResult {
        require(receiveFrequencyMHz.isFinite() && receiveFrequencyMHz > 0.0) { "接收频率必须大于 0" }
        require(offsetMHz.isFinite() && offsetMHz >= 0.0) { "下差必须是非负数" }
        require(tone.length <= MAX_TONE_LENGTH && tone.none(Char::isISOControl)) { "哑音格式不正确" }
        require(channelName.length <= MAX_CHANNEL_NAME_LENGTH) { "频道名称过长" }
        val signedOffset = offsetMHz * direction.multiplier
        require(receiveFrequencyMHz + signedOffset > 0.0) { "发射频率必须大于 0" }
        return RepeaterPlanResult(
            receiveFrequencyMHz = receiveFrequencyMHz,
            transmitFrequencyMHz = receiveFrequencyMHz + signedOffset,
            offsetMHz = signedOffset,
            tone = tone.trim().uppercase(),
            channelName = channelName.trim()
        )
    }

    private const val MAX_TONE_LENGTH = 12
    private const val MAX_CHANNEL_NAME_LENGTH = 40
}
