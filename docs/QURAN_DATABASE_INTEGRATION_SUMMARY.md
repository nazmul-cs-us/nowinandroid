# Quran Database Integration

Complete guide to the Quran database integration in Starception Submission app, including setup, usage, and current status.

---

## Overview

The complete Quran database has been successfully integrated into the app, providing access to all 114 Surahs and 6,236 Ayahs. The database is built using Room persistence library and includes comprehensive query methods for accessing Quranic content.

---

## Database Contents

| Item | Count |
|------|-------|
| **Surahs** | 114 |
| **Ayahs** | 6,236 |
| **Juz (Parts)** | 30 |
| **Hizb (Sections)** | 60 |
| **Pages** | 604 |
| **Meccan Surahs** | 86 |
| **Medinan Surahs** | 28 |
| **Sajda Ayahs** | ~15 |

---

## Architecture

### Database Schema

```sql
Tables:
- surahs (id, number, name_ar, name_en, name_en_translation, type)
- ayahs (id, number, text, number_in_surah, page, surah_id, hizb_id, juz_id, sajda)
- juzs (id, number)
- hizbs (id, number)

Indices:
- ayahs.surah_id
- ayahs.number
- ayahs.number_in_surah
```

### Files Created

**Database Components:**
```
app/src/main/kotlin/com/starception/submission/core/qurandatabase/
├── QuranEntity.kt           - Data models and Room entities
├── QuranDao.kt              - Database queries
├── QuranDatabase.kt         - Room database configuration
├── QuranRepository.kt       - Repository for data access
└── QuranDatabaseModule.kt   - Hilt dependency injection
```

**Database File:**
```
app/src/main/assets/databases/
└── quran.db                 - SQLite database (~25 MB)
```

**Conversion Script:**
```
convert_quran_sql_to_db.py   - Python script for SQL conversion
```

### Dependencies Added

```kotlin
// Room Database
implementation(libs.room.runtime)
implementation(libs.room.ktx)
ksp(libs.room.compiler)
```

---

## Integration Status

### Fully Integrated

1. **Database Infrastructure**
   - Room database with 114 Surahs & 6,236 Ayahs
   - `QuranRepository` ready for injection
   - `QuranDao` with 20+ query methods
   - `QuranDatabaseModule` with Hilt injection

2. **News Integration**
   - All 114 Surahs added as news items
   - Visible in "For You" feed
   - Filterable under "Holy Quran" topic
   - Generated using `generate_quran_news.py` script

3. **Asset Storage**
   - Database file: `app/src/main/assets/databases/quran.db`
   - Automatically loaded by Room when app starts
   - Available for any feature that injects `QuranRepository`

### Not Yet Using Database

**Quran Audio Player**
- Location: `app/src/main/kotlin/com/starception/submission/feature/quran/`
- Current: Uses hardcoded data from `QuranData.kt`
- Future: Can be migrated to use database for metadata

**Quran Text Reader**
- Status: Feature not yet built
- Database: Ready with all Ayah text

**Quran Search**
- Status: Feature not yet built
- Database: Search methods ready

---

## Available Query Methods

### Surah Queries

- `getAllSurahs()` - Get all Surahs
- `getAllSurahsWithCounts()` - Get Surahs with ayah counts
- `getSurahByNumber(number)` - Get specific Surah (1-114)
- `getSurahById(id)` - Get Surah by ID
- `getSurahsByType(type)` - Get Meccan/Medinan Surahs
- `searchSurahs(query)` - Search Surahs

### Ayah Queries

- `getAyahsBySurah(surahId)` - Get all Ayahs in Surah
- `getAyahByNumber(number)` - Get specific Ayah (1-6236)
- `getAyahsByPage(page)` - Get Ayahs by Mushaf page
- `getAyahsByJuz(juz)` - Get Ayahs by Juz (part)
- `getAyahsByHizb(hizb)` - Get Ayahs by Hizb (section)
- `getSajdaAyahs()` - Get Ayahs with Sajda
- `searchAyahs(query)` - Search Ayah text
- `searchAyahsWithLimit(query, limit)` - Search with result limit

### Statistics

- `getTotalAyahCount()` - Total Ayahs (6236)
- `getAyahCount(surahId)` - Ayahs in specific Surah
- `isDatabaseInitialized()` - Check database health

