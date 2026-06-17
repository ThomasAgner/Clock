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
            // Skilling staples
            "Cannonball", "Magic logs", "Yew logs", "Teak plank", "Mahogany plank",
            "Pure essence", "Coal", "Runite ore", "Adamantite ore", "Luminite",
            "Dragon bones", "Frost dragon bones", "Raw shark", "Raw rocktail",
            "Shark", "Rocktail", "Feather", "Crushed nest",
            // Runes
            "Death rune", "Blood rune", "Nature rune", "Law rune", "Soul rune",
            "Air rune", "Water rune", "Fire rune", "Earth rune",
            // Potions & herblore
            "Saradomin brew (4)", "Super restore (4)", "Prayer potion (4)",
            "Adrenaline potion (4)", "Ranarr seed", "Torstol seed",
            // Gear & gems
            "Abyssal whip", "Dragon scimitar", "Uncut dragonstone", "Onyx",
            "Dragonstone", "Battlestaff");
}
