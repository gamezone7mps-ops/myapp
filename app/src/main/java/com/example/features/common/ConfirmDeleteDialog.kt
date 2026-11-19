package com.example.features.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R

@Composable
fun ConfirmDeleteDialog(
  itemTitle: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.confirm_delete_title)) },
    text = { Text(text = stringResource(R.string.confirm_delete_body, itemTitle)) },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        modifier = Modifier.testTag("confirm_delete_button")
      ) {
        Text(
          text = stringResource(R.string.action_delete),
          color = MaterialTheme.colorScheme.error
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("cancel_delete_button")
      ) {
        Text(text = stringResource(R.string.action_cancel))
      }
    }
  )
}
