# Asset Inventory

This document tracks ALL assets used by the app, whether bundled in the APK or stored externally (SD card).

**Purpose:** Future migration to Cloudflare CDN for asset delivery.

**Last Updated:** February 2026

---

## Summary

| Storage Location | Total Size | File Count | Status |
|------------------|------------|------------|--------|
| APK (assets/) | ~2.7GB | 5,400+ | Bundled |
| SD Card (/sdcard/Quran/) | ~5.6GB | 343 | External |
| **TOTAL** | **~8.3GB** | **5,700+** | |

---

## 1. APK-BUNDLED ASSETS

Assets stored in `app/src/main/assets/` and packaged in the APK.

### 1.1 Audio Files

#### Bukhari Hadith Audio (Bengali)
| Path | Size | Files | Format | Used By |
|------|------|-------|--------|---------|
| `bukhari_audio_bn/` | 2.1GB | 5,323 | .ogg/.mp3 | `ActivityTracker.kt`, `HadithDetailScreen.kt` |

**File Pattern:** `bukhari_{number}.ogg` or `bukhari_{number}.mp3` (e.g., `bukhari_0001.ogg`)

**Code Reference:**
```kotlin
// ActivityTracker.kt:844-845
"bukhari_audio_bn/bukhari_$formattedNumber.ogg"
"bukhari_audio_bn/bukhari_$formattedNumber.mp3"

// HadithDetailScreen.kt:404-405
"bukhari_audio_bn/bukhari_$formattedNumber.ogg"
"bukhari_audio_bn/bukhari_$formattedNumber.mp3"
```

---

### 1.2 Database Files

#### Quran Databases
| File | Size | Description | Used By |
|------|------|-------------|---------|
| `databases/quran.db` | 7.2MB | Arabic Quran text | `QuranEnhancedDatabase.kt` |
| `databases/quran_enhanced.db` | 31MB | Enhanced Quran with tajweed | `QuranEnhancedDatabase.kt` |
| `databases/quran_bn.db` | 2.8MB | Bengali translation | Translation service |
| `databases/quran_en.db` | 1.4MB | English translation | Translation service |
| `databases/quran_es.db` | 1.4MB | Spanish translation | Translation service |
| `databases/quran_fr.db` | 1.4MB | French translation | Translation service |
| `databases/quran_id.db` | 1.6MB | Indonesian translation | Translation service |
| `databases/quran_ru.db` | 2.0MB | Russian translation | Translation service |
| `databases/quran_sv.db` | 1.5MB | Swedish translation | Translation service |
| `databases/quran_tr.db` | 1.5MB | Turkish translation | Translation service |
| `databases/quran_ur.db` | 2.1MB | Urdu translation | Translation service |
| `databases/quran_zh.db` | 1.2MB | Chinese translation | Translation service |
| `databases/quran_transliteration.db` | 1.0MB | Romanized transliteration | Translation service |

#### Hadith Databases
| File | Size | Description | Used By |
|------|------|-------------|---------|
| `databases/hadith/sahih_bukhari.db` | 17MB | Sahih Bukhari | `HadithDatabase.kt` |
| `databases/hadith/sahih_muslim.db` | 55MB | Sahih Muslim | `HadithDatabase.kt` |
| `databases/hadith/sunan_abu_dawud.db` | 61MB | Sunan Abu Dawud | `HadithDatabase.kt` |
| `databases/hadith/sunan_tirmidhi.db` | 71MB | Sunan Tirmidhi | `HadithDatabase.kt` |
| `databases/hadith/sunan_nasai.db` | 32MB | Sunan Nasai | `HadithDatabase.kt` |
| `databases/hadith/sunan_ibn_majah.db` | 27MB | Sunan Ibn Majah | `HadithDatabase.kt` |
| `databases/hadith/sunan_darimi.db` | 12MB | Sunan Darimi | `HadithDatabase.kt` |
| `databases/hadith/muwatta_malik.db` | 28MB | Muwatta Malik | `HadithDatabase.kt` |
| `databases/hadith/musnad_ahmad.db` | 112MB | Musnad Ahmad | `HadithDatabase.kt` |
| `databases/hadith/hadith_index.db` | 12KB | Hadith index | `HadithDatabase.kt` |

**Code Reference:**
```kotlin
// HadithDatabase.kt:103
context.assets.open("$HADITH_DB_PATH$databaseFile").use { input ->
```

