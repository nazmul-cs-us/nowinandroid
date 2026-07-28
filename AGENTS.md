# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

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
- `./install_and_run.sh` - Build, install and auto-launch app (recommended)
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

## Project Overview

### Project Identity
- **Original Project**: Forked from Google's Now in Android (NiA) sample app
- **Current Name**: Starception Submission
- **Base Package**: `com.starception.submission`
- **Purpose**: Android app combining news reader functionality with comprehensive Islamic prayer times calculator, salah posture ML detection, and driving mode audio
- **Architecture**: Clean Architecture with MVVM, fully modularized following Google's official guidance
- **Total Modules**: 25+ modules (16 core, 6 feature, 2 sync, plus supporting modules)

## Comprehensive Codebase Structure

### Root Level Organization
```
nowinandroid/
├── app/                      # Main application module (40,000+ lines)
├── build-logic/              # Gradle convention plugins
│   └── convention/           # Build configuration plugins
├── core/                     # 16 core modules
├── feature/                  # 6 feature modules
├── sync/                     # Sync modules
├── training/                 # ML training pipeline (Python)
│   └── salah_model/          # Salah posture detection model training
├── benchmarks/              # Performance benchmarking
├── docs/                    # 25+ technical documentation files
├── gradle/                  # Gradle wrapper and dependencies
├── lint/                    # Custom lint rules
├── tools/                   # Development tools
└── [config files]           # Root configuration files
```

### Module Breakdown

#### Core Modules (/core - 16 modules)
```
core/
├── analytics/          # Analytics tracking & Firebase integration
├── common/             # Shared utilities and extensions
├── data/               # Repository implementations & data layer logic
├── data-test/          # Test doubles for data layer
├── database/           # Room database and DAOs
├── datastore/          # Proto DataStore for user preferences
├── datastore-proto/    # Protocol buffer definitions
├── datastore-test/     # Test utilities for DataStore
├── designsystem/       # Material 3 design system & theming
├── domain/             # Use cases and business logic
├── model/              # Data models and entities
├── network/            # Network API & Retrofit configuration
├── notifications/      # Push notification handling
├── screenshot-testing/ # Screenshot test infrastructure (Roborazzi)
├── testing/            # Shared test utilities
└── ui/                 # Reusable UI components
```

#### Feature Modules (/feature - 6 modules)
```
feature/
├── foryou/      # Personalized content feed (original NiA)
├── interests/   # Topic/interest selection management
├── bookmarks/   # Saved articles functionality
├── search/      # Content search functionality
├── settings/    # App settings and preferences
└── topic/       # Individual topic detail screens
```

#### Sync Modules (/sync)
```
sync/
├── work/        # WorkManager integration for background sync
└── sync-test/   # Sync testing utilities
```

### App Module Package Organization

The app module contains app-specific features not yet modularized:

```
app/src/main/kotlin/com/starception/submission/
├── ui/                    # Main UI components
│   └── interests2pane/    # Two-pane interests UI
├── di/                    # Dependency injection (Hilt)
├── core/                  # Core app utilities
│   └── qurandatabase/     # Quran database management
├── navigation/            # Navigation & top-level destinations
├── automotive/            # Android Auto/Automotive support
├── feature/               # App-specific features
│   ├── prayertimes/       # Prayer Times UI Layer
│   │   ├── components/    # UI components (InteractivePrayerDial, etc.)
│   │   ├── navigation/    # Prayer times navigation
│   │   ├── utils/         # Display utilities
│   │   ├── animations/    # Prayer animations
│   │   └── data/          # Prayer time calculations
│   ├── quran/             # Quran player and data
│   ├── surah/             # Surah display
│   ├── course/            # Course features with voice recording
│   └── salah/             # Salah ML features
│       ├── datacollection/  # ML training data collection UI & ViewModel
│       └── visualization/   # LibGDX 3D visualization (scatter, humanoid, gravity)
├── prayer/                # Core Prayer System (Clean Architecture)
│   ├── model/             # Prayer data models
│   ├── calculator/        # Astronomical calculations
│   ├── repository/        # Settings persistence & auto-detection
│   ├── service/           # Location & calculation services
│   ├── viewmodel/         # State management
│   ├── ui/                # Prayer settings UI
│   ├── scheduler/         # Notification scheduling
│   ├── receiver/          # Broadcast receivers
│   ├── worker/            # Background workers
│   ├── cache/             # Location caching
│   └── util/              # Prayer utilities
├── ml/                    # Machine Learning (Salah posture detection)
│   ├── SalahPosture.kt        # Posture enum (7 ML + 2 non-ML)
│   ├── SalahDataSample.kt     # Sensor window data class
│   ├── SalahFeatureExtractor.kt # 30-feature extraction
│   ├── SalahDetectionEngine.kt  # TFLite inference engine
│   └── SalahSequenceValidator.kt # Posture transition state machine
├── sensor/                # Sensor services
│   └── SalahDataCollectionService.kt # 50Hz accelerometer + gyroscope recording
├── voice/                 # Voice system
│   ├── VoiceModule.kt          # Hilt DI module
│   ├── SherpaVoiceService.kt   # Sherpa ONNX text-to-speech
│   ├── WhisperVoiceService.kt  # Whisper speech recognition
│   └── VoiceCompletionManager.kt # Voice interaction flows
├── islamic/               # Islamic features (Clean Architecture)
│   ├── salah/             # Salah (prayer) feature
│   │   ├── domain/        # Business logic layer
│   │   └── presentation/  # UI layer
│   ├── qibla/             # Qibla compass feature
│   │   ├── domain/        # Qibla calculation logic
│   │   └── presentation/  # Qibla UI layer
│   │       └── component/ # QiblaGlobeView.kt (3D globe with WorldWind)
│   └── shared/            # Shared Islamic utilities
├── services/              # Background services & notifications
│   └── DrivingAudioService.kt # Foreground service with MediaSession
├── settings/              # App settings
│   └── components/        # Settings UI components (VoiceSettingsSection, etc.)
└── util/                  # Utility classes
    └── DebugDrivingReceiver.kt # ADB-triggered driving mode testing
```

