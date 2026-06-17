package ge.model;

/** The two Grand Exchange markets this tool understands. */
public enum Game {
    OSRS("Old School RuneScape"),
    RS3("RuneScape 3");

    private final String displayName;

    Game(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Game parse(String raw) {
        return switch (raw.toLowerCase()) {
            case "osrs", "07", "oldschool", "old-school" -> OSRS;
            case "rs3", "rs", "runescape3", "rs3ge" -> RS3;
            default -> throw new IllegalArgumentException("Unknown game: " + raw + " (use 'osrs' or 'rs3')");
        };
    }
}
