package com.poolaim.promax.ui.imageimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poolaim.promax.domain.model.ImageAnalysisState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for image import: validates the picked/shared image URI
 * (with takeable persistable permission) and exposes import state.
 */
class ImageImportViewModel(
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<ImageAnalysisState>(ImageAnalysisState.Idle)
    val state: StateFlow<ImageAnalysisState> = _state

    fun onImageUriReceived(uri: Uri?) {
        if (uri == null) {
            _state.value = ImageAnalysisState.Error(null, ImageAnalysisState.Reason.UNSUPPORTED_FORMAT)
            return
        }
        _state.value = ImageAnalysisState.Pending(uri)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver
                        .takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } // no-op when the grant is not persistable (e.g. Photo Picker)

                val isValid = runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        val bounds = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
                        bounds.outWidth > 0 && bounds.outHeight > 0
                    } ?: false
                }.getOrDefault(false)

                _state.value = if (isValid) {
                    ImageAnalysisState.Ready(uri)
                } else {
                    ImageAnalysisState.Error(uri, ImageAnalysisState.Reason.DECODE_FAILED)
                }
            }
        }
    }

    fun reset() {
        _state.value = ImageAnalysisState.Idle
    }
}