### Asset Organization
```
app/src/main/assets/
├── country_prayer_methods.json  # 80+ countries prayer calculation methods
├── salah_detector.tflite        # 177KB ML model for posture detection
├── salah_norm_params.json       # Normalization parameters (30 features)
├── whisper/                     # Whisper speech recognition model
│   └── whisper-tiny.en.tflite   # English language recognition
├── databases/                   # Quran translation databases
│   ├── quran.db                # Main Arabic text (7.2MB)
│   ├── quran_enhanced.db       # Enhanced DB with Tafseer + word meanings (30MB)
│   ├── quran_english.db        # English translation
│   ├── quran_bengali.db        # Bengali translation
│   └── [10+ translation DBs]    # Multiple language translations
└── [other assets]
```

### Drawable Resources
```
app/src/main/res/drawable/
├── ic_kaaba_marker.xml           # Custom Kaaba icon for 3D globe
│                                 # Green circle with Kaaba building, gold band, shadow
├── ic_user_location_arrow.xml    # Custom user location icon for 3D globe
│                                 # Blue circle with directional arrow and red compass point
└── [other drawables]             # Material icons and app graphics
```

### Build Configuration

#### Build Logic Convention Plugins
```
build-logic/convention/
├── AndroidCompose.kt        # Compose configuration
├── KotlinAndroid.kt         # Kotlin Android setup
├── NiaBuildType.kt          # Build types (Debug, Release)
├── NiaFlavor.kt             # Product flavors (Demo, Prod)
├── Jacoco.kt                # Code coverage
└── GradleManagedDevices.kt  # Test device management
```

#### Build Variants
- **Flavors**: demo (local data), prod (backend server)
- **Build Types**: debug, release
- **Combinations**: demoDebug, demoRelease, prodDebug, prodRelease

### Technology Stack
- **Language**: Kotlin 2.0.21
- **UI**: Jetpack Compose (100% declarative) with Compose BOM 2025.02.00
- **Design**: Material 3 Design System
- **DI**: Hilt 2.56
- **Database**: Room 2.7.2
- **Preferences**: Proto DataStore
- **Networking**: Retrofit
- **3D Visualization (Qibla)**: NASA WorldWind Android v0.8.0 (3D globe for Qibla direction)
- **3D Visualization (Salah ML)**: LibGDX 1.12.1 (scatter plot, humanoid model, gravity vectors)
- **ML Inference**: TensorFlow Lite (1D CNN salah posture detection, 177KB model)
- **TTS**: Sherpa ONNX (on-device text-to-speech for Hadith reading)
- **Speech Recognition**: Whisper TFLite (on-device English speech recognition)
- **ML Training**: Python + TensorFlow/Keras (offline training pipeline in `training/`)
- **Media**: MediaSession + Foreground Service (driving mode audio chain)
- **Testing**: JUnit4, Espresso, Roborazzi
- **Background**: WorkManager
- **Min SDK**: 24
- **Target SDK**: 35

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
    - Components: `AnalogWatchComponents.kt`, `PrayerTimeCards.kt`, `InteractivePrayerDial.kt`
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
  - **Features**: Location-based calculations, real-time updates, notification alerts, Material 3 design, interactive prayer time adjustment with PNG file icon aesthetic

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
- `app/src/main/kotlin/com/starception/submission/ml/` - Salah ML posture detection engine
- `app/src/main/kotlin/com/starception/submission/voice/` - Voice system (TTS + speech recognition)
- `app/src/main/kotlin/com/starception/submission/services/DrivingAudioService.kt` - Driving mode audio
- `training/salah_model/` - Python ML training pipeline

