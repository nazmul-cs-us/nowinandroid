# 🌍 Quran Translation Databases Integration Guide

## ✅ Successfully Integrated!

Your app now has complete Quran databases in **12 languages** ready to use!

---

## 📦 What's Included

### Translation Databases (12 total)
All databases are located in `app/src/main/assets/databases/`:

1. **`quran.db`** (6.8 MB) - Arabic (Original)
2. **`quran_en.db`** (1.1 MB) - English
3. **`quran_transliteration.db`** (892 KB) - English Transliteration
4. **`quran_bn.db`** (2.4 MB) - Bengali
5. **`quran_zh.db`** (1.0 MB) - Chinese
6. **`quran_es.db`** (1.1 MB) - Spanish
7. **`quran_fr.db`** (1.2 MB) - French
8. **`quran_id.db`** (1.3 MB) - Indonesian
9. **`quran_ru.db`** (1.7 MB) - Russian
10. **`quran_sv.db`** (1.2 MB) - Swedish
11. **`quran_tr.db`** (1.2 MB) - Turkish
12. **`quran_ur.db`** (1.8 MB) - Urdu

**Total Size**: ~22 MB for all translations

### Database Content
- **114 Surahs (Chapters)** in each database
- **6236 Ayahs (Verses)** in each database
- Complete chapter metadata (names, types, verse counts)
- Proper indexes for fast queries

---

## 🚀 How to Use

### Basic Usage - Load Specific Translation

```kotlin
import com.starception.submission.core.qurandatabase.*

// In your ViewModel, Activity, or Fragment
class QuranReaderViewModel(context: Context) : ViewModel() {
    
    // Create repository for English translation
    private val quranRepo = QuranTranslationRepository(context, "en")
    
    // Create repository for Arabic (default)
    private val arabicRepo = QuranTranslationRepository(context, "ar")
    
    // Create repository for Bengali
    private val bengaliRepo = QuranTranslationRepository(context, "bn")
    
    suspend fun loadAlFatiha() {
        // Load Al-Fatiha in English
        val surah = quranRepo.getSurahByNumber(1)
        println("Name: ${surah?.nameTranslation}") // "The Opener"
        
        // Load its verses in English
        val ayahs = quranRepo.getAyahsBySurahOnce(1)
        ayahs.forEach { ayah ->
            println("${ayah.numberInSurah}. ${ayah.text}")
        }
    }
}
```

### Switching Between Translations

```kotlin
class QuranReaderScreen : Composable {
    
    var currentTranslation = remember { mutableStateOf("en") }
    
    // Create repository for current translation
    val quranRepo = remember {
        QuranTranslationRepository(context, currentTranslation.value)
    }
    
    // UI to switch translations
    Column {
        // Translation selector
        TranslationSelector(
            current = currentTranslation.value,
            onTranslationChange = { code ->
                currentTranslation.value = code
                // Repository will automatically use new translation
            }
        )
        
        // Display Quran with current translation
        QuranReader(quranRepo = quranRepo)
    }
}
```

### Get Available Translations

```kotlin
// Get list of all available translation codes
val availableTranslations = QuranTranslationHelper.getAvailableTranslations()
// Returns: ["ar", "transliteration", "bn", "zh", "en", "es", "fr", "id", "ru", "sv", "tr", "ur"]

// Get display name for a translation
val displayName = QuranTranslationHelper.getTranslationName("en")
// Returns: "English"
```

### Search Across Translations

```kotlin
// Search in English Quran
val englishRepo = QuranTranslationRepository(context, "en")
val results = englishRepo.searchAyahsWithLimit("mercy", limit = 10)

results.forEach { ayah ->
    println("${ayah.surahId}:${ayah.numberInSurah} - ${ayah.text}")
}

// Search in Arabic Quran
val arabicRepo = QuranTranslationRepository(context, "ar")
val arabicResults = arabicRepo.searchAyahsWithLimit("رحم", limit = 10)
```

### Parallel Reading (Arabic + Translation Side-by-Side)

```kotlin
suspend fun loadBilingualReading(surahNumber: Int) {
    val arabicRepo = QuranTranslationRepository(context, "ar")
    val englishRepo = QuranTranslationRepository(context, "en")
    
    // Load same Surah from both databases
    val arabicAyahs = arabicRepo.getAyahsBySurahOnce(surahNumber)
    val englishAyahs = englishRepo.getAyahsBySurahOnce(surahNumber)
    
    // Combine for display
    val bilingualView = arabicAyahs.zip(englishAyahs) { arabic, english ->
        BilingualAyah(
            arabic = arabic.text,
            translation = english.text,
            number = arabic.numberInSurah
        )
    }
    
    return bilingualView
}
```

---

## 📊 API Reference

### `QuranTranslationHelper`

Main helper object for managing translation databases.

#### Methods

```kotlin
// Get database instance for a specific translation
fun getDatabase(context: Context, translationCode: String = "ar"): QuranDatabase

// Clear database cache
fun clearCache()

// Get all available translation codes
fun getAvailableTranslations(): List<String>

// Get display name for a translation code
fun getTranslationName(code: String): String
```

### `QuranTranslationRepository`

Repository for accessing Quran data with a specific translation.

#### Constructor

```kotlin
QuranTranslationRepository(
    private val context: Context,
    private val translationCode: String = "ar"  // Default: Arabic
)
```

#### Methods

All methods match the original `QuranRepository` API:

**Surah Operations:**
- `getAllSurahs(): Flow<List<Surah>>`
- `getAllSurahsWithCounts(): List<Surah>`
- `getSurahByNumber(surahNumber: Int): Surah?`
- `getSurahById(surahId: Int): Surah?`
- `getSurahsByType(revelationType: String): Flow<List<Surah>>`
- `searchSurahs(query: String): Flow<List<Surah>>`

**Ayah Operations:**
- `getAyahsBySurah(surahId: Int): Flow<List<Ayah>>`
- `getAyahsBySurahOnce(surahId: Int): List<Ayah>`
- `getAyahByNumber(ayahNumber: Int): Ayah?`
- `getAyahsByPage(pageNumber: Int): Flow<List<Ayah>>`
- `getAyahsByJuz(juzNumber: Int): Flow<List<Ayah>>`
- `getSajdaAyahs(): Flow<List<Ayah>>`
- `searchAyahs(query: String): Flow<List<Ayah>>`
- `searchAyahsWithLimit(query: String, limit: Int = 50): List<Ayah>`

**Statistics:**
- `getTotalAyahCount(): Int`
- `getAyahCount(surahId: Int): Int`

**Pagination:**
- `getAyahsPage(page: Int, pageSize: Int = 20): List<Ayah>`

---

## 🔧 Data Source

All data is sourced from the quran-json project:
- **Source**: https://github.com/risan/quran-json
- **CDN**: cdn.jsdelivr.net/npm/quran-json@3.1.2

**Note**: The main JSON files already contain complete chapter metadata (names, transliterations, translations, types, verse counts), so the separate chapter JSON endpoints are just convenience indices with the same data.

---

## 📝 Notes

1. **Database Loading**: Databases are lazy-loaded when first accessed
2. **Caching**: Each translation database is cached after first load
3. **Memory**: All databases are pre-packaged in assets, no download needed
4. **Offline**: Works completely offline
5. **Performance**: Fast queries with proper indexes
6. **Thread Safety**: All database operations use coroutines and flows

---

## 🎯 Next Steps

You now have everything needed to:
- Display Quran in multiple languages
- Let users switch between translations
- Search across different translations
- Show bilingual reading (Arabic + Translation)
- Build a comprehensive Quran reader app

Enjoy building your Quran reader! 📖

