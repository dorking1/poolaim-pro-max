package com.poolaim.promax.ui.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.poolaim.promax.data.settings.SettingsRepository
import com.poolaim.promax.data.vision.BallDetector
import com.poolaim.promax.data.vision.TableBoundaryDetector
import com.poolaim.promax.domain.model.PlayerGroup
import com.poolaim.promax.ui.balldetect.BallDetectScreen
import com.poolaim.promax.ui.balldetect.BallDetectViewModel
import com.poolaim.promax.ui.imageimport.ImageImportPlaceholder
import com.poolaim.promax.ui.imageimport.ImageImportViewModel
import com.poolaim.promax.ui.playergroup.PlayerGroupScreen
import com.poolaim.promax.ui.playergroup.PlayerGroupViewModel
import com.poolaim.promax.ui.tabledetect.TableDetectScreen
import com.poolaim.promax.ui.tabledetect.TableDetectViewModel

/**
 * Navigation flow Phases A–C:
 * PlayerGroup -> ImageImport -> TableDetect -> BallDetect -> (Phase D onwards)
 */
@Composable
fun PoolAimNavHost(activity: Activity) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val settingsRepository = remember { SettingsRepository(context) }
    val tableDetector = remember { TableBoundaryDetector(context.applicationContext) }
    val ballDetector = remember { BallDetector(context.applicationContext) }

    val playerGroupViewModel = remember { PlayerGroupViewModel(settingsRepository) }
    val imageImportViewModel = remember { ImageImportViewModel(context.applicationContext) }
    val tableDetectViewModel = remember { TableDetectViewModel(tableDetector) }

    // Latest confirmed table + player group are needed to build the
    // BallDetectViewModel when entering that destination.
    val savedGroup by playerGroupViewModel.selectedGroup.collectAsState()

    NavHost(navController = navController, startDestination = "player_group") {
        composable("player_group") {
            PlayerGroupScreen(
                viewModel = playerGroupViewModel,
                onConfirmed = { navController.navigate("image_import") },
            )
        }

        composable("image_import") {
            ImageImportPlaceholder(
                viewModel = imageImportViewModel,
                onImageReady = { uri ->
                    navController.navigate(
                        "table_detect/${java.net.URLEncoder.encode(uri.toString(), "UTF-8")}",
                    )
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable("table_detect/{uri}") { entry ->
            val uri = entry.arguments?.getString("uri")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: return@composable

            LaunchedEffect(uri) {
                tableDetectViewModel.analyze(android.net.Uri.parse(uri))
            }

            TableDetectScreen(
                viewModel = tableDetectViewModel,
                onConfirmed = { table ->
                    val encoded = java.net.URLEncoder.encode(uri, "UTF-8")
                    navController.navigate("ball_detect/$encoded")
                },
            )
        }

        composable("ball_detect/{uri}") { entry ->
            val uri = entry.arguments?.getString("uri")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: return@composable

            // Build the ViewModel with the confirmed table and group.
            val table = tableDetectViewModel.currentTable() ?: return@composable
            val group = savedGroup ?: PlayerGroup.SOLIDS

            val ballDetectViewModel = remember(uri) {
                BallDetectViewModel(ballDetector, group)
            }

            LaunchedEffect(uri) {
                ballDetectViewModel.detect(android.net.Uri.parse(uri), table)
            }

            BallDetectScreen(
                viewModel = ballDetectViewModel,
                onConfirmed = {
                    // Phase D (physics & shot calculation) is next.
                    activity.finish()
                },
                onRetry = {
                    ballDetectViewModel.detect(android.net.Uri.parse(uri), table)
                },
            )
        }
    }
}
