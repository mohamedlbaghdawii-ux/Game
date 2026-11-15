package com.example.game.data

import com.example.game.model.BackpackStyle
import com.example.game.model.CharacterAppearance
import com.example.game.model.HeadgearStyle
import com.example.game.model.WeaponType

enum class ArmoryCategory(val label: String) {
    CHARACTERS("CHARACTERS"),
    CLOTHING("CLOTHING"),
    HEADGEAR("HEADGEAR"),
    WEAPONS("WEAPONS")
}

data class ArmoryItem(
    val id: String,
    val category: ArmoryCategory,
    val name: String,
    val subtitle: String,
    val price: Int,
    val shirtHex: Long? = null,
    val pantsHex: Long? = null,
    val bootsHex: Long? = null,
    val visorHex: Long? = null,
    val backpackHex: Long? = null,
    val headgearStyle: HeadgearStyle? = null,
    val backpackStyle: BackpackStyle? = null,
    val weaponType: WeaponType? = null
)

/**
 * Full Armory & Character customization catalog with free starter gear + coin-unlockable upgrades.
 */
object ArmoryCatalog {

    val items: List<ArmoryItem> = listOf(
        // === CHARACTERS ===
        ArmoryItem(
            id = "char_striker",
            category = ArmoryCategory.CHARACTERS,
            name = "STRIKER-01",
            subtitle = "Standard Tactical Commando",
            price = 0,
            shirtHex = 0xFF3A523E,
            pantsHex = 0xFF2B3530,
            bootsHex = 0xFF1E2024,
            visorHex = 0xFF38BDF8,
            backpackHex = 0xFF47553E,
            headgearStyle = HeadgearStyle.M1_HELMET,
            backpackStyle = BackpackStyle.JET_THRUSTER
        ),
        ArmoryItem(
            id = "char_ghost",
            category = ArmoryCategory.CHARACTERS,
            name = "SHADOW PHANTOM",
            subtitle = "Stealth Night Ops Specialist",
            price = 250,
            shirtHex = 0xFF1E242D,
            pantsHex = 0xFF14181F,
            bootsHex = 0xFF0E1015,
            visorHex = 0xFF22C55E,
            backpackHex = 0xFF262D38,
            headgearStyle = HeadgearStyle.SPEC_OPS_MASK,
            backpackStyle = BackpackStyle.RADIO_PACK
        ),
        ArmoryItem(
            id = "char_viper",
            category = ArmoryCategory.CHARACTERS,
            name = "CRIMSON VIPER",
            subtitle = "Assault Shock Trooper",
            price = 380,
            shirtHex = 0xFF7F1D1D,
            pantsHex = 0xFF3F1D1D,
            bootsHex = 0xFF1E1B1B,
            visorHex = 0xFFFF9F1C,
            backpackHex = 0xFF5C1E1E,
            headgearStyle = HeadgearStyle.TACTICAL_VISOR,
            backpackStyle = BackpackStyle.JET_THRUSTER
        ),
        ArmoryItem(
            id = "char_titan",
            category = ArmoryCategory.CHARACTERS,
            name = "APEX JUGGERNAUT",
            subtitle = "Heavy Armored Breacher",
            price = 600,
            shirtHex = 0xFF334155,
            pantsHex = 0xFF1E293B,
            bootsHex = 0xFF0F172A,
            visorHex = 0xFFFACC15,
            backpackHex = 0xFF475569,
            headgearStyle = HeadgearStyle.HEAVY_JUGGERNAUT,
            backpackStyle = BackpackStyle.MUNITIONS_PACK
        ),

        // === CLOTHING (UNIFORMS) ===
        ArmoryItem(
            id = "cloth_military",
            category = ArmoryCategory.CLOTHING,
            name = "OLIVE MILITARY",
            subtitle = "Classic Garrison Combat Fatigues",
            price = 0,
            shirtHex = 0xFF3E563B,
            pantsHex = 0xFF2D3B2B,
            bootsHex = 0xFF1E211E
        ),
        ArmoryItem(
            id = "cloth_desert",
            category = ArmoryCategory.CLOTHING,
            name = "DESERT STORM",
            subtitle = "Arid Dune Tactical Uniform",
            price = 0,
            shirtHex = 0xFFA68A5B,
            pantsHex = 0xFF7C6542,
            bootsHex = 0xFF443624
        ),
        ArmoryItem(
            id = "cloth_forest",
            category = ArmoryCategory.CLOTHING,
            name = "FOREST CAMO",
            subtitle = "Deep Woodland Reconnaissance Gear",
            price = 150,
            shirtHex = 0xFF244C32,
            pantsHex = 0xFF1B3524,
            bootsHex = 0xFF1A201A
        ),
        ArmoryItem(
            id = "cloth_black",
            category = ArmoryCategory.CLOTHING,
            name = "BLACK TACTICAL",
            subtitle = "SWAT Urban Strike Suit",
            price = 220,
            shirtHex = 0xFF1C222A,
            pantsHex = 0xFF13171D,
            bootsHex = 0xFF0A0D10
        ),
        ArmoryItem(
            id = "cloth_blue",
            category = ArmoryCategory.CLOTHING,
            name = "COBALT BLUE",
            subtitle = "Blue Squad Airborne Rig",
            price = 180,
            shirtHex = 0xFF1D4ED8,
            pantsHex = 0xFF1E3A8A,
            bootsHex = 0xFF172554
        ),
        ArmoryItem(
            id = "cloth_red",
            category = ArmoryCategory.CLOTHING,
            name = "CRIMSON RED",
            subtitle = "Red Vanguard Combat Armor",
            price = 180,
            shirtHex = 0xFFB91C1C,
            pantsHex = 0xFF7F1D1D,
            bootsHex = 0xFF2A1215
        ),

        // === HEADGEAR ===
        ArmoryItem(
            id = "helm_m1",
            category = ArmoryCategory.HEADGEAR,
            name = "M1 STEEL HELMET",
            subtitle = "Standard Ballistic Helmet",
            price = 0,
            headgearStyle = HeadgearStyle.M1_HELMET
        ),
        ArmoryItem(
            id = "helm_visor",
            category = ArmoryCategory.HEADGEAR,
            name = "CYBER VISOR HELM",
            subtitle = "HUD-Equipped Tactical Visor",
            price = 0,
            headgearStyle = HeadgearStyle.TACTICAL_VISOR
        ),
        ArmoryItem(
            id = "helm_cap",
            category = ArmoryCategory.HEADGEAR,
            name = "RANGER PATROL CAP",
            subtitle = "Lightweight Field Cap",
            price = 120,
            headgearStyle = HeadgearStyle.DESERT_CAP
        ),
        ArmoryItem(
            id = "helm_mask",
            category = ArmoryCategory.HEADGEAR,
            name = "SPEC-OPS MASK",
            subtitle = "Dual-Lens Recon Respirator",
            price = 240,
            headgearStyle = HeadgearStyle.SPEC_OPS_MASK
        ),
        ArmoryItem(
            id = "helm_beret",
            category = ArmoryCategory.HEADGEAR,
            name = "COMMANDO BERET",
            subtitle = "Elite Airborne Crimson Beret",
            price = 280,
            headgearStyle = HeadgearStyle.COMMANDO_BERET
        ),
        ArmoryItem(
            id = "helm_heavy",
            category = ArmoryCategory.HEADGEAR,
            name = "TITAN HEAVY HELM",
            subtitle = "Reinforced Siege Faceplate",
            price = 420,
            headgearStyle = HeadgearStyle.HEAVY_JUGGERNAUT
        ),

        // === WEAPONS ===
        ArmoryItem(
            id = "wp_pistol",
            category = ArmoryCategory.WEAPONS,
            name = WeaponType.PISTOL.spec.displayName,
            subtitle = "Sidearm • Fast draw & accurate",
            price = 0,
            weaponType = WeaponType.PISTOL
        ),
        ArmoryItem(
            id = "wp_rifle",
            category = ArmoryCategory.WEAPONS,
            name = WeaponType.ASSAULT_RIFLE.spec.displayName,
            subtitle = "Assault Rifle • Balanced full-auto",
            price = 0,
            weaponType = WeaponType.ASSAULT_RIFLE
        ),
        ArmoryItem(
            id = "wp_shotgun",
            category = ArmoryCategory.WEAPONS,
            name = WeaponType.SHOTGUN.spec.displayName,
            subtitle = "Shotgun • 6-pellet close blast",
            price = 0,
            weaponType = WeaponType.SHOTGUN
        ),
        ArmoryItem(
            id = "wp_sniper",
            category = ArmoryCategory.WEAPONS,
            name = WeaponType.SNIPER_RIFLE.spec.displayName,
            subtitle = "Sniper Rifle • High-velocity precision",
            price = 0,
            weaponType = WeaponType.SNIPER_RIFLE
        ),
        ArmoryItem(
            id = "wp_lmg",
            category = ArmoryCategory.WEAPONS,
            name = WeaponType.MACHINE_GUN.spec.displayName,
            subtitle = "Machine Gun • 75-round belt suppressive fire",
            price = 320,
            weaponType = WeaponType.MACHINE_GUN
        ),
        ArmoryItem(
            id = "wp_rocket",
            category = ArmoryCategory.WEAPONS,
            name = WeaponType.ROCKET_LAUNCHER.spec.displayName,
            subtitle = "Rocket Launcher • Explosive AoE warhead",
            price = 520,
            weaponType = WeaponType.ROCKET_LAUNCHER
        )
    )

    fun applyItemToAppearance(current: CharacterAppearance, item: ArmoryItem): CharacterAppearance {
        return when (item.category) {
            ArmoryCategory.CHARACTERS -> current.copy(
                characterId = item.id,
                shirtHex = item.shirtHex ?: current.shirtHex,
                pantsHex = item.pantsHex ?: current.pantsHex,
                bootsHex = item.bootsHex ?: current.bootsHex,
                visorHex = item.visorHex ?: current.visorHex,
                backpackHex = item.backpackHex ?: current.backpackHex,
                headgearStyle = item.headgearStyle ?: current.headgearStyle,
                backpackStyle = item.backpackStyle ?: current.backpackStyle
            )
            ArmoryCategory.CLOTHING -> current.copy(
                clothingId = item.id,
                shirtHex = item.shirtHex ?: current.shirtHex,
                pantsHex = item.pantsHex ?: current.pantsHex,
                bootsHex = item.bootsHex ?: current.bootsHex
            )
            ArmoryCategory.HEADGEAR -> current.copy(
                headgearId = item.id,
                headgearStyle = item.headgearStyle ?: current.headgearStyle
            )
            ArmoryCategory.WEAPONS -> current
        }
    }
}
