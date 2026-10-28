package com.starception.submission.ui

import android.app.Application
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HomeContentOverlapTest {
    @Test fun homeRemovesTopBarOffsetAndOtherPagesRestoreIt() {
        val context = ContextThemeWrapper(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3Expressive_DayNight_NoActionBar,
        )
        val parent = CoordinatorLayout(context)
        val appBar = AppBarLayout(context)
        parent.addView(appBar, CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 56,
        ))
        val content = FrameLayout(context)
        parent.addView(content, CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply { behavior = AppBarLayout.ScrollingViewBehavior() })

        assertNotNull((content.layoutParams as CoordinatorLayout.LayoutParams).behavior)
        configureHomeContentOverlap(content, immersiveHome = true)
        assertNull((content.layoutParams as CoordinatorLayout.LayoutParams).behavior)
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.EXACTLY),
        )
        parent.layout(0, 0, 393, 852)
        assertEquals(0, content.top)

        configureHomeContentOverlap(content, immersiveHome = false)
        assertNotNull((content.layoutParams as CoordinatorLayout.LayoutParams).behavior)
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.EXACTLY),
        )
        parent.layout(0, 0, 393, 852)
        assertEquals(appBar.bottom, content.top)
    }
}
