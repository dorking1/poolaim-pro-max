package com.poolaim.promax.ui.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.poolaim.promax.data.settings.SettingsRepository
import com.poolaim.promax.data.vision.TableBoundaryDetector
import com.poolaim.promax.domain.model.TableModel
import com.poolaim.promax.ui.imageimport.ImageImportViewModel
import com.poolaim.promax.ui.playergroup.PlayerGroupScreen
import com.poolaim.promax.ui.playergroup.PlayerGroupViewModel
import com.poolaim.promax.ui.tabledetect.TableDetectScreen
import com.poolaim.promax.ui.tabledetect.TableDetectViewModel

/**
 * Navigation flow for Phases A–B:
 * PlayerGroup -> ImageImport -> TableDetect -> (Phase C onwards)
 */
@Composable
fun PoolAimNavHost(activity: Activity) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val settingsRepository = remember { SettingsRepository(context) }
    val detector = remember { TableBoundaryDetector(context.applicationContext) }

    val playerGroupViewModel = remember { PlayerGroupViewModel(settingsRepository) }
    val imageImportViewModel = remember { ImageImportViewModel(context.applicationContext) }
    val tableDetectViewModel = remember { TableDetectViewModel(detector) }

    NavHost(navController = navController, startDestination = "player_group") {
        composable("player_group") {
            PlayerGroupScreen(
                viewModel = playerGroupViewModel,
                onConfirmed = { navController.navigate("image_import") },
            )
        }

        composable("image_import") {
            ImageImportRoute(
                viewModel = imageImportViewModel,
                onImageReady = { navController.navigate("table_detect/${java.net.URLEncoder.encode(it.toString(), "UTF-8")}") },
                onBack = { navController.popBackStack() },
            )
        }

        composable("table_detect/{uri}") { entry ->
            val uri = entry.arguments?.getString("uri")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: return@composable

            LaunchedEffectUri(
                uri = uri,
                tableDetectViewModel = tableDetectViewModel,
            )

            TableDetectScreen(
                viewModel = tableDetectViewModel,
                onConfirmed = { table: TableModel ->
                    // Phase C (ball detection) is wired in the next phase.
                    activity.finish()
                },
            )
        }
    }
}

/** Triggers automatic detection once for the given image URI. */
@Composable
private fun LaunchedEffectUri(
    uri: String,
    tableDetectViewModel: TableDetectViewModel,
) {
    androidx.compose.runtime.LaunchedEffect(uri) {
        tableDetectViewModel.analyze(android.net.Uri.parse(uri))
    }
}

/**
 * Placeholder route wrapper for image import (UI for photo picking is
 * wired with Activity Result APIs in the interactive session).
 */
@Composable
private fun ImageImportRoute(
    viewModel: ImageImportViewModel,
    onImageReady: (android.net.Uri) -> Unit,
    onBack: () -> Unit,
) {
    // Photo Picker integration is wired in the interactive session;
    // validation and state pipeline are already implemented in the ViewModel.
    com.poolaim.promax.ui.imageimport.ImageImportPlaceholder(
        viewModel = viewModel,
        onImageReady = onImageReady,
        onBack = onBack,
    )
}
