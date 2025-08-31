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