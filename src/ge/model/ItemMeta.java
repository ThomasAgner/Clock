package ge.model;

/**
 * Static metadata about a tradeable Grand Exchange item.
 *
 * @param id        Grand Exchange item id
 * @param name      display name
 * @param game      which market the item belongs to
 * @param buyLimit  4-hour GE buy limit (0 when unknown; RS3 limits are not in the feed)
 * @param value     in-game store value / alch base (informational)
 * @param highAlch  high alchemy value (OSRS; 0 when unknown)
 * @param members   whether the item is members-only
 */
public record ItemMeta(
        int id,
        String name,
        Game game,
        int buyLimit,
        long value,
        int highAlch,
        boolean members) {
}
