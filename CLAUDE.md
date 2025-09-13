# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Common Development Commands

### Building
- `./gradlew assembleDebug` - Build debug APK for all flavors
- `./gradlew assembleDemoDebug` - Build debug APK for demo flavor (uses local data)
- `./gradlew assembleProdDebug` - Build debug APK for prod flavor (requires backend server)
- `./gradlew assembleRelease` - Build release APKs
- `./gradlew build` - Full build including tests

### Installation
- `./gradlew installDemoDebug` - Install demo debug APK on connected device
- `./gradlew installDemoDebug && adb shell monkey -p com.starception.submission.demo.debug -c android.intent.category.LAUNCHER 1` - Install and auto-launch app
- `adb -s 4B221FDAP002T6 install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk` - Install APK on Pixel 9 Pro device
- `adb devices` - List connected Android devices

**Note**: Always install on Pixel device (4B221FDAP002T6) when multiple devices are connected.

### Testing
- `./gradlew testDemoDebug` - Run unit tests for demo debug variant
- `./gradlew connectedDemoDebugAndroidTest` - Run instrumented tests for demo debug variant
- `./gradlew recordRoborazziDemoDebug` - Record new screenshot test baselines
- `./gradlew verifyRoborazziDemoDebug` - Verify screenshot tests against baselines

### Code Quality
- `./gradlew lint` - Run lint checks on default variant
- `./gradlew lintDemoDebug` - Run lint on demo debug variant
- `./gradlew check` - Run all verification tasks (tests, lint, etc.)
- `./gradlew assembleRelease -PenableComposeCompilerMetrics=true` - Generate Compose compiler metrics

## Architecture

This is a fully modularized Android app following official Android architecture guidance with three layers:

### Core Modules
- **core:model** - Data models and entities
- **core:data** - Repository implementations and data layer logic  
- **core:database** - Room database and DAOs
- **core:datastore** - Proto DataStore for user preferences
- **core:network** - Network API definitions and implementations
- **core:common** - Shared utilities and extensions
- **core:ui** - Reusable UI components
- **core:designsystem** - Design system components and theming
- **core:domain** - Use cases and business logic
- **core:analytics** - Analytics tracking
- **core:notifications** - Push notification handling

### Feature Modules
- **feature:foryou** - Personalized content feed
- **feature:interests** - Topic/interest selection and management
- **feature:bookmarks** - Saved articles functionality
- **feature:search** - Content search functionality
- **feature:settings** - App settings and preferences
- **feature:topic** - Individual topic detail screens

### App-Specific Features
- **Prayer Times** - Islamic prayer times calculator and display with comprehensive architecture
  - **UI Layer**: Located in `app/src/main/kotlin/com/starception/submission/feature/prayertimes/`
    - `PrayerTimesScreen.kt` - Main screen with Material 3 design and real-time updates
    - Components: `AnalogWatchComponents.kt`, `PrayerTimeCards.kt`
    - Navigation: `PrayerTimesNavigation.kt`
    - Utilities: `PrayerTimesUtils.kt`
  - **Core Prayer System**: Located in `app/src/main/kotlin/com/starception/submission/prayer/`
    - **Model**: Data classes for `CalculationMethod`, `Location`, `PrayerSettings`, `PrayerTime`
    - **Calculator**: `AstronomicalCalculator.kt` for precise prayer time calculations
    - **Repository**: `PrayerSettingsRepository.kt` for settings persistence
    - **Services**: Enhanced location services and prayer time calculation services
    - **ViewModels**: `PrayerTimesViewModel.kt` for state management
    - **UI**: Prayer settings screens and dialogs
  - **Notification System**: `PrayerNotificationService.kt` with background updates
  - **Permissions**: Location and notification permission handling
  - **Features**: Location-based calculations, real-time updates, notification alerts, Material 3 design

### App Module
- **app** - Main application module, navigation, and dependency injection setup

### Build Flavors
- **demo** - Uses local JSON data for immediate testing
- **prod** - Connects to backend server (not publicly available)

### Key Architectural Patterns
- Unidirectional data flow with Kotlin Flows
- Repository pattern for data access
- Use cases for business logic
- Jetpack Compose for UI with Material 3 design system
- Dependency injection with Hilt
- No mocking in tests - uses test doubles implementing real interfaces
- MVVM architecture with ViewModels for state management
- Comprehensive permission management system
- Background services for real-time updates

## Development Notes

### Recommended Workflow
1. Use `demoDebug` build variant for normal development
2. Use `demoRelease` for UI performance testing
3. Run `recordRoborazziDemoDebug` before running tests if screenshot tests are failing
4. Always run verification tasks before committing changes

