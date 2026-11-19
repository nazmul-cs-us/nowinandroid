package com.starception.submission.feature.ai

/** Reads the release identity from catalog filenames, independent of multipart delivery. */
fun modelVersion(category: String, assetKeys: Iterable<String>): String? {
    if (!category.startsWith("model_")) return null
    val pattern = Regex("""(?:^|/)[^/]+-v([0-9]+(?:\.[0-9]+)*)(?=[-.]|$)""")
    // A category containing multiple releases has no single unambiguous version.
    return assetKeys.mapNotNull { key -> pattern.find(key)?.groupValues?.get(1) }
        .distinct().singleOrNull()?.let { "v$it" }
}
