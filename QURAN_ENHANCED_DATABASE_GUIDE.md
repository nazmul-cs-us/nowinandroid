# Quran Enhanced Database Integration Guide

## Overview

The Enhanced Quran Database (`quran_enhanced.db`) provides comprehensive Arabic Quran features including Tafseer (interpretations), grammar analysis, line-by-line page layout, and more.

### Database Size & Features

- **File**: `app/src/main/assets/databases/quran_enhanced.db`
- **Size**: 30MB
- **Total Ayahs**: 6,236
- **Total Surahs**: 114
- **Total Pages**: 604 (traditional Mushaf pagination)
- **Juz Divisions**: 30 parts

### What's Included

#### Arabic Text Variants
1. **Full Tashkeel** (`aya_text`) - Complete Arabic with diacritics
2. **Simplified** (`aya_text_emlaey`) - Arabic without diacritics
3. **Tashkil Variant** (`aya_text_tashkil`) - Alternative tashkeel notation

#### Three Complete Tafseer Books (Arabic)
1. **Tafseer As-Sa'di** (`tafseer_saadi`) - Contemporary, widely popular
2. **Tafseer Al-Moyassar** (`tafseer_moysar`) - Simplified, approved by King Fahd Complex
3. **Tafseer Al-Baghawi** (`tafseer_bughiu`) - Classical interpretation

#### Additional Features
- **Word Meanings** (`maany_aya`) - Arabic explanations of difficult words
- **Grammar Analysis** (`earab_quran`) - I'rab (grammatical parsing)
- **Revelation Context** (`reasons_of_verses`) - Asbab al-Nuzul (reasons of revelation)
- **Line-by-Line Layout** (`line_start`, `line_end`) - For Mushaf page display
- **Page Numbers** - Traditional Mushaf pagination
- **Juz Numbers** - 30-part divisions

## Architecture

### Database Classes

```
QuranEnhancedEntity.kt       // Room entity with 18 columns
QuranEnhancedDao.kt           // DAO with 40+ query methods
QuranEnhancedDatabase.kt      // Room database configuration
QuranEnhancedRepository.kt    // Repository layer for clean access
QuranDatabaseModule.kt        // Hilt dependency injection
```

### Dependency Injection

Both standard and enhanced databases are available via Hilt:

```kotlin
@Inject lateinit var quranDao: QuranDao  // Standard database (translations)
@Inject lateinit var quranEnhancedDao: QuranEnhancedDao  // Enhanced database (Tafseer)
@Inject lateinit var quranEnhancedRepository: QuranEnhancedRepository  // Repository layer
@Inject lateinit var unifiedRepository: UnifiedQuranRepository  // Both databases
```

## Usage Examples

### 1. Basic Ayah Access

```kotlin
@HiltViewModel
class SurahViewModel @Inject constructor(
    private val repository: QuranEnhancedRepository
) : ViewModel() {

    // Get all Ayahs for a Surah (lightweight)
    suspend fun loadSurah(surahNumber: Int) {
        val ayahs = repository.getAyahsBySurah(surahNumber)
        ayahs.forEach { ayah ->
            println("${ayah.ayahNumber}. ${ayah.ayahText}")
        }
    }

    // Get single Ayah with all data
    suspend fun loadAyahDetails(surahNumber: Int, ayahNumber: Int) {
        val ayah = repository.getAyah(surahNumber, ayahNumber)
        ayah?.let {
            println("Surah: ${it.surahNameArabic}")
            println("Ayah ${it.ayahNumber}: ${it.ayahText}")
            println("Page: ${it.pageNumber}, Juz: ${it.juz}")
        }
    }
}
```

### 2. Surah List Display

```kotlin
@Composable
fun SurahListScreen(repository: QuranEnhancedRepository) {
    var surahs by remember { mutableStateOf<List<SurahInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        surahs = repository.getAllSurahs()
    }

    LazyColumn {
        items(surahs) { surah ->
            SurahItem(
                nameArabic = surah.surahNameArabic,
                nameEnglish = surah.surahNameEnglish,
                ayahCount = surah.ayahCount,
                surahNumber = surah.surahNumber
            )
        }
    }
}
```

### 3. Tafseer Display

```kotlin
@Composable
fun TafseerScreen(
    surahNumber: Int,
    ayahNumber: Int,
    repository: QuranEnhancedRepository
) {
    var tafseer by remember { mutableStateOf<QuranAyahTafseer?>(null) }
    var selectedTafseer by remember { mutableStateOf("saadi") }

    LaunchedEffect(surahNumber, ayahNumber) {
        tafseer = repository.getTafseerForAyah(surahNumber, ayahNumber)
    }

    tafseer?.let { t ->
        Column(modifier = Modifier.padding(16.dp)) {
            // Ayah text
            Text(
                text = t.ayahText,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.End
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tafseer selector
            Row {
                Button(onClick = { selectedTafseer = "saadi" }) {
                    Text("As-Sa'di")
                }
                Button(onClick = { selectedTafseer = "moysar" }) {
                    Text("Al-Moyassar")
                }
                Button(onClick = { selectedTafseer = "baghawi" }) {
                    Text("Al-Baghawi")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display selected Tafseer
            val tafseerText = when (selectedTafseer) {
                "saadi" -> t.tafseerSaadi
                "moysar" -> t.tafseerMoysar
                "baghawi" -> t.tafseerBaghawi
                else -> ""
            }

            if (tafseerText.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = tafseerText,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
```