### Documentation
All documentation is in the `docs/` directory. See the full listing in the "Documentation Files" section below.

## UI Design System

### Card Design Guidelines
All cards in the app follow a consistent design pattern:
- **Shape**: `RoundedCornerShape(16.dp)`
- **Colors**: `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)`
- **Internal padding**: `16.dp`
- **External margins**: `16.dp` on all sides for screen-level content
- **Click handling**: Cards should have `onClick` functionality where applicable

### Recent Updates (as of November 2025)
- Enhanced Prayer Times feature with comprehensive architecture
- Implemented Material 3 expressive design with asymmetrical shapes and layered backgrounds
- Added real-time prayer status tracking (Current/Next/Upcoming prayers)
- Integrated enhanced location services with permission management
- Added comprehensive notification system with prayer alerts
- Updated dependency versions to latest stable releases (Kotlin 2.0.21, Compose BOM 2025.02.00)
- Improved build configuration with optimized Gradle settings
- Added interactive prayer time adjustment with PNG file icon aesthetic
- Enhanced Qibla compass with improved gestures and theming
- Implemented comprehensive debugging and logging systems

## Prayer Times UI Improvements

### Interactive Prayer Time Adjustment
- **Professional PNG File Icon Circular Timer**: Complete redesign with clean, document-style aesthetic for prayer time adjustments
  - **PNG File Icon Design**: Clean white background with subtle shadows, folded corner effects, and professional document styling
  - **Live Dragging Feedback**: Real-time progress arc and knob movement following finger during drag interactions
  - **Prayer Name Display**: Shows prayer name (Dhuhr, Asr, Maghrib, Isha) prominently in center of dial
  - **Adjusted Time Display**: Real-time 12-hour format time display (e.g., "3:45 PM") with live updates
  - **Offset Indicators**: Current adjustment display (e.g., "+5m", "-3m") with color-coded feedback
  - **120 Precision Tick Marks**: Fine-grained minute adjustments with professional gray tick marks around circumference
  - **Teal Progress Arc**: Beautiful glowing arc with outer glow effects showing current adjustment range
  - **Interactive Knob**: Smooth draggable knob with enhanced feedback during dragging (larger size, better visibility)
  - **Long-Press Activation**: Transform individual tiles - only long-pressed tile becomes interactive dial
  - **Long-Press Save**: Clean interaction - long press dial again to save adjustment and exit (no buttons)
  - **Exclusive Tile Editing**: Only one tile in edit mode at a time with smooth scale animations for other tiles
  - **Haptic Feedback**: TextHandleMove haptic feedback during drag interactions for tactile response
  - **±180 Minute Range**: Full 6-hour adjustment range (±3 hours) with 6-degree-per-minute precision
  - **Perfect Circular Geometry**: AspectRatio constraints ensure perfect circles without oval distortion
  - **Tile Offset Display**: Small prayer tiles show current stored offsets (e.g., Dhuhr: "+5m", Asr: "-3m")
  - **Location**: `app/src/main/kotlin/com/starception/submission/feature/prayertimes/components/InteractivePrayerDial.kt`

#### Interactive Prayer Dial Usage Flow
1. **Initial State**: Small prayer tiles display prayer names, times, and current offsets (if any)
2. **Long Press Activation**: Long press any prayer tile (Dhuhr, Asr, Maghrib, Isha) to transform it into interactive circular dial
3. **Live Adjustment**: Drag the knob clockwise/counter-clockwise to adjust prayer time with real-time feedback
4. **Visual Feedback**: Progress arc, knob position, and time display update immediately during dragging
5. **Save & Exit**: Long press anywhere on the dial to save current adjustment and return to tile view
6. **Persistent Display**: Saved offsets are displayed on small tiles (e.g., "+5m", "-3m") and stored in prayer settings

