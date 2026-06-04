package com.starception.submission.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.search.SearchBar
import com.starception.submission.R

@Composable
fun AppTopSearchBar(
    title: String,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    val pillBackground = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    AndroidView(
        modifier = modifier.fillMaxWidth().padding(top = topInset),
        factory = { ctx ->
            val themed = ContextThemeWrapper(
                ctx,
                MaterialR.style.Theme_Material3Expressive_DayNight_NoActionBar,
            )
            val root = LayoutInflater.from(themed).inflate(R.layout.app_top_search_bar, null, false)
            val searchBar = root.findViewById<SearchBar>(R.id.app_search_bar)
            val leading = root.findViewById<MaterialButton>(R.id.leading_button)
            val settings = root.findViewById<MaterialButton>(R.id.settings_button)
            searchBar.hint = ctx.getString(R.string.app_top_bar_search_hint) + " " + title
            searchBar.setOnClickListener { onSearchClick() }
            searchBar.inflateMenu(R.menu.app_top_search_bar_menu)
            searchBar.setOnMenuItemClickListener { onSearchClick(); true }
            leading.setOnClickListener { onSearchClick() }
            settings.setOnClickListener { onSettingsClick() }
            root
        },
        update = { root ->
            val searchBar = root.findViewById<SearchBar>(R.id.app_search_bar)
            val appBar = root.findViewById<AppBarLayout>(R.id.app_bar_layout)
            searchBar.hint = root.context.getString(R.string.app_top_bar_search_hint) + " " + title
            root.background = ColorDrawable(Color.TRANSPARENT)
            appBar.background = ColorDrawable(Color.TRANSPARENT)
            appBar.backgroundTintList = null
            searchBar.backgroundTintList = ColorStateList.valueOf(pillBackground)
        },
    )
}
