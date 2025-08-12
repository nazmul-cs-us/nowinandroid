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
- **Prayer Times** - Islamic prayer times calculator and display
  - Located in `app/src/main/kotlin/com/starception/dua/feature/prayertimes/`
  - Components: `PrayerTimesScreen.kt`, `app/src/main/kotlin/com/starception/dua/prayer/ui/PrayerTimesCard.kt`
  - Features: Location-based prayer times, notifications, settings integration

### App Module
- **app** - Main application module, navigation, and dependency injection setup

### Build Flavors
- **demo** - Uses local JSON data for immediate testing
- **prod** - Connects to backend server (not publicly available)

### Key Architectural Patterns
- Unidirectional data flow with Kotlin Flows
- Repository pattern for data access
- Use cases for business logic
- Jetpack Compose for UI
- Dependency injection with Hilt
- No mocking in tests - uses test doubles implementing real interfaces

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
The app uses `com.starception.dua` as the base package (originally forked from Google's Now in Android sample).

### Important Files
- `gradle/libs.versions.toml` - Centralized dependency management
- `build-logic/convention/` - Gradle convention plugins for consistent module setup
- `app/src/main/baseline-prof.txt` - Baseline profile for app startup optimization

## UI Design System

### Card Design Guidelines
All cards in the app follow a consistent design pattern:
- **Shape**: `RoundedCornerShape(16.dp)`
- **Colors**: `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)`
- **Internal padding**: `16.dp`
- **External margins**: `16.dp` on all sides for screen-level content
- **Click handling**: Cards should have `onClick` functionality where applicable

### Recent Updates
- Fixed Prayer Times card margins and design consistency (August 2025)
- Updated card colors to match design system used in NewsResourceCard and other components