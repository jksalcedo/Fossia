package com.jksalcedo.librefind.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.jksalcedo.librefind.domain.model.ReportStatus
import com.jksalcedo.librefind.domain.model.ReportStatus.CLOSED
import com.jksalcedo.librefind.domain.model.ReportStatus.DUPLICATE
import com.jksalcedo.librefind.domain.model.ReportStatus.IN_PROGRESS
import com.jksalcedo.librefind.domain.model.ReportStatus.OPEN
import com.jksalcedo.librefind.domain.model.ReportStatus.RESOLVED
import com.jksalcedo.librefind.domain.model.ReportStatus.WONTFIX
import com.jksalcedo.librefind.domain.model.SubmissionStatus

@Composable
fun StatusLabel(status: SubmissionStatus) {
    val color = when (status) {
        SubmissionStatus.APPROVED -> MaterialTheme.colorScheme.tertiary
        SubmissionStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        SubmissionStatus.REJECTED -> MaterialTheme.colorScheme.error
    }

    Text(
        text = status.name.lowercase().replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = color
    )
}

@Composable
fun StatusLabel(status: ReportStatus) {
    val color = when (status) {
        OPEN -> MaterialTheme.colorScheme.tertiary
        IN_PROGRESS -> Color.Blue
        RESOLVED -> Color.Green
        WONTFIX -> Color.Gray
        DUPLICATE -> Color.Yellow
        CLOSED -> Color.Red
    }

    Text(
        text = status.name.lowercase().replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = color
    )
}