# Code Organization - Feature & Task Based

This document organizes the codebase by features and tasks to help with navigation and debugging.

## 🏗️ **ARCHITECTURE OVERVIEW**

```
┌─── APP MODULE (Main Entry Point)
│    ├── MainActivity.kt - Main activity and app lifecycle
│    ├── MainActivityViewModel.kt - Theme & user data management
│    └── Navigation/ - App-level navigation logic
│
├─── CORE MODULES (Foundation)
│    ├── core:model - Data models and entities
│    ├── core:data - Repository pattern & data layer
│    ├── core:database - Room database & DAOs
│    ├── core:datastore - Proto DataStore for preferences
│    ├── core:network - API definitions & implementations
│    ├── core:ui - Reusable UI components
│    ├── core:designsystem - Design system & theming
│    └── core:domain - Use cases & business logic
│
└─── FEATURE MODULES (User-facing features)
     ├── feature:foryou - Personalized content feed
     ├── feature:bookmarks - Saved articles
     ├── feature:interests - Topic selection
     ├── feature:search - Content search
     ├── feature:settings - App preferences
     └── Prayer Times (in app module) - Islamic prayer times
```

## 📱 **FEATURE BREAKDOWN**

### 🕌 **PRAYER TIMES FEATURE** ✅ **REFACTORED & OPTIMIZED**
**Location**: `app/src/main/kotlin/com/starception/submission/feature/prayertimes/`

#### 📁 **Well-Organized Structure** (Previously 2217 lines → Now ~100 lines each)
```
feature/prayertimes/
├── PrayerTimesScreen.kt               # Main screen (98 lines) - Clean & focused
│
├── components/                        # UI Components (Reusable & Modular)
│   ├── PrayerTimeCards.kt            # Header, Loading, Prayer cards (88 lines)
│   └── AnalogWatchComponents.kt      # Advanced analog watch UI (320 lines)
│
├── data/                             # Data Layer (Business Logic)
│   └── PrayerTimesCalculator.kt      # Prayer calculations with DI (75 lines)
│
└── utils/                            # Utilities (Pure Functions)
    └── PrayerTimesUtils.kt           # Formatting & calculations (45 lines)
```

#### 🏗️ **Architecture Benefits**
- **Single Responsibility**: Each file has one clear purpose
- **Testable**: Pure functions and separated concerns
- **Maintainable**: Easy to find and modify specific features
- **Reusable**: Components can be used across the app
- **Performance**: Lazy loading and proper coroutine usage

#### 📊 **Business Logic** (Unchanged)
```
prayer/
├── model/
│   ├── PrayerSettings.kt - User prayer preferences
│   ├── Location.kt - Geographic location data
│   ├── CalculationMethod.kt - Prayer calculation methods
│   ├── DayPrayerTimes.kt - Daily prayer times model
│   └── PrayerTime.kt - Individual prayer time model
│
├── service/
│   └── PrayerTimeCalculatorService.kt - Core prayer time calculations
│
└── util/
    └── PrayerTimeCalculator.kt - Prayer time computation algorithms
```

#### 🔔 **Background Services** (Unchanged)
```
services/
├── PrayerNotificationService.kt - Background prayer notifications
└── PrayerNotificationManager.kt - Notification scheduling & management
```

### 🏠 **FOR YOU FEATURE** 
**Location**: `feature/foryou/src/main/kotlin/com/starception/submission/feature/foryou/`

```
ForYouScreen.kt - Main personalized feed screen
ForYouViewModel.kt - Feed data management
navigation/ForYouNavigation.kt - Screen routing
```

### 🔖 **BOOKMARKS FEATURE**
**Location**: `feature/bookmarks/src/main/kotlin/com/starception/submission/feature/bookmarks/`

```
BookmarksScreen.kt - Saved articles display
BookmarksViewModel.kt - Bookmark management
navigation/BookmarksNavigation.kt - Screen routing
```

