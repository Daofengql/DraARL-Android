package cn.silverdragon.draarl.tools

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

object RadioNumberFormatter {
    fun format(value: Double?, maxFractionDigits: Int = DEFAULT_MAX_FRACTION_DIGITS): String = when {
        value == null || !value.isFinite() -> "-"
        value == 0.0 -> "0"
        else -> formatFiniteValue(value, maxFractionDigits)
    }

    private fun formatFiniteValue(value: Double, maxFractionDigits: Int): String {
        val magnitude = abs(value)
        val scientificLowerBound = 10.0.pow(-maxFractionDigits)
        if (magnitude >= SCIENTIFIC_UPPER_BOUND || magnitude < scientificLowerBound) {
            return formatScientific(value)
        }
        val integerDigits = if (magnitude >= 1.0) floor(log10(magnitude)).toInt() + 1 else 0
        val fractionDigits = (SIGNIFICANT_DIGITS - integerDigits).coerceIn(0, maxFractionDigits)
        return String.format(Locale.US, "%.${fractionDigits}f", value).trimZeros()
    }

    fun withUnit(value: Double?, unit: String, maxFractionDigits: Int = DEFAULT_MAX_FRACTION_DIGITS): String =
        if (value == null || !value.isFinite()) "-" else "${format(value, maxFractionDigits)} $unit"

    private fun formatScientific(value: Double): String {
        val parts = String.format(Locale.US, "%.4e", value).split('e')
        val mantissa = parts[0].trimZeros()
        val exponent = parts[1].toInt()
        return "$mantissa" + "e" + if (exponent >= 0) "+$exponent" else exponent.toString()
    }

    private fun String.trimZeros(): String = if (contains('.')) trimEnd('0').trimEnd('.') else this

    private const val DEFAULT_MAX_FRACTION_DIGITS = 6
    private const val SIGNIFICANT_DIGITS = 7
    private const val SCIENTIFIC_UPPER_BOUND = 1e9
}
