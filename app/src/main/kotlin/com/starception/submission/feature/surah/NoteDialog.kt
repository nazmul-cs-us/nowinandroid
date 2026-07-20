package com.starception.submission.feature.surah

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.starception.submission.core.designsystem.animation.NiaMotion
import com.starception.submission.core.qurandatabase.AyahNoteEntity
import java.text.SimpleDateFormat
import java.util.*
import com.starception.submission.core.designsystem.component.NiaOutlinedButton

/**
 * Material 3 Expressive Note Dialog for Quran Ayahs
 *
 * Features:
 * - Professional header with gradient accent
 * - Elegant ayah reference badge
 * - Tonal surface cards for notes
 * - Smooth animations for state transitions
 * - Modern empty state design
 * - FAB-style save action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDialog(
    surahNumber: Int,
    ayahNumber: Int,
    surahName: String,
    existingNotes: List<AyahNoteEntity>,
    onSaveNote: (String) -> Unit,
    onUpdateNote: (AyahNoteEntity) -> Unit,
    onDeleteNote: (AyahNoteEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<AyahNoteEntity?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf<AyahNoteEntity?>(null) }
    var isInputExpanded by remember { mutableStateOf(existingNotes.isEmpty()) }

    // Sync editing state
    LaunchedEffect(editingNote) {
        noteText = editingNote?.noteText ?: ""
        if (editingNote != null) isInputExpanded = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .heightIn(max = 600.dp), // Max height, but wrap content when smaller
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.wrapContentHeight()) {
                // Header with gradient accent
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column {
                        // Top row with close button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Title with icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Animated icon container
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Crossfade(
                                            targetState = editingNote != null,
                                            animationSpec = NiaMotion.standardTween(NiaMotion.Duration.SHORT_4)
                                        ) { isEditing ->
                                            Icon(
                                                imageVector = if (isEditing)
                                                    Icons.Outlined.EditNote
                                                else
                                                    Icons.Outlined.NoteAlt,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                Column {
                                    Crossfade(
                                        targetState = editingNote != null,
                                        animationSpec = NiaMotion.standardTween(NiaMotion.Duration.SHORT_4)
                                    ) { isEditing ->
                                        Text(
                                            text = if (isEditing) "Edit Note" else "Notes",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // Close button
                            FilledTonalIconButton(
                                onClick = {
                                    if (editingNote != null) {
                                        editingNote = null
                                        noteText = ""
                                    } else {
                                        onDismiss()
                                    }
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Ayah reference badge
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "$surahName",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$ayahNumber",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp)
                ) {
                    // Expandable input section
                    AnimatedVisibility(
                        visible = isInputExpanded || existingNotes.isEmpty(),
                        enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Note input card
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    OutlinedTextField(
                                        value = noteText,
                                        onValueChange = { noteText = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 120.dp, max = 180.dp),
                                        placeholder = {
                                            Text(
                                                "Write your thoughts, reflections, or reminders...",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        },
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                        shape = RoundedCornerShape(16.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Action buttons row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (editingNote != null) {
                                            TextButton(
                                                onClick = {
                                                    editingNote = null
                                                    noteText = ""
                                                    if (existingNotes.isNotEmpty()) {
                                                        isInputExpanded = false
                                                    }
                                                }
                                            ) {
                                                Text("Cancel")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        NiaOutlinedButton(
                                            onClick = {
                                                if (noteText.isNotBlank()) {
                                                    if (editingNote != null) {
                                                        onUpdateNote(editingNote!!.copy(
                                                            noteText = noteText.trim(),
                                                            updatedAt = System.currentTimeMillis()
                                                        ))
                                                        editingNote = null
                                                    } else {
                                                        onSaveNote(noteText.trim())
                                                    }
                                                    noteText = ""
                                                    if (existingNotes.isNotEmpty()) {
                                                        isInputExpanded = false
                                                    }
                                                }
                                            },
                                            enabled = noteText.isNotBlank(),
                                        ) {
                                            Icon(
                                                imageVector = if (editingNote != null) Icons.Default.Check else Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                if (editingNote != null) "Update" else "Save Note",
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Notes list section
                    if (existingNotes.isNotEmpty()) {
                        // Section header with add button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Your Notes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${existingNotes.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            // Add button (when collapsed)
                            AnimatedVisibility(
                                visible = !isInputExpanded,
                                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                FilledTonalIconButton(
                                    onClick = { isInputExpanded = true },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add note",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // Notes list
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp), // Max height for scrollable list
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(
                                items = existingNotes,
                                key = { it.id }
                            ) { note ->
                                NoteCard(
                                    note = note,
                                    isEditing = editingNote?.id == note.id,
                                    onEditClick = {
                                        editingNote = note
                                        isInputExpanded = true
                                    },
                                    onDeleteClick = { showDeleteConfirmation = note }
                                )
                            }
                        }
                    } else {
                        // Empty state (when no notes and input is collapsed - shouldn't happen often)
                        if (!isInputExpanded) {
                            EmptyNotesState(
                                onAddClick = { isInputExpanded = true }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    "Delete Note?",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "This note will be permanently removed. This action cannot be undone.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                NiaOutlinedButton(
                    onClick = {
                        showDeleteConfirmation?.let { onDeleteNote(it) }
                        if (editingNote?.id == showDeleteConfirmation?.id) {
                            editingNote = null
                            noteText = ""
                        }
                        showDeleteConfirmation = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
private fun NoteCard(
    note: AyahNoteEntity,
    isEditing: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val containerColor by animateColorAsState(
        targetValue = if (isEditing)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(300),
        label = "card_color"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = if (isEditing) 4.dp else 0.dp,
        shadowElevation = if (isEditing) 4.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Note content
            Text(
                text = note.noteText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isEditing)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Footer with timestamp and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timestamp with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "${dateFormat.format(Date(note.updatedAt))} at ${timeFormat.format(Date(note.updatedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Action buttons
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Edit button
                    FilledTonalIconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isEditing)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit note",
                            modifier = Modifier.size(18.dp),
                            tint = if (isEditing)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Delete button
                    FilledTonalIconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete note",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotesState(
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Decorative icon container
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.NoteAlt,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No notes yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add personal notes to remember\nimportant reflections on this ayah",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        NiaOutlinedButton(
            onClick = onAddClick,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Add Your First Note",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
