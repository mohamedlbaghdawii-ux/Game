package com.example.game.model

typealias PlayerId = String

enum class WeaponType(val spec: WeaponSpec) {
    PISTOL(
        WeaponSpec(
            id = "pistol",
            displayName = "M9 TACTICAL",
            shortLabel = "PISTOL",
            fireIntervalSec = 0.25f,
            damage = 22f,
            projectileSpeed = 37f,
            pellets = 1,
            spreadRad = 0.02f,
            magazineSize = 12,
            maxReserve = 84,
            reloadDurationSec = 1.0f,
            projectileRadius = 0.14f,
            recoilKick = 0.20f,
            tracerLength = 0.9f,
            maxRange = 42f,
            colorR = 1.0f, colorG = 0.85f, colorB = 0.25f
        )
    ),
    ASSAULT_RIFLE(
        WeaponSpec(
            id = "rifle",
            displayName = "VX-47 CARBINE",
            shortLabel = "AR-47",
            fireIntervalSec = 0.105f,
            damage = 14f,
            projectileSpeed = 43f,
            pellets = 1,
            spreadRad = 0.05f,
            magazineSize = 30,
            maxReserve = 180,
            reloadDurationSec = 1.5f,
            projectileRadius = 0.13f,
            recoilKick = 0.14f,
            tracerLength = 1.25f,
            maxRange = 48f,
            colorR = 1.0f, colorG = 0.62f, colorB = 0.12f
        )
    ),
    SHOTGUN(
        WeaponSpec(
            id = "shotgun",
            displayName = "BREACHER-12",
            shortLabel = "SHOTGUN",
            fireIntervalSec = 0.68f,
            damage = 13f,
            projectileSpeed = 32f,
            pellets = 6,
            spreadRad = 0.22f,
            magazineSize = 6,
            maxReserve = 42,
            reloadDurationSec = 1.85f,
            projectileRadius = 0.12f,
            recoilKick = 0.48f,
            tracerLength = 0.78f,
            maxRange = 19f,
            colorR = 1.0f, colorG = 0.36f, colorB = 0.12f
        )
    ),
    SNIPER_RIFLE(
        WeaponSpec(
            id = "sniper",
            displayName = "LONGSHOT .50",
            shortLabel = "SNIPER",
            fireIntervalSec = 1.0f,
            damage = 75f,
            projectileSpeed = 74f,
            pellets = 1,
            spreadRad = 0.003f,
            magazineSize = 5,
            maxReserve = 30,
            reloadDurationSec = 2.05f,
            projectileRadius = 0.16f,
            recoilKick = 0.56f,
            tracerLength = 2.5f,
            maxRange = 76f,
            colorR = 0.28f, colorG = 0.88f, colorB = 1.0f
        )
    ),
    MACHINE_GUN(
        WeaponSpec(
            id = "machine_gun",
            displayName = "VULCAN M249",
            shortLabel = "LMG",
            fireIntervalSec = 0.075f,
            damage = 12f,
            projectileSpeed = 40f,
            pellets = 1,
            spreadRad = 0.085f,
            magazineSize = 75,
            maxReserve = 225,
            reloadDurationSec = 2.45f,
            projectileRadius = 0.13f,
            recoilKick = 0.12f,
            tracerLength = 1.15f,
            maxRange = 46f,
            colorR = 1.0f, colorG = 0.78f, colorB = 0.22f
        )
    ),
    ROCKET_LAUNCHER(
        WeaponSpec(
            id = "rocket",
            displayName = "HYDRA RPG-9",
            shortLabel = "ROCKET",
            fireIntervalSec = 1.35f,
            damage = 88f,
            projectileSpeed = 24f,
            pellets = 1,
            spreadRad = 0.01f,
            magazineSize = 2,
            maxReserve = 10,
            reloadDurationSec = 2.3f,
            projectileRadius = 0.24f,
            recoilKick = 0.72f,
            tracerLength = 1.4f,
            maxRange = 64f,
            colorR = 1.0f, colorG = 0.25f, colorB = 0.10f,
            explosionRadius = 4.2f
        )
    )
}

data class WeaponSpec(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val fireIntervalSec: Float,
    val damage: Float,
    val projectileSpeed: Float,
    val pellets: Int,
    val spreadRad: Float,
    val magazineSize: Int,
    val maxReserve: Int,
    val reloadDurationSec: Float,
    val projectileRadius: Float,
    val recoilKick: Float,
    val tracerLength: Float,
    val maxRange: Float,
    val colorR: Float,
    val colorG: Float,
    val colorB: Float,
    val explosionRadius: Float = 0f
)

enum class AnimationState {
    IDLE,
    RUNNING,
    JUMPING,
    FALLING,
    CROUCHING,
    JETPACKING,
    SHOOTING,
    HURT,
    DEAD
}

