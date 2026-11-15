package com.example.game.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.game.model.AppLanguage
import com.example.game.model.BackpackStyle
import com.example.game.model.CharacterAppearance
import com.example.game.model.GameSettings
import com.example.game.model.GraphicsQuality
import com.example.game.model.HeadgearStyle
import com.example.game.model.PlayerProfile
import com.example.game.model.WeaponType
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.miniBattleDataStore by preferencesDataStore(name = "mini_battle_arena_save_v2")

/**
 * Local persistence layer for player profile, customization, unlocked armory items,
 * coins, XP/level, lifetime statistics, and game settings.
 */
class SaveSystem(private val context: Context) {

    suspend fun loadProfile(): PlayerProfile {
        val prefs = context.miniBattleDataStore.data.first()
        val raw = prefs[KEY_PROFILE_JSON] ?: return PlayerProfile()
        return runCatching { decodeProfile(raw) }.getOrDefault(PlayerProfile())
    }

    suspend fun saveProfile(profile: PlayerProfile) {
        val encoded = encodeProfile(profile)
        context.miniBattleDataStore.edit { prefs ->
            prefs[KEY_PROFILE_JSON] = encoded
        }
    }

    suspend fun loadSettings(): GameSettings {
        val prefs = context.miniBattleDataStore.data.first()
        val gfxName = prefs[KEY_GRAPHICS] ?: GraphicsQuality.HIGH.name
        val langName = prefs[KEY_LANGUAGE] ?: AppLanguage.ENGLISH.name
        return GameSettings(
            musicVolume = prefs[KEY_MUSIC_VOL] ?: 0.55f,
            sfxVolume = prefs[KEY_SFX_VOL] ?: 0.85f,
            graphicsQuality = GraphicsQuality.entries.firstOrNull { it.name == gfxName } ?: GraphicsQuality.HIGH,
            controlSensitivity = prefs[KEY_CTRL_SENS] ?: 1.0f,
            cameraSensitivity = prefs[KEY_CAM_SENS] ?: 1.0f,
            language = AppLanguage.entries.firstOrNull { it.name == langName } ?: AppLanguage.ENGLISH,
            vibrationEnabled = prefs[KEY_VIBRATION] ?: true,
            showMinimap = prefs[KEY_SHOW_MINIMAP] ?: true
        )
    }

    suspend fun saveSettings(settings: GameSettings) {
        context.miniBattleDataStore.edit { prefs ->
            prefs[KEY_MUSIC_VOL] = settings.musicVolume
            prefs[KEY_SFX_VOL] = settings.sfxVolume
            prefs[KEY_GRAPHICS] = settings.graphicsQuality.name
            prefs[KEY_CTRL_SENS] = settings.controlSensitivity
            prefs[KEY_CAM_SENS] = settings.cameraSensitivity
            prefs[KEY_LANGUAGE] = settings.language.name
            prefs[KEY_VIBRATION] = settings.vibrationEnabled
            prefs[KEY_SHOW_MINIMAP] = settings.showMinimap
        }
    }

    private fun encodeProfile(p: PlayerProfile): String {
        val json = JSONObject()
        json.put("nickname", p.nickname)
        json.put("coins", p.coins)
        json.put("xp", p.xp)
        json.put("level", p.level)
        json.put("equippedWeapon", p.equippedWeapon.name)
        json.put("totalKills", p.totalKills)
        json.put("totalDeaths", p.totalDeaths)
        json.put("totalWins", p.totalWins)
        json.put("matchesPlayed", p.matchesPlayed)
        json.put("hasCompletedFirstLaunch", p.hasCompletedFirstLaunch)

        val unlockedArr = JSONArray()
        p.unlockedItemIds.forEach { unlockedArr.put(it) }
        json.put("unlocked", unlockedArr)

        val appJson = JSONObject()
        appJson.put("characterId", p.appearance.characterId)
        appJson.put("headgearId", p.appearance.headgearId)
        appJson.put("clothingId", p.appearance.clothingId)
        appJson.put("headgearStyle", p.appearance.headgearStyle.name)
        appJson.put("backpackStyle", p.appearance.backpackStyle.name)
        appJson.put("shirtHex", p.appearance.shirtHex)
        appJson.put("pantsHex", p.appearance.pantsHex)
        appJson.put("bootsHex", p.appearance.bootsHex)
        appJson.put("backpackHex", p.appearance.backpackHex)
        appJson.put("visorHex", p.appearance.visorHex)
        json.put("appearance", appJson)
        return json.toString()
    }

