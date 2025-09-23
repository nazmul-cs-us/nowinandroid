# Interactive Prayer Dial Technical Guide

## Overview

The Interactive Prayer Dial is a sophisticated UI component that provides users with an intuitive way to adjust prayer times through a beautiful circular interface. Implemented in September 2025, it combines modern Android UI practices with Islamic prayer time functionality.

## Core Features

### PNG File Icon Aesthetic Design
- **Professional Styling**: Clean white background with subtle shadows and depth effects
- **Document-Style Appearance**: Folded corner effects and professional borders mimicking file icons
- **Perfect Circular Geometry**: AspectRatio constraints ensure perfect circles without oval distortion
- **Material 3 Integration**: Uses Material 3 colors and typography for consistency

### Interactive Capabilities
- **Live Dragging Feedback**: Real-time progress arc and knob movement following user input
- **Precision Adjustments**: 120 tick marks around circumference for precise minute-level adjustments
- **±180 Minute Range**: Full 6-hour adjustment range (±3 hours) with 6-degree-per-minute precision
- **Visual Feedback**: Teal progress arc with outer glow effects showing current adjustment

### Information Display
- **Prayer Name**: Prominently displays the prayer being adjusted (Dhuhr, Asr, Maghrib, Isha)
- **Adjusted Time**: Real-time 12-hour format time display (e.g., "3:45 PM")
- **Offset Indicator**: Current adjustment display (e.g., "+5m", "-3m") with color coding

## User Interaction Flow

### 1. Activation
```
Long Press Prayer Tile → Transform to Circular Dial
```
- Any prayer tile (Dhuhr, Asr, Maghrib, Isha) can be long-pressed
- Smooth animation transforms tile into interactive circular dial
- Other tiles scale down to 85% to avoid overlap

### 2. Adjustment
```
Drag Knob → Live Feedback → Visual Updates
```
- Drag the knob clockwise to increase time, counter-clockwise to decrease
- Progress arc, knob position, and time display update in real-time
- Haptic feedback (TextHandleMove) provides tactile response

### 3. Save & Exit
```
Long Press Dial → Save Adjustment → Return to Tile View
```
- Second long press saves the current adjustment
- Tile returns to normal size showing the stored offset
- Clean interaction without additional UI buttons

## Technical Implementation

### Component Architecture
```
InteractivePrayerCard (Container)
├── State Management (currentEditingTile)
├── Animation System (scale, alpha, contentSize)
└── InteractivePrayerDial (Core Component)
    ├── Canvas Drawing (tick marks, arcs, knob)
    ├── Gesture Handling (drag, tap)
    └── Information Display (prayer name, time, offset)
```

### Key Files
- **`PrayerTimesScreen.kt`**: Main screen with shared state management
- **`InteractivePrayerDial.kt`**: Core circular dial component
- **Shared State**: Ensures only one tile in edit mode at a time

### Performance Optimizations
- **Efficient Canvas Drawing**: Minimal recomposition with proper invalidation
- **Smart State Management**: Shared state prevents multiple simultaneous edits
- **Gesture Optimization**: Combined pointer input for drag and tap detection

## Design Specifications

### Visual Elements
- **Tick Marks**: 120 precision marks (major every 5 positions)
- **Progress Arc**: Teal color (#10B981) with outer glow effects
- **Knob**: Enhanced size during dragging for better visibility
- **Typography**: Material 3 typography scales for hierarchy

### Colors
- **Background**: Clean white with subtle shadows
- **Progress**: Professional teal-green (#10B981)
- **Text**: Material 3 color scheme integration
- **Borders**: Light gray (#E5E7EB) for definition

### Animations
- **Tile Scaling**: 85% scale for non-editing tiles (300ms tween)
- **Content Size**: Smooth transitions during tile transformation
- **No Rotation**: Clean animations without disorienting rotation effects

## Usage Guidelines

### Best Practices
1. **Single Edit Mode**: Only one prayer time should be adjustable at a time
2. **Clear Visual Hierarchy**: Editing tile should be prominent, others subdued
3. **Immediate Feedback**: All adjustments should provide instant visual response
4. **Persistent Storage**: Offsets should be saved to prayer settings for persistence

### User Experience Considerations
- **Intuitive Gestures**: Long press activation feels natural for mobile users
- **Clean Interface**: No unnecessary buttons or UI elements
- **Visual Consistency**: Matches overall app design language
- **Accessibility**: Haptic feedback enhances user experience

## Integration Points

### Prayer Settings System
- **Offset Storage**: Adjustments saved to `PrayerCalculationSettings.timeOffsets`
- **Settings Persistence**: Uses DataStore for reliable data persistence
- **Settings Screen**: Integrates with existing prayer settings functionality

### State Management
- **Shared State**: `currentEditingTile` ensures exclusive editing
- **Local State**: `timeAdjustment` tracks current dial adjustment
- **Persistent Display**: Tiles show stored offsets from settings

## Future Enhancements

### Potential Improvements
- **Multiple Tile Editing**: Allow simultaneous adjustment of multiple prayers
- **Custom Gestures**: Additional gesture support for power users
- **Animation Refinements**: Enhanced transition animations
- **Accessibility Features**: Voice guidance and screen reader support

## Conclusion

The Interactive Prayer Dial represents a successful integration of modern Android UI practices with Islamic prayer time functionality. It provides users with an intuitive, beautiful, and functional way to adjust prayer times while maintaining the app's overall design consistency and performance standards.