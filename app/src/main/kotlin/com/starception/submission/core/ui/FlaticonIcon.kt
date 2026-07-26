package com.starception.submission.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.starception.submission.R

/**
 * UIcons Regular Rounded glyphs bundled with the app. Keep this catalog semantic so
 * screens share one visual language instead of depending on icon-library names.
 */
object FlaticonIcons {
    const val ANGLE_DOWN = "\uf150"
    const val ANGLE_LEFT = "\uf151"
    const val ANGLE_RIGHT = "\uf152"
    const val ANGLE_UP = "\uf15b"
    const val ARROW_BACK = "\uf197"
    const val TRENDING_UP = "\uf1a9"
    const val AWARD = "\uf1df"
    const val VERIFIED = "\uf1eb"
    const val NOTIFICATIONS_ACTIVE = "\uf235"
    const val NOTIFICATIONS = "\uf239"
    const val QUICK_ACTION = "\uf26c"
    const val OPEN_BOOK = "\uf281"
    const val QURAN = "\uf283"
    const val BOOK = "\uf288"
    const val BOOKMARK = "\uf28b"
    const val KNOWLEDGE = "\uf2de"
    const val SYSTEM_THEME = "\uf2eb"
    const val SCHEDULE = "\uf325"
    const val TRAVEL = "\uf369"
    const val COMPLETED = "\uf3c4"
    const val CHECK = "\uf3c8"
    const val MORE = "\uf3ff"
    const val INCOMPLETE = "\uf42c"
    const val DEVELOPER = "\uf48a"
    const val STORAGE = "\uf525"
    const val DOWNLOAD = "\uf591"
    const val EDIT = "\uf5c1"
    const val GRADUATION = "\uf70f"
    const val HEADPHONES = "\uf793"
    const val INFO = "\uf80b"
    const val LOCK = "\uf8d5"
    const val MEDAL = "\uf910"
    const val ANNOUNCEMENT = "\uf917"
    const val MICROPHONE = "\uf944"
    const val REMOVE = "\uf94b"
    const val DARK_THEME = "\uf972"
    const val PRAYER_TIMES = "\uf978"
    const val APPEARANCE = "\uf9ed"
    const val PAUSE = "\ufa0c"
    const val SALAH_TRAINING = "\ufa55"
    const val PLAY = "\ufa9e"
    const val QUIZ = "\ufafa"
    const val REFRESH = "\ufb34"
    const val SCHOOL = "\ufba1"
    const val SETTINGS = "\ufbd1"
    const val SHARE = "\ufbd5"
    const val DIFFICULTY = "\ufc13"
    const val SPEED = "\ufc89"
    const val STAR = "\ufce9"
    const val STOP = "\ufcfa"
    const val TIMER = "\ufcfb"
    const val LIGHT_THEME = "\ufd17"
    const val TRAFFIC = "\ufddc"
    const val DELETE = "\ufe17"
    const val WARNING = "\ufe33"
    const val TROPHY = "\ufe39"
    const val STUDENT = "\ufe83"
    const val VOLUME = "\ufed0"
    const val VOICE = "\ufef1"
}

private val flaticonUiconsFont = FontFamily(Font(R.font.flaticon_uicons_rounded_regular))

/** A tintable Flaticon glyph that follows the surrounding Material content color. */
@Composable
fun FlaticonIcon(
    glyph: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    fontSize: TextUnit = 22.sp,
) {
    Box(
        modifier = if (contentDescription == null) {
            modifier
        } else {
            modifier.semantics { this.contentDescription = contentDescription }
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = tint,
            fontFamily = flaticonUiconsFont,
            fontSize = fontSize,
            lineHeight = fontSize,
        )
    }
}
