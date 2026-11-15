package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.model.ScreenMode
import com.example.game.render.GameGLSurfaceView
import com.example.ui.GameViewModel
import com.example.ui.Strings
import com.example.ui.components.GameHudOverlay
import com.example.ui.components.TouchControlsOverlay
import com.example.ui.screens.ArmoryOverlay
import com.example.ui.screens.CharacterCustomizeOverlay
import com.example.ui.screens.LoadingScreenOverlay
import com.example.ui.screens.MainMenuOverlay
import com.example.ui.screens.MatchOverOverlay
import com.example.ui.screens.MatchSetupOverlay
import com.example.ui.screens.MultiplayerLobbyOverlay
import com.example.ui.screens.PauseOverlay
import com.example.ui.screens.SettingsScreenOverlay
import com.example.ui.theme.MiniBattleArenaTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureImmersiveMode()

        setContent {
            MiniBattleArenaTheme {
                MiniBattleArenaApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.audioManager.resumeMusic()
    }

    override fun onPause() {
        super.onPause()
        viewModel.audioManager.pauseMusic()
    }

    private fun configureImmersiveMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun MiniBattleArenaApp(viewModel: GameViewModel) {
    val hud by viewModel.hudSnapshot.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val matchConfig by viewModel.matchConfig.collectAsStateWithLifecycle()
    val lanLobby by viewModel.lanLobbyState.collectAsStateWithLifecycle()
    val floatingTags by viewModel.floatingTags.collectAsStateWithLifecycle()
    val armoryPreviewWeapon by viewModel.armoryPreviewWeapon.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.loadingProgress.collectAsStateWithLifecycle()

    val strings = Strings.get(settings.language)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(hud.screenMode) {
        if (hud.screenMode == ScreenMode.PLAYING) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { viewModel.handleKeyEvent(it) }
    ) {
        // 1. Live 60 FPS 3D OpenGL ES Arena / Showcase Canvas
        GameGLSurfaceView(
            engine = viewModel.engine,
            armoryPreviewWeapon = armoryPreviewWeapon,
            onViewportChanged = viewModel::onViewportChanged,
            onFrameStepped = viewModel::onFrameStepped,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(hud.screenMode) {
                    if (hud.screenMode != ScreenMode.PLAYING) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val mouseChange = event.changes.firstOrNull { it.type == PointerType.Mouse }
                            if (mouseChange != null) {
                                val pos = mouseChange.position
                                when (event.type) {
                                    PointerEventType.Move -> viewModel.onMouseHoverOrDrag(pos.x, pos.y)
                                    PointerEventType.Press -> viewModel.onMouseButton(true, pos.x, pos.y)
                                    PointerEventType.Release -> viewModel.onMouseButton(false, pos.x, pos.y)
                                    else -> viewModel.onMouseHoverOrDrag(pos.x, pos.y)
                                }
                            }
                        }
                    }
                }
        )

        // 2. Active Gameplay HUD + Multi-Touch Controls
        if (hud.screenMode == ScreenMode.PLAYING) {
            GameHudOverlay(
                hud = hud,
                floatingTags = floatingTags,
                onPauseClick = viewModel::pauseGame,
                onSelectWeapon = viewModel::onSelectWeapon
            )
            TouchControlsOverlay(
                controlSensitivity = settings.controlSensitivity,
                grenadeCount = hud.localGrenades,
                isCrouching = hud.localIsCrouching,
                onMoveChange = viewModel::onTouchMove,
                onCrouchChange = viewModel::onTouchCrouch,
                onJetpackChange = viewModel::onTouchJetpack,
                onAimChange = viewModel::onTouchAim,
                onShootChange = viewModel::onTouchShootButton,
                onJump = viewModel::onJumpPressed,
                onGrenade = viewModel::onGrenadePressed,
                onSwitchWeapon = viewModel::onSwitchWeaponPressed,
                onReload = viewModel::onReloadPressed
            )
        }

        // 3. Screens & Overlays
        when (hud.screenMode) {
            ScreenMode.LOADING -> LoadingScreenOverlay(
                progress = loadingProgress,
                strings = strings
            )
            ScreenMode.MENU -> MainMenuOverlay(
                profile = profile,
                settings = settings,
                strings = strings,
                onPlayOffline = viewModel::openOfflineSetup,
                onPlayWithFriends = viewModel::openMultiplayerLobby,
                onCharacter = viewModel::openCharacterCustomize,
                onArmory = viewModel::openArmory,
                onSettings = { viewModel.openSettings(ScreenMode.MENU) },
                onToggleSfx = viewModel::toggleQuickSound,
                onToggleMusic = viewModel::toggleQuickMusic
            )
            ScreenMode.OFFLINE_SETUP -> MatchSetupOverlay(
                config = matchConfig,
                profile = profile,
                strings = strings,
                onUpdateConfig = viewModel::updateMatchConfig,
                onStartMatch = viewModel::startOfflineMatch,
                onBack = viewModel::exitToMainMenu
            )
            ScreenMode.MULTIPLAYER_LOBBY -> MultiplayerLobbyOverlay(
                lobbyState = lanLobby,
                matchConfig = matchConfig,
                strings = strings,
                onHostRoom = viewModel::hostLanGame,
                onJoinByCodeOrIp = viewModel::joinLanGame,
                onScanRooms = viewModel::startLanDiscovery,
                onUpdateHostConfig = viewModel::updateMatchConfig,
                onToggleTeam = viewModel::toggleLanTeam,
                onHostStartMatch = viewModel::hostStartLanMatch,
                onLeaveLobby = viewModel::leaveLanLobby
            )
            ScreenMode.CHARACTER_CUSTOMIZE -> CharacterCustomizeOverlay(
                profile = profile,
                strings = strings,
                onUpdateNickname = viewModel::updateNickname,
                onUpdateAppearance = viewModel::updateAppearance,
                onDragRotatePreview = viewModel::onPreviewDragYaw,
                onBack = viewModel::exitToMainMenu
            )
            ScreenMode.ARMORY -> ArmoryOverlay(
                profile = profile,
                strings = strings,
                onSelectCategory = { cat -> viewModel.setArmoryCategoryPreview(cat) },
                onPreviewWeapon = viewModel::previewWeaponInArmory,
                onBuyOrEquip = viewModel::buyOrEquipArmoryItem,
                onDragRotatePreview = viewModel::onPreviewDragYaw,
                onBack = viewModel::exitToMainMenu
            )
            ScreenMode.SETTINGS -> SettingsScreenOverlay(
                settings = settings,
                strings = strings,
                onUpdate = viewModel::updateSettings,
                onBack = viewModel::navigateBackFromSettings
            )
            ScreenMode.PAUSED -> PauseOverlay(
                strings = strings,
                isMultiplayer = matchConfig.isMultiplayer,
                onResume = viewModel::resumeGame,
                onRestart = { viewModel.startOfflineMatch() },
                onSettings = { viewModel.openSettings(ScreenMode.PAUSED) },
                onExitToMenu = viewModel::exitToMainMenu
            )
            ScreenMode.MATCH_OVER -> MatchOverOverlay(
                hud = hud,
                strings = strings,
                onPlayAgain = viewModel::playAgain,
                onMainMenu = viewModel::exitToMainMenu
            )
            ScreenMode.PLAYING -> Unit
        }
    }
}