#### Technical Implementation Features
- **State Management**: Shared state ensures only one tile can be in edit mode at a time
- **Animation System**: Smooth tile scaling (85% for non-editing tiles) and content size animations
- **Gesture Handling**: Combined drag gestures for adjustment and tap gestures for save functionality
- **Performance Optimization**: Efficient Canvas drawing with proper invalidation and minimal recomposition
- **Accessibility**: Haptic feedback integration for enhanced user experience

### Notification Settings Slider (December 2025)
- **Custom Slider Thumb with Value Display**: Professional slider design showing the value directly on the thumb
  - **Value on Thumb**: Minutes value (e.g., "15m", "20m", "Off") displayed on the slider thumb instead of separate badge
  - **Stadium-Shaped Pill**: Fully rounded corners (14dp radius) with fixed 28dp height for consistent appearance
  - **Shadow Effect**: 4dp elevation shadow for depth and visual polish
  - **Primary Color Theming**: Uses `MaterialTheme.colorScheme.primary` for active state, `surfaceContainerHighest` for off state
  - **Haptic Feedback**: TextHandleMove haptic feedback on each minute change for precise tactile control
  - **Centered Typography**: Properly centered text with adjusted line height for perfect vertical alignment
  - **Track Styling**: Semi-transparent active track with `primary.copy(alpha = 0.4f)` for visual continuity
  - **Location**: `app/src/main/kotlin/com/starception/submission/settings/components/NotificationsSection.kt`

#### Technical Implementation
- **ExperimentalMaterial3Api**: Uses custom thumb composable with `SliderDefaults.colors()`
- **MutableInteractionSource**: Tracks slider interaction state
- **Previous Value Tracking**: Compares values to trigger haptic only on actual changes
- **Single-Line Prayer Names**: 70dp width with `maxLines = 1` to prevent text wrapping (fixes "Maghrib" display)

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

### NASA WorldWind 3D Globe Integration (Latest - November 2025)

#### Overview
Integrated NASA WorldWind Android library to visualize Qibla direction on a 3D globe showing the great circle path from user location to the Kaaba in Makkah. The globe is displayed as the 4th swipeable tile in the main dashboard.

#### Key Features
- **3D Earth Visualization**: Full 3D globe using NASA's Blue Marble satellite imagery
- **Great Circle Path**: Shows shortest path from user location to Kaaba with bright green line
- **Custom Markers**:
  - User location: Blue circular icon with white directional arrow (compass-style)
  - Kaaba location: Green circular icon with Kaaba building and gold band
- **Dynamic Compass Rotation**: Globe rotates in real-time based on device orientation (magnetic + accelerometer sensors)
- **Touch Gestures**: Full support for pan, zoom, and rotate with intelligent compass pause during interaction
- **Smart Camera Positioning**: Auto-calculates optimal view based on tile dimensions and distance between locations
- **Lifecycle Management**: Proper OpenGL surface lifecycle handling (onResume/onPause)

#### Technical Implementation

**Dependencies Added:**
- Library: `com.github.NASAWorldWind:WorldWindAndroid:v0.8.0` (via JitPack)
- Location: `settings.gradle.kts` + `app/build.gradle.kts`

**Core Files:**
1. **QiblaGlobeView.kt** (`app/src/main/kotlin/com/starception/submission/islamic/qibla/presentation/component/`)
   - Main globe component with sensor integration
   - Touch-tracking controller for gesture handling
   - Compass sensor listener (MAGNETIC_FIELD + ACCELEROMETER)
   - Dynamic camera heading updates based on device orientation
   - 2-second timeout after user touch before compass resumes control

2. **Custom Icons** (`app/src/main/res/drawable/`)
   - `ic_kaaba_marker.xml` - Green circle with Kaaba building, gold band, and shadow
   - `ic_user_location_arrow.xml` - Blue circle with directional arrow and red compass point

3. **SwipeableBigTiles.kt** (Modified)
   - Extended HorizontalPager from 3 to 4 tiles
   - Added QiblaGlobeTile as 4th page
   - Integrated with existing prayer times data flow

#### Key Implementation Details

**Touch Gesture Conflict Resolution:**
```kotlin
// TouchTrackingController detects user interaction
private class TouchTrackingController(
    private val onTouchStart: () -> Unit,
    private val onTouchEnd: () -> Unit
) : BasicWorldWindowController()

// Compass updates only when user not touching (2-second timeout)
if (!isUserInteracting && (currentTime - lastInteractionTime) > 2000) {
    worldWindowRef?.let { ww ->
        updateGlobeCameraHeading(ww, deviceHeading, userLat, userLon, makkahLat, makkahLon)
    }
}
```

