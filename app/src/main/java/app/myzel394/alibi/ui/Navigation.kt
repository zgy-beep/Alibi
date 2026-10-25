package app.myzel394.alibi.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.myzel394.alibi.dataStore
import app.myzel394.alibi.enums.RecorderState
import app.myzel394.alibi.ui.enums.Screen
import app.myzel394.alibi.ui.models.AudioRecorderModel
import app.myzel394.alibi.ui.models.PhotoCaptureModel
import app.myzel394.alibi.ui.models.VideoRecorderModel
import app.myzel394.alibi.services.PhotoCaptureStatus
import app.myzel394.alibi.ui.components.atoms.LocalAlibiRuntimeStatus
import app.myzel394.alibi.ui.components.atoms.RuntimeStatus
import app.myzel394.alibi.ui.screens.AboutScreen
import app.myzel394.alibi.ui.screens.CustomRecordingNotificationsScreen
import app.myzel394.alibi.ui.screens.BlackPhotoScreen
import app.myzel394.alibi.ui.screens.PhotoSetupScreen
import app.myzel394.alibi.ui.screens.PhotoCaptureSettingsScreen
import app.myzel394.alibi.ui.screens.RecorderScreen
import app.myzel394.alibi.ui.screens.RecentPhotosScreen
import app.myzel394.alibi.ui.screens.SettingsScreen
import app.myzel394.alibi.ui.screens.WelcomeScreen
import app.myzel394.alibi.ui.theme.AlibiMotion
import app.myzel394.alibi.ui.theme.LocalReduceMotion

const val DEBUG_SKIP_WELCOME = false;