    private fun decodeProfile(raw: String): PlayerProfile {
        val json = JSONObject(raw)
        val unlockedArr = json.optJSONArray("unlocked")
        val unlocked = mutableSetOf<String>()
        if (unlockedArr != null) {
            for (i in 0 until unlockedArr.length()) {
                unlocked.add(unlockedArr.getString(i))
            }
        } else {
            unlocked.addAll(PlayerProfile().unlockedItemIds)
        }

        val appJson = json.optJSONObject("appearance") ?: JSONObject()
        val headgearName = appJson.optString("headgearStyle", HeadgearStyle.M1_HELMET.name)
        val backpackName = appJson.optString("backpackStyle", BackpackStyle.JET_THRUSTER.name)
        val appearance = CharacterAppearance(
            characterId = appJson.optString("characterId", "char_striker"),
            headgearId = appJson.optString("headgearId", "helm_m1"),
            clothingId = appJson.optString("clothingId", "cloth_military"),
            headgearStyle = HeadgearStyle.entries.firstOrNull { it.name == headgearName } ?: HeadgearStyle.M1_HELMET,
            backpackStyle = BackpackStyle.entries.firstOrNull { it.name == backpackName } ?: BackpackStyle.JET_THRUSTER,
            shirtHex = appJson.optLong("shirtHex", 0xFF3A523E),
            pantsHex = appJson.optLong("pantsHex", 0xFF2B3530),
            bootsHex = appJson.optLong("bootsHex", 0xFF1E2024),
            backpackHex = appJson.optLong("backpackHex", 0xFF47553E),
            visorHex = appJson.optLong("visorHex", 0xFF38BDF8)
        )

        val weaponName = json.optString("equippedWeapon", WeaponType.ASSAULT_RIFLE.name)
        return PlayerProfile(
            nickname = json.optString("nickname", "STRIKER").take(14).ifBlank { "STRIKER" },
            coins = json.optInt("coins", 500),
            xp = json.optInt("xp", 0),
            level = json.optInt("level", 1),
            appearance = appearance,
            equippedWeapon = WeaponType.entries.firstOrNull { it.name == weaponName } ?: WeaponType.ASSAULT_RIFLE,
            unlockedItemIds = unlocked,
            totalKills = json.optInt("totalKills", 0),
            totalDeaths = json.optInt("totalDeaths", 0),
            totalWins = json.optInt("totalWins", 0),
            matchesPlayed = json.optInt("matchesPlayed", 0),
            hasCompletedFirstLaunch = json.optBoolean("hasCompletedFirstLaunch", false)
        )
    }

    companion object {
        private val KEY_PROFILE_JSON = stringPreferencesKey("profile_json")
        private val KEY_MUSIC_VOL = floatPreferencesKey("music_volume")
        private val KEY_SFX_VOL = floatPreferencesKey("sfx_volume")
        private val KEY_GRAPHICS = stringPreferencesKey("graphics_quality")
        private val KEY_CTRL_SENS = floatPreferencesKey("control_sensitivity")
        private val KEY_CAM_SENS = floatPreferencesKey("camera_sensitivity")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_VIBRATION = booleanPreferencesKey("vibration_enabled")
        private val KEY_SHOW_MINIMAP = booleanPreferencesKey("show_minimap")
    }
}
