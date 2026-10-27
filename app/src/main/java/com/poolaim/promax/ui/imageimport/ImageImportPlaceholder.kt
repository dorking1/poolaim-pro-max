package com.poolaim.promax.ui.imageimport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.poolaim.promax.R
import com.poolaim.promax.domain.model.ImageAnalysisState

/**
 * Image import screen: system Photo Picker -> validation -> navigate on success.
 */
@Composable
fun ImageImportPlaceholder(
    viewModel: ImageImportViewModel,
    onImageReady: (Uri) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        viewModel.onImageUriReceived(uri)
    }

    // Navigate automatically once the image is validated.
    LaunchedEffect(state) {
        val s = state
        if (s is ImageAnalysisState.Ready) {
            onImageReady(s.uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.action_import_image),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(text = stringResource(R.string.image_import_hint))

        Button(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.image_import_pick_photo))
        }

        when (val s = state) {
            is ImageAnalysisState.Error -> {
                Text(
                    text = stringResource(
                        if (s.reason == ImageAnalysisState.Reason.PERMISSION_DENIED) {
                            R.string.image_import_error_permission
                        } else {
                            R.string.image_import_error_unsupported
                        },
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> Unit
        }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}
