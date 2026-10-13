package com.starception.submission.shared.ui

import androidx.compose.runtime.Composable
import io.github.alexzhirkevich.cupertino.theme.CupertinoTheme
import io.github.alexzhirkevich.cupertino.theme.darkColorScheme
import io.github.alexzhirkevich.cupertino.theme.lightColorScheme

@Composable
internal fun AppCupertinoTheme(
    useDarkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    CupertinoTheme(
        colorScheme = if (useDarkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