@Composable
fun Navigation(
    audioRecorder: AudioRecorderModel = viewModel(),
    videoRecorder: VideoRecorderModel = viewModel(),
    photoCapture: PhotoCaptureModel = viewModel(),
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val reduceMotion = LocalReduceMotion.current
    val settings = context
        .dataStore
        .data
        .collectAsState(initial = null)
        .value ?: return

    DisposableEffect(Unit) {
        audioRecorder.bindToService(context)
        videoRecorder.bindToService(context)
        photoCapture.bindToService(context)

        onDispose {
            audioRecorder.unbindFromService(context)
            videoRecorder.unbindFromService(context)
            photoCapture.unbindFromService()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> photoCapture.setAppVisible(true)
                Lifecycle.Event.ON_STOP -> photoCapture.setAppVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val audioActive = audioRecorder.recorderState in listOf(
        RecorderState.STARTING,
        RecorderState.RECORDING,
        RecorderState.PAUSED,
    )
    val videoActive = videoRecorder.recorderState in listOf(
        RecorderState.STARTING,
        RecorderState.RECORDING,
        RecorderState.PAUSED,
    )
    val photoActive =
        (
            photoCapture.isInSession &&
                photoCapture.state.status !in listOf(
                    PhotoCaptureStatus.ERROR,
                    PhotoCaptureStatus.STOPPING,
                )
            )
    val runtimeStatus = when {
        audioRecorder.recorderState == RecorderState.PAUSED ||
            videoRecorder.recorderState == RecorderState.PAUSED ->
            RuntimeStatus.PAUSED
        audioRecorder.recorderState == RecorderState.STARTING ||
            videoRecorder.recorderState == RecorderState.STARTING ||
            (photoActive && photoCapture.state.status == PhotoCaptureStatus.STARTING) ->
            RuntimeStatus.STARTING
        audioActive || videoActive || photoActive -> RuntimeStatus.RUNNING
        else -> RuntimeStatus.IDLE
    }

    CompositionLocalProvider(LocalAlibiRuntimeStatus provides runtimeStatus) {
        NavHost(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background),
            navController = navController,
            startDestination = if (settings.hasSeenOnboarding || DEBUG_SKIP_WELCOME) {
                Screen.AudioRecorder.route
            } else {
                Screen.Welcome.route
            },
        ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onNavigateToAudioRecorderScreen = {
                    navController.navigate(Screen.AudioRecorder.route)
                },
            )
        }
        composable(
            Screen.AudioRecorder.route,
            enterTransition = {
                when (initialState.destination.route) {
                    Screen.Welcome.route -> null
                    else -> popEnter(reduceMotion)
                }
            },
            exitTransition = { pushExit(reduceMotion) },
            popEnterTransition = { popEnter(reduceMotion) },
            popExitTransition = { popExit(reduceMotion) },
        ) {
            RecorderScreen(
                onNavigateToSettingsScreen = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToPhotoCapture = {
                    navController.navigate(Screen.PhotoCapture.route)
                },
                audioRecorder = audioRecorder,
                videoRecorder = videoRecorder,
                photoCapture = photoCapture,
                settings = settings,
            )
        }
        composable(
            Screen.PhotoCapture.route,
            enterTransition = { pushEnter(reduceMotion) },
            exitTransition = { fadeExit(reduceMotion) },
            popEnterTransition = { popEnter(reduceMotion) },
            popExitTransition = { popExit(reduceMotion) },
        ) {
            PhotoSetupScreen(
                photoCapture = photoCapture,
                settings = settings,
                onBackNavigate = navController::popBackStack,
                onOpenBlackScreen = {
                    navController.navigate(Screen.BlackPhoto.route)
                },
                onOpenPhotoSettings = {
                    navController.navigate(Screen.PhotoCaptureSettings.route)
                },
                onOpenGallery = {
                    navController.navigate(Screen.RecentPhotos.route)
                },
            )
        }
        composable(
            Screen.PhotoCaptureSettings.route,
            enterTransition = { pushEnter(reduceMotion) },
            exitTransition = { fadeExit(reduceMotion) },
            popEnterTransition = { popEnter(reduceMotion) },
            popExitTransition = { popExit(reduceMotion) },
        ) {
            PhotoCaptureSettingsScreen(
                photoCapture = photoCapture,
                onBackNavigate = navController::popBackStack,
            )
        }
        composable(
            Screen.BlackPhoto.route,
            enterTransition = {
                fadeIn(AlibiMotion.page(reduceMotion)) +
                    scaleIn(
                        initialScale = 0.99f,
                        animationSpec = AlibiMotion.page(reduceMotion),
                    )
            },
            exitTransition = {
                fadeOut(AlibiMotion.state(reduceMotion)) +
                    scaleOut(
                        targetScale = 1.01f,
                        animationSpec = AlibiMotion.state(reduceMotion),
                    )
            },
            popEnterTransition = { fadeEnter(reduceMotion) },
            popExitTransition = {
                fadeOut(AlibiMotion.state(reduceMotion)) +
                    scaleOut(
                        targetScale = 1.01f,
                        animationSpec = AlibiMotion.state(reduceMotion),
                    )
            },
        ) {
            BlackPhotoScreen(
                photoCapture = photoCapture,
                settings = settings,
                onExit = {
                    navController.popBackStack(Screen.AudioRecorder.route, false)
                },
            )
        }
        composable(
            Screen.RecentPhotos.route,
            enterTransition = { pushEnter(reduceMotion) },
            exitTransition = { fadeExit(reduceMotion) },
            popEnterTransition = { popEnter(reduceMotion) },
            popExitTransition = { popExit(reduceMotion) },
        ) {
            RecentPhotosScreen(onBackNavigate = navController::popBackStack)
        }
        composable(
            Screen.Settings.route,
            enterTransition = { pushEnter(reduceMotion) },
            exitTransition = { fadeExit(reduceMotion) },
            popEnterTransition = { popEnter(reduceMotion) },
            popExitTransition = { popExit(reduceMotion) },
        ) {
            SettingsScreen(
                onBackNavigate = navController::popBackStack,
                onNavigateToCustomRecordingNotifications = {
                    navController.navigate(Screen.CustomRecordingNotifications.route)
                },
                onNavigateToAboutScreen = { navController.navigate(Screen.About.route) },
                onNavigateToPhotoCaptureSettings = {
                    navController.navigate(Screen.PhotoCaptureSettings.route)
                },
                audioRecorder = audioRecorder,
                videoRecorder = videoRecorder,
                photoCapture = photoCapture,
            )
        }
        composable(
            Screen.CustomRecordingNotifications.route,
            enterTransition = { pushEnter(reduceMotion) },
            exitTransition = { fadeExit(reduceMotion) },
            popEnterTransition = { popEnter(reduceMotion) },
            popExitTransition = { popExit(reduceMotion) },
        ) {
            CustomRecordingNotificationsScreen(
                onBackNavigate = navController::popBackStack
            )
        }
        composable(
            Screen.About.route,
            enterTransition = { pushEnter(reduceMotion) },
            exitTransition = { fadeExit(reduceMotion) },
            popEnterTransition = { popEnter(reduceMotion) },
            popExitTransition = { popExit(reduceMotion) },
        ) {
            AboutScreen(
                onBackNavigate = navController::popBackStack,
            )
        }
        }
    }
}

private fun pushEnter(reduceMotion: Boolean): EnterTransition =
    slideInHorizontally(
        animationSpec = AlibiMotion.page(reduceMotion),
        initialOffsetX = { it / 12 },
    ) + fadeIn(AlibiMotion.page(reduceMotion))

private fun fadeEnter(reduceMotion: Boolean): EnterTransition =
    fadeIn(AlibiMotion.page(reduceMotion))

private fun pushExit(reduceMotion: Boolean): ExitTransition =
    slideOutHorizontally(
        animationSpec = AlibiMotion.page(reduceMotion),
        targetOffsetX = { -it / 16 },
    ) + fadeOut(AlibiMotion.state(reduceMotion))

private fun fadeExit(reduceMotion: Boolean): ExitTransition =
    fadeOut(AlibiMotion.state(reduceMotion))

private fun popEnter(reduceMotion: Boolean): EnterTransition =
    slideInHorizontally(
        animationSpec = AlibiMotion.page(reduceMotion),
        initialOffsetX = { -it / 12 },
    ) + fadeIn(AlibiMotion.page(reduceMotion))

private fun popExit(reduceMotion: Boolean): ExitTransition =
    slideOutHorizontally(
        animationSpec = AlibiMotion.page(reduceMotion),
        targetOffsetX = { it / 16 },
    ) + fadeOut(AlibiMotion.state(reduceMotion))