#### Dua & Islamic Databases
| File | Size | Description | Used By |
|------|------|-------------|---------|
| `databases/fortress_of_the_muslim.db` | 360KB | Hisnul Muslim duas | `DuaDatabase.kt` |
| `databases/fortress_of_the_muslim_backup.db` | 131KB | Backup | Backup |
| `databases/quranic_duas.db` | 41KB | Quranic duas | `QuranicDuaDatabase.kt` |
| `databases/topics.db` | 16KB | Topics database | `TopicsDatabase.kt` |
| `databases/news.db` | 5.9MB | Generated news content | `DuaDetailScreen.kt` |

---

### 1.3 JSON Data Files

| File | Size | Description | Used By |
|------|------|-------------|---------|
| `country_prayer_methods.json` | 60KB | 80+ countries prayer methods | `PrayerSettingsRepository.kt`, `CountryPrayerMethodService.kt` |
| `sahih_bukhari.json` | 3.9MB | Bukhari hadith translations | `BukhariLocalTranslation.kt` |
| `tajweed.json` | 5.3MB | Tajweed rules per ayah | `TajweedParser.kt` |
| `fortress_of_the_muslim.json` | 132KB | Hisnul Muslim JSON | Legacy |

**Code References:**
```kotlin
// PrayerSettingsRepository.kt:1422
context.assets.open("country_prayer_methods.json")

// BukhariLocalTranslation.kt:36
context.assets.open("sahih_bukhari.json")

// TajweedParser.kt:25
context.assets.open(TAJWEED_FILE) // "tajweed.json"
```

---

### 1.4 ML Model Files

#### Whisper Speech Recognition (STT)
| File | Size | Description | Used By |
|------|------|-------------|---------|
| `whisper/whisper-tiny.en.tflite` | 40MB | TensorFlow Lite model | `WhisperVoiceService.kt` |
| `whisper/filters_vocab_en.bin` | 572KB | Vocabulary file | `WhisperVoiceService.kt` |

**Code Reference:**
```kotlin
// WhisperVoiceService.kt:56-57
private const val MODEL_FILE = "whisper/whisper-tiny.en.tflite"
private const val VOCAB_FILE = "whisper/filters_vocab_en.bin"
```

#### Sherpa-ONNX TTS (Text-to-Speech)
| File | Size | Description | Used By |
|------|------|-------------|---------|
| `tts/en_US-lessac-medium.onnx` | 60MB | VITS voice model | `SherpaOnnxTtsService.kt` |
| `tts/en_US-lessac-medium.onnx.json` | 5KB | Model config | `SherpaOnnxTtsService.kt` |
| `tts/tokens.txt` | 1KB | Token vocabulary | `SherpaOnnxTtsService.kt` |
| `tts/espeak-ng-data/` | 18MB | eSpeak NG phoneme data | `SherpaOnnxTtsService.kt` |

**Code Reference:**
```kotlin
// SherpaOnnxTtsService.kt:55-57
private const val MODEL_FILE = "en_US-lessac-medium.onnx"
private const val TOKENS_FILE = "tokens.txt"
private const val DATA_DIR = "espeak-ng-data"
```

---

## 2. EXTERNAL ASSETS (SD CARD)

Assets stored on device SD card at `/sdcard/Quran/`. Must be copied separately.

### 2.1 Quran Audio (Full Surahs)

| Path | Size | Files | Reciter/Content | Used By |
|------|------|-------|-----------------|---------|
| `/sdcard/Quran/Arabic/` | ~1.1GB | 114 | Mishary Rashid Alafasy (Arabic) | `QuranPlaybackService.kt` |
| `/sdcard/Quran/Bengali/` | ~1.9GB | 114 | Bengali translation | `QuranPlaybackService.kt` |
| `/sdcard/Quran/English/` | ~1.8GB | 115 | English translation | `QuranPlaybackService.kt` |

**File Pattern:**
- Arabic: `001-al-fatihah.ogg`, `002-al-baqarah.ogg`, etc.
- English: `001 surah_al_fatihah.ogg`, etc.
- Bengali: Sorted by filename

**Code Reference:**
```kotlin
// QuranPlaybackService.kt:41-43
private val quranArabicPath = "/sdcard/Quran/Arabic"
private val quranBengaliPath = "/sdcard/Quran/Bengali"
private val quranEnglishPath = "/sdcard/Quran/English"

// QuranPlaybackService.kt:286-304
private fun getAudioFile(index: Int): File {
    val surah = QuranData.surahs[index]
    return when (audioLanguage) {
        AudioLanguage.ARABIC_ONLY -> {
            val fileName = String.format("%03d", surah.number) + "-" + ...
            File(quranArabicPath, fileName)
        }
        // etc.
    }
}
```

**Backup Location:** `/Users/smarterai/Desktop/quran/`

