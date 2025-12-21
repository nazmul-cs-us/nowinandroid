package com.starception.submission.core.duadatabase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for DuaRepository
 * Verifies database is properly loaded from assets and queries work correctly
 */
@RunWith(AndroidJUnit4::class)
class DuaRepositoryTest {

    private lateinit var database: DuaDatabase
    private lateinit var duaDao: DuaDao
    private lateinit var repository: DuaRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = DuaDatabase.getInstance(context)
        duaDao = database.duaDao()
        repository = DuaRepository(duaDao)
    }

    @After
    fun teardown() {
        DuaDatabase.closeDatabase()
    }

    @Test
    fun testGetMetadata() = runBlocking {
        val metadata = repository.getBookMetadata()
        assertNotNull("Metadata should not be null", metadata)
        assertEquals("Fortress Of the Muslim", metadata?.title)
        assertEquals("Invocations from the Qur'an & Sunnah", metadata?.subtitle)
        assertEquals("Darussalam", metadata?.publisher)
        println("✅ Metadata: ${metadata?.title} - ${metadata?.subtitle}")
    }

    @Test
    fun testGetAllChapters() = runBlocking {
        val chapters = repository.getAllChapters()
        assertEquals("Should have 129 chapters", 129, chapters.size)

        // Verify first and last chapters
        assertEquals("When waking up", chapters.first().title)
        assertEquals("How the prophet made tasbeeh", chapters.last().title)

        println("✅ Total chapters: ${chapters.size}")
        println("✅ First chapter: ${chapters.first().title}")
        println("✅ Last chapter: ${chapters.last().title}")
    }

    @Test
    fun testGetDuaCount() = runBlocking {
        val count = repository.getDuaCount()
        assertEquals("Should have 282 duas", 282, count)
        println("✅ Total duas: $count")
    }

    @Test
    fun testGetDuasByChapter() = runBlocking {
        // Chapter 1: "When waking up" should have 5 duas
        val duas = repository.getDuasByChapter(1)
        assertEquals("Chapter 1 should have 5 duas", 5, duas.size)

        // Verify first dua has Arabic text
        assertNotNull("First dua should have Arabic text", duas.first().arabic)
        assertTrue("First dua Arabic should not be empty", duas.first().arabic!!.isNotEmpty())

        println("✅ Chapter 1 duas: ${duas.size}")
        println("✅ First dua preview: ${duas.first().arabic?.take(50)}...")
    }

    @Test
    fun testGetRandomDua() = runBlocking {
        val randomDua = repository.getRandomDua()
        assertNotNull("Random dua should not be null", randomDua)
        assertNotNull("Random dua should have Arabic text", randomDua?.arabic)

        println("✅ Random dua from chapter: ${randomDua?.chapterTitle}")
        println("✅ Arabic preview: ${randomDua?.arabic?.take(50)}...")
    }

    @Test
    fun testSearchDuas() = runBlocking {
        // Search for "Allah" in translations
        val results = repository.searchByTranslation("Allah")
        assertTrue("Should find duas mentioning Allah", results.isNotEmpty())

        println("✅ Search 'Allah': Found ${results.size} duas")
    }

    @Test
    fun testSearchChapters() = runBlocking {
        // Search for "prayer"
        val results = repository.searchChapters("prayer")
        assertTrue("Should find chapters about prayer", results.isNotEmpty())

        println("✅ Search 'prayer' chapters: Found ${results.size}")
        results.take(3).forEach { println("   - ${it.title}") }
    }

    @Test
    fun testGetCategoriesWithCounts() = runBlocking {
        val categories = repository.getCategoriesWithCounts()
        assertTrue("Should have multiple categories", categories.isNotEmpty())

        // Verify total chapters covered
        val totalChapters = categories.sumOf { it.chapterCount }
        assertTrue("Total categorized chapters should be >= 129", totalChapters >= 129)

        println("✅ Categories: ${categories.size}")
        categories.forEach {
            println("   ${it.category.icon} ${it.category.displayName}: ${it.chapterCount} chapters, ${it.totalDuaCount} duas")
        }
    }

    @Test
    fun testGetChaptersByCategory() = runBlocking {
        // Get Prayer category chapters
        val prayerChapters = repository.getChaptersByCategory(DuaCategory.PRAYER)
        assertTrue("Should find prayer chapters", prayerChapters.isNotEmpty())

        println("✅ Prayer chapters: ${prayerChapters.size}")
        prayerChapters.take(5).forEach { println("   - ${it.title}") }
    }

    @Test
    fun testGetFootnotes() = runBlocking {
        val footnotes = repository.getAllFootnotes()
        assertEquals("Should have 43 footnotes", 43, footnotes.size)

        println("✅ Total footnotes: ${footnotes.size}")
    }

    @Test
    fun testChapterNavigation() = runBlocking {
        // Get next chapter from chapter 1
        val next = repository.getNextChapter(1)
        assertNotNull("Should have next chapter", next)
        assertEquals(2, next?.id)

        // Get previous chapter from chapter 2
        val prev = repository.getPreviousChapter(2)
        assertNotNull("Should have previous chapter", prev)
        assertEquals(1, prev?.id)

        println("✅ Navigation works: Chapter 1 -> Next: ${next?.title}")
        println("✅ Navigation works: Chapter 2 -> Prev: ${prev?.title}")
    }

    @Test
    fun testGetChapterWithDuas() = runBlocking {
        val chapterWithDuas = repository.getChapterWithDuas(27)
        assertNotNull("Should get chapter with duas", chapterWithDuas)
        assertEquals("Remembrance said in the morning and evening", chapterWithDuas?.chapter?.title)
        assertTrue("Morning/evening adhkar should have multiple duas", (chapterWithDuas?.invocations?.size ?: 0) > 10)

        println("✅ Chapter 27: ${chapterWithDuas?.chapter?.title}")
        println("✅ Duas: ${chapterWithDuas?.invocations?.size}")
        println("✅ Footnotes: ${chapterWithDuas?.footnotes?.size}")
    }
}
