/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.starception.submission.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Now in Android colors.
 */
internal val Blue10 = Color(0xFF001F28)
internal val Blue20 = Color(0xFF003544)
internal val Blue30 = Color(0xFF004D61)
internal val Blue40 = Color(0xFF006780)
internal val Blue80 = Color(0xFF5DD5FC)
internal val Blue90 = Color(0xFFB8EAFF)
internal val DarkGreen10 = Color(0xFF0D1F12)
internal val DarkGreen20 = Color(0xFF223526)
internal val DarkGreen30 = Color(0xFF394B3C)
internal val DarkGreen40 = Color(0xFF4F6352)
internal val DarkGreen80 = Color(0xFFB7CCB8)
internal val DarkGreen90 = Color(0xFFD3E8D3)
internal val DarkGreenGray10 = Color(0xFF1A1C1A)
internal val DarkGreenGray20 = Color(0xFF2F312E)
internal val DarkGreenGray90 = Color(0xFFE2E3DE)
// Public rather than internal because core:designsystem still references these
// from Theme.kt, and `internal` does not cross a module boundary. The rest of the
// palette stays internal — it is consumed by ColorSchemes.kt in this module.
val DarkGreenGray95 = Color(0xFFF0F1EC)
internal val DarkGreenGray99 = Color(0xFFFBFDF7)
internal val DarkPurpleGray10 = Color(0xFF201A1B)
internal val DarkPurpleGray20 = Color(0xFF362F30)
internal val DarkPurpleGray90 = Color(0xFFECDFE0)
internal val DarkPurpleGray95 = Color(0xFFFAEEEF)
internal val DarkPurpleGray99 = Color(0xFFFCFCFC)
internal val Green10 = Color(0xFF00210B)
internal val Green20 = Color(0xFF003919)
internal val Green30 = Color(0xFF005227)
internal val Green40 = Color(0xFF006D36)
internal val Green80 = Color(0xFF0EE37C)
internal val Green90 = Color(0xFF5AFF9D)
internal val GreenGray30 = Color(0xFF414941)
internal val GreenGray50 = Color(0xFF727971)
internal val GreenGray60 = Color(0xFF8B938A)
internal val GreenGray80 = Color(0xFFC1C9BF)
internal val GreenGray90 = Color(0xFFDDE5DB)
internal val Orange10 = Color(0xFF380D00)
internal val Orange20 = Color(0xFF5B1A00)
internal val Orange30 = Color(0xFF812800)
internal val Orange40 = Color(0xFFA23F16)
internal val Orange80 = Color(0xFFFFB59B)
internal val Orange90 = Color(0xFFFFDBCF)
internal val Purple10 = Color(0xFF36003C)
internal val Purple20 = Color(0xFF560A5D)
internal val Purple30 = Color(0xFF702776)
val Purple40 = Color(0xFF8B418F)
val Purple80 = Color(0xFFFFA9FE)
internal val Purple90 = Color(0xFFFFD6FA)
internal val PurpleGray30 = Color(0xFF4D444C)
internal val PurpleGray50 = Color(0xFF7F747C)
internal val PurpleGray60 = Color(0xFF998D96)
internal val PurpleGray80 = Color(0xFFD0C3CC)
internal val PurpleGray90 = Color(0xFFEDDEE8)
internal val Red10 = Color(0xFF410002)
internal val Red20 = Color(0xFF690005)
internal val Red30 = Color(0xFF93000A)
internal val Red40 = Color(0xFFBA1A1A)
internal val Red80 = Color(0xFFFFB4AB)
internal val Red90 = Color(0xFFFFDAD6)
internal val Teal10 = Color(0xFF001F26)
internal val Teal20 = Color(0xFF02363F)
internal val Teal30 = Color(0xFF214D56)
internal val Teal40 = Color(0xFF3A656F)
internal val Teal80 = Color(0xFFA2CED9)
internal val Teal90 = Color(0xFFBEEAF6)

