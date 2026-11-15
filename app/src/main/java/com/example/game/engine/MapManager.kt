package com.example.game.engine

import com.example.game.model.ArenaPickup
import com.example.game.model.ArenaProp
import com.example.game.model.MapId
import com.example.game.model.PropKind
import kotlin.math.sin

data class SpawnPoint(
    val id: String,
    val x: Float,
    val y: Float,
    val facingDir: Float,
    val teamHint: Int = 0
)

data class MountainPeak(
    val x: Float,
    val baseY: Float,
    val z: Float,
    val width: Float,
    val height: Float,
    val depth: Float,
    val r: Float,
    val g: Float,
    val b: Float
)

data class MapThemeColors(
    val skyTopR: Float, val skyTopG: Float, val skyTopB: Float,
    val skyMidR: Float, val skyMidG: Float, val skyMidB: Float,
    val skyBotR: Float, val skyBotG: Float, val skyBotB: Float,
    val fogR: Float, val fogG: Float, val fogB: Float
)

/**
 * Manages all 6 playable 3D arenas (Military Base, Desert War, Jungle Arena, Factory,
 * Snow Mountain, City Ruins), animated elevators, 12 spawn points, CTF bases, and control zones.
 */
class MapManager(initialMap: MapId = MapId.MILITARY_BASE) {

    var currentMapId: MapId = initialMap
        private set

    val minX = -38f
    val maxX = 38f
    val minY = 0f
    val maxY = 24f

    var props: List<ArenaProp> = emptyList()
        private set
    var solidColliders: List<ArenaProp> = emptyList()
        private set
    var spawnPoints: List<SpawnPoint> = emptyList()
        private set
    var initialPickups: List<ArenaPickup> = emptyList()
        private set
    var mountains: List<MountainPeak> = emptyList()
        private set
    var themeColors: MapThemeColors = themeFor(initialMap)
        private set
    var blueFlagBase: Pair<Float, Float> = -31f to 0.1f
        private set
    var redFlagBase: Pair<Float, Float> = 31f to 0.1f
        private set
    var controlZonePos: Pair<Float, Float> = 0f to 5.9f
        private set

    private var elevatorClock = 0f

    init {
        loadMap(initialMap)
    }

    fun loadMap(mapId: MapId) {
        currentMapId = mapId
        elevatorClock = 0f
        themeColors = themeFor(mapId)
        props = buildPropsFor(mapId)
        solidColliders = props.filter { it.isSolid }
        spawnPoints = buildSpawnsFor(mapId)
        initialPickups = buildPickupsFor(mapId)
        mountains = buildMountainsFor(mapId)
        when (mapId) {
            MapId.MILITARY_BASE -> { blueFlagBase = -32f to 0.1f; redFlagBase = 32f to 0.1f; controlZonePos = -1.5f to 5.95f }
            MapId.DESERT_WAR -> { blueFlagBase = -32f to 0.1f; redFlagBase = 32f to 0.1f; controlZonePos = 0f to 4.9f }
            MapId.JUNGLE_ARENA -> { blueFlagBase = -31.5f to 0.1f; redFlagBase = 31.5f to 0.1f; controlZonePos = 0f to 6.8f }
            MapId.FACTORY -> { blueFlagBase = -32.5f to 0.1f; redFlagBase = 32.5f to 0.1f; controlZonePos = 0f to 6.2f }
            MapId.SNOW_MOUNTAIN -> { blueFlagBase = -31.5f to 0.1f; redFlagBase = 31.5f to 0.1f; controlZonePos = 0f to 6.0f }
            MapId.CITY_RUINS -> { blueFlagBase = -32f to 0.1f; redFlagBase = 32f to 0.1f; controlZonePos = 0f to 5.6f }
        }
    }

    fun stepAnimatedProps(dt: Float) {
        elevatorClock += dt
        for (p in props) {
            if (p.moveAmplitudeY > 0f) {
                p.y = p.baseY + sin(elevatorClock * p.moveSpeed) * p.moveAmplitudeY
            }
        }
    }

    fun getShadowGroundY(x: Float, playerBottomY: Float): Float {
        var bestY = 0f
        for (prop in solidColliders) {
            if (x >= prop.minX && x <= prop.maxX && prop.maxY <= playerBottomY + 0.18f) {
                if (prop.maxY > bestY) bestY = prop.maxY
            }
        }
        return bestY
    }

