/*
 * Copyright 2026 Starception
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

package com.starception.submission.widget

import android.content.Context
import android.util.Log
import com.starception.submission.core.duadatabase.DuaCategory
import com.starception.submission.core.duadatabase.DuaRepository
import com.starception.submission.core.hadithdatabase.HadithRepository
import com.starception.submission.core.model.data.BukhariBooks
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import kotlin.random.Random

private const val TAG = "DailyReminder"

/** Sahih Bukhari, as the hadith database layer names it. */
private const val BUKHARI_DB = "sahih_bukhari.db"

/**
 * What the Daily Reminder widget is showing, and where it came from.
 *
 * [target] is the whole point of carrying identity alongside the text: without it the
 * widget can only open the app, and a reminder the user cannot follow up on is a dead end.
 * Null when the text is not something the app has a screen for.
 */
internal data class DailyReminder(
    val key: String,
    val text: String,
    val caption: String,
    val target: WidgetNavigationTarget?,
    /** Descriptive heading shown between the kind chip and body, used for Fortress duas. */
    val contentTitle: String? = null,
    /** Book this came from, for the footer's left corner — "Sahih Bukhari". */
    val sourceName: String? = null,
    /** Where in that book, for the footer's right corner — "#156". */
    val sourceDetail: String? = null,
    /**
     * The Arabic, shown only when the card has room left after the translation.
     *
     * Carried always and used conditionally, because whether it fits is a question about
     * the widget's size, which this layer cannot see.
     */
    val arabic: String? = null,
)

/**
 * Real content for the Daily Reminder widget: a Sahih Bukhari hadith or a Fortress of the
 * Muslim dua, drawn from the same databases the app's own screens read.
 *
 * This replaces a hardcoded list of five strings. Those were written to demonstrate a
 * layout, and they had no source, no reference and nothing to open — the card said
 * "Hadith" above a sentence that existed nowhere else in the app.
 *
 * Selection is by day, not at random, so the reminder is stable for anyone who looks at
 * their home screen twice in a morning; the refresh button advances it deliberately. The
 * day seeds the choice, so two widgets on the same screen agree with each other.
 */
internal object DailyReminderRepository {

    /**
     * The rotation, in order — one hadith, one dua, alternating.
     *
     * It was [HADITH, DUA, HADITH], weighted towards Bukhari because it has far more
     * entries to draw on. In use that was the wrong call: two thirds of refreshes returned
     * another hadith, so pressing refresh to see something different usually did not, and
     * the dua half of the widget looked broken. Strict alternation makes refresh mean
     * "show me the other kind", which is what a person pressing it is asking for.
     */
    private val SEQUENCE = listOf(Kind.HADITH, Kind.DUA)

    private enum class Kind { HADITH, DUA }

    suspend fun load(context: Context, offset: Int): DailyReminder {
        val day = LocalDate.now().toEpochDay().toInt()
        val index = Math.floorMod(day + offset, SEQUENCE.size)
        val seed = day + offset

        val loaded = when (SEQUENCE[index]) {
            Kind.HADITH -> loadHadith(context, seed)
            Kind.DUA -> loadDua(context, seed)
        }
        // A database that will not open must not blank the widget: the fallback still says
        // something true and still opens the app, it just cannot cite a source.
        return loaded ?: FALLBACK
    }

    private suspend fun loadHadith(context: Context, seed: Int): DailyReminder? = try {
        val repository = HadithRepository.getInstance(context)
        val random = Random(seed)
        // Several attempts, because a large share of Bukhari's entries are not readable on
        // their own — see [isSelfContained]. Picking the first number that comes up gave
        // the widget "Narrated Abu at-Tufail: The above mentioned Statement of `Ali.",
        // which is a cross-reference, not a reminder. Bounded so a database of nothing but
        // fragments cannot spin here; it falls through to the dua or the fallback instead.
        // The candidates are drawn up front, so which hadith a given day resolves to is a
        // pure function of the seed. Drawing them inside the loop made the choice depend on
        // how many lookups happened to succeed: a cold database that returned null for an
        // early candidate pushed the selection further down the sequence, so the same day
        // rendered a different hadith on a warm process than on a cold one. The card and
        // the link it carries are built together, but a re-render that silently changed its
        // mind is still a card whose text no longer matches what the user last read.
        //
        // The first few hundred are the well-known ones on intention, faith and prayer,
        // which read better on a home screen than a ruling pulled from the middle of a
        // chapter on inheritance.
        val candidates = List(CANDIDATE_ATTEMPTS) { random.nextInt(1, 300) }

        var found: DailyReminder? = null
        for (number in candidates) {
            val hadith = repository.getHadith(BUKHARI_DB, number)
            // A lookup that fails is a database problem, not a verdict on this hadith.
            // Moving on would make the selection depend on database health; stopping lets
            // the caller fall through to the dua, which is honest and still deterministic.
            if (hadith == null) break
            val fullText = hadith.textPlain?.let(::reflow)?.takeIf { it.isNotBlank() } ?: continue
            if (!isSelfContained(fullText)) continue
            val (narratorTitle, bodyText) = splitNarratorTitle(fullText)

            val collection = hadith.collectionName.takeIf { it.isNotBlank() } ?: "Sahih Bukhari"
            val category = BukhariBooks.findByHadithId(number)?.nameEnglish
            found = DailyReminder(
                key = "hadith-$number",
                // The narrator is the hadith's heading, not the first sentence of its
                // body. Splitting it prevents "Narrated Abu Huraira" from appearing
                // twice while giving Bukhari reminders the same strong hierarchy as a
                // Fortress dua's chapter title.
                text = bodyText,
                // The kind, not the source. The subtitle's job is to say what the reader is
                // looking at before they read it; "Bukhari" answers a question they had not
                // asked yet, and a Fortress chapter title ("Invocation for when you see the
                // first dates of the season") is a whole sentence competing with the one
                // underneath it. The source is still carried in [target], which is where it
                // matters — it is what the tap opens.
                caption = "Hadith",
                contentTitle = narratorTitle,
                sourceName = collection,
                sourceDetail = buildString {
                    append("#$number")
                    if (category != null) append(" · $category")
                },
                // Hadith carry their Arabic too, and a short one leaves the same empty
                // card a short dua does. Same rule decides whether it is shown.
                arabic = hadith.textArabic.let(::reflow).takeIf { it.isNotBlank() },
                target = WidgetNavigationTarget.Hadith(
                    databaseFile = BUKHARI_DB,
                    hadithNumber = number,
                    collectionName = collection,
                ),
            )
            break
        }
        found
    } catch (e: Exception) {
        Log.w(TAG, "Bukhari hadith unavailable for the widget", e)
        null
    }

