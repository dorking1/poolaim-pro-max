package com.poolaim.promax.ui.playergroup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.poolaim.promax.R
import com.poolaim.promax.capture.ScreenCaptureService
import com.poolaim.promax.domain.model.PlayerGroup
import com.poolaim.promax.overlay.FloatingAimService

/**
 * Start screen: select a ball group, enable the optional overlay, or consent to screen capture.
 */
@Composable
fun PlayerGroupScreen(
    viewModel: PlayerGroupViewModel,
    onConfirmed: () -> Unit,
) {
    val selected by viewModel.selectedGroup.collectAsState()
    val context = LocalContext.current

    fun startOverlayIfPermitted() {
        if (Settings.canDrawOverlays(context)) {
            runCatching { context.startService(Intent(context, FloatingAimService::class.java)) }
        }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { startOverlayIfPermitted() }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            val captureIntent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(context, captureIntent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.player_group_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.player_group_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Floating controls", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Enable a movable PoolAim bubble. Android will ask you to allow display over other apps.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = {
                        if (Settings.canDrawOverlays(context)) {
                            startOverlayIfPermitted()
                        } else {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}"),
                            )
                            overlaySettingsLauncher.launch(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enable floating bubble")
                }

                Text("Live table analysis", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Analyze the supplied 8-ball game's screen in real time. Android must show its screen-capture consent prompt. Detected circular candidates and estimated color families will be outlined locally; calibration and numbered-ball recognition are still experimental.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Allow and start live analysis")
                }
            }
        }

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
                    .selectable(selected = selected == group, onClick = { viewModel.select(group) }),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == group) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == group, onClick = { viewModel.select(group) })
                    Text(text = stringResource(labelRes), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        Button(
            onClick = {
                viewModel.confirmAndSave()
                onConfirmed()
            },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.player_group_confirm))
        }
    }
}
