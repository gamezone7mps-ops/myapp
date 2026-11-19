package com.example.features.common

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun ModuleFoundationScreen(
  moduleTitle: String,
  phaseBadge: String,
  description: String,
  recordCount: Int?,
  icon: ImageVector,
  onOpenDashboard: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  testTagPrefix: String
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(16.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 560.dp)
        .testTag("${testTagPrefix}_foundation_card"),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer
          ) {
            Icon(
              imageVector = icon,
              contentDescription = stringResource(R.string.cd_module_icon, moduleTitle),
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier
                .padding(12.dp)
                .size(28.dp)
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.module_foundation_title, moduleTitle),
              style = MaterialTheme.typography.titleLarge
            )
            Text(
              text = phaseBadge,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (recordCount != null) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small
          ) {
            Text(
              text = stringResource(R.string.module_foundation_db_count, recordCount),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = onOpenDashboard,
            modifier = Modifier
              .weight(1f)
              .testTag("${testTagPrefix}_back_dashboard_button")
          ) {
            Text(stringResource(R.string.action_back_to_dashboard))
          }
          Button(
            onClick = onOpenSettings,
            modifier = Modifier
              .weight(1f)
              .testTag("${testTagPrefix}_open_settings_button")
          ) {
            Text(stringResource(R.string.action_open_settings))
          }
        }
      }
    }
  }
}