### Pagination

- `getAyahsPage(page, pageSize)` - Paginated Ayahs

---

## Usage Guide

### 1. Inject Repository

```kotlin
@AndroidEntryPoint
class YourActivity : ComponentActivity() {

    @Inject
    lateinit var quranRepository: QuranRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            // Get all Surahs
            val surahs = quranRepository.getAllSurahsWithCounts()
            Log.d("Quran", "Loaded ${surahs.size} Surahs")
        }
    }
}
```

### 2. Query Data

```kotlin
// Get a specific Surah
val surah = quranRepository.getSurahByNumber(1) // Al-Fatiha

// Get Ayahs for a Surah
quranRepository.getAyahsBySurah(surahId).collect { ayahs ->
    ayahs.forEach { ayah ->
        println("${ayah.numberInSurah}. ${ayah.text}")
    }
}

// Search
quranRepository.searchAyahs("الله").collect { results ->
    println("Found ${results.size} results")
}
```

### 3. Display in Compose UI

```kotlin
@Composable
fun QuranSurahList(repository: QuranRepository) {
    val surahs by repository.getAllSurahs()
        .collectAsState(initial = emptyList())

    LazyColumn {
        items(surahs) { surah ->
            Text("${surah.number}. ${surah.nameEnglish}")
        }
    }
}
```

---

## Usage Examples

### Example 1: Surah List Screen

```kotlin
@Composable
fun SurahListScreen(repository: QuranRepository) {
    val surahs by repository.getAllSurahs()
        .collectAsState(initial = emptyList())

    LazyColumn {
        items(surahs) { surah ->
            Card(modifier = Modifier.padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = surah.nameArabic,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${surah.number}. ${surah.nameEnglish}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${surah.revelationType} • ${surah.ayahCount} Ayahs",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
```

### Example 2: Ayah Reader

```kotlin
@Composable
fun AyahReaderScreen(
    surahId: Int,
    repository: QuranRepository
) {
    val ayahs by repository.getAyahsBySurah(surahId)
        .collectAsState(initial = emptyList())

    LazyColumn {
        items(ayahs) { ayah ->
            Row(modifier = Modifier.padding(16.dp)) {
                // Ayah number badge
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "${ayah.numberInSurah}")
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Ayah text
                Text(
                    text = ayah.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textAlign = TextAlign.Right,
                        lineHeight = 40.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
```

### Example 3: Search Feature

```kotlin
@Composable
fun QuranSearchScreen(repository: QuranRepository) {
    var query by remember { mutableStateOf("") }
    val results by repository.searchAyahs(query)
        .collectAsState(initial = emptyList())

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search Quran") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        LazyColumn {
            items(results) { ayah ->
                SearchResultCard(ayah)
            }
        }
    }
}
```

---

## Potential Features (Database Ready)

### 1. Quran Text Reader
Display Ayahs with Arabic text for reading:

```kotlin
@Composable
fun QuranReaderScreen(
    quranRepository: QuranRepository = hiltViewModel()
) {
    val surahs by quranRepository.getAllSurahs()
        .collectAsState(initial = emptyList())

    LazyColumn {
        items(surahs) { surah ->
            SurahCard(surah) {
                // Navigate to Ayah reading screen
            }
        }
    }
}
```

### 2. Enhanced Quran Player
Upgrade audio player to use database metadata:

```kotlin
@HiltViewModel
class QuranPlayerViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
    private val context: Context
) : ViewModel() {

    suspend fun loadSurahInfo(surahNumber: Int) {
        val surah = quranRepository.getSurahByNumber(surahNumber)
        // Use rich metadata: ayah count, revelation type, etc.
    }
}
```

### 3. Quran by Juz/Hizb/Page

```kotlin
// Read by Juz (30 parts)
quranRepository.getAyahsByJuz(1).collect { ayahs ->
    // Display Juz 1
}

// Read by Page (604 pages)
quranRepository.getAyahsByPage(1).collect { ayahs ->
    // Display Page 1
}
```

### 4. Daily Ayah Feature

```kotlin
suspend fun getDailyAyah(): Ayah? {
    val randomNumber = (1..6236).random()
    return quranRepository.getAyahByNumber(randomNumber)
}
```

