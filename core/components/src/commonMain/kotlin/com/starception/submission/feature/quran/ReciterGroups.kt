package com.starception.submission.feature.quran

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Discovery groups, separate from each recording's riwayah and recitation style.
 * Based on https://recitid.ai/blog/top-20-quran-reciters (October 2026).
 */
enum class ReciterGroup(val label: String) {
    EGYPTIAN_LEGENDS("Egyptian legends"),
    HARAMAIN_IMAMS("Haramain imams"),
    CONTEMPORARY("Contemporary favourites"),
    NEW_WAVE("New wave"),
    MORE("More reciters"),
}

// The catalog uses several transliterations and offers multiple recordings per
// person. Match names rather than server/recording IDs, keeping every variant.
private val reciterGroupAliases = mapOf(
    ReciterGroup.EGYPTIAN_LEGENDS to listOf(
        "abdelbasset", "abdulbasit", "abdulbaset", "abdulbasset",
        "minshawi", "menshawi", "minchaoui", "menchaoui",
        "husary", "hussary", "hosary", "hossary",
        "mustafaismail", "mustaphaismail", "mustafaismael", "alialbanna", "alielbanna",
    ),
    ReciterGroup.HARAMAIN_IMAMS to listOf(
        "sudais", "sudays", "shuraim", "shuraym", "shoraim", "shoraym",
        "muaiqly", "muaiqli", "muayqly", "mueaqly", "maikli",
        "juhany", "juhani", "johany", "johani",
        "yasserdosari", "yasserdossari", "yasseraldosari", "yasseraldossari",
        "yassiraldosari", "yasseraldossary", "yasserdossary",
    ),
    ReciterGroup.CONTEMPORARY to listOf(
        "afasy", "afassi", "afasi", "saadalghamdi", "saadalghamdy", "saadalghamidi",
        "ahmedalajmi", "ahmadalajmi", "ahmedajmi", "ahmadalajami", "nasseralqatami", "nasseralqatamy",
        "hanialrifai", "haniarrifai", "hanirifai", "hanialrefai", "idrisabkar", "idreesabkar", "idrissabkar",
    ),
    ReciterGroup.NEW_WAVE to listOf(
        "faresabbad", "farisabbad", "faresabad", "raadalkurdi", "radalkurdi",
        "raadmuhammadalkurdi", "raadmohammedalkurdi", "raadmohamedalkurdi", "raadmohammadalkurdi",
        "khalifaaltunaiji", "khalifaaltunaijy", "khalifaaltunaji",
        "abdulrahmanalossi", "abdurrahmanalossi", "abdelrahmanalossi", "abderrahmanalossi",
        "abdulrahmanaloosi",
    ),
)

fun reciterGroup(name: String): ReciterGroup {
    val normalized = name.lowercase().filter(Char::isLetterOrDigit)
    return reciterGroupAliases.entries.firstOrNull { (_, aliases) ->
        aliases.any(normalized::contains)
    }?.key ?: ReciterGroup.MORE
}

@Composable
fun ReciterGroupFilters(selected: ReciterGroup?, onSelect: (ReciterGroup?) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        ReciterGroup.entries.forEach { group ->
            FilterChip(selected = selected == group, onClick = { onSelect(group) },
                label = { Text(group.label) })
        }
    }
}
