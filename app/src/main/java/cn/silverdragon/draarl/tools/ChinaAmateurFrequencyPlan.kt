package cn.silverdragon.draarl.tools

data class AmateurBand(val range: String, val lowerMHz: Double, val upperMHz: Double, val secondary: Boolean) {
    val status: String
        get() = if (secondary) "次要业务" else "主要业务"

    fun contains(frequencyMHz: Double): Boolean = frequencyMHz in lowerMHz..upperMHz
}

object ChinaAmateurFrequencyPlan {
    val bands = listOf(
        AmateurBand("135.7-137.8 kHz", 0.1357, 0.1378, true),
        AmateurBand("1800-2000 kHz", 1.8, 2.0, false),
        AmateurBand("3500-3900 kHz", 3.5, 3.9, false),
        AmateurBand("5351.5-5366.5 kHz", 5.3515, 5.3665, true),
        AmateurBand("7000-7100 kHz", 7.0, 7.1, false),
        AmateurBand("7100-7200 kHz", 7.1, 7.2, false),
        AmateurBand("10100-10150 kHz", 10.1, 10.15, true),
        AmateurBand("14000-14250 kHz", 14.0, 14.25, false),
        AmateurBand("14250-14350 kHz", 14.25, 14.35, false),
        AmateurBand("18068-18168 kHz", 18.068, 18.168, false),
        AmateurBand("21000-21450 kHz", 21.0, 21.45, false),
        AmateurBand("24890-24990 kHz", 24.89, 24.99, false),
        AmateurBand("28-29.7 MHz", 28.0, 29.7, false),
        AmateurBand("50-54 MHz", 50.0, 54.0, false),
        AmateurBand("144-146 MHz", 144.0, 146.0, false),
        AmateurBand("146-148 MHz", 146.0, 148.0, false),
        AmateurBand("430-440 MHz", 430.0, 440.0, true),
        AmateurBand("1240-1260 MHz", 1_240.0, 1_260.0, true),
        AmateurBand("1260-1300 MHz", 1_260.0, 1_300.0, true),
        AmateurBand("2300-2450 MHz", 2_300.0, 2_450.0, true),
        AmateurBand("3300-3400 MHz", 3_300.0, 3_400.0, true),
        AmateurBand("3400-3500 MHz", 3_400.0, 3_500.0, true),
        AmateurBand("5650-5725 MHz", 5_650.0, 5_725.0, true),
        AmateurBand("5725-5830 MHz", 5_725.0, 5_830.0, true),
        AmateurBand("5830-5850 MHz", 5_830.0, 5_850.0, true),
        AmateurBand("10-10.4 GHz", 10_000.0, 10_400.0, true),
        AmateurBand("10.4-10.45 GHz", 10_400.0, 10_450.0, true),
        AmateurBand("10.45-10.5 GHz", 10_450.0, 10_500.0, true),
        AmateurBand("24-24.05 GHz", 24_000.0, 24_050.0, false),
        AmateurBand("24.05-24.25 GHz", 24_050.0, 24_250.0, true),
        AmateurBand("47-47.2 GHz", 47_000.0, 47_200.0, false),
        AmateurBand("76-77.5 GHz", 76_000.0, 77_500.0, true),
        AmateurBand("77.5-78 GHz", 77_500.0, 78_000.0, false),
        AmateurBand("78-79 GHz", 78_000.0, 79_000.0, true),
        AmateurBand("79-81 GHz", 79_000.0, 81_000.0, true),
        AmateurBand("81-81.5 GHz", 81_000.0, 81_500.0, true),
        AmateurBand("122.25-123 GHz", 122_250.0, 123_000.0, true),
        AmateurBand("134-136 GHz", 134_000.0, 136_000.0, false),
        AmateurBand("136-141 GHz", 136_000.0, 141_000.0, true),
        AmateurBand("241-248 GHz", 241_000.0, 248_000.0, true),
        AmateurBand("248-250 GHz", 248_000.0, 250_000.0, false)
    )

    fun find(frequencyMHz: Double): List<AmateurBand> = bands.filter { it.contains(frequencyMHz) }
}