enum class BotDifficulty(
    val label: String,
    val reactionSec: Float,
    val aimJitterRad: Float,
    val fireRateScale: Float,
    val jetpackChance: Float
) {
    EASY("EASY", 0.65f, 0.28f, 1.45f, 0.08f),
    NORMAL("NORMAL", 0.36f, 0.14f, 1.12f, 0.25f),
    HARD("HARD", 0.20f, 0.065f, 0.92f, 0.50f),
    EXPERT("EXPERT", 0.10f, 0.025f, 0.78f, 0.78f)
}

enum class MapId(
    val title: String,
    val subtitle: String,
    val accentHex: Long
) {
    MILITARY_BASE("MILITARY BASE", "Containers, bunkers & steel catwalks", 0xFF38BDF8),
    DESERT_WAR("DESERT WAR", "Sandstone mesas, canyon rocks & armored vehicles", 0xFFF59E0B),
    JUNGLE_ARENA("JUNGLE ARENA", "Tropical canopy, wooden bridges & temple ruins", 0xFF22C55E),
    FACTORY("FACTORY", "Industrial machinery, lift platforms & smelting vats", 0xFFFB923C),
    SNOW_MOUNTAIN("SNOW MOUNTAIN", "Alpine ridge, ice platforms & arctic outpost", 0xFF7DD3FC),
    CITY_RUINS("CITY RUINS", "Shattered skyscrapers, rooftops & concrete barriers", 0xFFA78BFA)
}

enum class GameMode(val title: String, val shortTitle: String, val description: String) {
    DEATHMATCH("DEATHMATCH", "FFA", "Free-for-all combat. Highest eliminations wins."),
    TEAM_DEATHMATCH("TEAM DEATHMATCH", "TDM", "RED vs BLUE squad warfare. Highest team score wins."),
    CAPTURE_THE_FLAG("CAPTURE THE FLAG", "CTF", "Steal the enemy flag and return it to your base."),
    POINT_CONTROL("POINT CONTROL", "KOTH", "Hold the tactical control zone to score team points.")
}

enum class GraphicsQuality(val label: String, val particleScale: Float, val decorEnabled: Boolean) {
    LOW("LOW", 0.45f, false),
    MEDIUM("MEDIUM", 0.75f, true),
    HIGH("HIGH", 1.0f, true)
}

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    ARABIC("ar", "العربية"),
    FRENCH("fr", "Français")
}

enum class HeadgearStyle(val displayName: String) {
    M1_HELMET("M1 Infantry Helmet"),
    TACTICAL_VISOR("Cyber Visor Helm"),
    DESERT_CAP("Desert Ranger Cap"),
    SPEC_OPS_MASK("Spec-Ops Mask"),
    HEAVY_JUGGERNAUT("Heavy Titan Helm"),
    COMMANDO_BERET("Commando Beret")
}

enum class BackpackStyle(val displayName: String) {
    FIELD_PACK("Field Pack"),
    RADIO_PACK("Recon Radio Pack"),
    JET_THRUSTER("Twin Jet Thruster"),
    MUNITIONS_PACK("Heavy Munitions Pack")
}

data class CharacterAppearance(
    val characterId: String = "char_striker",
    val headgearId: String = "helm_m1",
    val clothingId: String = "cloth_military",
    val headgearStyle: HeadgearStyle = HeadgearStyle.M1_HELMET,
    val backpackStyle: BackpackStyle = BackpackStyle.JET_THRUSTER,
    val shirtHex: Long = 0xFF3A523E,
    val pantsHex: Long = 0xFF2B3530,
    val bootsHex: Long = 0xFF1E2024,
    val backpackHex: Long = 0xFF47553E,
    val visorHex: Long = 0xFF38BDF8
)

data class PlayerProfile(
    val nickname: String = "STRIKER",
    val coins: Int = 500,
    val xp: Int = 0,
    val level: Int = 1,
    val appearance: CharacterAppearance = CharacterAppearance(),
    val equippedWeapon: WeaponType = WeaponType.ASSAULT_RIFLE,
    val unlockedItemIds: Set<String> = setOf(
        "char_striker",
        "cloth_military",
        "cloth_desert",
        "helm_m1",
        "helm_visor",
        "wp_pistol",
        "wp_rifle",
        "wp_shotgun",
        "wp_sniper"
    ),
    val totalKills: Int = 0,
    val totalDeaths: Int = 0,
    val totalWins: Int = 0,
    val matchesPlayed: Int = 0,
    val hasCompletedFirstLaunch: Boolean = false
)

data class GameSettings(
    val musicVolume: Float = 0.55f,
    val sfxVolume: Float = 0.85f,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val controlSensitivity: Float = 1.0f,
    val cameraSensitivity: Float = 1.0f,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val vibrationEnabled: Boolean = true,
    val showMinimap: Boolean = true
)

