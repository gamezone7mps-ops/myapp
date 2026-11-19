package com.example.features.hub

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.features.navigation.LifeOsDestination

@Composable
fun WorkspaceHubScreen(
  onNavigate: (LifeOsDestination) -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 680.dp)
        .padding(horizontal = 16.dp)
        .testTag("hub_list"),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item { Spacer(modifier = Modifier.height(4.dp)) }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = stringResource(R.string.hub_title),
            style = MaterialTheme.typography.headlineMedium
          )
          Text(
            text = stringResource(R.string.hub_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      item {
        Text(
          text = stringResource(R.string.hub_section_secondary),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 6.dp)
        )
      }

      item {
        HubItemCard(
          title = stringResource(R.string.nav_documents),
          description = stringResource(R.string.desc_documents),
          phaseBadge = null,
          icon = Icons.Outlined.Description,
          onClick = { onNavigate(LifeOsDestination.DOCUMENTS) },
          testTag = "hub_item_documents"
        )
      }

      item {
        HubItemCard(
          title = stringResource(R.string.nav_insights),
          description = stringResource(R.string.desc_insights),
          phaseBadge = null,
          icon = Icons.Outlined.Insights,
          onClick = { onNavigate(LifeOsDestination.INSIGHTS) },
          testTag = "hub_item_insights"
        )
      }

      item {
        HubItemCard(
          title = stringResource(R.string.nav_assistant),
          description = stringResource(R.string.desc_assistant),
          phaseBadge = null,
          icon = Icons.Outlined.AutoAwesome,
          onClick = { onNavigate(LifeOsDestination.ASSISTANT) },
          testTag = "hub_item_assistant"
        )
      }

      item {
        Text(
          text = stringResource(R.string.hub_section_system),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 6.dp)
        )
      }

      item {
        HubItemCard(
          title = stringResource(R.string.nav_settings),
          description = stringResource(R.string.desc_settings),
          phaseBadge = null,
          icon = Icons.Outlined.Tune,
          onClick = { onNavigate(LifeOsDestination.SETTINGS) },
          testTag = "hub_item_settings"
        )
      }

      item { Spacer(modifier = Modifier.height(20.dp)) }
    }
  }
}

@Composable
private fun HubItemCard(
  title: String,
  description: String,
  phaseBadge: String?,
  icon: ImageVector,
  onClick: () -> Unit,
  testTag: String
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .clickable(onClick = onClick)
      .testTag(testTag),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = stringResource(R.string.cd_module_icon, title),
        tint = MaterialTheme.colorScheme.primary
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(text = title, style = MaterialTheme.typography.titleMedium)
          if (phaseBadge != null) {
            AssistChip(
              onClick = onClick,
              label = { Text(text = phaseBadge, style = MaterialTheme.typography.labelSmall) }
            )
          }
        }
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