    private fun themeFor(mapId: MapId): MapThemeColors = when (mapId) {
        MapId.MILITARY_BASE -> MapThemeColors(0.08f, 0.14f, 0.24f, 0.20f, 0.30f, 0.44f, 0.48f, 0.38f, 0.34f, 0.24f, 0.31f, 0.42f)
        MapId.DESERT_WAR -> MapThemeColors(0.18f, 0.36f, 0.62f, 0.56f, 0.66f, 0.72f, 0.92f, 0.68f, 0.36f, 0.68f, 0.58f, 0.42f)
        MapId.JUNGLE_ARENA -> MapThemeColors(0.07f, 0.24f, 0.26f, 0.18f, 0.44f, 0.36f, 0.42f, 0.64f, 0.44f, 0.22f, 0.42f, 0.34f)
        MapId.FACTORY -> MapThemeColors(0.07f, 0.08f, 0.12f, 0.18f, 0.16f, 0.20f, 0.44f, 0.24f, 0.14f, 0.22f, 0.18f, 0.16f)
        MapId.SNOW_MOUNTAIN -> MapThemeColors(0.14f, 0.28f, 0.48f, 0.44f, 0.64f, 0.82f, 0.82f, 0.90f, 0.96f, 0.64f, 0.76f, 0.88f)
        MapId.CITY_RUINS -> MapThemeColors(0.10f, 0.10f, 0.18f, 0.26f, 0.22f, 0.30f, 0.52f, 0.34f, 0.28f, 0.30f, 0.26f, 0.32f)
    }

    private fun baseArenaFrame(groundR: Float, groundG: Float, groundB: Float, stripR: Float, stripG: Float, stripB: Float): MutableList<ArenaProp> = mutableListOf(
        ArenaProp("ground_main", PropKind.GROUND, 0f, -2.2f, 0f, 84f, 2.2f, 11f, true, false, groundR, groundG, groundB, stripR, stripG, stripB),
        ArenaProp("wall_left_boundary", PropKind.CONCRETE_WALL, -39.2f, 0f, 0f, 2.4f, 18f, 7f, true, false, 0.40f, 0.43f, 0.47f, stripR, stripG, stripB),
        ArenaProp("wall_right_boundary", PropKind.CONCRETE_WALL, 39.2f, 0f, 0f, 2.4f, 18f, 7f, true, false, 0.40f, 0.43f, 0.47f, stripR, stripG, stripB)
    )

