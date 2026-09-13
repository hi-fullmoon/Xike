package com.xike.app

// Saved entries keep their original labels; only selection, search, and insights use these groups.
private val mergedTopics = mapOf(
    "社交" to "关系",
    "运动" to "身体",
    "创作" to "兴趣",
)

internal fun canonicalTopic(topic: String): String = mergedTopics[topic] ?: topic

internal fun Collection<String>.canonicalTopics(): List<String> =
    map(::canonicalTopic).distinct()

internal fun Collection<String>.containsTopic(topic: String): Boolean =
    any { canonicalTopic(it) == canonicalTopic(topic) }

internal fun toggleTopic(topics: Collection<String>, topic: String): List<String> =
    if (topics.containsTopic(topic)) {
        topics.filterNot { canonicalTopic(it) == canonicalTopic(topic) }
    } else {
        topics.toList() + canonicalTopic(topic)
    }

internal fun expandedTopicLabels(topics: Collection<String>): List<String> {
    val currentTopics = topics.canonicalTopics()
    return currentTopics + mergedTopics.filterValues { it in currentTopics }.keys
}
