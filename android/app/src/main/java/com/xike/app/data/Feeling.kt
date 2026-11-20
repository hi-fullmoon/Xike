package com.xike.app.data

import com.xike.app.tr
import org.json.JSONArray

enum class Feeling(private val chinese: String, private val english: String) {
    HAPPY("开心", "Happy"),
    PEACEFUL("平静", "Peaceful"),
    RELAXED("放松", "Relaxed"),
    HOPEFUL("期待", "Hopeful"),
    SAD("难过", "Sad"),
    ANXIOUS("焦虑", "Anxious"),
    IRRITATED("烦躁", "Irritated"),
    TIRED("疲惫", "Tired"),
    CONTENT("满足", "Content"),
    GRATEFUL("感激", "Grateful"),
    SAFE("安心", "At ease"),
    EXCITED("兴奋", "Excited"),
    ANGRY("生气", "Angry"),
    HURT("委屈", "Hurt"),
    LONELY("孤独", "Lonely"),
    DISAPPOINTED("失落", "Disappointed"),
    BORED("无聊", "Bored"),
    ENERGETIC("充沛", "Energetic");

    val label: String
        get() = tr(chinese, english)
}

internal fun feelingLabel(key: String): String =
    Feeling.entries.firstOrNull { it.name == key }?.label ?: key

internal fun JSONArray.feelingKeys(): List<String> =
    List(length()) { getString(it) }.filter(String::isNotBlank).distinct()

internal val Mood.overallLabel: String
    get() = Mood.fromName(name, 1).label

internal val JournalEntry.displayMoodLabel: String
    get() = if (moodScaleVersion > 0) mood.overallLabel else mood.label
