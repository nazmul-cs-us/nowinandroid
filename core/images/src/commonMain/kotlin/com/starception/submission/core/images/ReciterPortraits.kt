package com.starception.submission.core.images

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.starception.submission.core.images.resources.*
import org.jetbrains.compose.resources.painterResource

/** Explicit aliases only: never assign a stranger's portrait using a fuzzy name search. */
fun reciterPersonKey(name: String): String {
    val key = name.lowercase().filter(Char::isLetterOrDigit)
    return when {
        listOf("mishary", "mishari", "mshari").any(key::contains) &&
            listOf("afasy", "afassi", "afasi").any(key::contains) -> "alafasy"
        listOf("abdulbasit", "abdulbaset", "abdelbasset", "abdulbasset").any(key::contains) &&
            listOf("abdessamad", "abdulsamad", "abdussamad", "abdelsamad").any(key::contains) -> "abdul-basit"
        listOf("abdulrahman", "abdurrahman", "abdelrahman", "abderrahman").any(key::contains) &&
            listOf("sudais", "sudays", "sudaes").any(key::contains) -> "sudais"
        key.contains("mahmoud") && listOf("husary", "hussary", "hosary", "hossary").any(key::contains) -> "husary"
        listOf("muhammad", "mohamed", "mohammed").any(key::contains) &&
            listOf("minshawi", "menshawi", "menchaoui", "minchaoui").any(key::contains) -> "minshawi"
        listOf("saud", "saoud").any(key::contains) &&
            listOf("shuraim", "shuraym", "shoraim", "shoraym").any(key::contains) -> "shuraim"
        listOf("maher", "mahir").any(key::contains) &&
            listOf("muaiqly", "muaiqli", "muayqly", "mueaqly", "maikli").any(key::contains) -> "maher"
        else -> key
    }
}

/** Small bundled portraits remain available while offline. Unknown names use initials in the UI. */
@Composable
fun reciterPortrait(name: String): Painter? = when (reciterPersonKey(name)) {
    "alafasy" -> painterResource(Res.drawable.reciter_alafasy)
    "abdul-basit" -> painterResource(Res.drawable.reciter_abdul_basit)
    "sudais" -> painterResource(Res.drawable.reciter_sudais)
    "husary" -> painterResource(Res.drawable.reciter_husary)
    "minshawi" -> painterResource(Res.drawable.reciter_minshawi)
    "shuraim" -> painterResource(Res.drawable.reciter_shuraim)
    "maher" -> painterResource(Res.drawable.reciter_maher)
    else -> null
}