    /**
     * Reflows text that arrives hard-wrapped.
     *
     * The hadith database stores Bukhari wrapped at roughly sixty characters, with some
     * lines indented, which is how it reads as a book. A widget is a different width from
     * whatever that wrapping assumed, so honouring those breaks produces ragged short
     * lines and stray indents that look like broken formatting rather than a quotation.
     *
     * Single newlines are joined — they are wrapping, not meaning. Blank lines become a
     * single visual break: that still separates a narration chain from what was said,
     * without spending a full empty line in the compact widget.
     */
    private fun reflow(text: String): String = text
        .split(PARAGRAPH_BREAK)
        .joinToString("\n") { paragraph ->
            paragraph
                .split('\n')
                .joinToString(" ") { it.trim() }
                .replace(REPEATED_SPACE, " ")
                .replace(SPACE_BEFORE_PUNCTUATION, "$1")
                .trim()
        }
        .trim()

    private val PARAGRAPH_BREAK = Regex("""\n\s*\n""")
    private val REPEATED_SPACE = Regex("""\s{2,}""")
    private val SPACE_BEFORE_PUNCTUATION = Regex("""\s+([,.;:!?،؛؟])""")

    /**
     * Moves Bukhari's leading "Narrated …:" attribution into the reminder title.
     *
     * The bound avoids treating an unusually long narration chain as a heading. Hadith
     * without the standard opener keep their full text and simply omit the title.
     */
    private fun splitNarratorTitle(text: String): Pair<String?, String> {
        val match = NARRATOR_OPENER.find(text) ?: return null to text
        val title = match.groupValues[1].trim().replaceFirstChar { first ->
            if (first.isLowerCase()) first.titlecase() else first.toString()
        }
        val body = text.substring(match.range.last + 1).trimStart()
        return title to body.takeIf { it.isNotBlank() }.orEmpty()
    }

    private val NARRATOR_OPENER = Regex(
        pattern = """^(Narrated\s+[^:\n]{1,120}):\s*""",
        option = RegexOption.IGNORE_CASE,
    )

    /**
     * Whether a hadith says something on its own.
     *
     * Bukhari's text includes many entries that only make sense beside their neighbours —
     * chains of narration whose body is a pointer ("the same as above", "similar to the
     * previous hadith"), and one-clause continuations of the entry before. On a home screen
     * there is no previous entry, so these read as a bug rather than a reminder.
     *
     * A length floor does most of the work; the phrase list catches the ones that are long
     * enough to pass it but still say nothing without their context.
     */
    private fun isSelfContained(text: String): Boolean {
        // Narration chains are prose overhead, not content: measure what is left after the
        // "Narrated X:" opener, since that alone can carry a fragment past a raw length
        // check.
        val body = text.substringAfter(':', text).trim()
        if (body.length < MIN_BODY_LENGTH) return false
        val lowered = body.lowercase()
        return BACK_REFERENCES.none { it in lowered }
    }

    /** Fragments that point at a neighbouring entry rather than standing alone. */
    private val BACK_REFERENCES = listOf(
        "above mentioned",
        "as above",
        "same as",
        "similar to the previous",
        "see hadith",
        "mentioned above",
        "the same hadith",
        "narrated the same",
    )

    /** Shorter than this, after the narration chain, and there is no reminder in it. */
    private const val MIN_BODY_LENGTH = 80

    /** How many hadith to try before giving up and letting another source take the slot. */
    private const val CANDIDATE_ATTEMPTS = 12

