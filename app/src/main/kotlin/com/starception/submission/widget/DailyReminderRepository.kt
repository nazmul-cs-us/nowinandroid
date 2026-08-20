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
    /** Book this came from, for the footer's left corner — "Sahih Bukhari". */
    val sourceName: String? = null,
    /** Where in that book, for the footer's right corner — "#156". */
    val sourceDetail: String? = null,
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
            val text = hadith.textPlain?.trim() ?: continue
            if (!isSelfContained(text)) continue

            val collection = hadith.collectionName.takeIf { it.isNotBlank() } ?: "Sahih Bukhari"
            found = DailyReminder(
                key = "hadith-$number",
                text = text,
                // The kind, not the source. The subtitle's job is to say what the reader is
                // looking at before they read it; "Bukhari" answers a question they had not
                // asked yet, and a Fortress chapter title ("Invocation for when you see the
                // first dates of the season") is a whole sentence competing with the one
                // underneath it. The source is still carried in [target], which is where it
                // matters — it is what the tap opens.
                caption = "Hadith",
                sourceName = collection,
                sourceDetail = "#$number",
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
            val text = dua.translation?.takeIf { it.isNotBlank() } ?: return@let null
            DailyReminder(
                key = "dua-${dua.id}",
                text = text,
                caption = "Dua",
                sourceName = "Fortress of the Muslim",
                // The topic, not the chapter title. A Fortress chapter is named for the
                // occasion in full — "What to say if you see someone afflicted" — which is
                // a sentence, not a reference, and it ellipsised in the corner it sits in.
                // The category the app already groups these by ("Health & Sickness") is
                // short, stable and is the same label the user sees elsewhere in the app.
                sourceDetail = topicFor(dua.chapterTitle),
                target = WidgetNavigationTarget.Dua(
                    title = dua.chapterTitle,
                    content = text,
                    duaNumber = dua.position,
                ),
            )
        }
    }

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
