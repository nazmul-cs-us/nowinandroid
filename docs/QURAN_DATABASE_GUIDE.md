# 📖 Quran Database Integration Guide

## ✅ Successfully Integrated!

The complete Quran database has been successfully integrated into your app with all **114 Surahs** and **6236 Ayahs**.

---

## 📦 What's Included

### 1. **Database File**
- **Location**: `app/src/main/assets/databases/quran.db`
- **Size**: ~25 MB
- **Format**: SQLite database with Room annotations
- **Content**: 
  - 114 Surahs (Chapters)
  - 6236 Ayahs (Verses)
  - Juz and Hizb information
  - Page numbers
  - Sajda (prostration) markers

### 2. **Database Components**
- `QuranEntity.kt` - Room entities for Surah, Ayah, Juz, Hizb
- `QuranDao.kt` - Data Access Object with query methods
- `QuranDatabase.kt` - Room database configuration
- `QuranRepository.kt` - Repository pattern for data access
- `QuranDatabaseModule.kt` - Hilt dependency injection

---

## 🚀 How to Use

### Basic Usage

#### 1. **Inject the Repository**
```kotlin
@AndroidEntryPoint
class YourActivity : ComponentActivity() {
    
    @Inject
    lateinit var quranRepository: QuranRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            // Load all Surahs
            quranRepository.getAllSurahsWithCounts().collect { surahs ->
                // Use surahs
            }
        }
    }
}
```

#### 2. **Get All Surahs**
```kotlin
// Flow (real-time updates)
quranRepository.getAllSurahs().collect { surahs ->
    // List of all 114 Surahs
}

// One-time read
val surahs = quranRepository.getAllSurahsWithCounts()
// Returns List<Surah> with ayah counts
```

#### 3. **Get Specific Surah**
```kotlin
// By number (1-114)
val surah = quranRepository.getSurahByNumber(1) // Al-Fatiha
println("${surah.nameEnglish} - ${surah.nameArabic}")
println("Type: ${surah.revelationType}")
println("Ayah count: ${surah.ayahCount}")
```

#### 4. **Get Ayahs**
```kotlin
// Get all ayahs for a surah
quranRepository.getAyahsBySurah(surahId).collect { ayahs ->
    ayahs.forEach { ayah ->
        println("Ayah ${ayah.numberInSurah}: ${ayah.text}")
    }
}

// Get specific ayah by its global number
val ayah = quranRepository.getAyahByNumber(1) // First ayah
println(ayah.text)
```

#### 5. **Search**
```kotlin
// Search Surahs
quranRepository.searchSurahs("Baqara").collect { surahs ->
    // Results containing "Baqara"
}

// Search Ayahs
quranRepository.searchAyahs("الله").collect { ayahs ->
    // All ayahs containing "الله"
}

// Search with limit
val results = quranRepository.searchAyahsWithLimit("الله", limit = 50)
```

#### 6. **Get by Juz/Page/Hizb**
```kotlin
// Get ayahs by Juz (part)
quranRepository.getAyahsByJuz(1).collect { ayahs ->
    // All ayahs in Juz 1
}

// Get ayahs by page
quranRepository.getAyahsByPage(1).collect { ayahs ->
    // All ayahs on page 1
}

// Get ayahs with Sajda
quranRepository.getSajdaAyahs().collect { ayahs ->
    // All ayahs requiring prostration
}
```

---

## 🗂️ Data Models

### **Surah**
```kotlin
data class Surah(
    val id: Int,                    // Internal ID
    val number: Int,                // Surah number (1-114)
    val nameArabic: String,         // Arabic name
    val nameEnglish: String,        // English name
    val nameTranslation: String,    // English translation
    val revelationType: String,     // "Meccan" or "Medinan"
    val ayahCount: Int              // Number of ayahs
)
```

### **Ayah**
```kotlin
data class Ayah(
    val id: Int,                    // Internal ID
    val number: Int,                // Global ayah number (1-6236)
    val text: String,               // Arabic text
    val numberInSurah: Int,         // Ayah number within surah
    val page: Int,                  // Mushaf page number
    val surahId: Int,               // Parent surah ID
    val hizbId: Int,                // Hizb (section) number
    val juzId: Int,                 // Juz (part) number
    val sajda: Boolean              // Requires prostration
)
```

---

## 🎯 Common Use Cases

### **Example 1: Display Surah List**
```kotlin
@Composable
fun SurahListScreen(quranRepository: QuranRepository) {
    val surahs by quranRepository.getAllSurahs()
        .collectAsState(initial = emptyList())
    
    LazyColumn {
        items(surahs) { surah ->
            SurahCard(surah)
        }
    }
}
```

### **Example 2: Display Ayahs in a Surah**
```kotlin
@Composable
fun SurahDetailScreen(
    surahId: Int,
    quranRepository: QuranRepository
) {
    val ayahs by quranRepository.getAyahsBySurah(surahId)
        .collectAsState(initial = emptyList())
    
    LazyColumn {
        items(ayahs) { ayah ->
            Text(
                text = ayah.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
```

### **Example 3: Search Functionality**
```kotlin
@Composable
fun QuranSearchScreen(quranRepository: QuranRepository) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by quranRepository.searchAyahs(searchQuery)
        .collectAsState(initial = emptyList())
    
    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Quran") }
        )
        
        LazyColumn {
            items(searchResults) { ayah ->
                AyahSearchResult(ayah)
            }
        }
    }
}
```

---

## 📊 Database Statistics

- **Total Surahs**: 114
- **Total Ayahs**: 6,236
- **Meccan Surahs**: 86
- **Medinan Surahs**: 28
- **Total Juz (Parts)**: 30
- **Total Hizb (Sections)**: 60
- **Total Pages**: 604

---

## ✨ Features

✅ **Complete Quran Text** - All 114 Surahs with 6,236 Ayahs  
✅ **Fast Search** - Indexed database for quick searches  
✅ **Offline Access** - Database is embedded in the app  
✅ **Room Integration** - Type-safe queries with Room  
✅ **Hilt Injection** - Easy dependency injection  
✅ **Flow Support** - Reactive data streams  
✅ **Juz & Hizb Info** - Complete organizational structure  
✅ **Sajda Markers** - Prostration ayahs marked  
✅ **Page Numbers** - Mushaf page references  

---

## 🔧 Advanced Usage

### **Check Database Status**
```kotlin
lifecycleScope.launch {
    val isValid = quranRepository.isDatabaseInitialized()
    if (isValid) {
        Log.d("Quran", "✅ Database loaded successfully")
    }
}
```

### **Get Statistics**
```kotlin
val totalAyahs = quranRepository.getTotalAyahCount()
val ayahsInSurah = quranRepository.getAyahCount(surahId)
```

### **Pagination**
```kotlin
// Get page 0 with 20 ayahs per page
val ayahsPage = quranRepository.getAyahsPage(page = 0, pageSize = 20)
```

---

## 📝 Notes

1. **Database Size**: The database is ~25 MB, which will be included in your APK.
2. **First Load**: The database is loaded from assets on first run.
3. **Performance**: All queries are indexed for optimal performance.
4. **Arabic Text**: Full Arabic text with diacritics (Tashkeel).
5. **Translations**: Currently only Arabic text is included. Translations can be added separately.

---

## 🎉 You're All Set!

The Quran database is now fully integrated and ready to use in your app. Simply inject `QuranRepository` wherever you need to access Quran data.

For questions or issues, refer to the code documentation in the database module files.

---

**May Allah accept this work and make it beneficial for the Ummah. Ameen.** 🤲