    private suspend fun loadDua(context: Context, seed: Int): DailyReminder? = try {
        val repository = EntryPointAccessors
            .fromApplication(context.applicationContext, DailyReminderEntryPoint::class.java)
            .duaRepository()
        loadSeededDua(repository, seed)
    } catch (e: Exception) {
        Log.w(TAG, "Fortress dua unavailable for the widget", e)
        null
    }

    private suspend fun loadSeededDua(
        repository: DuaRepository,
        seed: Int,
    ): DailyReminder? = run {
        // Seeded, not getRandomDua(). Random selection made the card change its dua on
        // every redraw — a scheduled refresh, a theme change, the launcher re-asking — so
        // the reminder a user had half-read could vanish for reasons they never triggered.
        // It also made the hadith and dua halves behave differently, since the hadith side
        // has always been a pure function of the seed.
        val count = repository.getDuaCount()
        if (count <= 0) return@run null
        val id = Random(seed).nextInt(1, count + 1)
        repository.getDuaById(id)?.let { dua ->
            // The translation, not the Arabic: the widget's typeface is Ubuntu Sans and
            // the card is a few lines tall, neither of which serves an Arabic text well.
            // The detail screen this opens shows the Arabic properly.
            val text = dua.translation
                ?.let(::reflow)
                ?.let(::cleanDuaText)
                ?.takeIf { it.isNotBlank() }
                ?: return@let null
            DailyReminder(
                key = "dua-${dua.id}",
                text = text,
                caption = "Dua",
                contentTitle = dua.chapterTitle
                    .let(::withoutDuaNumber)
                    .takeIf { it.isNotBlank() },
                sourceName = "Fortress of the Muslim",
                // The topic, not the chapter title. A Fortress chapter is named for the
                // occasion in full — "What to say if you see someone afflicted" — which is
                // a sentence, not a reference, and it ellipsised in the corner it sits in.
                // The category the app already groups these by ("Health & Sickness") is
                // short, stable and is the same label the user sees elsewhere in the app.
                sourceDetail = topicFor(dua.chapterTitle),
                arabic = dua.arabic?.let(::reflow)?.takeIf { it.isNotBlank() },
                target = WidgetNavigationTarget.Dua(
                    // "{Chapter}: Dua N", which is the contract DuaDetailScreen documents
                    // and detects with `title.contains(": Dua ")`. Sent as a bare chapter
                    // title the screen classified it as a Quranic dua instead, fell through
                    // to id-matching, found nothing and opened page 1 of 291 — "Accept from
                    // us" — rather than the dua the widget was showing.
                    title = "${dua.chapterTitle}: Dua ${dua.position}",
                    content = text,
                    duaNumber = dua.position,
                ),
            )
        }
    }

    /** Removes display-only invocation numbering while leaving meaningful numbers intact. */
    private fun withoutDuaNumber(value: String): String = value
        .replace(DUA_NUMBER_PREFIX, "")
        .replace(DUA_NUMBER_SUFFIX, "")
        .trim()

    private fun cleanDuaText(value: String): String = withoutDuaNumber(value)
        // Fortress translations contain inline footnote markers such as "morning 1 and"
        // and "laziness.)2". They have no corresponding footnotes in the widget, so they
        // read like invocation numbering and should not be exposed there.
        .replace(DUA_FOOTNOTE_MARKER, "")
        .replace(REPEATED_SPACE, " ")
        .replace(MISSING_SENTENCE_SPACE, "$1 ")
        .trim()

    private val DUA_NUMBER_PREFIX = Regex(
        pattern = """^\s*(?:(?:dua|supplication|invocation)\s*(?:no\.?|number|#)?\s*#?\d+\s*[.):-]?|\d+\s*[.):-])\s*""",
        option = RegexOption.IGNORE_CASE,
    )
    private val DUA_NUMBER_SUFFIX = Regex(
        pattern = """\s*[:\-–—]?\s*(?:dua|supplication|invocation)\s*(?:no\.?|number|#)?\s*\d+\s*$""",
        option = RegexOption.IGNORE_CASE,
    )
    private val DUA_FOOTNOTE_MARKER = Regex("""(?<!\d)[1-9](?!\d)""")
    private val MISSING_SENTENCE_SPACE = Regex("""([.)])(?=[A-Z])""")

    /**
     * The app's own topic for a Fortress chapter, matched the same way [DuaCategory] does.
     *
     * Falls back to the chapter title when nothing matches, so the corner still says
     * something rather than going blank.
     */
    private fun topicFor(chapterTitle: String): String? {
        if (chapterTitle.isBlank()) return null
        val lowered = chapterTitle.lowercase()
        return DuaCategory.entries
            .firstOrNull { category -> category.keywords.any { it.lowercase() in lowered } }
            ?.displayName
            ?: chapterTitle
    }

    /** Shown only when both databases are unreachable — never cited, never linked. */
    private val FALLBACK = DailyReminder(
        key = "fallback",
        text = "Remember Allah in times of ease, and He will remember you in times of hardship.",
        caption = "Reminder",
        target = null,
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DailyReminderEntryPoint {
    fun duaRepository(): DuaRepository
}
