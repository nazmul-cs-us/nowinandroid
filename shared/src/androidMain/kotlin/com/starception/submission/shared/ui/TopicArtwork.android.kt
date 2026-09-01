/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.starception.submission.shared.ui

import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.starception.submission.core.designsystem.icon.NiaIcons

@Composable
internal actual fun TopicArtwork(topicName: String, modifier: Modifier) {
    val resourceId = drawableId(topicArtworkResourceName(topicName))
    if (resourceId != 0) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        Icon(NiaIcons.Upcoming, contentDescription = null, modifier = modifier)
    }
}

@Composable
internal actual fun NewsHeaderArtwork(resourceName: String, modifier: Modifier) {
    val resourceId = drawableId(resourceName)
    if (resourceId != 0) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
internal actual fun LocationMarkerArtwork(tint: Color, modifier: Modifier) {
    val resourceId = drawableId("ic_flaticon_location_marker")
    if (resourceId != 0) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
private fun drawableId(resourceName: String): Int {
    val context = LocalContext.current
    return context.resources.getIdentifier(resourceName, "drawable", context.packageName)
}
