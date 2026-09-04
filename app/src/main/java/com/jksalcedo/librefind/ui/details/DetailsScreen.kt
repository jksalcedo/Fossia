package com.jksalcedo.librefind.ui.details

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jksalcedo.librefind.R
import com.jksalcedo.librefind.domain.model.Alternative
import com.jksalcedo.librefind.ui.common.AppInfoCard
import com.jksalcedo.librefind.ui.common.FullScreenLoading
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    appName: String,
    packageName: String,
    onBackClick: () -> Unit,
    onAlternativeClick: (String) -> Unit,
    onSuggestAsFoss: (appName: String, packageName: String) -> Unit = { _, _ -> },
    onSuggestAsProprietary: (appName: String, packageName: String) -> Unit = { _, _ -> },
    onAddAlternativeClick: (appName: String, packageName: String) -> Unit = { _, _ -> },
    onSuggestCorrection: (packageName: String) -> Unit = {},
    onSubmitSigningKey: (appName: String, packageName: String) -> Unit = { _, _ -> },
    onViewPendingSubmission: (submissionId: String) -> Unit = {},
    viewModel: DetailsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(packageName) {
        viewModel.loadAlternatives(packageName)
    }

    val showFab = !state.isLoading && state.error == null && !state.isUnknown && !state.isFoss

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title_format, appName)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (state.isUnknown && state.isSignedIn) {
                        IconButton(onClick = { onSubmitSigningKey(appName, packageName) }) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = stringResource(R.string.signing_key_title)
                            )
                        }
                    }
                    IconButton(onClick = { onSuggestCorrection(packageName) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.correction_title)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = { onAddAlternativeClick(appName, packageName) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.details_suggest_alternative)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    FullScreenLoading()
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.error ?: stringResource(R.string.details_error_unknown),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry(packageName) }) {
                            Text(stringResource(R.string.details_retry))
                        }
                    }
                }

                state.alternatives.isEmpty() && state.siblingAlternatives.isEmpty() && state.pendingSubmissions.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        AppInfoCard(
                            appName = state.appInfo?.name ?: appName,
                            packageName = packageName,
                            category = state.appInfo?.category,
                            license = state.appInfo?.license,
                            description = state.appInfo?.description
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.details_no_alternatives),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    when {
                                        state.isUnknown -> R.string.details_not_in_db
                                        state.fossCategoryUnset -> R.string.details_foss_category_unset
                                        state.isFoss -> R.string.details_no_siblings
                                        else -> R.string.details_no_suggested_alternatives
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.isUnknown) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.details_help_categorize),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { onSuggestAsFoss(appName, packageName) },
                                        modifier = Modifier.width(160.dp)
                                    ) {
                                        Text(stringResource(R.string.details_suggest_foss))
                                    }
                                    Button(
                                        onClick = { onSuggestAsProprietary(appName, packageName) },
                                        modifier = Modifier.width(160.dp)
                                    ) {
                                        Text(stringResource(R.string.details_suggest_proprietary))
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    val displayList =
                        if (state.isFoss) state.siblingAlternatives else state.alternatives
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            AppInfoCard(
                                appName = state.appInfo?.name ?: appName,
                                packageName = packageName,
                                category = state.appInfo?.category,
                                license = state.appInfo?.license,
                                description = state.appInfo?.description
                            )
                            if (displayList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (state.isFoss) {
                                        stringResource(
                                            R.string.details_siblings_found_format,
                                            displayList.size,
                                            if (displayList.size > 1) "s" else ""
                                        )
                                    } else {
                                        stringResource(
                                            R.string.details_found_format,
                                            displayList.size,
                                            if (displayList.size > 1) "s" else ""
                                        )
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }

                        items(displayList) { alternative ->
                            AlternativeListItem(
                                alternative = alternative,
                                onClick = { onAlternativeClick(alternative.id) },
                                onMatchVote = if (!state.isFoss) { vote ->
                                    viewModel.castMatchVote(alternative.packageName, vote)
                                } else null
                            )
                        }

                        items(state.pendingSubmissions, key = { it.id }) { submission ->
                            PendingAlternativeCard(
                                name = submission.submittedApp.name,
                                description = submission.submittedApp.description,
                                license = submission.submittedApp.license,
                                upvotes = submission.upvotes,
                                downvotes = submission.downvotes,
                                onClick = { onViewPendingSubmission(submission.id) }
                            )
                        }

                        if (state.isUnknown && displayList.isEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.details_help_categorize),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = { onSuggestAsFoss(appName, packageName) },
                                            modifier = Modifier.width(160.dp)
                                        ) {
                                            Text(stringResource(R.string.details_suggest_foss))
                                        }
                                        Button(
                                            onClick = { onSuggestAsProprietary(appName, packageName) },
                                            modifier = Modifier.width(160.dp)
                                        ) {
                                            Text(stringResource(R.string.details_suggest_proprietary))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlternativeListItem(
    modifier: Modifier = Modifier,
    alternative: Alternative,
    onClick: () -> Unit,
    onMatchVote: ((vote: Int) -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alternative.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = alternative.license,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (onMatchVote != null) {
                    // Match-vote buttons: "Is this a good replacement?"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val upvoted = alternative.userMatchVote == 1
                        val downvoted = alternative.userMatchVote == -1
                        FilledTonalIconButton(
                            onClick = { onMatchVote(1) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (upvoted) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (upvoted) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ThumbUp,
                                contentDescription = stringResource(R.string.vote_upvote),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = alternative.matchScore.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FilledTonalIconButton(
                            onClick = { onMatchVote(-1) },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (downvoted) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (downvoted) MaterialTheme.colorScheme.onError
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ThumbDown,
                                contentDescription = stringResource(R.string.vote_downvote),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    // FOSS sibling context: show star rating (read-only)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = alternative.displayRating,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (alternative.ratingCount > 0) {
                            Text(
                                text = " (${alternative.ratingCount})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (alternative.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = alternative.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PendingAlternativeCard(
    name: String,
    description: String,
    license: String,
    upvotes: Int,
    downvotes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (license.isNotBlank()) {
                        Text(
                            text = license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                AssistChip(
                    onClick = onClick,
                    label = { Text(stringResource(R.string.details_pending_chip)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = upvotes.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Default.ThumbDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = downvotes.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