### 4. Full Surah Tafseer

```kotlin
@Composable
fun SurahTafseerScreen(
    surahNumber: Int,
    repository: QuranEnhancedRepository
) {
    var tafseerItems by remember { mutableStateOf<List<AyahTafseerItem>>(emptyList()) }

    LaunchedEffect(surahNumber) {
        // Load Tafseer Saadi for entire Surah
        tafseerItems = repository.getTafseerSaadiBySurah(surahNumber)
    }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(tafseerItems) { item ->
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                // Ayah number and text
                Text(
                    text = "${item.ayahNumber}. ${item.ayahText}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tafseer
                if (item.tafseer.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = item.tafseer,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
```

### 5. Page-Based Mushaf Display

```kotlin
@Composable
fun MushafPageScreen(
    pageNumber: Int,
    repository: QuranEnhancedRepository
) {
    var ayahs by remember { mutableStateOf<List<QuranAyahPage>>(emptyList()) }

    LaunchedEffect(pageNumber) {
        ayahs = repository.getAyahsByPage(pageNumber)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Page header
        Text(
            text = "Page $pageNumber",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )

        // Group Ayahs by line
        val ayahsByLine = ayahs.groupBy { it.lineStart }

        LazyColumn {
            ayahsByLine.forEach { (lineNumber, lineAyahs) ->
                item {
                    // Line-by-line display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = lineAyahs.joinToString(" ") { it.ayahText },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
```

### 6. Line-by-Line Audio Playback

```kotlin
@HiltViewModel
class QuranPlayerViewModel @Inject constructor(
    private val repository: QuranEnhancedRepository
) : ViewModel() {

    private var currentPage = 1
    private var currentLine = 1

    suspend fun playNextLine() {
        val ayahs = repository.getAyahsByLineRange(
            pageNumber = currentPage,
            startLine = currentLine,
            endLine = currentLine
        )

        ayahs.forEach { ayah ->
            // Play audio for this Ayah
            playAudio(ayah.surahNumber, ayah.ayahNumber)

            // Update UI to highlight current line
            _currentAyahState.value = ayah
        }

        currentLine++
    }

    suspend fun getTotalPages(): Int {
        return repository.getTotalPages() // Returns 604
    }
}
```

### 7. Grammar Analysis Display

```kotlin
@Composable
fun GrammarAnalysisScreen(
    surahNumber: Int,
    ayahNumber: Int,
    repository: QuranEnhancedRepository
) {
    var analysis by remember { mutableStateOf<AyahAnalysisItem?>(null) }
    var meanings by remember { mutableStateOf<AyahMeaningsItem?>(null) }

    LaunchedEffect(surahNumber, ayahNumber) {
        analysis = repository.getGrammaticalAnalysis(surahNumber, ayahNumber)
        meanings = repository.getAyahMeanings(surahNumber, ayahNumber)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        // Ayah text
        analysis?.let {
            Text(
                text = it.ayahText,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.End
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Word meanings
            meanings?.meanings?.let { m ->
                if (m.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Word Meanings:",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = m,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grammar analysis (I'rab)
            if (it.analysis.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Grammatical Analysis (I'rab):",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it.analysis,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}
```

### 8. Revelation Context (Asbab al-Nuzul)

```kotlin
suspend fun showRevelationContext(
    surahNumber: Int,
    ayahNumber: Int,
    repository: QuranEnhancedRepository
) {
    val reasons = repository.getRevelationReasons(surahNumber, ayahNumber)
    reasons?.let {
        if (it.reasons.isNotEmpty()) {
            println("Revelation Context for ${it.ayahNumber}:")
            println(it.reasons)
        }
    }
}
```

### 9. Search Functionality

```kotlin
@Composable
fun QuranSearchScreen(repository: QuranEnhancedRepository) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<QuranEnhancedAyah>>(emptyList()) }
    var searchType by remember { mutableStateOf("arabic") }

    Column(modifier = Modifier.padding(16.dp)) {
        // Search input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Quran") },
            modifier = Modifier.fillMaxWidth()
        )

        // Search type selector
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { searchType = "arabic" }) {
                Text("Arabic Text")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { searchType = "tafseer" }) {
                Text("Tafseer")
            }
        }

        // Search button
        Button(
            onClick = {
                viewModelScope.launch {
                    searchResults = when (searchType) {
                        "arabic" -> repository.searchAyahsEmlaey(searchQuery)
                        "tafseer" -> repository.searchAllTafseer(searchQuery)
                        else -> emptyList()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Search")
        }

        // Results
        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(searchResults) { ayah ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${ayah.surahNameArabic} - ${ayah.ayahNumber}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = ayah.ayahText,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}
```