---

## Performance

- **Indexed Queries** - All searches are optimized with indices
- **Lazy Loading** - Uses Kotlin Flow for reactive data
- **Efficient Storage** - SQLite binary format
- **Fast Access** - Database loaded once on app start
- **Memory Efficient** - Query results streamed as needed

---

## Integration Details

The database is fully integrated with the app:

1. **Automatic Loading** - Database loads from assets automatically
2. **Hilt Injection** - Use `@Inject lateinit var quranRepository: QuranRepository`
3. **Room Integration** - Type-safe queries with compile-time verification
4. **Flow Support** - Reactive updates with Kotlin Flow
5. **Error Handling** - Comprehensive error logging

---

## Migration History

### Initial Setup
- Converted MySQL SQL dump to SQLite database
- Created `quran.db` in `app/src/main/assets/databases/`
- Verified all 114 Surahs and 6,236 Ayahs imported

### Room Database Setup
- Created Room entities (`SurahEntity`, `AyahEntity`, `JuzEntity`, `HizbEntity`)
- Created DAO interface (`QuranDao`) with 20+ query methods
- Created Room database class (`QuranDatabase`)
- Created repository pattern (`QuranRepository`)
- Added Hilt dependency injection (`QuranDatabaseModule`)

### Testing & Verification
- Built app successfully
- Installed on device
- Verified database loads from assets
- Confirmed all queries working

---

## Current Usage Summary

| Component | Status | Database Usage |
|-----------|--------|----------------|
| **Database Setup** | ✅ Complete | N/A |
| **News Feed** | ✅ Active | ✅ Used in script |
| **Quran Player** | ✅ Active | ❌ Not using DB |
| **Quran Reader** | ❌ Not built | ⏳ DB ready |
| **Quran Search** | ❌ Not built | ⏳ DB ready |
| **Repository** | ✅ Ready | ⏳ Available |

---

## Future Enhancements

### Recommended Next Steps

1. **Quran Reader with Translation** - Use database for Arabic text + add translation tables
2. **Bookmark System** - Store user's reading position
3. **Advanced Search** - Search by keywords, topics, revelation type
4. **Reading Progress** - Track which Surahs/Ayahs user has read
5. **Daily Ayah Widget** - Random Ayah from database

### Migration Option for Quran Player

Replace `QuranData.kt` with database calls:

**Before:**
```kotlin
val currentSurah: Surah
    get() = QuranData.surahs[currentSurahIndex]
```

**After:**
```kotlin
@HiltViewModel
class QuranPlayerViewModel @Inject constructor(
    private val quranRepository: QuranRepository
) : ViewModel() {

    private val _currentSurah = MutableStateFlow<Surah?>(null)
    val currentSurah: StateFlow<Surah?> = _currentSurah

    suspend fun loadSurah(index: Int) {
        _currentSurah.value = quranRepository.getSurahByNumber(index + 1)
    }
}
```

---

## Sample Data Verification

```
✅ Surahs: 114/114
✅ Ayahs: 6,236/6,236
✅ Tables: 5
✅ Indices: Created
✅ Build: Successful
✅ Installation: Successful

Sample Surahs:
1. Al-Faatiha (سورة الفاتحة) - Meccan - 7 Ayahs
2. Al-Baqara (سورة البقرة) - Medinan - 286 Ayahs
3. Aal-i-Imraan (سورة آل عمران) - Medinan - 200 Ayahs
...
114. An-Naas (سورة الناس) - Meccan - 6 Ayahs
```

---

## Resources

- **Database Location**: `app/src/main/assets/databases/quran.db`
- **Code Location**: `app/src/main/kotlin/com/starception/submission/core/qurandatabase/`
- **Conversion Script**: `convert_quran_sql_to_db.py`

---

## Summary

**Database Status**: ✅ Fully Functional & Ready
**Current Usage**: News generation only
**Potential**: Ready for Quran reading, search, and display features

The database is waiting to be used for any Quran text-based features. All infrastructure is in place, with 20+ query methods available through the `QuranRepository` for building comprehensive Quranic features.

---

**Alhamdulillah! May this work be beneficial for the Ummah.**
