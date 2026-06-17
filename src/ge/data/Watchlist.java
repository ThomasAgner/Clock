package ge.data;

import java.util.List;

/**
 * Default RuneScape 3 watchlist.
 *
 * <p>RS3 has no cheap whole-market volume feed, so scans run over a curated set
 * of liquid, popular items instead. Entries are <em>names</em> (resolved to ids
 * at runtime) so the list never goes stale, and callers may override it with
 * {@code --names a,b,c} or {@code --ids 1,2,3} on the command line.
 */
public final class Watchlist {

    private Watchlist() {
    }

    public static final List<String> RS3_DEFAULT = List.of(
            // Skilling: logs, ores, planks, essence
            "Cannonball", "Magic logs", "Yew logs", "Maple logs", "Willow logs",
            "Elder logs", "Acadia logs", "Teak plank", "Mahogany plank",
            "Pure essence", "Coal", "Runite ore", "Adamantite ore", "Mithril ore",
            "Gold ore", "Luminite", "Light animica", "Soft clay", "Flax",
            // Skilling: bones, fish, secondaries
            "Dragon bones", "Frost dragon bones", "Raw shark", "Raw rocktail",
            "Shark", "Rocktail", "Cavefish", "Sailfish", "Feather", "Crushed nest",
            // Runes
            "Death rune", "Blood rune", "Nature rune", "Law rune", "Soul rune",
            "Air rune", "Water rune", "Fire rune", "Earth rune",
            // Potions
            "Saradomin brew (4)", "Super restore (4)", "Prayer potion (4)",
            "Prayer renewal (4)", "Super prayer (4)", "Super antifire (4)",
            "Super strength (4)", "Super attack (4)", "Super defence (4)",
            "Magic potion (4)", "Ranging potion (4)",
            "Extreme attack (4)", "Extreme strength (4)",
            "Extreme magic (4)", "Extreme ranging (4)",
            // Herbs & seeds
            "Ranarr seed", "Torstol seed", "Snapdragon seed",
            "Grimy ranarr", "Grimy torstol", "Grimy snapdragon",
            // Gear, gems & ammo
            "Abyssal whip", "Dragon scimitar", "Dragon dagger", "Rune platebody",
            "Magic shortbow", "Battlestaff", "Uncut dragonstone", "Dragonstone",
            "Onyx", "Uncut onyx", "Diamond bolts (e)", "Ruby bolts (e)",
            "Onyx bolts", "Rune arrow", "Dragon arrow",
            // Utility
            "Divine charge", "Crystal key", "Vial of water");
}