---

## 3. CLOUDFLARE MIGRATION PLAN

### 3.1 Priority Order (by size & impact)

| Priority | Asset | Size | Migration Approach |
|----------|-------|------|-------------------|
| 1 | SD Card Quran Audio | 5.6GB | Stream from CDN, cache locally |
| 2 | Bukhari Audio | 2.1GB | Download on-demand, cache |
| 3 | Hadith Databases | 415MB | Download on first use |
| 4 | Quran Databases | 55MB | Download on language select |
| 5 | TTS Model | 78MB | Keep bundled (required for offline) |
| 6 | Whisper Model | 40MB | Keep bundled (required for offline) |
| 7 | JSON files | 10MB | Keep bundled (small) |

### 3.2 Suggested CDN Structure

```
cdn.starception.com/
├── audio/
│   ├── quran/
│   │   ├── arabic/
│   │   │   ├── mishary/
│   │   │   │   ├── 001.ogg
│   │   │   │   └── ...
│   │   ├── bengali/
│   │   └── english/
│   └── hadith/
│       └── bukhari/
│           └── bengali/
│               ├── 0001.ogg
│               └── ...
├── databases/
│   ├── quran/
│   │   ├── quran.db
│   │   ├── quran_bn.db
│   │   └── ...
│   └── hadith/
│       ├── sahih_bukhari.db
│       └── ...
└── models/
    ├── whisper-tiny.en.tflite
    └── en_US-lessac-medium.onnx
```

### 3.3 Code Changes Required

1. **Create AssetManager wrapper:**
   - Check local cache first
   - Download from CDN if not cached
   - Support progress callbacks

2. **Update paths in:**
   - `QuranPlaybackService.kt` (SD card → CDN/cache)
   - `HadithDatabase.kt` (assets → CDN/cache)
   - `ActivityTracker.kt` (bukhari audio)

3. **Add download UI:**
   - Settings → "Download Content"
   - Progress indicators
   - Storage management

---

## 4. FILE REFERENCE INDEX

Quick lookup by code file:

| Code File | Assets Used |
|-----------|-------------|
| `QuranPlaybackService.kt` | `/sdcard/Quran/Arabic/*.ogg`, `/sdcard/Quran/Bengali/*.ogg`, `/sdcard/Quran/English/*.ogg` |
| `ActivityTracker.kt` | `bukhari_audio_bn/*.ogg`, `bukhari_audio_bn/*.mp3` |
| `HadithDetailScreen.kt` | `bukhari_audio_bn/*.ogg`, `bukhari_audio_bn/*.mp3` |
| `HadithDatabase.kt` | `databases/hadith/*.db` |
| `QuranEnhancedDatabase.kt` | `databases/quran.db`, `databases/quran_enhanced.db` |
| `DuaDatabase.kt` | `databases/fortress_of_the_muslim.db` |
| `QuranicDuaDatabase.kt` | `databases/quranic_duas.db` |
| `TopicsDatabase.kt` | `databases/topics.db` |
| `DuaDetailScreen.kt` | `databases/news.db` |
| `PrayerSettingsRepository.kt` | `country_prayer_methods.json` |
| `CountryPrayerMethodService.kt` | `country_prayer_methods.json` |
| `BukhariLocalTranslation.kt` | `sahih_bukhari.json` |
| `TajweedParser.kt` | `tajweed.json` |
| `WhisperVoiceService.kt` | `whisper/whisper-tiny.en.tflite`, `whisper/filters_vocab_en.bin` |
| `SherpaOnnxTtsService.kt` | `tts/en_US-lessac-medium.onnx`, `tts/tokens.txt`, `tts/espeak-ng-data/` |

---

## 5. GITIGNORE STATUS

Assets excluded from git (must be backed up separately):

```gitignore
# Audio files
app/src/main/assets/bukhari_audio_bn/

# Database files
app/src/main/assets/databases/*.db
app/src/main/assets/databases/hadith/*.db

# ML models
app/src/main/assets/whisper/*.tflite
app/src/main/assets/whisper/*.bin
app/src/main/assets/tts/*.onnx
app/src/main/assets/tts/espeak-ng-data/

# Large JSON files
app/src/main/assets/sahih_bukhari.json
app/src/main/assets/tajweed.json
app/src/main/assets/fortress_of_the_muslim.json

# AAR libraries
app/libs/*.aar
```

**Tracked in git (small files):**
- `country_prayer_methods.json` (60KB)
- `tts/tokens.txt` (1KB)
- `tts/MODEL_CARD` (351B)
- `tts/en_US-lessac-medium.onnx.json` (5KB)
