package com.example.game.engine

import com.example.game.audio.AudioManager
import com.example.game.model.AnimationState
import com.example.game.model.ArenaPickup
import com.example.game.model.FlagStatus
import com.example.game.model.GameMode
import com.example.game.model.GameSettings
import com.example.game.model.HudPlayerSummary
import com.example.game.model.HudSnapshot
import com.example.game.model.KillFeedEntry
import com.example.game.model.MatchConfig
import com.example.game.model.MinimapDot
import com.example.game.model.PlayerId
import com.example.game.model.PlayerInputState
import com.example.game.model.PlayerProfile
import com.example.game.model.PlayerState
import com.example.game.model.ScreenMode
import com.example.game.model.WeaponType
import com.example.game.network.ConnectedPeer
import com.example.game.network.LanRole
import com.example.game.network.LocalMultiplayerManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Core GameEngine coordinating MapManager, GameModeManager, MatchManager, PlayerController,
 * BotAI (up to 11 bots), WeaponSystem, ProjectileSystem, CameraController, and LAN Multiplayer sync.
 */
class GameEngine(
    private val audioManager: AudioManager = AudioManager(),
    private val multiplayerManager: LocalMultiplayerManager? = null,
    private val rng: Random = Random.Default
) {
    val mapManager = MapManager()
    val gameModeManager = GameModeManager()
    val matchManager = MatchManager()
    val collisionSystem = CollisionSystem(mapManager)
    val weaponSystem = WeaponSystem(rng)
    val projectileSystem = ProjectileSystem(rng)
    val playerController = PlayerController(weaponSystem, collisionSystem, projectileSystem)
    val botAI = BotAI(mapManager, collisionSystem, gameModeManager, rng)
    val cameraController = CameraController(mapManager)

    var localPlayerId: PlayerId = "player_local"
        private set
    val players = LinkedHashMap<PlayerId, PlayerState>()
    val pickups = mutableListOf<ArenaPickup>()
    val killFeed = mutableListOf<KillFeedEntry>()

    var screenMode: ScreenMode = ScreenMode.LOADING
    var settings: GameSettings = GameSettings()
        private set
    var profile: PlayerProfile = PlayerProfile()
        private set
    var matchConfig: MatchConfig = MatchConfig()
        private set

    var matchSecondsLeft: Float = 180f
        private set
    private var hitMarkerAlpha: Float = 0f
    private var damageVignetteAlpha: Float = 0f
    private var nextKillId = 1L
    private var matchElapsed = 0f
    private var currentFps = 60
    private var fpsAccumTime = 0f
    private var fpsFrames = 0

    var onVibrate: ((Long) -> Unit)? = null
    var onMatchFinishedCallback: ((MatchOutcome, Int, Int) -> Unit)? = null

    @Volatile
    var latestHudSnapshot: HudSnapshot = HudSnapshot()
        private set

    private val localInputLock = Any()
    private var pendingLocalInput = PlayerInputState()
    private var queuedSwitchDelta = 0
    private var queuedSelectWeapon = -1
    private var queuedReload = false
    private var queuedJump = false
    private var queuedGrenade = false

    private var lastOutcome = MatchOutcome("", "", false, 0, 0)

    init {
        setupShowcasePlayer()
        setupLanHooks()
    }

    private fun setupLanHooks() {
        multiplayerManager?.onHostModeUpdate = { bs, rs, bfx, bfy, bfs, rfx, rfy, rfs, cpo, cpp, tl, mo, wn ->
            if (multiplayerManager.lobbyState.value.role == LanRole.CLIENT) {
                gameModeManager.blueFlag.x = bfx
                gameModeManager.blueFlag.y = bfy
                gameModeManager.blueFlag.status = FlagStatus.entries.getOrElse(bfs) { FlagStatus.AT_BASE }
                gameModeManager.redFlag.x = rfx
                gameModeManager.redFlag.y = rfy
                gameModeManager.redFlag.status = FlagStatus.entries.getOrElse(rfs) { FlagStatus.AT_BASE }
                gameModeManager.controlPoint.ownerTeam = cpo
                gameModeManager.controlPoint.captureProgress = cpp
                matchSecondsLeft = tl
                if (mo && screenMode == ScreenMode.PLAYING) {
                    val local = players[localPlayerId]
                    lastOutcome = MatchOutcome(wn, wn, false, 45, 50)
                    onMatchFinishedCallback?.invoke(lastOutcome, local?.kills ?: 0, local?.deaths ?: 0)
                    screenMode = ScreenMode.MATCH_OVER
                }
            }
        }
    }

    fun updateSettings(newSettings: GameSettings) {
        settings = newSettings
        audioManager.setSfxVolume(newSettings.sfxVolume)
        audioManager.setMusicVolume(newSettings.musicVolume)
        projectileSystem.particleScale = newSettings.graphicsQuality.particleScale
    }

    fun updateProfile(newProfile: PlayerProfile) {
        profile = newProfile
        if (screenMode != ScreenMode.PLAYING && screenMode != ScreenMode.PAUSED) {
            setupShowcasePlayer()
        }
    }

    fun startOfflineMatch(config: MatchConfig) {
        matchConfig = config.copy(isMultiplayer = false)
        localPlayerId = "player_local"
        buildMatchWorld(peers = null)
        screenMode = ScreenMode.PLAYING
        publishHudSnapshot()
    }

    fun startLanMatch(config: MatchConfig, peers: List<ConnectedPeer>, myPeerId: PlayerId) {
        matchConfig = config.copy(isMultiplayer = true, botCount = 0)
        localPlayerId = myPeerId
        buildMatchWorld(peers = peers)
        screenMode = ScreenMode.PLAYING
        publishHudSnapshot()
    }

    fun setScreen(mode: ScreenMode) {
        screenMode = mode
        if (mode == ScreenMode.MENU || mode == ScreenMode.CHARACTER_CUSTOMIZE || mode == ScreenMode.ARMORY) {
            setupShowcasePlayer()
        }
        publishHudSnapshot()
    }

    fun setLocalInput(
        moveX: Float,
        crouchPressed: Boolean,
        jetpackHeld: Boolean,
        aimAngleRad: Float,
        hasExplicitAim: Boolean,
        isFiring: Boolean
    ) {
        synchronized(localInputLock) {
            pendingLocalInput = PlayerInputState(
                moveX = moveX,
                crouchPressed = crouchPressed,
                jetpackHeld = jetpackHeld,
                aimAngleRad = aimAngleRad,
                hasExplicitAim = hasExplicitAim,
                isFiring = isFiring
            )
        }
    }

    fun triggerJump() { synchronized(localInputLock) { queuedJump = true } }
    fun triggerGrenade() { synchronized(localInputLock) { queuedGrenade = true } }
    fun triggerWeaponSwitch(delta: Int = 1) { synchronized(localInputLock) { queuedSwitchDelta += delta } }
    fun selectWeapon(index: Int) { synchronized(localInputLock) { queuedSelectWeapon = index } }
    fun triggerReload() { synchronized(localInputLock) { queuedReload = true } }

    fun step(rawDt: Float, viewportAspectRatio: Float) {
        val dt = rawDt.coerceIn(0.001f, 0.04f)

        fpsAccumTime += rawDt
        fpsFrames++
        if (fpsAccumTime >= 0.5f) {
            currentFps = (fpsFrames / fpsAccumTime).toInt().coerceIn(1, 120)
            fpsAccumTime = 0f
            fpsFrames = 0
        }

        val localPlayer = players[localPlayerId]

        if (screenMode == ScreenMode.LOADING ||
            screenMode == ScreenMode.MENU ||
            screenMode == ScreenMode.OFFLINE_SETUP ||
            screenMode == ScreenMode.MULTIPLAYER_LOBBY ||
            screenMode == ScreenMode.CHARACTER_CUSTOMIZE ||
            screenMode == ScreenMode.ARMORY ||
            screenMode == ScreenMode.SETTINGS
        ) {
            if (localPlayer != null) {
                localPlayer.animTime += dt
                localPlayer.animState = AnimationState.IDLE
            }
            cameraController.update(localPlayer, dt, viewportAspectRatio, settings.cameraSensitivity, screenMode)
            return
        }

        if (screenMode != ScreenMode.PLAYING) {
            cameraController.update(localPlayer, dt, viewportAspectRatio, settings.cameraSensitivity, screenMode)
            return
        }

        // Drain queued local one-shot inputs
        val localInput = synchronized(localInputLock) {
            val inp = pendingLocalInput.copy(
                jumpPressed = queuedJump,
                grenadeRequested = queuedGrenade,
                switchWeaponDelta = queuedSwitchDelta,
                selectWeaponIndex = queuedSelectWeapon,
                reloadRequested = queuedReload
            )
            queuedJump = false
            queuedGrenade = false
            queuedSwitchDelta = 0
            queuedSelectWeapon = -1
            queuedReload = false
            inp
        }

        val lanRole = multiplayerManager?.lobbyState?.value?.role ?: LanRole.NONE
        if (matchConfig.isMultiplayer && lanRole == LanRole.CLIENT && multiplayerManager != null) {
            // LAN Client: send input to host & interpolate from host UDP snapshots
            multiplayerManager.sendClientInput(localInput)
            if (localPlayer != null) {
                playerController.stepPlayer(localPlayer, localInput, dt)
            }
            multiplayerManager.applyClientInterpolation(players, dt)
            mapManager.stepAnimatedProps(dt)
            cameraController.update(localPlayer, dt, viewportAspectRatio, settings.cameraSensitivity, screenMode)
            publishHudSnapshot()
            return
        }

        // Authoritative Simulation (Offline Bot Mode OR LAN Host)
        matchElapsed += dt
        mapManager.stepAnimatedProps(dt)

        if (matchConfig.durationMinutes > 0) {
            matchSecondsLeft = (matchSecondsLeft - dt).coerceAtLeast(0f)
            if (matchSecondsLeft <= 0f) {
                finishMatch()
                return
            }
        }

        hitMarkerAlpha = (hitMarkerAlpha - dt * 3.2f).coerceAtLeast(0f)
        damageVignetteAlpha = (damageVignetteAlpha - dt * 2.1f).coerceAtLeast(0f)

        for (player in players.values) {
            if (!player.isAlive) {
                player.respawnTimer -= dt
                if (player.respawnTimer <= 0f) {
                    respawnPlayer(player)
                } else {
                    playerController.stepPlayer(player, PlayerInputState(), dt)
                }
                continue
            }

            if (player.isBot) {
                val aiInput = botAI.computeInput(player, players.values, matchConfig, dt)
                playerController.stepPlayer(
                    player = player,
                    input = aiInput,
                    dt = dt,
                    fireRateMultiplier = matchConfig.botDifficulty.fireRateScale,
                    onFireResult = { res ->
                        if (res is FireResult.Fired && localPlayer != null) {
                            val dist = hypot(player.x - localPlayer.x, player.y - localPlayer.y)
                            val atten = (1f - dist / 42f).coerceIn(0.06f, 0.72f)
                            audioManager.playWeaponShot(res.weaponType, atten)
                        }
                    }
                )
            } else {
                val input = if (player.id == localPlayerId) {
                    localInput
                } else {
                    multiplayerManager?.remoteInputs?.get(player.id) ?: PlayerInputState()
                }
                playerController.stepPlayer(
                    player = player,
                    input = input,
                    dt = dt,
                    onFireResult = { res ->
                        if (player.id == localPlayerId) {
                            when (res) {
                                is FireResult.Fired -> {
                                    audioManager.playWeaponShot(res.weaponType, 1f)
                                    val shake = when (res.weaponType) {
                                        WeaponType.PISTOL -> 0.06f
                                        WeaponType.ASSAULT_RIFLE -> 0.055f
                                        WeaponType.SHOTGUN -> 0.19f
                                        WeaponType.SNIPER_RIFLE -> 0.25f
                                        WeaponType.MACHINE_GUN -> 0.065f
                                        WeaponType.ROCKET_LAUNCHER -> 0.34f
                                    }
                                    cameraController.addShake(shake)
                                    if (settings.vibrationEnabled) onVibrate?.invoke(16L)
                                }
                                is FireResult.ReloadStarted -> audioManager.play(AudioManager.SfxType.RELOAD, 0.9f)
                                is FireResult.EmptyClick -> audioManager.play(AudioManager.SfxType.EMPTY_CLICK, 0.75f)
                                FireResult.None -> Unit
                            }
                        }
                    },
                    onJump = { if (player.id == localPlayerId) audioManager.play(AudioManager.SfxType.JUMP, 0.65f) },
                    onGrenadeThrown = { if (player.id == localPlayerId) audioManager.play(AudioManager.SfxType.GRENADE_THROW, 0.85f) }
                )
            }
        }

        projectileSystem.step(
            dt = dt,
            players = players.values,
            collisionSystem = collisionSystem,
            canDamage = { ownerId, victim ->
                val owner = players[ownerId]
                owner == null || !gameModeManager.isFriendlyFire(matchConfig, owner, victim)
            },
            onDamage = { dmg -> handleDamageEvent(dmg) },
            onExplosion = { ex, ey ->
                audioManager.play(AudioManager.SfxType.EXPLOSION, 0.95f)
                if (localPlayer != null) {
                    val d = hypot(ex - localPlayer.x, ey - localPlayer.y)
                    if (d < 16f) {
                        cameraController.addShake((1f - d / 16f) * 0.45f)
                        if (settings.vibrationEnabled) onVibrate?.invoke(45L)
                    }
                }
            }
        )

        gameModeManager.stepModeObjectives(matchConfig, players, dt) { flagMsg ->
            audioManager.play(AudioManager.SfxType.FLAG_EVENT, 0.9f)
            killFeed.add(0, KillFeedEntry(nextKillId++, flagMsg, "", "CTF", matchElapsed))
            if (killFeed.size > 4) killFeed.removeAt(killFeed.lastIndex)
        }

        val scoreWinner = gameModeManager.checkScoreWinner(matchConfig, players.values)
        if (scoreWinner != null) {
            finishMatch()
            return
        }

        stepPickups(dt)
        killFeed.removeAll { matchElapsed - it.timestampSec > 6.5f }
        cameraController.update(localPlayer, dt, viewportAspectRatio, settings.cameraSensitivity, screenMode)

        if (matchConfig.isMultiplayer && lanRole == LanRole.HOST && multiplayerManager != null) {
            multiplayerManager.hostBroadcastWorldSnapshot(
                players = players.values,
                blueScore = gameModeManager.blueTeamScore,
                redScore = gameModeManager.redTeamScore,
                blueFlagX = gameModeManager.blueFlag.x,
                blueFlagY = gameModeManager.blueFlag.y,
                blueFlagStatus = gameModeManager.blueFlag.status,
                redFlagX = gameModeManager.redFlag.x,
                redFlagY = gameModeManager.redFlag.y,
                redFlagStatus = gameModeManager.redFlag.status,
                cpOwner = gameModeManager.controlPoint.ownerTeam,
                cpProgress = gameModeManager.controlPoint.captureProgress,
                timeLeft = matchSecondsLeft,
                matchOver = false,
                winnerLabel = ""
            )
        }

        publishHudSnapshot()
    }

    private fun finishMatch() {
        val local = players[localPlayerId]
        lastOutcome = matchManager.computeOutcome(matchConfig, players.values, localPlayerId, gameModeManager)
        onMatchFinishedCallback?.invoke(lastOutcome, local?.kills ?: 0, local?.deaths ?: 0)
        screenMode = ScreenMode.MATCH_OVER
        if (matchConfig.isMultiplayer && multiplayerManager?.lobbyState?.value?.role == LanRole.HOST) {
            multiplayerManager.hostBroadcastWorldSnapshot(
                players = players.values,
                blueScore = gameModeManager.blueTeamScore,
                redScore = gameModeManager.redTeamScore,
                blueFlagX = gameModeManager.blueFlag.x,
                blueFlagY = gameModeManager.blueFlag.y,
                blueFlagStatus = gameModeManager.blueFlag.status,
                redFlagX = gameModeManager.redFlag.x,
                redFlagY = gameModeManager.redFlag.y,
                redFlagStatus = gameModeManager.redFlag.status,
                cpOwner = gameModeManager.controlPoint.ownerTeam,
                cpProgress = gameModeManager.controlPoint.captureProgress,
                timeLeft = 0f,
                matchOver = true,
                winnerLabel = lastOutcome.winnerLabel
            )
        }
        publishHudSnapshot()
    }

    private fun handleDamageEvent(dmg: DamageEvent) {
        val victim = players[dmg.victimId] ?: return
        val attacker = dmg.attackerId?.let { players[it] }

        victim.health = (victim.health - dmg.damage).coerceAtLeast(0f)
        victim.hurtFlashTimer = 0.16f

        if (attacker?.id == localPlayerId && victim.id != localPlayerId) {
            hitMarkerAlpha = 1.0f
            audioManager.play(AudioManager.SfxType.HIT_MARKER, 0.85f)
        }
        if (victim.id == localPlayerId) {
            damageVignetteAlpha = 0.85f
            cameraController.addShake(0.22f)
            audioManager.play(AudioManager.SfxType.PLAYER_HURT, 0.85f)
            if (settings.vibrationEnabled) onVibrate?.invoke(28L)
        }

        if (victim.health <= 0f && victim.isAlive) {
            victim.isAlive = false
            victim.deaths += 1
            victim.respawnTimer = matchConfig.respawnSeconds
            victim.animState = AnimationState.DEAD
            victim.animTime = 0f

            gameModeManager.onPlayerEliminated(matchConfig, attacker, victim)

            if (attacker != null && attacker.id == localPlayerId && victim.id != localPlayerId) {
                audioManager.play(AudioManager.SfxType.KILL_CONFIRM, 0.95f)
            }

            killFeed.add(
                0,
                KillFeedEntry(
                    id = nextKillId++,
                    killerName = attacker?.callsign ?: "ARENA",
                    victimName = victim.callsign,
                    weaponShort = dmg.weaponShort,
                    timestampSec = matchElapsed
                )
            )
            if (killFeed.size > 4) killFeed.removeAt(killFeed.lastIndex)
        }
    }

    private fun stepPickups(dt: Float) {
        for (pickup in pickups) {
            if (!pickup.active) {
                pickup.respawnTimer -= dt
                if (pickup.respawnTimer <= 0f) pickup.active = true
                continue
            }

            for (player in players.values) {
                if (!player.isAlive) continue
                val dx = abs(player.x - pickup.x)
                val dy = abs((player.y + 0.9f) - pickup.y)
                if (dx < 1.15f && dy < 1.35f) {
                    val consumed = when {
                        pickup.isHealthPack -> if (player.health < player.maxHealth) {
                            player.health = (player.health + 45f).coerceAtMost(player.maxHealth); true
                        } else false
                        pickup.isGrenadePack -> if (player.grenades < 4) {
                            player.grenades = (player.grenades + 2).coerceAtMost(4); true
                        } else false
                        else -> {
                            weaponSystem.addReserveAmmo(player); true
                        }
                    }
                    if (consumed) {
                        pickup.active = false
                        pickup.respawnTimer = 14f
                        if (!player.isBot) audioManager.play(AudioManager.SfxType.PICKUP, 0.8f)
                        break
                    }
                }
            }
        }
    }

    private fun respawnPlayer(player: PlayerState) {
        val spawn = chooseBestSpawnPoint(player)
        player.x = spawn.x
        player.y = spawn.y
        player.vx = 0f
        player.vy = 0f
        player.facingDir = spawn.facingDir
        player.aimAngleRad = if (spawn.facingDir >= 0f) 0f else Math.PI.toFloat()
        player.health = player.maxHealth
        player.jetpackFuel = 100f
        player.isAlive = true
        player.respawnTimer = 0f
        player.spawnProtectTimer = 1.6f
        player.hurtFlashTimer = 0f
        player.animState = AnimationState.IDLE
        weaponSystem.refillAllAmmo(player)
    }

    private fun chooseBestSpawnPoint(forPlayer: PlayerState): SpawnPoint {
        val candidates = if (matchConfig.gameMode != GameMode.DEATHMATCH) {
            mapManager.spawnPoints.filter { it.teamHint == forPlayer.teamIndex }.ifEmpty { mapManager.spawnPoints }
        } else mapManager.spawnPoints

        var best = candidates.first()
        var bestScore = -1f
        for (sp in candidates) {
            var minDist = Float.MAX_VALUE
            for (other in players.values) {
                if (other.id == forPlayer.id || !other.isAlive) continue
                val d = hypot(other.x - sp.x, other.y - sp.y)
                if (d < minDist) minDist = d
            }
            val score = minDist + rng.nextFloat() * 3f
            if (score > bestScore) {
                bestScore = score
                best = sp
            }
        }
        return best
    }

    private fun setupShowcasePlayer() {
        players.clear()
        projectileSystem.clear()
        mapManager.loadMap(matchConfig.mapId)
        gameModeManager.resetForMatch(mapManager)
        localPlayerId = "player_local"
        val p = PlayerState(
            id = localPlayerId,
            callsign = profile.nickname,
            teamIndex = 0,
            isBot = false,
            appearance = profile.appearance,
            x = 0f,
            y = 0f,
            facingDir = 1f,
            currentWeaponIndex = profile.equippedWeapon.ordinal
        )
        players[localPlayerId] = p
        cameraController.reset(p.x, p.y)
    }

    private fun buildMatchWorld(peers: List<ConnectedPeer>?) {
        mapManager.loadMap(matchConfig.mapId)
        gameModeManager.resetForMatch(mapManager)
        players.clear()
        projectileSystem.clear()
        botAI.reset()
        killFeed.clear()
        pickups.clear()
        pickups.addAll(mapManager.initialPickups.map { it.copy(active = true, respawnTimer = 0f) })

        matchSecondsLeft = if (matchConfig.durationMinutes > 0) matchConfig.durationMinutes * 60f else 0f
        matchElapsed = 0f
        hitMarkerAlpha = 0f
        damageVignetteAlpha = 0f
        lastOutcome = MatchOutcome("", "", false, 0, 0)

        if (peers != null) {
            peers.forEachIndexed { idx, peer ->
                val sp = mapManager.spawnPoints[idx % mapManager.spawnPoints.size]
                players[peer.id] = PlayerState(
                    id = peer.id,
                    callsign = peer.callsign,
                    teamIndex = peer.teamIndex,
                    isBot = false,
                    appearance = peer.appearance,
                    x = sp.x,
                    y = sp.y,
                    facingDir = sp.facingDir,
                    currentWeaponIndex = matchConfig.startingWeapon.ordinal
                )
            }
        } else {
            val localTeam = if (matchConfig.gameMode == GameMode.DEATHMATCH) 0 else matchConfig.localTeam
            val localSp = mapManager.spawnPoints.firstOrNull { it.teamHint == localTeam } ?: mapManager.spawnPoints[1]
            players[localPlayerId] = PlayerState(
                id = localPlayerId,
                callsign = profile.nickname,
                teamIndex = localTeam,
                isBot = false,
                appearance = profile.appearance,
                x = localSp.x,
                y = localSp.y,
                facingDir = localSp.facingDir,
                currentWeaponIndex = matchConfig.startingWeapon.ordinal
            )

            val botNames = listOf("VIPER", "KOBRA", "TITAN", "FALCON", "GHOST", "REAPER", "ATLAS", "RAVEN", "NOMAD", "SPARK", "BLITZ")
            val botCount = matchConfig.botCount.coerceIn(0, 11)
            for (i in 0 until botCount) {
                val team = if (matchConfig.gameMode == GameMode.DEATHMATCH) {
                    (i + 1) % 4
                } else {
                    if (i % 2 == 0) 1 - localTeam else localTeam
                }
                val teamSpawns = if (matchConfig.gameMode == GameMode.DEATHMATCH) {
                    mapManager.spawnPoints
                } else {
                    mapManager.spawnPoints.filter { it.teamHint == team }.ifEmpty { mapManager.spawnPoints }
                }
                val sp = teamSpawns[(i + 1) % teamSpawns.size]
                val weaponIdx = (i + 1) % WeaponType.entries.size
                val botApp = com.example.game.model.CharacterAppearance(
                    shirtHex = if (team == 1) 0xFF7F1D1D else 0xFF1E3A8A,
                    visorHex = if (team == 1) 0xFFEF4444 else 0xFF38BDF8
                )
                players["bot_$i"] = PlayerState(
                    id = "bot_$i",
                    callsign = botNames[i % botNames.size],
                    teamIndex = team,
                    isBot = true,
                    appearance = botApp,
                    x = sp.x + (i % 3 - 1) * 1.1f,
                    y = sp.y,
                    facingDir = sp.facingDir,
                    currentWeaponIndex = weaponIdx
                )
            }
        }

        val local = players[localPlayerId]
        if (local != null) cameraController.reset(local.x, local.y)
    }

    fun publishHudSnapshot() {
        val local = players[localPlayerId]
        val slot = local?.activeWeaponSlot
        val spec = slot?.weapon?.spec
        val reloadProgress = if (slot != null && slot.isReloading && spec != null && spec.reloadDurationSec > 0f) {
            (1f - slot.reloadTimer / spec.reloadDurationSec).coerceIn(0f, 1f)
        } else 0f

        val arenaW = mapManager.maxX - mapManager.minX
        val arenaH = mapManager.maxY - mapManager.minY

        val dots = players.values.map { p ->
            MinimapDot(
                id = p.id,
                nx = ((p.x - mapManager.minX) / arenaW).coerceIn(0f, 1f),
                ny = ((p.y - mapManager.minY) / arenaH).coerceIn(0f, 1f),
                isLocal = p.id == localPlayerId,
                isAlive = p.isAlive,
                teamIndex = p.teamIndex
            )
        }

        val summaries = players.values.map { p ->
            HudPlayerSummary(
                id = p.id,
                callsign = p.callsign,
                health = p.health,
                maxHealth = p.maxHealth,
                isAlive = p.isAlive,
                kills = p.kills,
                deaths = p.deaths,
                score = p.score,
                isBot = p.isBot,
                teamIndex = p.teamIndex,
                worldX = p.x,
                worldY = p.y
            )
        }

        val blueCarrierName = gameModeManager.blueFlag.carrierId?.let { players[it]?.callsign }
        val redCarrierName = gameModeManager.redFlag.carrierId?.let { players[it]?.callsign }

        latestHudSnapshot = HudSnapshot(
            screenMode = screenMode,
            gameMode = matchConfig.gameMode,
            mapId = matchConfig.mapId,
            localHealth = local?.health ?: 0f,
            localMaxHealth = local?.maxHealth ?: 100f,
            localJetpackFuel = local?.jetpackFuel ?: 0f,
            localGrenades = local?.grenades ?: 0,
            localIsCrouching = local?.isCrouching ?: false,
            localIsAlive = local?.isAlive ?: false,
            localTeam = local?.teamIndex ?: 0,
            respawnSecondsLeft = local?.respawnTimer ?: 0f,
            localKills = local?.kills ?: 0,
            localDeaths = local?.deaths ?: 0,
            currentWeapon = slot?.weapon ?: WeaponType.ASSAULT_RIFLE,
            ammoInMag = slot?.ammoInMag ?: 0,
            reserveAmmo = slot?.reserveAmmo ?: 0,
            isReloading = slot?.isReloading ?: false,
            reloadProgress = reloadProgress,
            matchSecondsLeft = matchSecondsLeft,
            isUnlimitedTime = matchConfig.durationMinutes == 0,
            blueTeamScore = gameModeManager.blueTeamScore,
            redTeamScore = gameModeManager.redTeamScore,
            blueFlagStatus = gameModeManager.blueFlag.status,
            redFlagStatus = gameModeManager.redFlag.status,
            blueFlagCarrier = blueCarrierName,
            redFlagCarrier = redCarrierName,
            controlPointOwner = gameModeManager.controlPoint.ownerTeam,
            controlPointProgress = gameModeManager.controlPoint.captureProgress,
            controlPointContested = gameModeManager.controlPoint.contested,
            hitMarkerAlpha = hitMarkerAlpha,
            damageVignetteAlpha = damageVignetteAlpha,
            fps = currentFps,
            showMinimap = settings.showMinimap,
            minimapDots = dots,
            players = summaries,
            killFeed = killFeed.toList(),
            winnerLabel = lastOutcome.winnerLabel,
            mvpLabel = lastOutcome.mvpCallsign,
            lastEarnedCoins = lastOutcome.earnedCoins,
            lastEarnedXp = lastOutcome.earnedXp
        )
    }
}
