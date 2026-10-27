package com.poolaim.promax.ui.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.poolaim.promax.data.local.HistoryRepository
import com.poolaim.promax.data.settings.SettingsRepository
import com.poolaim.promax.data.vision.BallDetector
import com.poolaim.promax.data.vision.TableBoundaryDetector
import com.poolaim.promax.domain.model.BallModel
import com.poolaim.promax.domain.model.PlayerGroup
import com.poolaim.promax.domain.model.TableModel
import com.poolaim.promax.ui.balldetect.BallDetectScreen
import com.poolaim.promax.ui.balldetect.BallDetectViewModel
import com.poolaim.promax.ui.history.HistoryScreen
import com.poolaim.promax.ui.history.HistoryViewModel
import com.poolaim.promax.ui.imageimport.ImageImportPlaceholder
import com.poolaim.promax.ui.imageimport.ImageImportViewModel
import com.poolaim.promax.ui.playergroup.PlayerGroupScreen
import com.poolaim.promax.ui.playergroup.PlayerGroupViewModel
import com.poolaim.promax.ui.shotplan.ShotPlanScreen
import com.poolaim.promax.ui.shotplan.ShotPlanViewModel
import com.poolaim.promax.ui.tabledetect.TableDetectScreen
import com.poolaim.promax.ui.tabledetect.TableDetectViewModel
import kotlinx.coroutines.launch

/**
 * Navigation flow Phases A-F:
 * PlayerGroup -> ImageImport -> TableDetect -> BallDetect -> ShotPlan
 * History is reachable from the start screen after the group is chosen.
 */
@Composable
fun PoolAimNavHost(activity: Activity) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val settingsRepository = remember { SettingsRepository(context) }
    val tableDetector = remember { TableBoundaryDetector(context.applicationContext) }
    val ballDetector = remember { BallDetector(context.applicationContext) }
    val historyRepository = remember { HistoryRepository(context) }

    val playerGroupViewModel = remember { PlayerGroupViewModel(settingsRepository) }
    val imageImportViewModel = remember { ImageImportViewModel(context.applicationContext) }
    val tableDetectViewModel = remember { TableDetectViewModel(tableDetector) }
    val shotPlanViewModel = remember { ShotPlanViewModel() }
    val historyViewModel = remember { HistoryViewModel(historyRepository) }

    val savedGroup by playerGroupViewModel.selectedGroup.collectAsState()
    val scope = rememberCoroutineScope()

    // Hoisted detection results shared between destinations.
    var confirmedBalls by remember { mutableStateOf<List<BallModel>?>(null) }
    var confirmedTable by remember { mutableStateOf<TableModel?>(null) }
    var confirmedCue by remember { mutableStateOf<BallModel?>(null) }
    var confirmedPlayerBalls by remember { mutableStateOf<List<BallModel>?>(null) }
    var currentImageUri by remember { mutableStateOf<String?>(null) }

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
                    currentImageUri = uri.toString()
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
                    tableDetectViewModel.confirmTable(table)
                    confirmedTable = table
                    val encoded = java.net.URLEncoder.encode(uri, "UTF-8")
                    navController.navigate("ball_detect/$encoded")
                },
            )
        }

        composable("ball_detect/{uri}") { entry ->
            val uri = entry.arguments?.getString("uri")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: return@composable

            val table = confirmedTable ?: tableDetectViewModel.currentTable() ?: return@composable
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
                    val success = ballDetectViewModel.state.value as? BallDetectViewModel.UiState.Success
                    if (success != null) {
                        confirmedBalls = success.allBalls
                        confirmedPlayerBalls = success.playerBalls
                        confirmedCue = success.cueBall
                        shotPlanViewModel.compute(
                            cueBall = success.cueBall,
                            playerBalls = success.playerBalls,
                            allBalls = success.allBalls,
                            table = success.table,
                        )
                        navController.navigate("shot_plan/${java.net.URLEncoder.encode(uri, "UTF-8")}")
                    }
                },
                onRetry = {
                    ballDetectViewModel.detect(android.net.Uri.parse(uri), table)
                },
            )
        }

        composable("shot_plan/{uri}") { entry ->
            val uri = entry.arguments?.getString("uri")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: return@composable

            val table = confirmedTable ?: return@composable
            val balls = confirmedBalls ?: return@composable

            ShotPlanScreen(
                viewModel = shotPlanViewModel,
                imageUri = uri,
                table = table,
                allBalls = balls,
                onFinished = {
                    // Phase F: persist the completed analysis to history.
                    val plan = (shotPlanViewModel.state.value as? ShotPlanViewModel.UiState.Success)?.plan
                    val imageUriValue = currentImageUri ?: uri
                    scope.launch {
                        runCatching {
                            historyRepository.save(
                                imageUri = imageUriValue,
                                playerGroup = (savedGroup ?: PlayerGroup.SOLIDS).name,
                                balls = balls,
                                table = table,
                                bestShot = plan?.bestShot,
                            )
                        }
                    }
                    activity.finish()
                },
            )
        }

        composable("history") {
            HistoryScreen(
                viewModel = historyViewModel,
                onOpenSession = { /* Re-open flow wired in a later refinement */ },
            )
        }
    }
}