    private fun buildPropsFor(mapId: MapId): List<ArenaProp> = when (mapId) {
        MapId.MILITARY_BASE -> baseArenaFrame(0.22f, 0.25f, 0.29f, 0.92f, 0.65f, 0.12f).apply {
            add(ArenaProp("c_olive", PropKind.CONTAINER, -24.5f, 0f, 0f, 6.4f, 2.9f, 3.2f, true, false, 0.25f, 0.36f, 0.24f, 0.18f, 0.26f, 0.17f))
            add(ArenaProp("c_rust", PropKind.CONTAINER, -9.5f, 0f, 0f, 5.6f, 2.8f, 3.2f, true, false, 0.62f, 0.26f, 0.19f, 0.45f, 0.18f, 0.13f))
            add(ArenaProp("c_navy", PropKind.CONTAINER, 22.0f, 0f, 0f, 6.4f, 2.9f, 3.2f, true, false, 0.19f, 0.32f, 0.48f, 0.13f, 0.22f, 0.35f))
            add(ArenaProp("c_amber", PropKind.CONTAINER, 23.2f, 2.9f, 0f, 4.6f, 2.6f, 3.0f, true, false, 0.72f, 0.46f, 0.15f, 0.54f, 0.33f, 0.10f))
            add(ArenaProp("bar_1", PropKind.BARRIER, -16.5f, 0f, 0f, 1.6f, 0.95f, 2.8f, true, false, 0.56f, 0.58f, 0.60f, 0.95f, 0.70f, 0.15f))
            add(ArenaProp("bar_2", PropKind.BARRIER, -2.8f, 0f, 0f, 1.5f, 0.95f, 2.8f, true, false, 0.56f, 0.58f, 0.60f, 0.95f, 0.70f, 0.15f))
            add(ArenaProp("bar_3", PropKind.BARRIER, 9.0f, 0f, 0f, 1.5f, 0.95f, 2.8f, true, false, 0.56f, 0.58f, 0.60f, 0.95f, 0.70f, 0.15f))
            add(ArenaProp("bunker", PropKind.CONCRETE_WALL, 3.2f, 0f, 0f, 1.8f, 3.4f, 3.1f, true, false, 0.48f, 0.51f, 0.54f, 0.25f, 0.28f, 0.32f))
            add(ArenaProp("cr_1", PropKind.CRATE, -31.5f, 0f, 0f, 1.6f, 1.6f, 2.2f, true, false, 0.65f, 0.45f, 0.24f, 0.42f, 0.28f, 0.13f))
            add(ArenaProp("cr_2", PropKind.CRATE, 14.5f, 0f, 0f, 1.7f, 1.7f, 2.2f, true, false, 0.65f, 0.45f, 0.24f, 0.42f, 0.28f, 0.13f))
            add(ArenaProp("p_w_lo", PropKind.PLATFORM, -30.5f, 4.6f, 0f, 7.2f, 0.48f, 3.2f, true, true, 0.32f, 0.38f, 0.45f, 0.95f, 0.68f, 0.14f))
            add(ArenaProp("p_w_hi", PropKind.PLATFORM, -19.5f, 6.2f, 0f, 8.0f, 0.48f, 3.2f, true, true, 0.32f, 0.38f, 0.45f, 0.22f, 0.72f, 0.92f))
            add(ArenaProp("p_mid", PropKind.PLATFORM, -1.5f, 5.4f, 0f, 10.5f, 0.52f, 3.3f, true, true, 0.35f, 0.40f, 0.46f, 0.95f, 0.68f, 0.14f))
            add(ArenaProp("p_top", PropKind.PLATFORM, 0.5f, 9.6f, 0f, 7.6f, 0.48f, 3.0f, true, true, 0.30f, 0.36f, 0.44f, 0.22f, 0.72f, 0.92f))
            add(ArenaProp("p_e_mid", PropKind.PLATFORM, 13.0f, 5.2f, 0f, 8.4f, 0.48f, 3.2f, true, true, 0.32f, 0.38f, 0.45f, 0.95f, 0.68f, 0.14f))
            add(ArenaProp("p_e_hi", PropKind.PLATFORM, 29.8f, 7.6f, 0f, 7.5f, 0.48f, 3.2f, true, true, 0.32f, 0.38f, 0.45f, 0.22f, 0.72f, 0.92f))
            add(ArenaProp("d_radar", PropKind.TOWER, -14.0f, 0f, -5.6f, 3.4f, 14.5f, 3.4f, false, false, 0.27f, 0.31f, 0.36f, 0.92f, 0.34f, 0.22f))
            add(ArenaProp("d_silo1", PropKind.SILO, 7.5f, 0f, -6.2f, 4.2f, 11.2f, 4.2f, false, false, 0.36f, 0.40f, 0.44f, 0.92f, 0.64f, 0.16f))
            add(ArenaProp("d_lamp1", PropKind.LAMP_POST, -27.5f, 0f, -4.2f, 0.5f, 10.5f, 0.5f, false, false, 0.26f, 0.29f, 0.33f, 1f, 0.92f, 0.65f))
            add(ArenaProp("d_lamp2", PropKind.LAMP_POST, 26.5f, 0f, -4.2f, 0.5f, 10.5f, 0.5f, false, false, 0.26f, 0.29f, 0.33f, 1f, 0.92f, 0.65f))
        }

        MapId.DESERT_WAR -> baseArenaFrame(0.76f, 0.60f, 0.36f, 0.56f, 0.42f, 0.22f).apply {
            add(ArenaProp("d_rock1", PropKind.ROCK, -22f, 0f, 0f, 5.4f, 2.5f, 3.4f, true, false, 0.68f, 0.46f, 0.26f, 0.54f, 0.35f, 0.18f))
            add(ArenaProp("d_truck1", PropKind.VEHICLE, -9.5f, 0f, 0f, 5.8f, 2.4f, 3.0f, true, false, 0.62f, 0.54f, 0.36f, 0.25f, 0.25f, 0.22f))
            add(ArenaProp("d_wall", PropKind.CONCRETE_WALL, 0f, 0f, 0f, 3.2f, 2.4f, 3.0f, true, false, 0.72f, 0.58f, 0.38f, 0.52f, 0.40f, 0.25f))
            add(ArenaProp("d_truck2", PropKind.VEHICLE, 13.5f, 0f, 0f, 5.8f, 2.4f, 3.0f, true, false, 0.46f, 0.44f, 0.32f, 0.22f, 0.22f, 0.20f))
            add(ArenaProp("d_rock2", PropKind.ROCK, 24.5f, 0f, 0f, 6.2f, 3.2f, 3.4f, true, false, 0.66f, 0.44f, 0.24f, 0.50f, 0.32f, 0.16f))
            add(ArenaProp("d_crate1", PropKind.CRATE, -16f, 0f, 0f, 1.8f, 1.6f, 2.2f, true, false, 0.65f, 0.45f, 0.24f, 0.42f, 0.28f, 0.13f))
            add(ArenaProp("d_crate2", PropKind.CRATE, 6.5f, 0f, 0f, 1.8f, 1.6f, 2.2f, true, false, 0.65f, 0.45f, 0.24f, 0.42f, 0.28f, 0.13f))
            add(ArenaProp("dp_1", PropKind.PLATFORM, -28f, 4.4f, 0f, 8.2f, 0.55f, 3.2f, true, true, 0.62f, 0.44f, 0.24f, 0.92f, 0.68f, 0.22f))
            add(ArenaProp("dp_2", PropKind.PLATFORM, -13f, 6.8f, 0f, 8.0f, 0.55f, 3.2f, true, true, 0.62f, 0.44f, 0.24f, 0.92f, 0.68f, 0.22f))
            add(ArenaProp("dp_mid", PropKind.PLATFORM, 0f, 4.4f, 0f, 9.5f, 0.55f, 3.2f, true, true, 0.58f, 0.42f, 0.24f, 0.95f, 0.72f, 0.24f))
            add(ArenaProp("dp_top", PropKind.PLATFORM, 0f, 9.2f, 0f, 7.2f, 0.50f, 3.0f, true, true, 0.62f, 0.44f, 0.24f, 0.95f, 0.72f, 0.24f))
            add(ArenaProp("dp_3", PropKind.PLATFORM, 15f, 6.5f, 0f, 8.2f, 0.55f, 3.2f, true, true, 0.62f, 0.44f, 0.24f, 0.92f, 0.68f, 0.22f))
            add(ArenaProp("dp_4", PropKind.PLATFORM, 28f, 5.2f, 0f, 7.6f, 0.55f, 3.2f, true, true, 0.62f, 0.44f, 0.24f, 0.92f, 0.68f, 0.22f))
            add(ArenaProp("d_radar", PropKind.TOWER, -6f, 0f, -6.5f, 3.0f, 12.5f, 3.0f, false, false, 0.55f, 0.46f, 0.34f, 0.95f, 0.65f, 0.18f))
            add(ArenaProp("d_bg_rock", PropKind.ROCK, 19f, 0f, -7.2f, 8.5f, 7.5f, 5.0f, false, false, 0.64f, 0.45f, 0.27f, 0.52f, 0.36f, 0.20f))
        }

        MapId.JUNGLE_ARENA -> baseArenaFrame(0.19f, 0.38f, 0.18f, 0.36f, 0.54f, 0.22f).apply {
            add(ArenaProp("j_ruin1", PropKind.CONCRETE_WALL, -18f, 0f, 0f, 2.4f, 3.0f, 3.0f, true, false, 0.40f, 0.44f, 0.38f, 0.24f, 0.48f, 0.22f))
            add(ArenaProp("j_ruin2", PropKind.CONCRETE_WALL, 18f, 0f, 0f, 2.4f, 3.0f, 3.0f, true, false, 0.40f, 0.44f, 0.38f, 0.24f, 0.48f, 0.22f))
            add(ArenaProp("j_crate1", PropKind.CRATE, -7f, 0f, 0f, 2.2f, 1.8f, 2.4f, true, false, 0.52f, 0.36f, 0.18f, 0.34f, 0.22f, 0.10f))
            add(ArenaProp("j_crate2", PropKind.CRATE, 7f, 0f, 0f, 2.2f, 1.8f, 2.4f, true, false, 0.52f, 0.36f, 0.18f, 0.34f, 0.22f, 0.10f))
            add(ArenaProp("j_bar", PropKind.BARRIER, 0f, 0f, 0f, 1.8f, 0.95f, 2.8f, true, false, 0.42f, 0.46f, 0.38f, 0.22f, 0.58f, 0.26f))
            add(ArenaProp("jp_1", PropKind.PLATFORM, -28f, 4.2f, 0f, 8.6f, 0.48f, 3.2f, true, true, 0.48f, 0.32f, 0.16f, 0.26f, 0.72f, 0.30f))
            add(ArenaProp("jp_2", PropKind.PLATFORM, -14f, 7.2f, 0f, 8.5f, 0.48f, 3.2f, true, true, 0.48f, 0.32f, 0.16f, 0.26f, 0.72f, 0.30f))
            add(ArenaProp("jp_mid", PropKind.PLATFORM, 0f, 6.2f, 0f, 11.0f, 0.50f, 3.4f, true, true, 0.48f, 0.32f, 0.16f, 0.28f, 0.78f, 0.32f))
            add(ArenaProp("jp_top", PropKind.PLATFORM, 0f, 10.6f, 0f, 7.6f, 0.48f, 3.0f, true, true, 0.48f, 0.32f, 0.16f, 0.28f, 0.78f, 0.32f))
            add(ArenaProp("jp_3", PropKind.PLATFORM, 14f, 7.2f, 0f, 8.5f, 0.48f, 3.2f, true, true, 0.48f, 0.32f, 0.16f, 0.26f, 0.72f, 0.30f))
            add(ArenaProp("jp_4", PropKind.PLATFORM, 28f, 4.2f, 0f, 8.6f, 0.48f, 3.2f, true, true, 0.48f, 0.32f, 0.16f, 0.26f, 0.72f, 0.30f))
            add(ArenaProp("j_tree1", PropKind.TREE, -28f, 0f, -4.8f, 2.2f, 11.5f, 2.2f, false, false, 0.38f, 0.25f, 0.14f, 0.16f, 0.52f, 0.20f))
            add(ArenaProp("j_tree2", PropKind.TREE, -11f, 0f, -5.5f, 2.5f, 13.5f, 2.5f, false, false, 0.38f, 0.25f, 0.14f, 0.14f, 0.46f, 0.18f))
            add(ArenaProp("j_tree3", PropKind.TREE, 11f, 0f, -5.5f, 2.5f, 13.5f, 2.5f, false, false, 0.38f, 0.25f, 0.14f, 0.14f, 0.46f, 0.18f))
            add(ArenaProp("j_tree4", PropKind.TREE, 28f, 0f, -4.8f, 2.2f, 11.5f, 2.2f, false, false, 0.38f, 0.25f, 0.14f, 0.16f, 0.52f, 0.20f))
        }

        MapId.FACTORY -> baseArenaFrame(0.18f, 0.20f, 0.24f, 0.96f, 0.54f, 0.12f).apply {
            add(ArenaProp("f_cont1", PropKind.CONTAINER, -23f, 0f, 0f, 6.2f, 2.9f, 3.2f, true, false, 0.34f, 0.28f, 0.24f, 0.92f, 0.50f, 0.12f))
            add(ArenaProp("f_cont2", PropKind.CONTAINER, 23f, 0f, 0f, 6.2f, 2.9f, 3.2f, true, false, 0.34f, 0.28f, 0.24f, 0.92f, 0.50f, 0.12f))
            add(ArenaProp("f_mach", PropKind.CONCRETE_WALL, 0f, 0f, 0f, 3.4f, 2.6f, 3.2f, true, false, 0.30f, 0.33f, 0.38f, 0.96f, 0.45f, 0.10f))
            add(ArenaProp("f_bar1", PropKind.BARRIER, -10f, 0f, 0f, 1.6f, 0.95f, 2.8f, true, false, 0.42f, 0.44f, 0.48f, 0.96f, 0.58f, 0.12f))
            add(ArenaProp("f_bar2", PropKind.BARRIER, 10f, 0f, 0f, 1.6f, 0.95f, 2.8f, true, false, 0.42f, 0.44f, 0.48f, 0.96f, 0.58f, 0.12f))
            // Animated moving factory elevator lifts!
            add(ArenaProp("f_elev_l", PropKind.ELEVATOR, -13.5f, 4.5f, 0f, 5.4f, 0.55f, 3.2f, true, true, 0.40f, 0.44f, 0.50f, 0.98f, 0.55f, 0.12f, baseY = 4.5f, moveAmplitudeY = 2.8f, moveSpeed = 1.4f))
            add(ArenaProp("f_elev_r", PropKind.ELEVATOR, 13.5f, 4.5f, 0f, 5.4f, 0.55f, 3.2f, true, true, 0.40f, 0.44f, 0.50f, 0.98f, 0.55f, 0.12f, baseY = 4.5f, moveAmplitudeY = 2.8f, moveSpeed = -1.4f))
            add(ArenaProp("fp_w", PropKind.PLATFORM, -29f, 5.6f, 0f, 7.6f, 0.48f, 3.2f, true, true, 0.28f, 0.32f, 0.38f, 0.96f, 0.52f, 0.12f))
            add(ArenaProp("fp_mid", PropKind.PLATFORM, 0f, 5.8f, 0f, 9.6f, 0.50f, 3.3f, true, true, 0.28f, 0.32f, 0.38f, 0.96f, 0.52f, 0.12f))
            add(ArenaProp("fp_top", PropKind.PLATFORM, 0f, 10.2f, 0f, 8.2f, 0.48f, 3.0f, true, true, 0.28f, 0.32f, 0.38f, 0.25f, 0.78f, 0.95f))
            add(ArenaProp("fp_e", PropKind.PLATFORM, 29f, 5.6f, 0f, 7.6f, 0.48f, 3.2f, true, true, 0.28f, 0.32f, 0.38f, 0.96f, 0.52f, 0.12f))
            add(ArenaProp("f_silo1", PropKind.SILO, -19f, 0f, -5.8f, 4.4f, 12.5f, 4.4f, false, false, 0.28f, 0.30f, 0.34f, 0.98f, 0.42f, 0.10f))
            add(ArenaProp("f_silo2", PropKind.SILO, 19f, 0f, -5.8f, 4.4f, 12.5f, 4.4f, false, false, 0.28f, 0.30f, 0.34f, 0.98f, 0.42f, 0.10f))
            add(ArenaProp("f_lamp", PropKind.LAMP_POST, 0f, 0f, -4.6f, 0.6f, 13.0f, 0.6f, false, false, 0.24f, 0.26f, 0.30f, 1f, 0.65f, 0.22f))
        }

        MapId.SNOW_MOUNTAIN -> baseArenaFrame(0.84f, 0.90f, 0.95f, 0.42f, 0.74f, 0.94f).apply {
            add(ArenaProp("s_rock1", PropKind.ROCK, -23f, 0f, 0f, 5.6f, 2.7f, 3.2f, true, false, 0.48f, 0.58f, 0.68f, 0.88f, 0.94f, 0.98f))
            add(ArenaProp("s_rock2", PropKind.ROCK, 23f, 0f, 0f, 5.6f, 2.7f, 3.2f, true, false, 0.48f, 0.58f, 0.68f, 0.88f, 0.94f, 0.98f))
            add(ArenaProp("s_bar1", PropKind.BARRIER, -9f, 0f, 0f, 1.8f, 0.95f, 2.8f, true, false, 0.54f, 0.76f, 0.92f, 0.88f, 0.95f, 1f))
            add(ArenaProp("s_bar2", PropKind.BARRIER, 9f, 0f, 0f, 1.8f, 0.95f, 2.8f, true, false, 0.54f, 0.76f, 0.92f, 0.88f, 0.95f, 1f))
            add(ArenaProp("s_crate", PropKind.CRATE, 0f, 0f, 0f, 2.2f, 1.9f, 2.4f, true, false, 0.46f, 0.56f, 0.66f, 0.28f, 0.72f, 0.94f))
            add(ArenaProp("sp_1", PropKind.PLATFORM, -28f, 4.6f, 0f, 8.0f, 0.52f, 3.2f, true, true, 0.62f, 0.84f, 0.96f, 0.92f, 0.97f, 1f))
            add(ArenaProp("sp_2", PropKind.PLATFORM, -13f, 7.6f, 0f, 7.8f, 0.52f, 3.2f, true, true, 0.62f, 0.84f, 0.96f, 0.92f, 0.97f, 1f))
            add(ArenaProp("sp_mid", PropKind.PLATFORM, 0f, 5.6f, 0f, 10.2f, 0.54f, 3.3f, true, true, 0.56f, 0.80f, 0.95f, 0.92f, 0.97f, 1f))
            add(ArenaProp("sp_top", PropKind.PLATFORM, 0f, 10.2f, 0f, 7.4f, 0.50f, 3.0f, true, true, 0.56f, 0.80f, 0.95f, 0.92f, 0.97f, 1f))
            add(ArenaProp("sp_3", PropKind.PLATFORM, 13f, 7.6f, 0f, 7.8f, 0.52f, 3.2f, true, true, 0.62f, 0.84f, 0.96f, 0.92f, 0.97f, 1f))
            add(ArenaProp("sp_4", PropKind.PLATFORM, 28f, 4.6f, 0f, 8.0f, 0.52f, 3.2f, true, true, 0.62f, 0.84f, 0.96f, 0.92f, 0.97f, 1f))
            add(ArenaProp("s_pine1", PropKind.TREE, -17f, 0f, -5.2f, 1.4f, 9.5f, 1.4f, false, false, 0.36f, 0.24f, 0.16f, 0.24f, 0.46f, 0.42f))
            add(ArenaProp("s_pine2", PropKind.TREE, 17f, 0f, -5.2f, 1.4f, 9.5f, 1.4f, false, false, 0.36f, 0.24f, 0.16f, 0.24f, 0.46f, 0.42f))
            add(ArenaProp("s_tower", PropKind.TOWER, 0f, 0f, -6.5f, 3.2f, 13.5f, 3.2f, false, false, 0.44f, 0.52f, 0.60f, 0.25f, 0.78f, 0.96f))
        }

        MapId.CITY_RUINS -> baseArenaFrame(0.20f, 0.22f, 0.26f, 0.72f, 0.72f, 0.76f).apply {
            add(ArenaProp("r_wall1", PropKind.CONCRETE_WALL, -22f, 0f, 0f, 4.2f, 3.2f, 3.2f, true, false, 0.42f, 0.40f, 0.44f, 0.28f, 0.28f, 0.32f))
            add(ArenaProp("r_wall2", PropKind.CONCRETE_WALL, 22f, 0f, 0f, 4.2f, 3.2f, 3.2f, true, false, 0.42f, 0.40f, 0.44f, 0.28f, 0.28f, 0.32f))
            add(ArenaProp("r_car1", PropKind.VEHICLE, -10.5f, 0f, 0f, 4.8f, 1.9f, 2.8f, true, false, 0.48f, 0.24f, 0.24f, 0.18f, 0.18f, 0.20f))
            add(ArenaProp("r_bar1", PropKind.BARRIER, -2.5f, 0f, 0f, 1.6f, 0.95f, 2.8f, true, false, 0.52f, 0.52f, 0.56f, 0.95f, 0.72f, 0.18f))
            add(ArenaProp("r_bar2", PropKind.BARRIER, 7.5f, 0f, 0f, 1.6f, 0.95f, 2.8f, true, false, 0.52f, 0.52f, 0.56f, 0.95f, 0.72f, 0.18f))
            add(ArenaProp("rp_1", PropKind.PLATFORM, -28f, 5.2f, 0f, 8.2f, 0.54f, 3.2f, true, true, 0.36f, 0.36f, 0.40f, 0.65f, 0.52f, 0.92f))
            add(ArenaProp("rp_2", PropKind.PLATFORM, -13f, 7.8f, 0f, 7.6f, 0.54f, 3.2f, true, true, 0.36f, 0.36f, 0.40f, 0.65f, 0.52f, 0.92f))
            add(ArenaProp("rp_mid", PropKind.PLATFORM, 0f, 5.1f, 0f, 10.8f, 0.54f, 3.3f, true, true, 0.36f, 0.36f, 0.40f, 0.95f, 0.65f, 0.18f))
            add(ArenaProp("rp_top", PropKind.PLATFORM, 0f, 9.6f, 0f, 7.6f, 0.50f, 3.0f, true, true, 0.36f, 0.36f, 0.40f, 0.65f, 0.52f, 0.92f))
            add(ArenaProp("rp_3", PropKind.PLATFORM, 13f, 7.8f, 0f, 7.6f, 0.54f, 3.2f, true, true, 0.36f, 0.36f, 0.40f, 0.65f, 0.52f, 0.92f))
            add(ArenaProp("rp_4", PropKind.PLATFORM, 28f, 5.2f, 0f, 8.2f, 0.54f, 3.2f, true, true, 0.36f, 0.36f, 0.40f, 0.65f, 0.52f, 0.92f))
            add(ArenaProp("r_bld1", PropKind.BUILDING, -24f, 0f, -6.5f, 9.5f, 15.0f, 5.0f, false, false, 0.24f, 0.24f, 0.30f, 0.95f, 0.72f, 0.28f))
            add(ArenaProp("r_bld2", PropKind.BUILDING, 0f, 0f, -7.2f, 11.0f, 17.5f, 5.0f, false, false, 0.20f, 0.22f, 0.28f, 0.40f, 0.78f, 0.96f))
            add(ArenaProp("r_bld3", PropKind.BUILDING, 24f, 0f, -6.5f, 9.5f, 14.0f, 5.0f, false, false, 0.26f, 0.25f, 0.31f, 0.95f, 0.72f, 0.28f))
            add(ArenaProp("r_lamp1", PropKind.LAMP_POST, -14f, 0f, -4.0f, 0.45f, 9.5f, 0.45f, false, false, 0.24f, 0.26f, 0.30f, 1f, 0.85f, 0.55f))
            add(ArenaProp("r_lamp2", PropKind.LAMP_POST, 14f, 0f, -4.0f, 0.45f, 9.5f, 0.45f, false, false, 0.24f, 0.26f, 0.30f, 1f, 0.85f, 0.55f))
        }
    }

