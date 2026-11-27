package com.starception.submission.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.images.resources.Res
import com.starception.submission.core.images.resources.lora_regular
import org.jetbrains.compose.resources.Font

internal enum class HomeReadingKind { Quran, Hadith, Dua }
internal fun homeReadingKind(target: DeenlyKnowledgeTarget): HomeReadingKind = when (target) {
    is DeenlyKnowledgeTarget.Surah -> HomeReadingKind.Quran
    is DeenlyKnowledgeTarget.FortressDua -> HomeReadingKind.Dua
    is DeenlyKnowledgeTarget.BukhariHadith, is DeenlyKnowledgeTarget.ShamayelBook -> HomeReadingKind.Hadith
}

/** Same editorial list, serif source titles and compact citations as Android Home. */
@Composable
internal fun HomeReadingRecommendations(
    cards: List<DeenlyKnowledgeCard>, preparing: Boolean,
    modifier: Modifier = Modifier, onOpen: (DeenlyKnowledgeCard) -> Unit,
) {
    if (cards.isEmpty() && !preparing) return
    val ink = MaterialTheme.colorScheme.onBackground
    val headline = HomeTypography.reflection.copy(fontFamily = FontFamily(Font(Res.font.lora_regular)),
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp)
    Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
        HorizontalDivider(color = ink.copy(alpha = .16f))
        Text("Further reading", modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            style = HomeTypography.eyebrow, color = ink.copy(alpha = .72f))
        for (card in cards) {
            val title = when (val target = card.target) {
                is DeenlyKnowledgeTarget.Surah -> com.starception.submission.feature.quran.QuranData.surahs
                    .firstOrNull { it.number == target.number }?.let { "Surah ${it.nameEnglish}" } ?: "Surah ${target.number}"
                is DeenlyKnowledgeTarget.FortressDua -> card.title.ifBlank { "Dua" }
                else -> card.title.ifBlank { "Hadith" }
            }
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(card) }
                .heightIn(min = 48.dp).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = headline, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(homeEvidencePreview(card.sourceText), style = HomeTypography.body, color = ink.copy(alpha = .84f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(card.reference, style = HomeTypography.caption, color = ink.copy(alpha = .72f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(color = ink.copy(alpha = .10f))
        }
        if (preparing) Text("Preparing recommendations…", modifier = Modifier.padding(vertical = 8.dp),
            style = HomeTypography.caption, color = ink.copy(alpha = .72f))
    }
}
