package com.jksalcedo.librefind.ui.community

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jksalcedo.librefind.R
import com.jksalcedo.librefind.ui.common.LibreFindLoadingIndicator
import com.jksalcedo.librefind.ui.components.CommentSection
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionDetailScreen(
    submissionId: String,
    onBackClick: () -> Unit,
    onEditClick: (String) -> Unit,
    viewModel: CommunitySubmissionsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val submission = state.submissions.find { it.id == submissionId }
    var showDownvoteSheet by remember { mutableStateOf(false) }

    if (showDownvoteSheet && submission != null) {
        DownvoteSheet(
            onDismiss = { showDownvoteSheet = false },
            onConfirm = { reason, detail ->
                viewModel.castVote(submission, -1, reason, detail)
                showDownvoteSheet = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.submission_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (submission == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.isLoading) {
                    LibreFindLoadingIndicator()
                } else {
                    Text(stringResource(R.string.submission_detail_not_found))
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card (Title, Submitter, and Votes)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Type Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = submission.type.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = submission.submittedApp.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Submitter Info
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = submission.submitterUsername,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (submission.lastEditedBy != null) {
                            Text(
                                text = stringResource(
                                    R.string.submission_detail_edited_by,
                                    submission.lastEditedBy
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Inline Action Bar (Votes & Edit)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VoteButton(
                                icon = if (submission.userVote == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                label = "${submission.upvotes}",
                                active = submission.userVote == 1,
                                activeColor = MaterialTheme.colorScheme.primary,
                                contentDescription = stringResource(R.string.submission_upvote),
                                onClick = { viewModel.castVote(submission, 1) }
                            )
                            VoteButton(
                                icon = if (submission.userVote == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                label = "${submission.downvotes}",
                                active = submission.userVote == -1,
                                activeColor = MaterialTheme.colorScheme.error,
                                contentDescription = stringResource(R.string.submission_downvote),
                                onClick = {
                                    if (submission.userVote == -1) viewModel.castVote(
                                        submission,
                                        -1
                                    )
                                    else showDownvoteSheet = true
                                }
                            )
                        }

                        Button(onClick = { onEditClick(submission.id) }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.submission_detail_edit))
                        }
                    }
                }
            }

            // Relationship Boxes (Targets and Solutions)
            if (submission.proprietaryPackages.isNotBlank() || submission.linkedAlternatives.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (submission.proprietaryPackages.isNotBlank()) {
                        RelationshipBox(
                            title = stringResource(R.string.submission_detail_replaces),
                            items = submission.proprietaryPackages,
                            isProprietary = true
                        )
                    }
                    if (submission.linkedAlternatives.isNotEmpty()) {
                        RelationshipBox(
                            title = stringResource(R.string.submission_detail_linked_solutions),
                            items = submission.linkedAlternatives.joinToString(", "),
                            isProprietary = false
                        )
                    }
                }
            }

            // App Metadata Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.submission_detail_app_details),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    DetailItemWithIcon(
                        icon = Icons.Default.Code,
                        label = stringResource(R.string.submission_detail_package_name),
                        value = submission.submittedApp.packageName,
                        isMono = true
                    )
                    DetailItemWithIcon(
                        icon = Icons.Default.Category,
                        label = stringResource(R.string.submission_detail_category),
                        value = submission.category
                            ?: stringResource(R.string.submission_detail_unknown)
                    )
                    DetailItemWithIcon(
                        icon = Icons.Default.Gavel,
                        label = stringResource(R.string.submission_detail_license),
                        value = submission.submittedApp.license.ifBlank { stringResource(R.string.submission_detail_unknown) }
                    )
                    DetailItemWithIcon(
                        icon = Icons.Default.Link,
                        label = stringResource(R.string.submission_detail_repo_url),
                        value = submission.submittedApp.repoUrl.ifBlank { stringResource(R.string.submission_detail_na) },
                        isClickable = true
                    )
                    DetailItemWithIcon(
                        icon = Icons.Default.Shop,
                        label = stringResource(R.string.submission_detail_fdroid_id),
                        value = submission.submittedApp.fdroidId.ifBlank { stringResource(R.string.submission_detail_na) }
                    )

                    // Description Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.submission_detail_description),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = submission.submittedApp.description.ifBlank {
                                    stringResource(
                                        R.string.submission_detail_no_description
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Comments Feed
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.submission_detail_discussion),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                CommentSection(targetId = submission.id)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailItemWithIcon(
    icon: ImageVector,
    label: String,
    value: String,
    isMono: Boolean = false,
    isClickable: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val context = LocalContext.current
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (isClickable) TextDecoration.Underline else TextDecoration.None,
                modifier = Modifier.clickable(isClickable) {
                    if (isClickable) {
                        val intent = Intent(Intent.ACTION_VIEW, value.toUri())
                        context.startActivity(intent)
                    }
                }
            )
        }
    }
}

@Composable
private fun RelationshipBox(title: String, items: String, isProprietary: Boolean) {
    val containerColor =
        if (isProprietary) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val contentColor =
        if (isProprietary) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = items,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = contentColor
            )
        }
    }
}

@Composable
private fun VoteButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    val tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = tint
        )
    }
}