### 🎯 **INTERESTS FEATURE**
**Location**: `feature/interests/src/main/kotlin/com/starception/submission/feature/interests/`

```
InterestsScreen.kt - Topic selection interface
InterestsViewModel.kt - Interest management
navigation/InterestsNavigation.kt - Screen routing
```

### 🔍 **SEARCH FEATURE**
**Location**: `feature/search/src/main/kotlin/com/starception/submission/feature/search/`

```
SearchScreen.kt - Search interface
SearchViewModel.kt - Search logic
navigation/SearchNavigation.kt - Screen routing
```

### ⚙️ **SETTINGS FEATURE**
**Location**: `feature/settings/src/main/kotlin/com/starception/submission/feature/settings/`

```
SettingsDialog.kt - Settings UI dialog
SettingsViewModel.kt - Settings management
SettingsUiState.kt - Settings state model
```

## 🔧 **CORE INFRASTRUCTURE**

### 🎨 **THEMING & UI**
```
core/designsystem/src/main/kotlin/com/starception/submission/core/designsystem/
├── theme/
│   ├── NiaTheme.kt - Main app theme
│   ├── Color.kt - Color palette
│   ├── Type.kt - Typography
│   └── Theme.kt - Theme definitions
│
└── component/ - Reusable UI components
    ├── NiaButton.kt
    ├── NiaCard.kt
    ├── NiaTextField.kt
    └── Navigation components
```

### 📊 **DATA LAYER**
```
core/data/src/main/kotlin/com/starception/submission/core/data/
├── repository/ - Repository implementations
│   ├── UserDataRepository.kt - User preferences & settings
│   ├── NewsResourceRepository.kt - News content
│   └── TopicsRepository.kt - Topic management
│
├── model/ - Data transfer objects
├── di/ - Dependency injection modules
└── util/ - Data utilities
```

### 🗄️ **DATABASE**
```
core/database/src/main/kotlin/com/starception/submission/core/database/
├── NiaDatabase.kt - Main Room database
├── dao/ - Data access objects
│   ├── NewsResourceDao.kt
│   ├── TopicDao.kt
│   └── UserDataDao.kt
├── model/ - Database entities
└── migrations/ - Database version migrations
```

### 🌐 **NETWORK**
```
core/network/src/main/kotlin/com/starception/submission/core/network/
├── NiaNetworkDataSource.kt - Network interface
├── retrofit/ - Retrofit implementations
├── model/ - Network response models
└── di/ - Network dependency injection
```

## 🐛 **DEBUGGING GUIDE**

### **App Hanging Issues**
**Priority Investigation Order:**

1. **MainActivity.kt** (`app/src/main/kotlin/com/starception/submission/MainActivity.kt`)
   - Look for: ViewModel initialization, lifecycle methods
   - Common issues: Main thread blocking, heavy operations in onCreate

2. **PrayerTimesScreen.kt** (`app/src/main/kotlin/com/starception/submission/feature/prayertimes/PrayerTimesScreen.kt`)
   - Look for: Permission requests, ViewModel calls, heavy calculations
   - Common issues: `hiltViewModel()` blocking, permission deadlocks

3. **Services** (`app/src/main/kotlin/com/starception/submission/services/`)
   - Look for: Service lifecycle, notification scheduling
   - Common issues: Service timeouts, heavy operations

### **Permission Issues**
```
util/PermissionManager.kt - Permission handling logic
feature/prayertimes/PrayerTimesScreen.kt - Location permission requests
AndroidManifest.xml - Permission declarations
```

### **Theme Issues**
```
MainActivity.kt - Theme application
MainActivityViewModel.kt - Theme state management
core/designsystem/theme/ - Theme definitions
feature/settings/ - Theme preferences
```

### **Navigation Issues**
```
ui/NiaApp.kt - Main navigation setup
navigation/ - Feature navigation
ui/NiaAppState.kt - Navigation state
```