### Testing Strategy
- Unit tests use test repositories with additional testing hooks
- Screenshot tests use Roborazzi framework
- Instrumented tests run against real DataStore in temporary directories
- No mocking libraries - all test doubles implement production interfaces

### Package Structure
The app uses `com.starception.submission` as the base package (originally forked from Google's Now in Android sample).

**Prayer Times Package Structure:**
- `feature.prayertimes` - UI layer with screens, components, and navigation
- `prayer.model` - Core data models and entities
- `prayer.calculator` - Astronomical calculations for prayer times
- `prayer.repository` - Data persistence and settings management
- `prayer.service` - Location and calculation services
- `prayer.viewmodel` - State management with ViewModels
- `prayer.ui` - Reusable prayer-related UI components
- `services` - Background services and notifications
- `util` - Utility classes for permissions and extensions

### Important Files
- `gradle/libs.versions.toml` - Centralized dependency management (updated to latest versions)
- `build-logic/convention/` - Gradle convention plugins for consistent module setup
- `app/src/main/baseline-prof.txt` - Baseline profile for app startup optimization
- `gradle.properties` - Optimized Gradle configuration with memory settings
- `app/src/main/kotlin/com/starception/submission/prayer/` - Core prayer times system
- `app/src/main/kotlin/com/starception/submission/feature/prayertimes/PrayerTimesScreen.kt` - Main prayer times UI

### Documentation
- `docs/PRAYER_TIMES_TECHNICAL_GUIDE.md` - Comprehensive technical guide for the prayer times system
- `docs/PRAYER_CALCULATION_METHODOLOGY.md` - Detailed explanation of Islamic prayer time calculations
- `README.md` - Updated with detailed prayer time features and capabilities

## UI Design System

### Card Design Guidelines
All cards in the app follow a consistent design pattern:
- **Shape**: `RoundedCornerShape(16.dp)`
- **Colors**: `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)`
- **Internal padding**: `16.dp`
- **External margins**: `16.dp` on all sides for screen-level content
- **Click handling**: Cards should have `onClick` functionality where applicable

### Recent Updates
- Enhanced Prayer Times feature with comprehensive architecture (August 2025)
- Implemented Material 3 expressive design with asymmetrical shapes and layered backgrounds
- Added real-time prayer status tracking (Current/Next/Upcoming prayers)
- Integrated enhanced location services with permission management
- Added comprehensive notification system with prayer alerts
- Updated dependency versions to latest stable releases (Kotlin 2.1.10, Compose BOM 2025.02.00)
- Improved build configuration with optimized Gradle settings

## Prayer Times Recent UI Improvements (September 2025)

### Qibla Compass Enhancements
- **Removed Time Display**: Eliminated remaining prayer time text from Qibla compass components to prevent visual clutter
  - Updated `QiblaCompass.kt` and `CompassProgressIndicator.kt` to remove `timeText` parameter and display
  - Modified `SwipeableBigTiles.kt` and `SalahDashboard.kt` to remove time text from component calls

### Compass Popup Improvements  
- **Pull-down Gesture**: Added intuitive pull-down to close gesture for the compass popup window
  - Implemented `detectVerticalDragGestures` with visual feedback using a draggable handle bar
  - Added drag threshold detection to trigger popup dismissal on sufficient downward swipe
  - Isolated gesture handling to specific handle area to prevent conflicts with scroll views

- **UI Consistency**: Fixed close button sizing to match the settings icon from the home page
  - Standardized button sizing using consistent Material 3 `IconButton` components
  - Ensured visual harmony between navigation elements across the app

- **Theme Integration**: Updated popup to use Material 3 theme colors instead of hardcoded black
  - Replaced hardcoded black background with `MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)`
  - Updated all text colors to use theme-aware color schemes (`MaterialTheme.colorScheme.onSurface`)
  - Enables proper light/dark mode support and maintains design consistency

### Smart Content Optimization
- **Text Display Improvements**: Resolved text truncation issues in Smart Prediction tiles
  - Modified content generation functions to produce shorter, more display-friendly text
  - Optimized layout to prevent text cutoff while maintaining readability
  - Updated `SmartContentUtils.kt` with better content formatting for various tile sizes

## Prayer Settings Auto-Detection System

### Implementation Details
The Prayer Settings Auto-Detection System automatically configures appropriate Islamic prayer calculation methods based on the user's location. The system follows a specific algorithm implemented in `PrayerSettingsRepository.kt`.

#### Algorithm Flow
1. **Initialization**: Detect cached country → Load auto-detected settings → Load cached settings → Populate UI
2. **User Changes**: Update cached_prayer_settings → Recalculate times  
3. **Restore Logic**: Compare cached vs auto-detected JSON → Show/hide restore option

#### Core Components
- **`getAutoDetectedSettingsForCountry(countryCode: String)`**: Loads country-specific prayer settings from JSON database
- **`getCachedCountry()`**: Extracts country code from current location settings with comprehensive debugging
- **`initializeSettings()`**: Implements the 3-step algorithm for settings initialization
- **`shouldShowRestoreOption()`**: Compares settings to determine restore button visibility
- **`restoreToAutoDetected()`**: Restores original auto-detected settings from backup

#### Comprehensive Logging System
The system includes emoji-based structured logging for debugging:
- **🔍 CACHED COUNTRY DEBUG**: Detailed location data analysis
- **🌍 COUNTRY AUTO-DETECTION**: Country lookup process
- **📦 JSON LOADING**: Database loading performance (<1ms typical)
- **📊 JSON PARSING**: Parsing performance and available countries list
- **🔍 Country found/not found**: Lookup results with potential matches
- **⚙️ CUSTOM ANGLES**: Detection of region-specific calculation parameters
- **🔄 METHOD MAPPING**: Conversion from JSON names to enum values
- **✅ AUTO-DETECTION COMPLETE**: Final results summary with timing

#### Error Handling & Debugging
- **Country Code Validation**: Lists all available countries when lookup fails
- **Potential Match Detection**: Shows similar country codes for troubleshooting
- **Performance Monitoring**: Tracks JSON loading and parsing times
- **Backup System**: Maintains original auto-detected settings for restore functionality

## Country-Based Prayer Calculation Methods

### JSON Data Integration
- **Comprehensive Database**: Added `country_prayer_methods.json` in `app/src/main/assets/`
  - Contains 80+ countries with appropriate calculation methods and madhab selections
  - Includes calculation method parameters (Fajr/Isha angles, offsets) for precise astronomical calculations
  - Provides madhhab-specific Asr calculation ratios and regional information

### Location-Based Auto-Configuration
- **Smart Defaults**: Automatic prayer calculation method selection based on detected country location
  - Example: UAE → Uses "Umm_al_Qura_University_Makkah" method with "Maliki" madhhab
  - Each country entry includes appropriate calculation method, madhhab, and geographic coordinates
  - Supports 6 different madhhab options: Hanafi, Shafi, Maliki, Hanbali, Jafari, Ibadi

### Data Structure Features
- **Countries**: ISO country codes mapped to prayer calculation preferences
- **Calculation Methods**: 25+ recognized Islamic calculation methods with precise parameters
- **Madhhab Options**: Shadow ratio configurations for different schools of Islamic jurisprudence
- **Geographic Data**: Coordinate information for location-based validation and defaults

## Auto-Detection System Logging

### Comprehensive Logging Framework
- **Structured Logs**: Emoji-based categorization for easy identification and filtering
- **Performance Monitoring**: Database loading times, geocoding performance tracking
- **Debug Support**: Complete flow visibility from GPS coordinates to final settings
- **Error Tracking**: Detailed error reporting with fallback scenario logging

### Logging Categories
- **🌍 Location Detection**: GPS processing and reverse geocoding status
- **📦 Data Loading**: JSON database loading and parsing performance  
- **🔄 Method Mapping**: Calculation method and madhhab mapping process
- **📊 Final Results**: Complete auto-detection results summary
- **⚠️ Error Handling**: Warning and error scenarios with context
- **🔧 UI Interactions**: Settings screen auto-detection indicator updates

### Development Commands
```bash
# View all auto-detection logs (updated service names)
adb logcat -s "PrayerSettingsRepository" "PrayerSettingsDialog"

# View only errors and warnings  
adb logcat "*:W" -s "PrayerSettingsRepository"

# View location and country detection flow
adb logcat -s "PrayerSettingsRepository" | grep "🌍\|🔍\|🏳️\|📦\|📊"

# Monitor performance and results
adb logcat -s "PrayerSettingsRepository" | grep "✅.*ms\|📊.*countries\|🔍.*Country found"

# Debug country lookup issues
adb logcat -s "PrayerSettingsRepository" | grep "CACHED COUNTRY\|Looking for country\|All available countries"
```

### Documentation
- `docs/AUTO_DETECTION_LOGGING_GUIDE.md` - Complete logging guide with debugging examples
- Structured log format enables integration with crash reporting and analytics
- Performance benchmarks for database loading (<50ms optimal) and geocoding (<2s normal)