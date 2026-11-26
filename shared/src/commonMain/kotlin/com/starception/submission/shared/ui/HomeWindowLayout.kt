package com.starception.submission.shared.ui

/** The current artwork Home is shared by portrait phones and all tablet/Mac windows. */
internal fun useArtworkHomeLayout(width: Float, height: Float): Boolean =
    (width >= 600f && height >= 600f) || width < height