**Camera Positioning Algorithm:**
- Calculates great circle distance between user and Kaaba
- Uses field-of-view (45°) and tile aspect ratio for optimal range calculation
- Ensures minimum range (1.5x Earth radius) for proper curvature visibility
- Positions camera at midpoint with tilt angle for perspective

**Custom Marker Loading:**
- Converts vector drawable XML to Bitmap for WorldWind compatibility
- Uses PlacemarkAttributes with ImageSource.fromBitmap()
- Configurable imageScale for different marker sizes

#### Critical Requirements
- **Internet Connection**: Required for downloading NASA Blue Marble satellite imagery on first load
- **Essential Layers**: BackgroundLayer + BlueMarbleLandsatLayer (without these, globe renders black)
- **Lifecycle**: Must call worldWindow.onResume()/onPause() with lifecycle events
- **Sensors**: Both MAGNETIC_FIELD and ACCELEROMETER sensors needed for compass heading

#### Integration Points
- Location data flows from prayer times system (DayPrayerTimes.location)
- Displayed in 4th position of swipeable big tiles
- Uses existing Material 3 theming for overlays and UI elements
- Compass heading displayed in overlay card

#### Known Behavior
- Globe starts with optimized view showing both user location and Kaaba
- Compass rotation updates in real-time as device rotates
- Touch gestures pause compass for 2 seconds after release
- Green path shows true great circle route (may look curved due to sphere projection)
- Markers scale appropriately with zoom level

#### Files Modified/Created
- **Created**: `QiblaGlobeView.kt`, `ic_kaaba_marker.xml`, `ic_user_location_arrow.xml`
- **Modified**: `SwipeableBigTiles.kt`, `settings.gradle.kts`, `app/build.gradle.kts`

### Smart Content Optimization
- **Text Display Improvements**: Resolved text truncation issues in Smart Prediction tiles
  - Modified content generation functions to produce shorter, more display-friendly text
  - Optimized layout to prevent text cutoff while maintaining readability
  - Updated `SmartContentUtils.kt` with better content formatting for various tile sizes

### Landscape Layout Support (December 2025)

Comprehensive landscape orientation support for all prayer times and Islamic features screens.

#### Files Modified for Landscape Support
1. **PrayerTimesScreen.kt** - Main prayer times dashboard
   - Side-by-side layout with swipeable tiles on left (50%) and prayer cards on right (50%)
   - Prayer cards displayed in 2-column grid for landscape
   - Location card at bottom of left column
   - Orientation detection using `LocalConfiguration.current`

2. **SwipeableBigTiles.kt** - All 4 swipeable tiles
   - Added `isLandscape: Boolean` parameter to all tile composables
   - Dynamic pager height: `weight(1f)` in landscape vs fixed `200.dp` in portrait
   - Compact page indicators (8dp/6dp in landscape vs 12dp/8dp in portrait)
   - Compact swipe hint text ("Swipe" vs "Swipe for more insights")

3. **SalahDashboard.kt** - Salah dashboard screen
   - Two-column side-by-side layout for landscape
   - Next prayer card + Qibla globe on left, prayer times list on right

4. **CompassPopupScreen.kt** - Full-screen compass popup
   - Side-by-side layout: compass on left (45%), calibration guidance on right (55%)
   - Smaller compass size (180dp in landscape vs 260dp in portrait)
   - Scrollable calibration content for limited vertical space

#### Tile-Specific Landscape Optimizations

**Smart Prediction Tile (NextPrayerTile):**
- Compass size: 85dp (landscape) vs 120dp (portrait)
- Centered compass container with `fillMaxHeight()` alignment
- Reduced padding: 16dp (landscape) vs 24dp (portrait)
- Compact text with single-line content

**Smart Tracking Tile (SmartInfoTile):**
- Reduced padding and spacing for compact display
- Smaller header icons and text
- All content fits without cutoff

**Noble Quran Tile (DailyStatsTile):**
- Inline time labels with slider (landscape) vs time labels above slider (portrait)
- Smaller play button: 32dp (landscape) vs 42dp (portrait)
- Smaller skip buttons: 24dp (landscape) vs 32dp (portrait)
- Hidden English surah name in landscape to save vertical space
- Smaller surah badge: 24dp (landscape) vs 28dp (portrait)
- Reduced vertical padding: 4dp (landscape) vs 10dp (portrait)

**Qibla Globe Tile:**
- Automatically adapts to available space
- Maintains proper aspect ratio in landscape

#### Implementation Pattern
```kotlin
// Orientation detection
val configuration = LocalConfiguration.current
val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

// Conditional layout
if (isLandscape) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left column
        Column(modifier = Modifier.weight(0.5f)) { ... }
        // Right column
        Column(modifier = Modifier.weight(0.5f)) { ... }
    }
} else {
    // Portrait layout
    Column { ... }
}

// Conditional sizing
Modifier.size(if (isLandscape) 85.dp else 120.dp)
```

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

