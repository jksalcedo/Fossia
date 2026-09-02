package com.jksalcedo.librefind.ui.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jksalcedo.librefind.R
import com.jksalcedo.librefind.ui.common.FullScreenLoading
import com.jksalcedo.librefind.ui.components.CommentSection
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlternativeDetailScreen(
    altId: String,
    onBackClick: () -> Unit,
    viewModel: AlternativeDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showVotingSection by remember { mutableStateOf(false) }
    var isScorecardExpanded by rememberSaveable { mutableStateOf(true) }
    var feedbackType by remember { mutableIntStateOf(0) }
    var feedbackText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val reportSuccessMsg = stringResource(R.string.alt_detail_report_success)
    val reportErrorMsg = stringResource(R.string.alt_detail_report_error)

    LaunchedEffect(state.reportSubmitted, state.reportError) {
        if (state.reportSubmitted) {
            snackbarHostState.showSnackbar(reportSuccessMsg)
            viewModel.clearReportState()
        }
        state.reportError?.let {
            snackbarHostState.showSnackbar(reportErrorMsg)
            viewModel.clearReportState()
        }
    }

    LaunchedEffect(altId) {
        viewModel.loadAlternative(altId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.alternative?.name ?: stringResource(R.string.alt_detail_title_default)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.alt_detail_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            state.alternative?.let { alt ->
                                if (alt.fdroidId.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.alt_detail_open_fdroid)) },
                                        onClick = {
                                            menuExpanded = false
                                            val fdroidUri =
                                                "market://details?id=${alt.fdroidId}".toUri()
                                            val webUri =
                                                "https://f-droid.org/packages/${alt.fdroidId}/".toUri()
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, fdroidUri)
                                                intent.setPackage("com.fdroid.fdroid")
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        webUri
                                                    )
                                                )
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Shop, null) }
                                    )
                                }

                                if (alt.fdroidId.isBlank() && alt.repoUrl.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.alt_detail_open_obtainium)) },
                                        onClick = {
                                            menuExpanded = false
                                            val obtainiumUri =
                                                "obtainium://add/${alt.repoUrl}".toUri()
                                            try {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        obtainiumUri
                                                    )
                                                )
                                            } catch (_: Exception) {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        "https://obtainium.imranr.dev".toUri()
                                                    )
                                                )
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Download, null) }
                                    )
                                }

                                if (alt.website.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.alt_detail_open_website)) },
                                        onClick = {
                                            menuExpanded = false
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    alt.website.toUri()
                                                )
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.Language, null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.alt_detail_view_source)) },
                                    onClick = {
                                        menuExpanded = false
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                alt.repoUrl.toUri()
                                            )
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Default.Code, null) }
                                )
                                HorizontalDivider()
                                val packageLabel = stringResource(R.string.package_label)
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.alt_detail_copy_package)) },
                                    onClick = {
                                        menuExpanded = false
                                        val clipboard =
                                            context.getSystemService(ClipboardManager::class.java)
                                        clipboard?.setPrimaryClip(
                                            ClipData.newPlainText(
                                                packageLabel,
                                                alt.packageName
                                            )
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.alt_detail_report)) },
                                    onClick = {
                                        menuExpanded = false
                                        showReportDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Flag, null) }
                                )
                            }
                        }
                    }
                }
            )
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

                state.alternative == null -> {
                    Text(
                        stringResource(R.string.alt_detail_not_found),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    val alt = state.alternative ?: return@Box
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Header
                        Text(
                            alt.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            alt.license,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Dimensional Rating Scorecard
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isScorecardExpanded = !isScorecardExpanded },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.alt_detail_ratings_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = if (isScorecardExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isScorecardExpanded) stringResource(
                                            R.string.alt_detail_collapse
                                        ) else stringResource(R.string.alt_detail_expand)
                                    )
                                }

                                AnimatedVisibility(visible = isScorecardExpanded) {
                                    Column {
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Privacy Rating
                                        DimensionalRatingRow(
                                            label = stringResource(R.string.alt_detail_privacy),
                                            description = stringResource(R.string.alt_detail_privacy_desc),
                                            rating = alt.privacyRating,
                                            displayRating = alt.displayPrivacyRating
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Usability Rating
                                        DimensionalRatingRow(
                                            label = stringResource(R.string.alt_detail_usability),
                                            description = stringResource(R.string.alt_detail_usability_desc),
                                            rating = alt.usabilityRating,
                                            displayRating = alt.displayUsabilityRating
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Feature Parity Rating
                                        DimensionalRatingRow(
                                            label = stringResource(R.string.alt_detail_feature_parity),
                                            description = stringResource(R.string.alt_detail_features_desc),
                                            rating = alt.featuresRating,
                                            displayRating = alt.displayFeaturesRating
                                        )
                                    }
                                }
                            }
                        }

                        // User Rating Section with Button
                        if (state.isSignedIn) {
                            Spacer(modifier = Modifier.height(16.dp))
                            if (!showVotingSection) {
                                Button(
                                    onClick = { showVotingSection = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.alt_detail_rate_app))
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                stringResource(R.string.alt_detail_your_ratings),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            IconButton(onClick = { showVotingSection = false }) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = stringResource(R.string.alt_detail_close),
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .rotate(45f)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Privacy
                                        UserRatingRow(
                                            label = stringResource(R.string.alt_detail_privacy),
                                            userRating = alt.userPrivacyRating,
                                            onRate = { stars ->
                                                viewModel.rateDimension(
                                                    com.jksalcedo.librefind.domain.model.VoteType.PRIVACY,
                                                    stars
                                                )
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Usability
                                        UserRatingRow(
                                            label = stringResource(R.string.alt_detail_usability),
                                            userRating = alt.userUsabilityRating,
                                            onRate = { stars ->
                                                viewModel.rateDimension(
                                                    com.jksalcedo.librefind.domain.model.VoteType.USABILITY,
                                                    stars
                                                )
                                            }
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Feature Parity
                                        UserRatingRow(
                                            label = stringResource(R.string.alt_detail_features),
                                            userRating = alt.userFeaturesRating,
                                            onRate = { stars ->
                                                viewModel.rateDimension(
                                                    com.jksalcedo.librefind.domain.model.VoteType.FEATURES,
                                                    stars
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Description
                        if (alt.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.alt_detail_description),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(alt.description, style = MaterialTheme.typography.bodyMedium)
                        }

                        // Features
                        if (alt.features.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.alt_detail_features_list),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            alt.features.forEach { feature ->
                                Text(
                                    "• $feature",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()

                        // Pros
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.alt_detail_pros),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            IconButton(onClick = {
                                feedbackType = 0; showFeedbackDialog = true
                            }) {
                                Icon(
                                    Icons.Default.Add,
                                    stringResource(R.string.alt_detail_add_pro_desc)
                                )
                            }
                        }
                        if (alt.pros.isEmpty()) {
                            Text(
                                stringResource(R.string.alt_detail_no_pros),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            alt.pros.forEach { pro ->
                                Text(
                                    "• $pro",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }

                        // Cons
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.alt_detail_cons),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            IconButton(onClick = {
                                feedbackType = 1; showFeedbackDialog = true
                            }) {
                                Icon(
                                    Icons.Default.Add,
                                    stringResource(R.string.alt_detail_add_con_desc)
                                )
                            }
                        }
                        if (alt.cons.isEmpty()) {
                            Text(
                                stringResource(R.string.alt_detail_no_cons),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            alt.cons.forEach { con ->
                                Text(
                                    "• $con",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }

                        // Comments Feed
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        CommentSection(targetId = altId)
                    }
                }
            }
        }
    }

    // Feedback dialog
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = {
                Text(
                    if (feedbackType == 0) stringResource(R.string.alt_detail_add_pro) else stringResource(
                        R.string.alt_detail_add_con
                    )
                )
            },
            text = {
                Column {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = feedbackType == 0,
                            onClick = { feedbackType = 0 },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)
                        ) { Text(stringResource(R.string.alt_detail_pro)) }
                        SegmentedButton(
                            selected = feedbackType == 1,
                            onClick = { feedbackType = 1 },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)
                        ) { Text(stringResource(R.string.alt_detail_con)) }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        label = { Text(stringResource(R.string.alt_detail_feedback_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.submitFeedback(
                            if (feedbackType == 0) "PRO" else "CON",
                            feedbackText
                        )
                        feedbackText = ""
                        showFeedbackDialog = false
                    },
                    enabled = feedbackText.isNotBlank()
                ) { Text(stringResource(R.string.alt_detail_submit)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFeedbackDialog = false
                }) { Text(stringResource(R.string.alt_detail_cancel)) }
            }
        )
    }

    // Report dialog
    if (showReportDialog) {
        state.alternative?.let { alt ->
            ReportDialog(
                packageName = alt.name,
                onDismiss = { showReportDialog = false },
                onSubmit = { issueType, description ->
                    viewModel.submitAppReport(issueType, description)
                    showReportDialog = false
                }
            )
        }
    }
}

@Preview
@Composable
fun AltDetailsPreview() {
    AlternativeDetailScreen(
        "",
        onBackClick = {}
    )
}

@Composable
private fun DimensionalRatingRow(
    label: String,
    description: String,
    rating: Float,
    displayRating: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(5) { index ->
                Icon(
                    Icons.Default.Star,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = if (index < rating.toInt()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                displayRating,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(30.dp)
            )
        }
    }
}

@Composable
private fun UserRatingRow(
    label: String,
    userRating: Int?,
    onRate: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium
        )
        Row {
            (1..5).forEach { star ->
                IconButton(
                    onClick = { onRate(star) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        stringResource(R.string.alt_detail_rate_star, star),
                        tint = if (userRating != null && star <= userRating) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReportDialog(
    packageName: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    val issueLabels = listOf(
        stringResource(R.string.alt_detail_report_not_foss),
        stringResource(R.string.alt_detail_report_unrelated),
        stringResource(R.string.alt_detail_report_broken_link),
        stringResource(R.string.alt_detail_report_malware),
        stringResource(R.string.alt_detail_report_other)
    )
    var selectedIssue by remember { mutableStateOf(issueLabels.first()) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alt_detail_report_title, packageName)) },
        text = {
            Column {
                Text(stringResource(R.string.alt_detail_report_prompt))
                Spacer(Modifier.height(8.dp))

                issueLabels.forEach { issue ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectedIssue = issue }
                    ) {
                        RadioButton(
                            selected = (selectedIssue == issue),
                            onClick = { selectedIssue = issue }
                        )
                        Text(text = issue)
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.alt_detail_report_details)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(selectedIssue, description) }) {
                Text(stringResource(R.string.alt_detail_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.alt_detail_cancel)) }
        }
    )
}