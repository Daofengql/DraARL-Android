package cn.silverdragon.draarl.radio

internal fun interface RadioClock {
    fun nowMillis(): Long
}

internal object SystemRadioClock : RadioClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
