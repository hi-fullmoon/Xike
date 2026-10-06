package com.xike.app

// Saved entries keep their original labels; only selection, search, and insights use these groups.
private val mergedTopics = mapOf(
    "社交" to "关系",
    "运动" to "身体",
    "创作" to "兴趣",
)

private val englishTopicKeys = mapOf(
    "work" to "工作",
    "learning" to "学习",
    "relationships" to "关系",
    "family" to "家庭",
    "body" to "身体",
    "sleep" to "睡眠",
    "food" to "饮食",
    "money" to "金钱",
    "self" to "自我",
    "interests" to "兴趣",
    "travel" to "出行",
    "home" to "居住",
    "other" to "其他",
    "social" to "关系",
    "exercise" to "身体",
    "creating" to "兴趣",
)

/** Search translated labels alongside the original term without rewriting notes or tag keys. */
internal fun searchTopicLabels(term: String): List<String> {
    val canonical = englishTopicKeys[term.lowercase(java.util.Locale.ROOT)] ?: term
    return (listOf(term) + expandedTopicLabels(listOf(canonical))).distinct()
}

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
