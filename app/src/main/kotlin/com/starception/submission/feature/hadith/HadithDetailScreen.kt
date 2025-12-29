package com.starception.submission.feature.hadith

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starception.submission.core.hadithdatabase.Hadith
import com.starception.submission.core.hadithdatabase.HadithRepository
import com.starception.submission.feature.surah.QuranFonts

// Gradient colors for hadith header - earthy tones
private val HadithGradientColors = listOf(
    Color(0xFF795548),  // Brown
    Color(0xFF8D6E63),  // Light brown
    Color(0xFF6D4C41),  // Dark brown
    Color(0xFF5D4037),  // Darker brown
    Color(0xFF4E342E)   // Deepest brown
)

/**
 * Hadith Detail Screen - displays a single hadith from a collection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HadithDetailScreen(
    collectionName: String,
    hadithNumber: Int,
    databaseFile: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { HadithRepository.getInstance(context) }

    var hadith by remember { mutableStateOf<Hadith?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Load hadith
    LaunchedEffect(databaseFile, hadithNumber) {
        isLoading = true
        error = null
        try {
            hadith = repository.getHadith(databaseFile, hadithNumber)
            if (hadith == null) {
                error = "Hadith not found"
            }
        } catch (e: Exception) {
            error = e.message ?: "Error loading hadith"
            android.util.Log.e("HadithDetailScreen", "Error loading hadith", e)
        }
        isLoading = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                isLoading -> {
                    HadithShimmerLoading(onBackClick = onBackClick)
                }
                error != null -> {
                    HadithErrorContent(
                        error = error!!,
                        onBackClick = onBackClick
                    )
                }
                hadith != null -> {
                    HadithContent(
                        hadith = hadith!!,
                        collectionName = collectionName,
                        hadithNumber = hadithNumber,
                        onBackClick = onBackClick
                    )
                }
            }
        }
    }
}

@Composable
private fun HadithContent(
    hadith: Hadith,
    collectionName: String,
    hadithNumber: Int,
    onBackClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Gradient Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(
                            brush = Brush.linearGradient(colors = HadithGradientColors)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .padding(top = 80.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Icon
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.25f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = "Hadith",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Collection name
                        Text(
                            text = hadith.collectionNameEnglish.ifEmpty { collectionName },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )

                        // Hadith number
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Hadith #$hadithNumber",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        // Author
                        if (hadith.author.isNotEmpty()) {
                            Text(
                                text = "Compiled by ${hadith.author}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            }

            // Arabic Text Card
            if (hadith.textArabic.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8F5F0), // Cream background
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = hadith.textArabic,
                                fontFamily = QuranFonts.PDMSSaleem,
                                fontSize = 26.sp,
                                lineHeight = 48.sp,
                                textAlign = TextAlign.Center,
                                color = Color(0xFF3D3D3D)
                            )
                        }
                    }
                }
            }

            // English Translation
            if (!hadith.textPlain.isNullOrEmpty()) {
                item {
                    HadithSectionCard(
                        title = "Translation",
                        accentColor = Color(0xFF8D6E63), // Brown accent
                        content = hadith.textPlain!!
                    )
                }
            }

            // Elaboration/Explanation
            if (!hadith.elaboration.isNullOrEmpty()) {
                item {
                    HadithSectionCard(
                        title = "Explanation",
                        accentColor = Color(0xFF6D4C41), // Dark brown accent
                        content = hadith.elaboration!!,
                        isExpanded = false
                    )
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Fixed toolbar at top
        Surface(
            color = HadithGradientColors[0],
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Collection badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Text(
                        text = collectionName,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HadithSectionCard(
    title: String,
    accentColor: Color,
    content: String,
    isExpanded: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent border
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(androidx.compose.ui.unit.Dp.Unspecified)
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    ),
                    color = Color(0xFF5D5D5D)
                )
            }
        }
    }
}

@Composable
private fun HadithErrorContent(
    error: String,
    onBackClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Error loading hadith",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
    }
}

@Composable
private fun HadithShimmerLoading(
    onBackClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        brush = Brush.linearGradient(colors = HadithGradientColors)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon placeholder
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(16.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Title placeholder
                    Box(
                        modifier = Modifier
                            .size(width = 150.dp, height = 24.dp)
                            .background(
                                Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp)
                            )
                    )
                }
            }

            // Content shimmer
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                    )
                }
            }
        }

        // Back button
        Surface(
            color = HadithGradientColors[0],
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
