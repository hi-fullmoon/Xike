package com.xike.app

internal data class VoiceWaveformTimeline(
    val values: List<Float?> = List(256) { 0f },
    val lastSlot: Long = 0L,
) {
    fun isSampleDue(durationMillis: Long): Boolean = slotAt(durationMillis) > lastSlot

    fun append(durationMillis: Long, amplitude: Float): VoiceWaveformTimeline {
        val slot = slotAt(durationMillis)
        val passed = slot - lastSlot
        if (passed <= 0L) return this
        // An unread interval is a gap, never an invented silent or repeated sample.
        val missing = (passed - 1L).coerceAtMost(values.size - 1L).toInt()
        val previous = if (passed == 1L) values.lastOrNull() ?: 0f else 0f
        val target = amplitude.coerceIn(0f, 1f)
        val response = if (target > previous) 0.72f else 0.48f
        val smoothed = previous + (target - previous) * response
        return VoiceWaveformTimeline(
            values.drop(missing + 1) + List<Float?>(missing) { null } + smoothed,
            slot,
        )
    }

    fun offset(durationMillis: Long): Float {
        val scaled = scaledDuration(durationMillis)
        val whole = scaled / SLOT_DURATION_UNITS - lastSlot
        val fraction = (scaled % SLOT_DURATION_UNITS).toFloat() / SLOT_DURATION_UNITS
        return (whole.toFloat() + fraction).coerceAtLeast(0f)
    }

    fun nextSampleDelay(durationMillis: Long): Long =
        (((lastSlot + 1L) * SLOT_DURATION_UNITS + SPEED_UNITS - 1L) / SPEED_UNITS -
            durationMillis).coerceAtLeast(1L)

    private fun slotAt(durationMillis: Long): Long = scaledDuration(durationMillis) / SLOT_DURATION_UNITS

    private fun scaledDuration(durationMillis: Long): Long = durationMillis.coerceAtLeast(0L) * SPEED_UNITS

    private companion object {
        // 1.4 / 75 milliseconds equals exactly 7 / 375.
        const val SPEED_UNITS = 7L
        const val SLOT_DURATION_UNITS = 375L
    }
}