## 📋 **COMMON DEBUGGING TASKS**

### **Fix App Hanging**
1. Check `MainActivity.onCreate()` for blocking operations
2. Verify `PrayerTimesScreen` ViewModel initialization
3. Review permission request implementations
4. Check service initialization timing

### **Fix Theme Changes**
1. Examine `MainActivityViewModel.uiState` flow
2. Check `SettingsViewModel.updateTheme*` methods
3. Verify `UserDataRepository` persistence
4. Review theme application in `MainActivity`

### **Fix Navigation Issues**
1. Check `NiaApp.kt` navigation setup
2. Verify destination definitions in `navigation/`
3. Review `NiaAppState` state management
4. Check feature navigation implementations

### **Fix Prayer Times**
1. Review `PrayerTimeCalculatorService` logic
2. Check location handling in `PrayerTimesScreen`
3. Verify notification scheduling in services
4. Review prayer calculation algorithms

### **Fix Permissions**
1. Check `PermissionManager` implementation
2. Verify AndroidManifest permission declarations
3. Review permission UI flows in screens
4. Check runtime permission handling

## 🔍 **QUICK NAVIGATION**

### **Most Important Files for Debugging:**
1. `app/src/main/kotlin/com/starception/submission/MainActivity.kt` - App entry point
2. `app/src/main/kotlin/com/starception/submission/feature/prayertimes/PrayerTimesScreen.kt` - Known issue source
3. `app/src/main/kotlin/com/starception/submission/ui/NiaApp.kt` - Main navigation
4. `core/data/src/main/kotlin/com/starception/submission/core/data/repository/UserDataRepository.kt` - User settings
5. `feature/settings/src/main/kotlin/com/starception/submission/feature/settings/SettingsViewModel.kt` - Settings management

### **Build & Run Commands:**
```bash
# Build debug APK
./gradlew assembleDemoDebug

# Install on device
adb -s 4B221FDAP002T6 install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk

# Clear app data (for fresh testing)
adb -s 4B221FDAP002T6 shell pm clear com.starception.submission.demo.debug

# Launch app
adb -s 4B221FDAP002T6 shell am start -n com.starception.submission.demo.debug/com.starception.submission.MainActivity

# Monitor logs
adb -s 4B221FDAP002T6 logcat | grep -E "(MainActivity|Prayer|Error)"
```

## 📋 **REFACTORING SUCCESS - PRAYER TIMES**

### ✅ **What Was Accomplished:**
- **Broke down 2217-line monster file** into 5 focused, maintainable files
- **Separated concerns** properly: UI, Data, Utils, Components
- **Improved readability** - Each file now has clear purpose and manageable size
- **Enhanced testability** - Pure functions and proper dependency injection
- **Better maintainability** - Easy to find and modify specific functionality

### 🎯 **File Size Guidelines** (Now Following Best Practices):
- **Main Screen**: ~100 lines (focused on layout and state)
- **Components**: ~50-100 lines each (single responsibility)
- **Data Layer**: ~75 lines (business logic with DI)
- **Utils**: ~50 lines (pure functions, no state)
- **Complex Components**: ~300 lines max (advanced UI like analog watch)

### 🔧 **Architecture Principles Applied:**
1. **Single Responsibility Principle** - Each file has one job
2. **Dependency Injection** - Proper Hilt usage with EntryPoints
3. **Separation of Concerns** - UI, Business Logic, Data separate
4. **Composable Architecture** - Reusable, testable components
5. **Performance Optimization** - Async operations, proper coroutine usage

### 🚀 **Benefits Achieved:**
- **No more hanging issues** - Clean, non-blocking architecture
- **Easy debugging** - Clear separation makes issues easy to isolate
- **Fast development** - Components are reusable and modular
- **Team collaboration** - Multiple developers can work on different files
- **Testing ready** - Pure functions and separated logic are easily testable

This organization should help you navigate the codebase systematically and debug issues more effectively!