## Enhanced Preference Logging System

### Detailed Preference Operation Tracking
- **Comprehensive Logging**: All preference read/write operations are logged with detailed metadata
- **Specific Tags**: Dedicated log tags for easy filtering and monitoring:
  - `PrayerSettings_PREF_READ` - All preference read operations
  - `PrayerSettings_PREF_WRITE` - All preference write operations  
  - `PrayerSettings_JSON_READ` - JSON preference read operations with content analysis
  - `PrayerSettings_JSON_WRITE` - JSON preference write operations with validation
  - `PrayerSettings_PREF_VERIFY` - Write operation verification and validation

### Log Format Features
- **Timestamp Precision**: Millisecond-level timestamps for performance analysis
- **Type Detection**: Automatic data type identification (String, Int, Float, Boolean, etc.)
- **Content Analysis**: JSON validation, field counting, and structure analysis
- **Size Tracking**: Character count for JSON content and performance monitoring
- **Preview Content**: Truncated content preview for large JSON objects
- **Verification Status**: Success/failure status for all write operations

### Development Commands for Preference Debugging

```bash
# View all preference operations (all types)
adb logcat -s "PrayerSettings_PREF_READ" "PrayerSettings_PREF_WRITE" "PrayerSettings_JSON_READ" "PrayerSettings_JSON_WRITE" "PrayerSettings_PREF_VERIFY"

# Monitor only preference reads
adb logcat -s "PrayerSettings_PREF_READ"

# Monitor only preference writes  
adb logcat -s "PrayerSettings_PREF_WRITE"

# Monitor JSON operations with content analysis
adb logcat -s "PrayerSettings_JSON_READ" "PrayerSettings_JSON_WRITE"

# Monitor write verification and validation
adb logcat -s "PrayerSettings_PREF_VERIFY"

# Monitor specific preference key (example: prayer time offsets)
adb logcat -s "PrayerSettings_PREF_READ" "PrayerSettings_PREF_WRITE" | grep "cached_prayer_settings"

# Monitor JSON validation and parsing issues
adb logcat -s "PrayerSettings_JSON_READ" "PrayerSettings_JSON_WRITE" | grep "INVALID\|FAILED\|ERROR"

# Performance monitoring for preference operations
adb logcat -s "PrayerSettings_PREF_READ" "PrayerSettings_PREF_WRITE" "PrayerSettings_JSON_READ" "PrayerSettings_JSON_WRITE" | grep -E "[0-9]+ch|[0-9]+ms"

# Real-time preference monitoring during app usage
adb logcat -s "PrayerSettings_PREF_READ" "PrayerSettings_PREF_WRITE" -v time
```

### Log Analysis Examples

#### Successful Preference Write:
```
PrayerSettings_PREF_WRITE: 💾 WRITE | 14:32:15.123 | key='notifications_enabled' | type=Boolean | value=true | file=prayer_settings
PrayerSettings_PREF_VERIFY: 🔍 VERIFY | 14:32:15.125 | key='notifications_enabled' | op='notification settings' | status=SUCCESS | expected=true | actual=true
```

#### JSON Content Analysis:
```
PrayerSettings_JSON_WRITE: 💾 JSON_WRITE | 14:32:20.456 | key='calculation_settings_json' | desc='prayer calculation settings' | status=VALID | size=324ch | fields=6 | file=prayer_settings
PrayerSettings_JSON_WRITE: 🏷️ FIELDS | key='calculation_settings_json' | names=[calculationMethod, madhab, timeOffsets, location, manualAdjustments, lastUpdated]
PrayerSettings_JSON_WRITE: 📄 CONTENT | key='calculation_settings_json' | preview={"calculationMethod":"ISNA","madhab":"HANAFI","timeOffsets":{"fajr":0,"sunrise":0...}
```

#### Failed Operation Detection:
```
PrayerSettings_PREF_VERIFY: 🔍 VERIFY | 14:32:25.789 | key='invalid_key' | op='test operation' | status=FAILED | expected=test_value | actual=null
PrayerSettings_PREF_VERIFY: ❌ FAILED | key='invalid_key' | expected_type=String | actual_type=null
```

