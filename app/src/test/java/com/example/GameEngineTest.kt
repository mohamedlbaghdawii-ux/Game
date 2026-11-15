package com.example

import com.example.game.audio.AudioManager
import com.example.game.engine.GameEngine
import com.example.game.model.BotDifficulty
import com.example.game.model.FlagStatus
import com.example.game.model.GameMode
import com.example.game.model.MapId
import com.example.game.model.MatchConfig
import com.example.game.model.WeaponType
import com.example.game.network.LocalMultiplayerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    @Test
    fun `all 6 maps load valid geometry and 12 spawn points`() {
        val engine = GameEngine(AudioManager(null), null, Random(42))
        for (mapId in MapId.entries) {
            engine.startOfflineMatch(MatchConfig(mapId = mapId, botCount = 11))
            assertEquals("Map $mapId should support 12 players", 12, engine.players.size)
            assertEquals("Map $mapId should define 12 spawn points", 12, engine.mapManager.spawnPoints.size)
            assertTrue("Map $mapId should have solid ground", engine.mapManager.solidColliders.isNotEmpty())
        }
    }

    @Test
    fun `offline match supports 11 bots and all 4 bot difficulties`() {
        for (diff in BotDifficulty.entries) {
            val engine = GameEngine(AudioManager(null), null, Random(17))
            engine.startOfflineMatch(MatchConfig(botCount = 11, botDifficulty = diff))
            assertEquals(12, engine.players.size)
            assertEquals(11, engine.players.values.count { it.isBot })
            engine.step(0.016f, 16f / 9f)
        }
    }

    @Test
    fun `movement, jump, crouch, jetpack, and 6-weapon switching work`() {
        val engine = GameEngine(AudioManager(null), null, Random(7))
        engine.startOfflineMatch(MatchConfig(mapId = MapId.MILITARY_BASE, botCount = 1))
        val local = engine.players[engine.localPlayerId]!!
        val startX = local.x

        // Move right
        engine.setLocalInput(moveX = 1f, crouchPressed = false, jetpackHeld = false, aimAngleRad = 0f, hasExplicitAim = true, isFiring = false)
        repeat(15) { engine.step(0.016f, 16f / 9f) }
        assertTrue("Player should move right", local.x > startX + 1.0f)

        // Crouch reduces effectiveHeight
        val standH = local.effectiveHeight
        engine.setLocalInput(moveX = 0f, crouchPressed = true, jetpackHeld = false, aimAngleRad = 0f, hasExplicitAim = true, isFiring = false)
        engine.step(0.016f, 16f / 9f)
        assertTrue("Crouching should reduce hitbox height", local.effectiveHeight < standH)

        // Jetpack lifts player and consumes fuel
        val startFuel = local.jetpackFuel
        val yBeforeJet = local.y
        engine.setLocalInput(moveX = 0f, crouchPressed = false, jetpackHeld = true, aimAngleRad = 0f, hasExplicitAim = true, isFiring = false)
        repeat(20) { engine.step(0.016f, 16f / 9f) }
        assertTrue("Jetpack should thrust upward", local.y > yBeforeJet + 0.5f)
        assertTrue("Jetpack should consume fuel", local.jetpackFuel < startFuel)

        // Weapon switching cycles through all 6 weapons
        val firstWeapon = local.activeWeaponSlot.weapon
        engine.triggerWeaponSwitch(1)
        engine.step(0.016f, 16f / 9f)
        assertNotEquals(firstWeapon, local.activeWeaponSlot.weapon)

        engine.selectWeapon(WeaponType.ROCKET_LAUNCHER.ordinal)
        engine.step(0.016f, 16f / 9f)
        assertEquals(WeaponType.ROCKET_LAUNCHER, local.activeWeaponSlot.weapon)
    }

    @Test
    fun `shooting and grenades deal damage, kill, and trigger respawn`() {
        val engine = GameEngine(AudioManager(null), null, Random(99))
        engine.startOfflineMatch(MatchConfig(mapId = MapId.MILITARY_BASE, botCount = 1))
        val local = engine.players[engine.localPlayerId]!!
        val bot = engine.players.values.first { it.isBot }

        local.x = -20.5f; local.y = 0f; local.vx = 0f; local.vy = 0f; local.spawnProtectTimer = 5f
        bot.x = -14.5f; bot.y = 0f; bot.vx = 0f; bot.vy = 0f; bot.spawnProtectTimer = 0f

        engine.selectWeapon(WeaponType.SNIPER_RIFLE.ordinal)
        engine.setLocalInput(0f, false, false, 0f, true, true)
        engine.step(0.016f, 16f / 9f)
        assertTrue(engine.projectileSystem.projectiles.any { it.ownerId == engine.localPlayerId })

        engine.setLocalInput(0f, false, false, 0f, true, false)
        repeat(15) { engine.step(0.016f, 16f / 9f) }
        assertTrue("Bot health should drop after sniper hit", bot.health < 100f)

        // Second fatal shot
        repeat(70) { engine.step(0.016f, 16f / 9f) }
        local.x = -20.5f; local.y = 0f
        bot.x = -14.5f; bot.y = 0f; bot.vx = 0f; bot.vy = 0f
        engine.setLocalInput(0f, false, false, 0f, true, true)
        engine.step(0.016f, 16f / 9f)
        engine.setLocalInput(0f, false, false, 0f, true, false)
        repeat(15) { engine.step(0.016f, 16f / 9f) }

        assertFalse("Bot should be eliminated", bot.isAlive)
        assertTrue("Kill count should increment", local.kills >= 1)

        // Throw grenade consumes grenade & spawns projectile
        val grenadesBefore = local.grenades
        engine.triggerGrenade()
        engine.step(0.016f, 16f / 9f)
        assertEquals(grenadesBefore - 1, local.grenades)
        assertEquals(1, engine.projectileSystem.grenades.size)
    }

    @Test
    fun `tdm prevents friendly fire and ctf flag capture scores point`() {
        val engine = GameEngine(AudioManager(null), null, Random(21))
        engine.startOfflineMatch(
            MatchConfig(
                mapId = MapId.DESERT_WAR,
                gameMode = GameMode.CAPTURE_THE_FLAG,
                botCount = 2,
                localTeam = 0
            )
        )
        val local = engine.players[engine.localPlayerId]!!
        local.spawnProtectTimer = 0f
        val teammate = engine.players.values.firstOrNull { it.isBot && it.teamIndex == 0 }
        if (teammate != null) {
            assertTrue(engine.gameModeManager.isFriendlyFire(engine.matchConfig, local, teammate))
        }

        // Move local player onto Red Flag to carry it, then back to Blue Base
        val redFlag = engine.gameModeManager.redFlag
        val blueFlag = engine.gameModeManager.blueFlag
        local.x = redFlag.baseX
        local.y = redFlag.baseY
        engine.step(0.016f, 16f / 9f)
        assertEquals(FlagStatus.CARRIED, redFlag.status)
        assertEquals(local.id, redFlag.carrierId)

        local.x = blueFlag.baseX
        local.y = blueFlag.baseY
        engine.step(0.016f, 16f / 9f)
        assertEquals(1, engine.gameModeManager.blueTeamScore)
        assertEquals(FlagStatus.AT_BASE, redFlag.status)
    }

    @Test
    fun `lan room code encodes and decodes ipv4 cleanly`() {
        val mgr = LocalMultiplayerManager(null)
        val ip = "192.168.43.1"
        val code = mgr.encodeRoomCode(ip)
        assertEquals("C0A82B01", code)
        assertEquals(ip, mgr.decodeRoomCodeToIp(code))
        assertEquals(ip, mgr.decodeRoomCodeToIp(ip))
    }
}
