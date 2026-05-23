/*
 * Copyright 2026 Starception
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

package com.starception.submission.core.model.data

/**
 * Independent text accent color picker. Applies on top of the chosen [ThemeBrand]
 * and tints highlight/header text without changing the rest of the color scheme.
 * Lives separately from [ThemeBrand] so a user can have, say, the Coastal theme
 * but want Garnet headings.
 */
enum class TextAccentColor {
    /** Use the active theme's primary color (no override). */
    DEFAULT,
    GOLD,
    SAGE,
    GARNET,
    LAPIS,
}