### Use Cases for Enhanced Logging
- **Performance Analysis**: Track preference operation timing and JSON size optimization
- **Debug Data Corruption**: Identify when preference values don't match expectations
- **Content Validation**: Monitor JSON structure changes and parsing issues
- **User Behavior Tracking**: Understand which settings are modified most frequently
- **Integration Testing**: Verify preference operations in automated testing scenarios
- **Production Monitoring**: Track preference operation success rates and performance

## Unique Features & Enhancements

### Major Enhancements from Original NiA

#### 1. Complete Islamic Prayer Times System
- **25+ Calculation Methods**: Comprehensive support for worldwide Islamic calculation methods
- **Auto-Detection**: Country-based automatic configuration using 80+ country database
- **Interactive Adjustments**: PNG file icon aesthetic circular timer for precise time adjustments
- **Astronomical Calculations**: High-precision prayer time calculations with solar position algorithms
- **Real-time Updates**: Live prayer status tracking (Current/Next/Upcoming)
- **Background Services**: Notification scheduling with prayer alerts

#### 2. Quran Integration System
- **12 Translation Databases**: Arabic + 11 language translations (English, Bengali, Spanish, French, etc.)
- **Quran Player**: Full audio player with bookmark support
- **Surah Navigation**: Easy navigation through all 114 Surahs
- **Transliteration Support**: Romanized text for non-Arabic readers

#### 3. Islamic Features Architecture
- **Salah Dashboard**: Comprehensive prayer management dashboard
- **Qibla Compass**: Direction finder with Material 3 theming
- **3D Globe Visualization**: NASA WorldWind 3D globe showing great circle path from user to Kaaba with custom markers and compass-based rotation
- **Smart Content**: AI-powered Islamic content suggestions
- **Swipeable Tiles**: Interactive prayer time tiles with gestures

#### 4. Salah ML Posture Detection
- **On-Device ML**: 1D CNN model (177KB TFLite) detecting 7 prayer postures from phone-in-pocket sensors
- **Data Collection**: In-app recording at 50Hz with quality feedback and auto-trimming
- **Training Pipeline**: Python scripts for feature engineering, augmentation, training, and TFLite export
- **Sequence Validation**: State machine for filtering false positives and counting rak'ahs
- **3D Visualization**: LibGDX 1.12.1 engine with scatter plot, animated humanoid, and gravity vector modes
- **Feature Engineering**: 30 statistical features (accelerometer + gyroscope) per 100ms window

#### 5. Driving Mode & Voice System
- **Driving Audio Service**: Foreground service with MediaSession playing Travel Dua -> Hadith -> Quran -> Voice prompt
- **Sherpa ONNX TTS**: On-device text-to-speech for reading Hadith text
- **Whisper Recognition**: On-device English speech recognition for voice commands
- **Activity Detection**: Accelerometer + gyroscope based driving/walking detection
- **Media Controls**: Lock screen, notification, and Bluetooth audio support

#### 6. Word Study & Tafseer
- **Enhanced Quran Database**: 30MB database with word meanings and 3 Tafseer books
- **Interactive Word Study**: Tap any ayah to see detailed Arabic word meanings
- **3 Tafseer Books**: As-Sa'di, Al-Moyassar, Al-Baghawi with filter chip switching

#### 8. Enhanced Location Services
- **Smart Timeouts**: 3-second timeout to prevent UI blocking
- **Location Caching**: Efficient location data caching
- **Country Detection**: Automatic country detection for prayer methods
- **Fallback Mechanisms**: Multiple fallback strategies for location failures

#### 9. Advanced Notification System
- **Prayer Notifications**: Timely alerts for all prayer times
- **Background Workers**: Reliable notification delivery using WorkManager
- **Boot Receivers**: Notifications persist after device restart
- **Custom Sounds**: Support for custom notification sounds

### File & Directory Count Summary
- **Total Kotlin Files**: 420+ files
- **Total Lines of Code**: ~55,000+ lines (app module: 40,000+)
- **Documentation Files**: 31 comprehensive guides in `docs/`
- **Build Configurations**: 30+ Gradle files
- **Asset Databases**: 12 Quran translation databases + 1 enhanced Tafseer DB
- **ML Models**: 2 TFLite models (salah posture + Whisper speech)
- **Python Training Scripts**: 4 scripts in `training/salah_model/`
- **Module Count**: 25+ modules

### Development Scripts & Tools
```
scripts/
├── build_android_release.sh      # Release build automation
├── capture_location_logs.sh      # Location debugging
├── convert_quran_sql_to_db.py    # Database conversion
├── generate_quran_news.py        # Quran news generation
└── generateModuleGraphs.sh       # Module dependency graphs
```