/**
 * Elegant Serenity theme colors - sophisticated palette for prayer and meditation
 * Features deep forest greens, warm golds, and soft sage tones
 */
// Forest Green Family - Deep, sophisticated greens
internal val ForestGreen10 = Color(0xFF0D1B0F)  // Deep forest shadow
internal val ForestGreen20 = Color(0xFF1A2F1D)  // Dark forest green
internal val ForestGreen30 = Color(0xFF2E4B32)  // Medium forest green
internal val ForestGreen40 = Color(0xFF3E5B41)  // Elegant forest green - sophisticated
internal val ForestGreen80 = Color(0xFF9BB99E)  // Light sage green - calming
internal val ForestGreen90 = Color(0xFFC8D4CA)  // Pale sage green

// Warm Gold Family - Elegant metallic accents
internal val WarmGold10 = Color(0xFF2A1F0A)     // Deep bronze
internal val WarmGold20 = Color(0xFF4A3B18)     // Dark gold
internal val WarmGold30 = Color(0xFF6B5526)     // Medium gold
internal val WarmGold40 = Color(0xFF8B6914)     // Rich warm gold - luxurious
internal val WarmGold80 = Color(0xFFD4C078)     // Light champagne gold
internal val WarmGold90 = Color(0xFFE8DFB8)     // Pale golden cream

// Soft Sage Family - Peaceful natural tones
internal val SoftSage10 = Color(0xFF1C1F1A)     // Deep sage shadow
internal val SoftSage20 = Color(0xFF2F332B)     // Dark sage
internal val SoftSage30 = Color(0xFF4A523E)     // Medium sage
internal val SoftSage40 = Color(0xFF5C6B4F)     // Gentle sage - peaceful
internal val SoftSage80 = Color(0xFFB8C3AC)     // Light sage
internal val SoftSage90 = Color(0xFFD6DFD0)     // Pale sage cream

// Garnet Family - Deep refined red (Apple "Garnet" accent)
internal val Garnet10 = Color(0xFF2A0A0E)       // Very deep garnet
internal val Garnet20 = Color(0xFF4A1A20)       // Dark garnet
internal val Garnet30 = Color(0xFF6B2A30)       // Medium dark garnet
internal val Garnet40 = Color(0xFF9B3D3A)       // Rich garnet — primary
internal val Garnet80 = Color(0xFFE6A89F)       // Soft garnet tint
internal val Garnet90 = Color(0xFFF6D9D2)       // Pale garnet cream

// Lapis Family - Deep refined blue (Apple "Lapis" accent)
internal val Lapis10 = Color(0xFF071029)        // Deep ink blue
internal val Lapis20 = Color(0xFF152547)        // Dark lapis
internal val Lapis30 = Color(0xFF233E70)        // Medium dark lapis
internal val Lapis40 = Color(0xFF2D5DA8)        // Rich lapis — primary
internal val Lapis80 = Color(0xFFA8C4EE)        // Soft lapis tint
internal val Lapis90 = Color(0xFFD6E2F7)        // Pale lapis cream

// Wave Gray Family - Premium neutral tones
internal val WaveGray10 = Color(0xFF1C1E20)   // Dark wave shadow
internal val WaveGray20 = Color(0xFF2F3133)   // Medium dark gray
internal val WaveGray30 = Color(0xFF424547)   // Medium gray
internal val WaveGray50 = Color(0xFF707578)   // Neutral gray
internal val WaveGray60 = Color(0xFF8A8E91)   // Light medium gray
internal val WaveGray80 = Color(0xFFC4C7CA)   // Premium light gray
internal val WaveGray90 = Color(0xFFE4E6E9)   // Elegant very light gray
internal val WaveGray95 = Color(0xFFF2F4F6)   // Sophisticated near white
internal val WaveGray99 = Color(0xFFFAFBFC)   // Pure coastal white
