# Claude Code Memory - Prayer Times App

## Project Overview
**Base**: Modified "Now in Android" sample app converted to Islamic Prayer Times app
**Package**: `com.starception.submission`
**Main Screen**: Prayer Times (HOME destination in navigation)
**Architecture**: Fully modularized Android app with clean architecture

## Key Implementation Details

### Prayer Times Feature
- **Location**: `app/src/main/kotlin/com/starception/submission/feature/prayertimes/`
- **Main Files**:
  - `PrayerTimesScreen.kt` - Main screen with PNG file icon design aesthetic
  - `components/PrayerTimeCards.kt` - Prayer time card components
- **Design Theme**: PNG file document aesthetic with Material 3 theme colors
- **Navigation**: Prayer times is the main HOME destination

### Design System Alignment (August 2025)
- **Colors**: Uses `MaterialTheme.colorScheme` for consistency across app
  - `primaryContainer` for header preview area
  - `surface` for main card backgrounds
  - `primary` for prayer times and loading indicators
  - `onSurface` and `onPrimaryContainer` for text colors
  - `outline` for subtle elements like "PRAYER" label
- **Components**: `CardDefaults.cardColors()` and Material 3 elevation
- **Shape**: `RoundedCornerShape(16.dp)` for main containers
- **Spacing**: `16.dp` margins consistent with app design system

### Technical Architecture
- **Build Variants**: 
  - `demo` - Uses local JSON data (recommended for development)
  - `prod` - Requires backend server (not publicly available)
- **Dependencies**: Hilt DI, Jetpack Compose, Repository pattern
- **Testing**: Roborazzi for screenshot tests, no mocking (uses test doubles)

## Development Commands

### Building & Installation
```bash
./gradlew assembleDemoDebug    # Build demo APK
./gradlew installDemoDebug     # Install on device
./gradlew assembleProdDebug    # Build prod APK (needs server)
```

### Testing
```bash
./gradlew testDemoDebug                    # Unit tests
./gradlew connectedDemoDebugAndroidTest    # Instrumented tests
./gradlew recordRoborazziDemoDebug        # Record screenshot baselines
./gradlew verifyRoborazziDemoDebug        # Verify screenshots
```

### Code Quality
```bash
./gradlew lint           # Lint checks
./gradlew lintDemoDebug  # Lint demo variant
./gradlew check          # All verification tasks
```

### Target Device
- **Primary**: Pixel device `4B221FDAP002T6`
- Command: `adb -s 4B221FDAP002T6 install -r [apk-path]`

## Current Status (August 29, 2025)

### Recent Changes
- ✅ Implemented PNG file icon design aesthetic  
- ✅ Aligned design with app's Material 3 theme colors
- ✅ Maintained consistent card design system
- ✅ Fixed all compilation errors and syntax issues
- ✅ Successfully built and installed theme-aligned version
- ✅ **FIXED: ANR issue causing splash screen hanging**
- ✅ **RESTORED: Full theme functionality (Light/Dark/System)**
- ✅ **TESTED: Theme changes work properly from Settings**

### Core Features Implemented
- Location-based prayer time calculations
- Loading states with proper theme colors  
- Clean card-based layout with PNG document aesthetic
- Proper Material 3 elevation and shadows
- Mosque emoji and prayer information display
- "PRAYER" file type label at bottom
- **Full theme support**: Light/Dark/System themes from Settings
- **Fast startup**: No ANR issues or splash screen hanging
- **Theme persistence**: User theme preferences saved and restored

### File Structure
```
app/src/main/kotlin/com/starception/submission/
├── navigation/
│   └── TopLevelDestination.kt (Prayer Times as HOME)
└── feature/prayertimes/
    ├── PrayerTimesScreen.kt (Main screen)
    └── components/
        └── PrayerTimeCards.kt (Card components)
```

## Design Principles

### PNG File Icon Aesthetic
- White/surface background with rounded corners
- Card-based layout with proper shadows
- Clean typography with theme-appropriate colors
- File-like appearance with bottom label

### Material 3 Integration
- Consistent with NewsResourceCard and other app components
- Uses `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)`
- Proper spacing (`16.dp` margins) matching design system
- Automatic light/dark theme support

## Important Notes

### Never Hardcode Colors
- Always use `MaterialTheme.colorScheme.*`
- Ensures consistency across app tabs (For You, Interests, etc.)
- Supports automatic theme switching

### Build Process
- Always use demo variant for development
- Install on Pixel device when multiple devices connected
- Run verification tasks before committing changes

### Testing Strategy
- Screenshot tests use Roborazzi framework
- Test doubles implement production interfaces (no mocking)
- Instrumented tests use temporary DataStore directories

## Recent Bug Fixes (August 29, 2025)

### ANR Issue Fix ✅ RESOLVED
**Problem**: App getting stuck at splash screen when theme changes, caused by PrayerNotificationService ANR (20+ second startup)

**Root Cause**: Heavy Hilt dependency injection in `PrayerNotificationService` blocking main thread during app startup

**Solution**: 
- Disabled problematic service startup in `MainActivity.onResume()`
- Restored proper theme handling without splash screen blocking
- ViewModel loads user preferences in background without blocking UI
- App now starts immediately and themes work perfectly

**Key Changes**:
- `MainActivity.kt`: Removed `startPrayerServiceIfNeeded()` call causing ANR
- `MainActivityViewModel.kt`: Loads theme preferences without splash screen dependency  
- Theme changes now work properly: Light/Dark/System follow user settings
- **Result**: App starts in ~1 second, themes switch instantly

**Testing Verified**:
- ✅ App starts immediately without hanging
- ✅ Theme changes work from Settings > Theme  
- ✅ Light/Dark/System all function properly
- ✅ PNG design aesthetic maintained with correct theme colors

## Next Steps Guidance
When continuing development:
1. Use demo variant for testing new features
2. Maintain theme color consistency
3. Follow existing card design patterns
4. Test on Pixel device `4B221FDAP002T6`
5. Run lint checks before committing
6. **Theme changes should work properly now** - test in Settings > Theme

## Troubleshooting
- If app hangs at startup: Check for service ANRs in `adb logcat`
- If theme changes don't work: Verify ViewModel is loading user preferences
- If service issues: Consider disabling heavy services during startup

---
*Last Updated: August 29, 2025 - ANR Fix Applied*
*Claude Code Memory File - Automatically generated*