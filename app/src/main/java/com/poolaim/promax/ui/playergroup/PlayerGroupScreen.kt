package com.poolaim.promax.ui.playergroup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.poolaim.promax.R
import com.poolaim.promax.domain.model.PlayerGroup

/**
 * Player group selection screen: solids, stripes, 8-ball, or custom.
 */
@Composable
fun PlayerGroupScreen(
    viewModel: PlayerGroupViewModel,
    onConfirmed: () -> Unit,
) {
    val selected by viewModel.selectedGroup.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.player_group_title),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.player_group_subtitle),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )

        val options = listOf(
            PlayerGroup.SOLIDS to R.string.player_group_solid,
            PlayerGroup.STRIPES to R.string.player_group_stripes,
            PlayerGroup.EIGHT_BALL to R.string.player_group_eight_ball,
            PlayerGroup.CUSTOM to R.string.player_group_custom,
        )

        options.forEach { (group, labelRes) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == group,
                        onClick = { viewModel.select(group) },
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == group) {
                        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == group,
                        onClick = { viewModel.select(group) },
                    )
                    Text(
                        text = stringResource(labelRes),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        Button(
            onClick = {
                viewModel.confirmAndSave()
                onConfirmed()
            },
            enabled = selected != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.player_group_confirm))
        }
    }
}
