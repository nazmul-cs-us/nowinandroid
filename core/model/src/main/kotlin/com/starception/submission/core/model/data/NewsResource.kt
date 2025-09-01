/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.core.model.data

import kotlinx.datetime.Instant

/**
 * NEWS RESOURCE MODEL: Complete representation of a news article
 * 
 * This data class represents a news article with all its associated metadata,
 * content, and relationships. It serves as the core content model throughout
 * the app's data and UI layers.
 * 
 * CONTENT STRUCTURE:
 * - Basic metadata: ID, title, publish date
 * - Content: Full article text and external URL
 * - Media: Optional header image
 * - Classification: Type and associated topics
 * 
 * DATA SOURCES:
 * - Network API: Fetched from remote content management system
 * - Local database: Cached for offline access
 * - Demo data: Static content for development/testing
 * 
 * UI INTEGRATION:
 * - Used in article lists, detail views, search results
 * - Supports bookmarking and read status tracking
 * - Enables content filtering by topics
 * 
 * IMMUTABILITY:
 * - Immutable data class for thread-safe operations
 * - Changes require creating new instances
 * - Safe to share across multiple UI components
 * 
 * @param id Unique identifier for the news article
 * @param title Display title for the article
 * @param content Full article text content
 * @param url External URL to the full article (for web view)
 * @param headerImageUrl Optional URL to article's featured image
 * @param publishDate When the article was published (as Instant for timezone handling)
 * @param type Content type classification (e.g., "article", "video", "podcast")
 * @param topics List of topics this article covers (for categorization and filtering)
 */
data class NewsResource(
    val id: String,
    val title: String,
    val content: String,
    val url: String,
    val headerImageUrl: String?,
    val publishDate: Instant,
    val type: String,
    val topics: List<Topic>,
)