data class MatchConfig(
    val mapId: MapId = MapId.MILITARY_BASE,
    val gameMode: GameMode = GameMode.DEATHMATCH,
    val durationMinutes: Int = 3, // 3, 5, 10, 15, or 0 for Unlimited
    val scoreLimit: Int = 20, // 10, 20, 30, 50
    val botCount: Int = 3, // 0..11
    val botDifficulty: BotDifficulty = BotDifficulty.NORMAL,
    val localTeam: Int = 0, // 0 = Blue, 1 = Red (for team modes)
    val startingWeapon: WeaponType = WeaponType.ASSAULT_RIFLE,
    val respawnSeconds: Float = 3.0f,
    val isMultiplayer: Boolean = false
)

data class PlayerInputState(
    val moveX: Float = 0f,
    val crouchPressed: Boolean = false,
    val jumpPressed: Boolean = false,
    val jetpackHeld: Boolean = false,
    val aimAngleRad: Float = 0f,
    val hasExplicitAim: Boolean = false,
    val isFiring: Boolean = false,
    val grenadeRequested: Boolean = false,
    val switchWeaponDelta: Int = 0,
    val selectWeaponIndex: Int = -1,
    val reloadRequested: Boolean = false
)

data class WeaponSlotState(
    val weapon: WeaponType,
    var ammoInMag: Int = weapon.spec.magazineSize,
    var reserveAmmo: Int = weapon.spec.maxReserve,
    var isReloading: Boolean = false,
    var reloadTimer: Float = 0f,
    var fireCooldown: Float = 0f
)

/**
 * Decoupled authoritative soldier state supporting up to 12 players (offline bots or LAN humans).
 */
data class PlayerState(
    val id: PlayerId,
    val callsign: String,
    val teamIndex: Int, // 0 = Blue, 1 = Red (in FFA each uses teamIndex for color variety or 0/1/2/3)
    val isBot: Boolean,
    val appearance: CharacterAppearance = CharacterAppearance(),
    var x: Float,
    var y: Float,
    var z: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var width: Float = 0.90f,
    var height: Float = 1.88f,
    var isCrouching: Boolean = false,
    var facingDir: Float = 1f,
    var aimAngleRad: Float = 0f,
    var onGround: Boolean = false,
    var coyoteTimer: Float = 0f,
    var jumpBufferTimer: Float = 0f,
    var jumpsUsed: Int = 0,
    var jetpackFuel: Float = 100f,
    var isJetpacking: Boolean = false,
    var health: Float = 100f,
    val maxHealth: Float = 100f,
    var grenades: Int = 3,
    var grenadeCooldown: Float = 0f,
    var isAlive: Boolean = true,
    var respawnTimer: Float = 0f,
    var kills: Int = 0,
    var deaths: Int = 0,
    var score: Int = 0,
    var animState: AnimationState = AnimationState.IDLE,
    var animTime: Float = 0f,
    var hurtFlashTimer: Float = 0f,
    var muzzleFlashTimer: Float = 0f,
    var recoilOffset: Float = 0f,
    var spawnProtectTimer: Float = 0f,
    var currentWeaponIndex: Int = 1,
    val weapons: MutableList<WeaponSlotState> = WeaponType.entries.map { WeaponSlotState(it) }.toMutableList()
) {
    val activeWeaponSlot: WeaponSlotState
        get() = weapons[currentWeaponIndex]
    val effectiveHeight: Float
        get() = if (isCrouching) 1.24f else height
}

data class ProjectileState(
    val id: Long,
    val ownerId: PlayerId,
    val ownerTeam: Int,
    val weaponType: WeaponType,
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float,
    val radius: Float,
    val explosionRadius: Float = 0f,
    var distanceTraveled: Float = 0f,
    val maxRange: Float,
    val tracerLength: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    var active: Boolean = true
)

data class GrenadeState(
    val id: Long,
    val ownerId: PlayerId,
    val ownerTeam: Int,
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var fuseTimer: Float = 1.65f,
    var active: Boolean = true
)

data class ExplosionEffect(
    val x: Float,
    val y: Float,
    val z: Float,
    val maxRadius: Float,
    var life: Float,
    val maxLife: Float
)

data class ParticleState(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var size: Float,
    var life: Float,
    val maxLife: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val gravityScale: Float = 1f,
    var active: Boolean = true
)

enum class PropKind {
    GROUND,
    PLATFORM,
    ELEVATOR,
    CONTAINER,
    CONCRETE_WALL,
    BARRIER,
    CRATE,
    TOWER,
    SILO,
    LAMP_POST,
    VEHICLE,
    TREE,
    ROCK,
    BUILDING
}

