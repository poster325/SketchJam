import java.awt.*;
import java.io.*;

/**
 * Manages custom fonts for SketchJam
 */
public class FontManager {
    
    private static Font regularFont;
    private static Font boldFont;
    private static boolean initialized = false;
    
    /**
     * Initialize fonts - call once at startup
     */
    public static void init() {
        if (initialized) return;
        
        try {
            // Load regular font
            File regularFile = new File("fonts/Paperlogy-4Regular.ttf");
            if (regularFile.exists()) {
                regularFont = Font.createFont(Font.TRUETYPE_FONT, regularFile);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(regularFont);
            }
            
            // Load bold font
            File boldFile = new File("fonts/Paperlogy-7Bold.ttf");
            if (boldFile.exists()) {
                boldFont = Font.createFont(Font.TRUETYPE_FONT, boldFile);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(boldFont);
            }
            
            initialized = true;
            System.out.println("Custom fonts loaded successfully");
            
        } catch (Exception e) {
            System.err.println("Could not load custom fonts: " + e.getMessage());
            // Fall back to system fonts
            regularFont = new Font("SansSerif", Font.PLAIN, 12);
            boldFont = new Font("SansSerif", Font.BOLD, 12);
        }
    }
    
    /**
     * Get regular font at specified size
     */
    public static Font getRegular(float size) {
        if (!initialized) init();
        if (regularFont != null) {
            return regularFont.deriveFont(size);
        }
        return new Font("SansSerif", Font.PLAIN, (int) size);
    }
    
    /**
     * Get bold font at specified size
     */
    public static Font getBold(float size) {
        if (!initialized) init();
        if (boldFont != null) {
            return boldFont.deriveFont(size);
        }
        return new Font("SansSerif", Font.BOLD, (int) size);
    }
}