    private fun buildSpawnsFor(mapId: MapId): List<SpawnPoint> = listOf(
        SpawnPoint("sp_01", -34.0f, 0.2f, 1f, 0),
        SpawnPoint("sp_02", -29.0f, 6.2f, 1f, 0),
        SpawnPoint("sp_03", -23.5f, 3.4f, 1f, 0),
        SpawnPoint("sp_04", -14.0f, 8.4f, 1f, 0),
        SpawnPoint("sp_05", -7.5f, 0.2f, 1f, 0),
        SpawnPoint("sp_06", -1.5f, 6.5f, 1f, 0),
        SpawnPoint("sp_07", 0.5f, 11.2f, -1f, 1),
        SpawnPoint("sp_08", 7.5f, 0.2f, -1f, 1),
        SpawnPoint("sp_09", 14.0f, 8.4f, -1f, 1),
        SpawnPoint("sp_10", 23.5f, 3.4f, -1f, 1),
        SpawnPoint("sp_11", 29.0f, 6.2f, -1f, 1),
        SpawnPoint("sp_12", 34.0f, 0.2f, -1f, 1)
    )

    private fun buildPickupsFor(mapId: MapId): List<ArenaPickup> = listOf(
        ArenaPickup("med_west", -19.5f, 8.4f, isHealthPack = true),
        ArenaPickup("med_east", 28.5f, 8.4f, isHealthPack = true),
        ArenaPickup("ammo_top", 0.2f, 11.2f, isHealthPack = false),
        ArenaPickup("gren_mid", -4.5f, 0.5f, isHealthPack = false, isGrenadePack = true)
    )