### Documentation Files
```
docs/
├── ACTIVITY_DETECTION_IMPROVEMENTS.md          # Gyroscope-based detection improvements
├── ACTIVITY_DETECTION_TECHNICAL_GUIDE.md       # Activity detection full technical guide
├── ACTIVITY_NOTIFICATION_PERSISTENCE.md        # Notification mode persistence
├── AGENT.md                                     # Agent configuration
├── ANR_PREVENTION_GUIDE.md                      # Application Not Responding prevention
├── ASSET_INVENTORY.md                           # Complete asset inventory
├── AUTO_DETECTION_LOGGING_GUIDE.md              # Auto-detection logging system
├── ArchitectureLearningJourney.md               # Official Android architecture guide
├── CLAUDE_MEMORY.md                             # Codex context memory
├── CODE_ORGANIZATION.md                         # Code organization patterns
├── COMPASS_INTEGRATION_GUIDE.md                 # Qibla compass implementation
├── DRIVING_VOICE_FEATURES_GUIDE.md              # Driving mode audio & voice system
├── EXPRESSIVE_DESIGN_INTEGRATION_GUIDE.md       # Material 3 expressive design
├── INTERACTIVE_PRAYER_DIAL_GUIDE.md             # Interactive prayer time adjustment
├── LIBGDX_3D_VISUALIZATION_GUIDE.md             # LibGDX 3D sensor data visualization
├── ModularizationLearningJourney.md             # Modularization best practices
├── NOTIFICATION_SYSTEM_READY.md                 # Notification system status & fixes
├── NOTIFICATION_SYSTEM_VERIFICATION.md          # Notification testing and verification
├── PICKUP_FALSE_DETECTION_FIX.md                # False walking detection fix
├── PRAYER_CALCULATION_METHODOLOGY.md            # Islamic prayer time calculations
├── PRAYER_TIMES_DEBUG_LOGGING_GUIDE.md          # Debug logging for prayer times
├── PRAYER_TIMES_TECHNICAL_GUIDE.md              # Comprehensive prayer system guide
├── QURAN_DATABASE_GUIDE.md                      # Quran database architecture
├── QURAN_DATABASE_INTEGRATION_SUMMARY.md        # Quran DB integration & usage status
├── QURAN_ENHANCED_DATABASE_GUIDE.md             # Enhanced DB with Tafseer & meanings
├── QURAN_NEWS_INTEGRATION.md                    # Quran news feature integration
├── QURAN_TRANSLATION_DATABASES_GUIDE.md         # 12-language translation databases
├── RELIABLE_PRAYER_NOTIFICATIONS_GUIDE.md       # Reliable notification delivery
├── SALAH_ML_TRAINING_GUIDE.md                   # ML posture detection training pipeline
├── TIME_SIMULATION_TESTING_GUIDE.md             # Time simulation for testing
└── WORD_STUDY_TAFSEER_FEATURE_SUMMARY.md        # Word study & Tafseer features
```

## Key Differences from Original Now in Android

### Architectural Additions
1. **Islamic Domain Layer**: Complete domain layer for Islamic features
2. **Prayer Calculation Engine**: Astronomical calculation algorithms
3. **Multi-Database Support**: SQLite databases for Quran translations + enhanced Tafseer DB
4. **Enhanced Services**: Location, notification, and calculation services
5. **Country Database**: JSON-based country prayer method configurations
6. **ML Pipeline**: Complete data collection -> training -> on-device inference pipeline
7. **Voice System**: Sherpa ONNX TTS + Whisper speech recognition with Hilt DI
8. **3D Visualization**: LibGDX rendering engine embedded in Jetpack Compose
9. **Driving Audio**: Foreground service with MediaSession and audio chain management

### UI/UX Enhancements
1. **Interactive Dials**: Circular timer UI with drag gestures
2. **Swipeable Components**: Gesture-based prayer tile interactions
3. **Popup Windows**: Compass and prayer bubble popups
4. **Material 3 Extensions**: Custom shapes and animations
5. **Haptic Feedback**: Enhanced tactile responses
6. **3D Data Visualization**: Interactive scatter plot, humanoid model, gravity vectors
7. **Word Study & Tafseer**: In-context Quran study with expandable ayah actions

### Technical Improvements
1. **Comprehensive Logging**: Structured emoji-based logging system
2. **Performance Monitoring**: Built-in performance tracking
3. **Error Recovery**: Robust fallback mechanisms
4. **State Persistence**: Enhanced preference management
5. **Testing Infrastructure**: Extended test coverage for Islamic features
6. **On-Device ML**: TFLite inference with sequence validation state machine
7. **Python Training Pipeline**: Feature engineering, augmentation, and model export