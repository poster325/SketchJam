import java.awt.Color;

/**
 * Centralized color constants used throughout the application.
 */
public final class Colors {
    
    private Colors() {} // Prevent instantiation
    
    // ==================== Track Colors ====================
    
    /** Colors assigned to recording tracks */
    public static final Color[] TRACK_COLORS = {
        new Color(0x00, 0xBF, 0xFF), // Track 1 - Cyan/Blue
        new Color(0xFF, 0x00, 0x00), // Track 2 - Red
        new Color(0xFF, 0x8C, 0x00), // Track 3 - Orange
        new Color(0xFF, 0xD7, 0x00), // Track 4 - Gold/Yellow
        new Color(0x32, 0xCD, 0x32), // Track 5 - Lime Green
        new Color(0x00, 0xCE, 0xD1), // Track 6 - Dark Turquoise
        new Color(0x94, 0x00, 0xD3), // Track 7 - Dark Violet
    };
    
    /**
     * Get track color by index (wraps around if index exceeds array length)
     */
    public static Color getTrackColor(int trackIndex) {
        if (trackIndex < 0) return TRACK_COLORS[0];
        return TRACK_COLORS[trackIndex % TRACK_COLORS.length];
    }
    
    // ==================== Note Names ====================
    
    /** Note names matching chromatic order */
    public static final String[] NOTE_NAMES = {
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };
    
    /** 12 chromatic note colors (C to B), mapped by hue */
    public static final Color[] NOTE_COLORS = {
        Color.decode("#FF0000"), // C  - Red (0°)
        Color.decode("#FF8000"), // C# - Orange (30°)
        Color.decode("#FFFF00"), // D  - Yellow (60°)
        Color.decode("#80FF00"), // D# - Yellow-Green (90°)
        Color.decode("#00FF00"), // E  - Green (120°)
        Color.decode("#00FF80"), // F  - Green-Cyan (150°)
        Color.decode("#00FFFF"), // F# - Cyan (180°)
        Color.decode("#0080FF"), // G  - Cyan-Blue (210°)
        Color.decode("#0000FF"), // G# - Blue (240°)
        Color.decode("#8000FF"), // A  - Purple (270°)
        Color.decode("#FF00FF"), // A# - Magenta (300°)
        Color.decode("#FF0080"), // B  - Pink-Red (330°)
    };
}