data class ArenaProp(
    val id: String,
    val kind: PropKind,
    val x: Float,
    var y: Float,
    val z: Float,
    val width: Float,
    val height: Float,
    val depth: Float,
    val isSolid: Boolean,
    val isOneWayPlatform: Boolean = false,
    val colorR: Float,
    val colorG: Float,
    val colorB: Float,
    val accentR: Float = colorR,
    val accentG: Float = colorG,
    val accentB: Float = colorB,
    val baseY: Float = y,
    val moveAmplitudeY: Float = 0f,
    val moveSpeed: Float = 0f
) {
    val minX: Float get() = x - width * 0.5f
    val maxX: Float get() = x + width * 0.5f
    val minY: Float get() = y
    val maxY: Float get() = y + height
}

data class ArenaPickup(
    val id: String,
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val isHealthPack: Boolean,
    val isGrenadePack: Boolean = false,
    var active: Boolean = true,
    var respawnTimer: Float = 0f
)

enum class FlagStatus {
    AT_BASE,
    CARRIED,
    DROPPED
}

data class FlagState(
    val teamIndex: Int, // 0 = Blue, 1 = Red
    val baseX: Float,
    val baseY: Float,
    var x: Float = baseX,
    var y: Float = baseY,
    var status: FlagStatus = FlagStatus.AT_BASE,
    var carrierId: PlayerId? = null,
    var dropTimer: Float = 0f
)

data class ControlPointState(
    val x: Float,
    val y: Float,
    val radius: Float = 3.8f,
    var ownerTeam: Int = -1, // -1 neutral, 0 blue, 1 red
    var captureProgress: Float = 0f, // -1 (Red) .. 0 (Neutral) .. +1 (Blue)
    var contested: Boolean = false
)

data class KillFeedEntry(
    val id: Long,
    val killerName: String,
    val victimName: String,
    val weaponShort: String,
    val timestampSec: Float
)

enum class ScreenMode {
    LOADING,
    MENU,
    OFFLINE_SETUP,
    MULTIPLAYER_LOBBY,
    CHARACTER_CUSTOMIZE,
    ARMORY,
    SETTINGS,
    PLAYING,
    PAUSED,
    MATCH_OVER
}

data class MinimapDot(
    val id: PlayerId,
    val nx: Float,
    val ny: Float,
    val isLocal: Boolean,
    val isAlive: Boolean,
    val teamIndex: Int
)

data class HudPlayerSummary(
    val id: PlayerId,
    val callsign: String,
    val health: Float,
    val maxHealth: Float,
    val isAlive: Boolean,
    val kills: Int,
    val deaths: Int,
    val score: Int,
    val isBot: Boolean,
    val teamIndex: Int,
    val worldX: Float,
    val worldY: Float
)

data class HudSnapshot(
    val screenMode: ScreenMode = ScreenMode.LOADING,
    val gameMode: GameMode = GameMode.DEATHMATCH,
    val mapId: MapId = MapId.MILITARY_BASE,
    val localHealth: Float = 100f,
    val localMaxHealth: Float = 100f,
    val localJetpackFuel: Float = 100f,
    val localGrenades: Int = 3,
    val localIsCrouching: Boolean = false,
    val localIsAlive: Boolean = true,
    val localTeam: Int = 0,
    val respawnSecondsLeft: Float = 0f,
    val localKills: Int = 0,
    val localDeaths: Int = 0,
    val currentWeapon: WeaponType = WeaponType.ASSAULT_RIFLE,
    val ammoInMag: Int = 30,
    val reserveAmmo: Int = 180,
    val isReloading: Boolean = false,
    val reloadProgress: Float = 0f,
    val matchSecondsLeft: Float = 180f,
    val isUnlimitedTime: Boolean = false,
    val blueTeamScore: Int = 0,
    val redTeamScore: Int = 0,
    val blueFlagStatus: FlagStatus = FlagStatus.AT_BASE,
    val redFlagStatus: FlagStatus = FlagStatus.AT_BASE,
    val blueFlagCarrier: String? = null,
    val redFlagCarrier: String? = null,
    val controlPointOwner: Int = -1,
    val controlPointProgress: Float = 0f,
    val controlPointContested: Boolean = false,
    val hitMarkerAlpha: Float = 0f,
    val damageVignetteAlpha: Float = 0f,
    val fps: Int = 60,
    val showMinimap: Boolean = true,
    val minimapDots: List<MinimapDot> = emptyList(),
    val players: List<HudPlayerSummary> = emptyList(),
    val killFeed: List<KillFeedEntry> = emptyList(),
    val winnerLabel: String = "",
    val mvpLabel: String = "",
    val lastEarnedCoins: Int = 0,
    val lastEarnedXp: Int = 0
)