### 10. Juz Navigation

```kotlin
@Composable
fun JuzNavigationScreen(repository: QuranEnhancedRepository) {
    var juzList by remember { mutableStateOf<List<JuzInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        juzList = repository.getAllJuzInfo()
    }

    LazyColumn {
        items(juzList) { juz ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable {
                        // Navigate to Juz detail screen
                    },
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Juz ${juz.juzNumber}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${juz.ayahCount} Ayahs",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
```

## Database Comparison

### Standard Database (quran.db + translations)
- ✅ Multiple language translations (12 languages)
- ✅ Normalized schema (separate tables)
- ✅ Smaller size per database
- ❌ No Tafseer
- ❌ No grammar analysis
- ❌ No line-by-line layout

### Enhanced Database (quran_enhanced.db)
- ✅ 3 complete Tafseer books
- ✅ Grammar analysis (I'rab)
- ✅ Word meanings
- ✅ Revelation context
- ✅ Line-by-line page layout
- ✅ Multiple Arabic text variants
- ❌ Arabic only (no translations)
- ❌ Larger size (30MB)

## Best Practices

### 1. Use Repository Layer
```kotlin
// ✅ Good - Use repository
@Inject lateinit var repository: QuranEnhancedRepository
val ayahs = repository.getAyahsBySurah(surahNumber)

// ❌ Avoid - Direct DAO access
@Inject lateinit var dao: QuranEnhancedDao
val ayahs = dao.getAyahsBySurah(surahNumber)
```

### 2. Choose Appropriate Models
```kotlin
// ✅ For list display - use lightweight model
val ayahs: List<QuranAyahBasic> = repository.getAyahsBySurah(surahNumber)

// ✅ For detail view - use full model
val ayah: QuranEnhancedAyah? = repository.getAyah(surahNumber, ayahNumber)
```

### 3. Use UnifiedRepository for Both Databases
```kotlin
@Inject lateinit var unifiedRepository: UnifiedQuranRepository

// Get Arabic with Tafseer
val tafseer = unifiedRepository.getTafseer(surahNumber, ayahNumber)

// Get translation
val ayahsWithTranslation = unifiedRepository.getAyahsWithTranslation(surahId)
```

### 4. Search Optimization
```kotlin
// ✅ Better - Search without diacritics
repository.searchAyahsEmlaey("بسم الله")  // Easier for users

// ⚠️ Works but less flexible - Search with full tashkeel
repository.searchAyahsArabic("بِسۡمِ ٱللَّهِ")
```

## Performance Tips

1. **Use Flow for reactive updates**: `getAyahsByPageFlow()` instead of suspend functions
2. **Limit search results**: Always specify a limit for search queries
3. **Load lightweight models first**: Use `QuranAyahBasic` for lists, full models for details
4. **Cache frequently accessed data**: Store Surah list in memory
5. **Paginate large results**: Use offset/limit for long Surahs

## Testing

```kotlin
@Test
fun testEnhancedDatabase() = runTest {
    val database = QuranEnhancedDatabase.getInstance(context)
    val dao = database.quranEnhancedDao()

    // Test basic query
    val ayahs = dao.getAyahsBySurah(1) // Al-Fatiha
    assertEquals(7, ayahs.size)

    // Test Tafseer
    val tafseer = dao.getTafseerForAyah(1, 1)
    assertNotNull(tafseer)
    assertTrue(tafseer!!.tafseerSaadi.isNotEmpty())

    // Test page query
    val pageAyahs = dao.getAyahsByPage(1)
    assertTrue(pageAyahs.isNotEmpty())
}
```

## Migration from Old System

If you're currently using the standard database and want to add enhanced features:

```kotlin
// Old code
@Inject lateinit var quranDao: QuranDao
val ayahs = quranDao.getAyahsBySurahOnce(surahId)

// New code - Keep old code, add enhanced features alongside
@Inject lateinit var quranDao: QuranDao  // Keep for translations
@Inject lateinit var quranEnhancedRepository: QuranEnhancedRepository  // Add for Tafseer

// Use both
val ayahsWithTranslation = quranDao.getAyahsBySurahOnce(surahId)
val tafseer = quranEnhancedRepository.getTafseerForAyah(surahNumber, ayahNumber)
```

## Troubleshooting

### Database not found
- Ensure `quran_enhanced.db` is in `app/src/main/assets/databases/`
- Clean and rebuild project
- Check file size is ~30MB

### Empty results
- Verify Surah numbers are 1-114
- Check Ayah numbers are valid for the Surah
- Check page numbers are 1-604

### Performance issues
- Use Flow for reactive updates
- Implement pagination for large lists
- Cache frequently accessed data
- Use appropriate model sizes (Basic vs Full)

## Future Enhancements

Potential additions to the system:
- Audio sync with line-by-line playback
- Bookmarking Tafseer passages
- Notes on specific Tafseer interpretations
- Comparison view for multiple Tafseer books
- Translation of Tafseer to other languages
- Export Tafseer to PDF/text files
