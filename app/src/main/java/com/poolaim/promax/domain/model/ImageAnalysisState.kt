package com.poolaim.promax.domain.model

import android.net.Uri

/**
 * State of the imported table image at any point in time.
 */
sealed interface ImageAnalysisState {

    /** No image imported yet — waiting for user input. */
    data object Idle : ImageAnalysisState

    /** An image URI is present but has not been validated yet. */
    data class Pending(val uri: Uri) : ImageAnalysisState

    /** Image is valid and ready for table detection. */
    data class Ready(val uri: Uri) : ImageAnalysisState

    /** Validation or import failed; [reason] explains what went wrong. */
    data class Error(val uri: Uri?, val reason: Reason) : ImageAnalysisState

    enum class Reason {
        UNSUPPORTED_FORMAT,
        PERMISSION_DENIED,
        DECODE_FAILED,
    }
}
