package com.alyaqdhan.mybattery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Routes error state to the right compact overlay dialog.
 * Place this inside whatever fragment/screen is currently on-screen.
 */
@Composable
fun ErrorDialogHost(errorDialog: ErrorDialog?, onDismiss: () -> Unit) {
    when (errorDialog) {
        is ErrorDialog.WrongFolder  -> WrongFolderDialog(errorDialog.errorMessage, onDismiss)
        is ErrorDialog.FolderDeleted -> FolderDeletedDialog(onDismiss)
        is ErrorDialog.PermissionLost -> PermissionLostDialog(onDismiss)
        null -> Unit
    }
}

// ── Compact overlay: Wrong folder ─────────────────────────────────────────────

@Composable
fun WrongFolderDialog(errorMessage: String, onDismiss: () -> Unit) {
    val isNotFound  = errorMessage == "NO_LOG_FOUND"
    val accentColor = if (isNotFound) MaterialTheme.colorScheme.tertiary
                      else MaterialTheme.colorScheme.error

    ErrorOverlayDialog(onDismiss = onDismiss) {
        ErrorIconRow(icon = painterResource(R.drawable.warning), tint = accentColor)
        Spacer(Modifier.height(16.dp))
        Text(
            if (isNotFound) "Wrong Folder Selected" else "Log File Not Readable",
            color     = textPrimary(),
            textAlign = TextAlign.Center,
            style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (isNotFound)
                "No dumpState_*.log file was found.\n\nSelect the correct log folder after running dumpstate from *#9900#."
            else
                "The log file was found but could not be read.\n\nGenerate a fresh log and select the folder again.",
            color     = textSecondary(),
            textAlign = TextAlign.Center,
            style     = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
        )
        Spacer(Modifier.height(20.dp))
        ErrorDismissButton(onClick = onDismiss)
    }
}

// ── Compact overlay: Folder deleted ──────────────────────────────────────────

@Composable
fun FolderDeletedDialog(onDismiss: () -> Unit) {
    ErrorOverlayDialog(onDismiss = onDismiss) {
        ErrorIconRow(icon = painterResource(R.drawable.delete), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(
            "Folder Not Found",
            color     = textPrimary(),
            textAlign = TextAlign.Center,
            style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "The log folder has been deleted or is no longer accessible.\n\nYou'll need to generate a new log file.",
            color     = textSecondary(),
            textAlign = TextAlign.Center,
            style     = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
        )
        Spacer(Modifier.height(20.dp))
        ErrorDismissButton(onClick = onDismiss)
    }
}

// ── Compact overlay: Permission lost ─────────────────────────────────────────

@Composable
fun PermissionLostDialog(onDismiss: () -> Unit) {
    ErrorOverlayDialog(onDismiss = onDismiss) {
        ErrorIconRow(icon = painterResource(R.drawable.warning), tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(16.dp))
        Text(
            "Permission Removed",
            color     = textPrimary(),
            textAlign = TextAlign.Center,
            style     = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Folder permission was revoked in Android settings.\n\nGrant permission again to refresh your battery info.",
            color     = textSecondary(),
            textAlign = TextAlign.Center,
            style     = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
        )
        Spacer(Modifier.height(20.dp))
        ErrorDismissButton(onClick = onDismiss)
    }
}

// ── Shared dialog shell ───────────────────────────────────────────────────────

@Composable
private fun ErrorOverlayDialog(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier  = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape     = RoundedCornerShape(20.dp),
            color     = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier            = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content             = content
            )
        }
    }
}

@Composable
private fun ErrorIconRow(icon: androidx.compose.ui.graphics.painter.Painter, tint: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .size(64.dp)
            .background(tint.copy(alpha = 0.1f), CircleShape)
            .border(1.5.dp, tint.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun ErrorDismissButton(onClick: () -> Unit) {
    Button(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().height(48.dp),
        shape     = RoundedCornerShape(12.dp),
        colors    = ButtonDefaults.buttonColors(
            containerColor = accentGreenEffective(),
            contentColor   = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(painterResource(R.drawable.check_circle), null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Got it", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
    }
}
