package com.jksalcedo.librefind.ui.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jksalcedo.librefind.R
import com.jksalcedo.librefind.domain.model.Submission
import com.jksalcedo.librefind.domain.model.SubmissionStatus
import com.jksalcedo.librefind.domain.model.SubmissionType
import com.jksalcedo.librefind.domain.model.SubmittedApp

@Composable
fun CommunitySubmissionItem(
    submission: Submission,
    onClick: () -> Unit,
    onUserClick: () -> Unit = {},
    onUpvote: () -> Unit,
    onDownvote: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val title =
                        "Linking ${submission.proprietaryPackages} to ${submission.linkedAlternatives.size} alternative(s)"
                    val mono = submission.proprietaryPackages
                    Text(
                        text = if (submission.type == SubmissionType.LINKING) {
                            buildAnnotatedString {
                                append(title)
                                // Find where the word starts
                                val startIndex = title.indexOf(mono)

                                if (startIndex >= 0) {
                                    val endIndex = startIndex + mono.length

                                    // Apply the monospace style just to those indices (package name)
                                    addStyle(
                                        style = SpanStyle(fontFamily = FontFamily.Monospace),
                                        start = startIndex,
                                        end = endIndex
                                    )
                                }
                            }
                        } else {
                            AnnotatedString(submission.submittedApp.name)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { onUserClick() }
                    ) {
                        Text(
                            text = submission.submitterUsername,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        if (!submission.submitterBadge.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = submission.submitterBadge,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8f,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Text(
                            text = "(${submission.submitterReputation})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (submission.status == SubmissionStatus.REJECTED && !submission.rejectionReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = stringResource(R.string.my_submissions_rejection_reason),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = submission.rejectionReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = submission.type.name.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onUpvote, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (submission.userVote == 1) Icons.Filled.ThumbUp
                            else Icons.Outlined.ThumbUp,
                            contentDescription = stringResource(R.string.submission_upvote),
                            tint = if (submission.userVote == 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "${submission.upvotes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onDownvote, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (submission.userVote == -1) Icons.Filled.ThumbDown
                            else Icons.Outlined.ThumbDown,
                            contentDescription = stringResource(R.string.submission_downvote),
                            tint = if (submission.userVote == -1) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "${submission.downvotes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CommunitySubmissionItemPreview() {
    MaterialTheme {
        CommunitySubmissionItem(
            submission = Submission(
                id = "1",
                type = SubmissionType.NEW_ALTERNATIVE,
                proprietaryPackages = "com.spotify.music",
                submittedApp = SubmittedApp(
                    name = "Vimusic",
                    packageName = "it.vfsfitvnm.vimusic",
                    repoUrl = "https://github.com/vfsfitvnm/ViMusic",
                    fdroidId = "it.vfsfitvnm.vimusic",
                    description = "A Jetpack Compose based open source Apple Music / Spotify alternative.",
                    license = "GPL-3.0"
                ),
                submitterUid = "1234567809",
                submitterUsername = "john doe",
                submitterReputation = 42,
                submitterBadge = "Scout",
                status = SubmissionStatus.PENDING,
                upvotes = 10,
                downvotes = 2
            ),
            onClick = {},
            onUserClick = {},
            onUpvote = {},
            onDownvote = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CommunitySubmissionItemLinkingPreview() {
    MaterialTheme {
        CommunitySubmissionItem(
            submission = Submission(
                id = "2",
                type = SubmissionType.LINKING,
                proprietaryPackages = "com.microsoft.teams",
                submittedApp = SubmittedApp(
                    name = "Linking 2 alternatives",
                    packageName = "im.vector.app",
                    repoUrl = "https://github.com/element-hq/element-android",
                    fdroidId = "im.vector.app",
                    description = "A secure communication app for Matrix.",
                    license = "Apache-2.0"
                ),
                linkedAlternatives = listOf("im.vector.app", "org.thoughtcrime.securesms"),
                submitterUid = "1234567890",
                submitterUsername = "jane doe",
                submitterReputation = 1500,
                submitterBadge = "Vanguard",
                status = SubmissionStatus.APPROVED,
                upvotes = 50,
                downvotes = 1
            ),
            onClick = {},
            onUserClick = {},
            onUpvote = {},
            onDownvote = {}
        )
    }
}