    private fun buildMountainsFor(mapId: MapId): List<MountainPeak> {
        val (r1, g1, b1, r2, g2, b2) = when (mapId) {
            MapId.MILITARY_BASE -> floatArrayOf(0.15f, 0.21f, 0.31f, 0.21f, 0.28f, 0.37f)
            MapId.DESERT_WAR -> floatArrayOf(0.56f, 0.38f, 0.22f, 0.66f, 0.46f, 0.28f)
            MapId.JUNGLE_ARENA -> floatArrayOf(0.12f, 0.28f, 0.20f, 0.16f, 0.36f, 0.24f)
            MapId.FACTORY -> floatArrayOf(0.12f, 0.13f, 0.17f, 0.17f, 0.18f, 0.22f)
            MapId.SNOW_MOUNTAIN -> floatArrayOf(0.46f, 0.60f, 0.74f, 0.76f, 0.86f, 0.94f)
            MapId.CITY_RUINS -> floatArrayOf(0.16f, 0.16f, 0.24f, 0.22f, 0.22f, 0.30f)
        }
        return listOf(
            MountainPeak(-46f, -3f, -24f, 34f, 26f, 14f, r1, g1, b1),
            MountainPeak(-18f, -3f, -28f, 42f, 33f, 16f, r1, g1, b1),
            MountainPeak(14f, -3f, -26f, 40f, 30f, 15f, r1, g1, b1),
            MountainPeak(44f, -3f, -24f, 36f, 27f, 14f, r1, g1, b1),
            MountainPeak(-32f, -3f, -17f, 24f, 16f, 10f, r2, g2, b2),
            MountainPeak(0f, -3f, -18f, 28f, 19f, 11f, r2, g2, b2),
            MountainPeak(30f, -3f, -17f, 25f, 17f, 10f, r2, g2, b2)
        )
    }

    private operator fun FloatArray.component6(): Float = this[5]
}
