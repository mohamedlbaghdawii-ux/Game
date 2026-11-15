package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game.audio.AudioManager
import com.example.game.data.ArmoryCatalog
import com.example.game.data.ArmoryCategory
import com.example.game.data.ArmoryItem
import com.example.game.data.SaveSystem
import com.example.game.engine.GameEngine
import com.example.game.model.CharacterAppearance
import com.example.game.model.GameSettings
import com.example.game.model.HudSnapshot
import com.example.game.model.MatchConfig
import com.example.game.model.PlayerId
import com.example.game.model.PlayerProfile
import com.example.game.model.ScreenMode
import com.example.game.model.WeaponType
import com.example.game.network.LanLobbyState
import com.example.game.network.LanRole
import com.example.game.network.LocalMultiplayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2

data class FloatingHealthTag(
    val id: PlayerId,
    val callsign: String,
    val screenX: Float,
    val screenY: Float,
    val healthFraction: Float,
    val isLocal: Boolean,
    val teamIndex: Int
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val saveSystem = SaveSystem(application.applicationContext)
    val audioManager = AudioManager(application.applicationContext)
    val multiplayerManager = LocalMultiplayerManager(application.applicationContext)
    val engine = GameEngine(audioManager, multiplayerManager)

    private val _hudSnapshot = MutableStateFlow(engine.latestHudSnapshot)
    val hudSnapshot: StateFlow<HudSnapshot> = _hudSnapshot.asStateFlow()

    private val _profile = MutableStateFlow(PlayerProfile())
    val profile: StateFlow<PlayerProfile> = _profile.asStateFlow()

    private val _settings = MutableStateFlow(GameSettings())
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    private val _matchConfig = MutableStateFlow(MatchConfig())
    val matchConfig: StateFlow<MatchConfig> = _matchConfig.asStateFlow()

    val lanLobbyState: StateFlow<LanLobbyState> = multiplayerManager.lobbyState

    private val _floatingTags = MutableStateFlow<List<FloatingHealthTag>>(emptyList())
    val floatingTags: StateFlow<List<FloatingHealthTag>> = _floatingTags.asStateFlow()

    private val _armoryPreviewWeapon = MutableStateFlow<WeaponType?>(null)
    val armoryPreviewWeapon: StateFlow<WeaponType?> = _armoryPreviewWeapon.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private var viewportW = 1280
    private var viewportH = 720

    // Combined Touch + Keyboard/Mouse input state
    private var touchMoveX = 0f
    private var touchCrouch = false
    private var touchJetpack = false
    private var touchAimAngle = 0f
    private var touchHasAim = false
    private var touchAimAutoFire = false
    private var touchShootPressed = false

    private var keyLeft = false
    private var keyRight = false
    private var keyCrouch = false
    private var keyJetpack = false
    private var mouseAimAngle = 0f
    private var mouseHasAim = false
    private var mousePressed = false
    private var lastMouseX = 0f
    private var lastMouseY = 0f

    private var returnFromSettings: ScreenMode = ScreenMode.MENU

    init {
        setupEngineCallbacks()
        setupLanMatchSignal()
        initializeAndLoadSavedState()
    }

    private fun setupEngineCallbacks() {
        val appContext = getApplication<Application>().applicationContext
        val vibrator: Vibrator? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                mgr?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()

        engine.onVibrate = { durationMs ->
            if (vibrator != null && vibrator.hasVibrator()) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                durationMs.coerceIn(10L, 120L),
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(durationMs.coerceIn(10L, 120L))
                    }
                }
            }
        }

        engine.onMatchFinishedCallback = { outcome, kills, deaths ->
            viewModelScope.launch {
                val updated = engine.matchManager.applyOutcomeToProfile(
                    profile = _profile.value,
                    outcome = outcome,
                    localKills = kills,
                    localDeaths = deaths
                )
                _profile.value = updated
                engine.updateProfile(updated)
                saveSystem.saveProfile(updated)
            }
        }
    }

    private fun setupLanMatchSignal() {
        multiplayerManager.onMatchStartSignal = { cfg, peers ->
            viewModelScope.launch {
                resetInputs()
                _matchConfig.value = cfg
                engine.startLanMatch(cfg, peers, multiplayerManager.localPlayerId)
                _hudSnapshot.value = engine.latestHudSnapshot
            }
        }
    }

    private fun initializeAndLoadSavedState() {
        viewModelScope.launch {
            engine.setScreen(ScreenMode.LOADING)
            _hudSnapshot.value = engine.latestHudSnapshot

            val loadedProfile = saveSystem.loadProfile()
            val loadedSettings = saveSystem.loadSettings()

            _profile.value = loadedProfile
            _settings.value = loadedSettings
            _matchConfig.value = _matchConfig.value.copy(startingWeapon = loadedProfile.equippedWeapon)

            engine.updateProfile(loadedProfile)
            engine.updateSettings(loadedSettings)

            // First launch displays full cinematic loading screen; subsequent launches warm up quickly
            val totalDurationMs = if (loadedProfile.hasCompletedFirstLaunch) 450L else 2100L
            val steps = if (loadedProfile.hasCompletedFirstLaunch) 15 else 50
            val stepDelay = totalDurationMs / steps
            for (i in 1..steps) {
                _loadingProgress.value = i / steps.toFloat()
                delay(stepDelay)
            }

            if (!loadedProfile.hasCompletedFirstLaunch) {
                val marked = loadedProfile.copy(hasCompletedFirstLaunch = true)
                _profile.value = marked
                engine.updateProfile(marked)
                saveSystem.saveProfile(marked)
            }

            engine.setScreen(ScreenMode.MENU)
            _hudSnapshot.value = engine.latestHudSnapshot
        }
    }

    fun onViewportChanged(width: Int, height: Int) {
        viewportW = width
        viewportH = height
    }

    fun onFrameStepped() {
        if (mouseHasAim) {
            recomputeMouseAimAngle()
            pushCombinedInput()
        }
        val snap = engine.latestHudSnapshot
        _hudSnapshot.value = snap

        if (snap.screenMode == ScreenMode.PLAYING || snap.screenMode == ScreenMode.PAUSED) {
            val tags = ArrayList<FloatingHealthTag>(snap.players.size)
            for (p in engine.players.values) {
                if (!p.isAlive) continue
                val (sx, sy) = engine.cameraController.worldToScreen(
                    p.x, p.y + p.effectiveHeight + 0.48f,
                    viewportW.toFloat(), viewportH.toFloat()
                )
                if (sx in -140f..(viewportW + 140f) && sy in -90f..(viewportH + 90f)) {
                    tags.add(
                        FloatingHealthTag(
                            id = p.id,
                            callsign = p.callsign,
                            screenX = sx,
                            screenY = sy,
                            healthFraction = (p.health / p.maxHealth).coerceIn(0f, 1f),
                            isLocal = p.id == engine.localPlayerId,
                            teamIndex = p.teamIndex
                        )
                    )
                }
            }
            _floatingTags.value = tags
        } else {
            _floatingTags.value = emptyList()
        }
    }

    // === Menu & Navigation Actions ===
    fun openOfflineSetup() {
        _armoryPreviewWeapon.value = null
        engine.setScreen(ScreenMode.OFFLINE_SETUP)
        _hudSnapshot.value = engine.latestHudSnapshot
    }

    fun openMultiplayerLobby() {
        _armoryPreviewWeapon.value = null
        engine.setScreen(ScreenMode.MULTIPLAYER_LOBBY)
        _hudSnapshot.value = engine.latestHudSnapshot
        multiplayerManager.startDiscovery()
    }

    fun openCharacterCustomize() {
        _armoryPreviewWeapon.value = null
        engine.cameraController.previewYawOffsetDeg = 18f
        engine.setScreen(ScreenMode.CHARACTER_CUSTOMIZE)
        _hudSnapshot.value = engine.latestHudSnapshot
    }

    fun openArmory() {
        _armoryPreviewWeapon.value = null
        engine.cameraController.previewYawOffsetDeg = 18f
        engine.setScreen(ScreenMode.ARMORY)
        _hudSnapshot.value = engine.latestHudSnapshot
    }

    fun openSettings(fromMode: ScreenMode) {
        returnFromSettings = fromMode
        _armoryPreviewWeapon.value = null
        engine.setScreen(ScreenMode.SETTINGS)
        _hudSnapshot.value = engine.latestHudSnapshot
    }

    fun navigateBackFromSettings() {
        engine.setScreen(returnFromSettings)
        _hudSnapshot.value = engine.latestHudSnapshot
    }

    fun exitToMainMenu() {
        resetInputs()
        _armoryPreviewWeapon.value = null
        if (multiplayerManager.lobbyState.value.role != LanRole.NONE) {
            multiplayerManager.stopAll()
        }
        engine.setScreen(ScreenMode.MENU)
        _hudSnapshot.value = engine.latestHudSnapshot
    }

    fun onPreviewDragYaw(deltaX: Float) {
        engine.cameraController.previewYawOffsetDeg =
            (engine.cameraController.previewYawOffsetDeg + deltaX * 0.5f) % 360f
    }

    fun setArmoryCategoryPreview(category: ArmoryCategory, previewWeapon: WeaponType? = null) {
        _armoryPreviewWeapon.value = if (category == ArmoryCategory.WEAPONS) {
            previewWeapon ?: _profile.value.equippedWeapon
        } else null
    }

    fun previewWeaponInArmory(weapon: WeaponType) {
        _armoryPreviewWeapon.value = weapon
    }

    // === Offline Match Config & Launch ===
    fun updateMatchConfig(newConfig: MatchConfig) {
        _matchConfig.value = newConfig
        if (multiplayerManager.lobbyState.value.role == LanRole.HOST) {
            multiplayerManager.updateHostConfig(newConfig)
        }
    }

    fun startOfflineMatch(config: MatchConfig = _matchConfig.value) {
        resetInputs()
        _armoryPreviewWeapon.value = null
        _matchConfig.value = config.copy(isMultiplayer = false)
        engine.startOfflineMatch(_matchConfig.value)
        _hudSnapshot.value = engine.latestHudSnapshot
    }

    fun playAgain() {
        val current = _matchConfig.value
        if (current.isMultiplayer) {
            val lobby = multiplayerManager.lobbyState.value
            if (lobby.role == LanRole.HOST) {
                multiplayerManager.hostStartMatch()
            } else {
                exitToMainMenu()
            }
        } else {
            startOfflineMatch(current)
        }
    }

    fun pauseGame() {
        if (engine.screenMode == ScreenMode.PLAYING) {
            resetInputs()
            engine.setScreen(ScreenMode.PAUSED)
            _hudSnapshot.value = engine.latestHudSnapshot
        }
    }

    fun resumeGame() {
        if (engine.screenMode == ScreenMode.PAUSED) {
            engine.setScreen(ScreenMode.PLAYING)
            _hudSnapshot.value = engine.latestHudSnapshot
        }
    }

    // === Local Wi-Fi / Hotspot LAN Multiplayer ===
    fun hostLanGame() {
        val p = _profile.value
        multiplayerManager.startHosting(p.nickname, p.appearance, _matchConfig.value)
    }

    fun joinLanGame(roomCodeOrIp: String) {
        val p = _profile.value
        multiplayerManager.joinRoomByCodeOrIp(roomCodeOrIp, p.nickname, p.appearance)
    }

    fun startLanDiscovery() {
        multiplayerManager.startDiscovery()
    }

    fun toggleLanTeam() {
        multiplayerManager.toggleLocalTeam()
    }

    fun hostStartLanMatch() {
        multiplayerManager.hostStartMatch()
    }

    fun leaveLanLobby() {
        multiplayerManager.stopAll()
        exitToMainMenu()
    }

    // === Character Customization & Armory ===
    fun updateNickname(rawName: String) {
        val clean = rawName.trim().take(14).uppercase().ifBlank { "STRIKER" }
        val updated = _profile.value.copy(nickname = clean)
        persistProfile(updated)
    }

    fun updateAppearance(appearance: CharacterAppearance) {
        val updated = _profile.value.copy(appearance = appearance)
        persistProfile(updated)
    }

    fun buyOrEquipArmoryItem(item: ArmoryItem): String? {
        val current = _profile.value
        val alreadyUnlocked = current.unlockedItemIds.contains(item.id) || item.price == 0
        val afterUnlock = if (alreadyUnlocked) {
            current
        } else {
            if (current.coins < item.price) {
                return "Need ${item.price - current.coins} more coins"
            }
            current.copy(
                coins = current.coins - item.price,
                unlockedItemIds = current.unlockedItemIds + item.id
            )
        }

        val equipped = if (item.category == ArmoryCategory.WEAPONS && item.weaponType != null) {
            _armoryPreviewWeapon.value = item.weaponType
            _matchConfig.value = _matchConfig.value.copy(startingWeapon = item.weaponType)
            afterUnlock.copy(equippedWeapon = item.weaponType)
        } else {
            afterUnlock.copy(
                appearance = ArmoryCatalog.applyItemToAppearance(afterUnlock.appearance, item)
            )
        }
        persistProfile(equipped)
        return null
    }

    private fun persistProfile(updated: PlayerProfile) {
        _profile.value = updated
        engine.updateProfile(updated)
        viewModelScope.launch {
            saveSystem.saveProfile(updated)
        }
    }

    // === Settings ===
    fun updateSettings(newSettings: GameSettings) {
        _settings.value = newSettings
        engine.updateSettings(newSettings)
        viewModelScope.launch {
            saveSystem.saveSettings(newSettings)
        }
    }

    fun toggleQuickSound() {
        val cur = _settings.value
        val nextVol = if (cur.sfxVolume > 0.05f) 0f else 0.85f
        updateSettings(cur.copy(sfxVolume = nextVol))
    }

    fun toggleQuickMusic() {
        val cur = _settings.value
        val nextVol = if (cur.musicVolume > 0.05f) 0f else 0.55f
        updateSettings(cur.copy(musicVolume = nextVol))
    }

    // === Touch Control Callbacks ===
    fun onTouchMove(moveX: Float) {
        touchMoveX = moveX.coerceIn(-1f, 1f)
        pushCombinedInput()
    }

    fun onTouchCrouch(crouching: Boolean) {
        touchCrouch = crouching
        pushCombinedInput()
    }

    fun onTouchJetpack(held: Boolean) {
        touchJetpack = held
        pushCombinedInput()
    }

    fun onTouchAim(angleRad: Float, isActive: Boolean, autoFire: Boolean) {
        touchHasAim = isActive
        if (isActive) {
            touchAimAngle = angleRad
        }
        touchAimAutoFire = autoFire
        pushCombinedInput()
    }

    fun onTouchShootButton(pressed: Boolean) {
        touchShootPressed = pressed
        pushCombinedInput()
    }

    fun onJumpPressed() {
        engine.triggerJump()
    }

    fun onGrenadePressed() {
        engine.triggerGrenade()
    }

    fun onSwitchWeaponPressed() {
        engine.triggerWeaponSwitch(1)
    }

    fun onSelectWeapon(index: Int) {
        engine.selectWeapon(index)
    }

    fun onReloadPressed() {
        engine.triggerReload()
    }

    // === Keyboard & Mouse Testing Support ===
    fun handleKeyEvent(event: KeyEvent): Boolean {
        val isDown = event.type == KeyEventType.KeyDown
        val isUp = event.type == KeyEventType.KeyUp
        if (!isDown && !isUp) return false

        when (event.key) {
            Key.A, Key.DirectionLeft -> {
                keyLeft = isDown
                pushCombinedInput()
                return true
            }
            Key.D, Key.DirectionRight -> {
                keyRight = isDown
                pushCombinedInput()
                return true
            }
            Key.S, Key.C, Key.DirectionDown -> {
                keyCrouch = isDown
                pushCombinedInput()
                return true
            }
            Key.ShiftLeft, Key.ShiftRight -> {
                keyJetpack = isDown
                pushCombinedInput()
                return true
            }
            Key.W, Key.Spacebar, Key.DirectionUp -> {
                if (isDown && engine.screenMode == ScreenMode.PLAYING) {
                    engine.triggerJump()
                }
                return true
            }
            Key.G -> {
                if (isDown && engine.screenMode == ScreenMode.PLAYING) {
                    engine.triggerGrenade()
                }
                return true
            }
            Key.Q -> {
                if (isDown && engine.screenMode == ScreenMode.PLAYING) {
                    engine.triggerWeaponSwitch(1)
                }
                return true
            }
            Key.R -> {
                if (isDown && engine.screenMode == ScreenMode.PLAYING) {
                    engine.triggerReload()
                }
                return true
            }
            Key.One -> { if (isDown) engine.selectWeapon(0); return true }
            Key.Two -> { if (isDown) engine.selectWeapon(1); return true }
            Key.Three -> { if (isDown) engine.selectWeapon(2); return true }
            Key.Four -> { if (isDown) engine.selectWeapon(3); return true }
            Key.Five -> { if (isDown) engine.selectWeapon(4); return true }
            Key.Six -> { if (isDown) engine.selectWeapon(5); return true }
            Key.Escape, Key.P -> {
                if (isDown) {
                    if (engine.screenMode == ScreenMode.PLAYING) pauseGame()
                    else if (engine.screenMode == ScreenMode.PAUSED) resumeGame()
                }
                return true
            }
            else -> return false
        }
    }

    fun onMouseHoverOrDrag(screenX: Float, screenY: Float) {
        lastMouseX = screenX
        lastMouseY = screenY
        mouseHasAim = true
        recomputeMouseAimAngle()
        pushCombinedInput()
    }

    fun onMouseButton(pressed: Boolean, screenX: Float, screenY: Float) {
        lastMouseX = screenX
        lastMouseY = screenY
        mouseHasAim = true
        mousePressed = pressed
        recomputeMouseAimAngle()
        pushCombinedInput()
    }

    private fun recomputeMouseAimAngle() {
        val local = engine.players[engine.localPlayerId] ?: return
        val (wx, wy) = engine.cameraController.screenToWorld(
            lastMouseX, lastMouseY, viewportW.toFloat(), viewportH.toFloat()
        )
        val dx = wx - local.x
        val dy = wy - (local.y + if (local.isCrouching) 0.84f else 1.24f)
        if (dx * dx + dy * dy > 0.01f) {
            mouseAimAngle = atan2(dy, dx)
        }
    }

    private fun pushCombinedInput() {
        val keyMove = (if (keyRight) 1f else 0f) - (if (keyLeft) 1f else 0f)
        val moveX = if (abs(touchMoveX) > 0.05f) touchMoveX else keyMove
        val crouch = touchCrouch || keyCrouch
        val jetpack = touchJetpack || keyJetpack

        val hasAim = touchHasAim || mouseHasAim
        val aimAngle = if (touchHasAim) touchAimAngle else mouseAimAngle
        val isFiring = touchShootPressed || touchAimAutoFire || mousePressed

        engine.setLocalInput(
            moveX = moveX,
            crouchPressed = crouch,
            jetpackHeld = jetpack,
            aimAngleRad = aimAngle,
            hasExplicitAim = hasAim,
            isFiring = isFiring
        )
    }

    private fun resetInputs() {
        touchMoveX = 0f
        touchCrouch = false
        touchJetpack = false
        touchHasAim = false
        touchAimAutoFire = false
        touchShootPressed = false
        keyLeft = false
        keyRight = false
        keyCrouch = false
        keyJetpack = false
        mousePressed = false
        engine.setLocalInput(0f, false, false, 0f, false, false)
    }

    override fun onCleared() {
        super.onCleared()
        multiplayerManager.stopAll()
        audioManager.release()
    }
}
