import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Color palette with 12 colors × 4 saturation levels
 * Total size: 300×100, each cell is 25×25 (12 cols × 4 rows = 300×100)
 * Colors mapped to musical notes: C, C#, D, D#, E, F, F#, G, G#, A, A#, B
 */
public class ColorPalette extends JPanel {
    
    private static final int CELL_SIZE = 25;
    private static final int PADDING = 25;  // 25px padding around the color matrix
    private static final int COLS = 12;
    private static final int ROWS = 4;
    
    // Saturation levels: 100%, 80%, 60%, 40%
    private static final float[] SATURATION_LEVELS = {1.0f, 0.8f, 0.6f, 0.4f};
    
    // Note names for reference
    public static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    
    // 12 colors for 12 notes, starting with C = Red, going around the color wheel
    // C=0°, C#=30°, D=60°, D#=90°, E=120°, F=150°, F#=180°, G=210°, G#=240°, A=270°, A#=300°, B=330°
    private static final Color[] PALETTE_COLORS = {
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
    
    private Color[][] colorMatrix;
    private SketchCanvas canvas;
    private ScalePreset scalePreset; // Reference to clear selection when palette is clicked
    private int selectedRow = -1;
    private int selectedCol = -1;
    
    public ColorPalette(SketchCanvas canvas) {
        this.canvas = canvas;
        setPreferredSize(new Dimension(PADDING * 2 + COLS * CELL_SIZE, PADDING * 2 + ROWS * CELL_SIZE)); // 350×150 with 25px padding
        setBackground(new Color(0x38, 0x38, 0x38)); // Match window background
        
        // Build color matrix with saturation variations
        buildColorMatrix();
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int col = (e.getX() - PADDING) / CELL_SIZE;
                int row = (e.getY() - PADDING) / CELL_SIZE;
                
                if (col >= 0 && col < COLS && row >= 0 && row < ROWS) {
                    selectedCol = col;
                    selectedRow = row;
                    Color selected = colorMatrix[row][col];
                    canvas.setCurrentColor(selected);
                    
                    // Clear selection in scale preset
                    if (scalePreset != null) {
                        scalePreset.clearSelection();
                    }
                    
                    // Play the note for audio feedback (octave 3 for preview)
                    // Use column index directly to ensure correct note mapping
                    SoundManager.getInstance().playPianoByIndex(col, 3, 1.0f);
                    
                    repaint();
                }
            }
        });
        
        // Set initial color to C (red)
        canvas.setCurrentColor(colorMatrix[0][0]);
    }
    
    /**
     * Set reference to scale preset for mutual selection clearing
     */
    public void setScalePreset(ScalePreset preset) {
        this.scalePreset = preset;
    }
    
    /**
     * Clear selection (called when scale preset is clicked)
     */
    public void clearSelection() {
        selectedCol = -1;
        selectedRow = -1;
        repaint();
    }
    
    /**
     * Get the note name for a given column index
     */
    public static String getNoteForColumn(int col) {
        if (col >= 0 && col < NOTE_NAMES.length) {
            return NOTE_NAMES[col];
        }
        return "";
    }
    
    /**
     * Get the column index for a given color (finds closest match)
     */
    public int getColumnForColor(Color color) {
        for (int col = 0; col < COLS; col++) {
            if (colorMatrix[0][col].equals(color)) {
                return col;
            }
        }
        return 0;
    }
    
    private void buildColorMatrix() {
        colorMatrix = new Color[ROWS][COLS];
        
        for (int col = 0; col < COLS; col++) {
            Color baseColor = PALETTE_COLORS[col];
            float[] hsb = Color.RGBtoHSB(
                baseColor.getRed(),
                baseColor.getGreen(),
                baseColor.getBlue(),
                null
            );
            
            for (int row = 0; row < ROWS; row++) {
                // Adjust saturation based on row
                float saturation = hsb[1] * SATURATION_LEVELS[row];
                // Keep brightness high, blend with white for lower saturation
                float brightness = hsb[2];
                
                // For desaturated colors, we blend towards white
                // This creates a tint effect rather than just reducing saturation
                Color fullColor = Color.getHSBColor(hsb[0], hsb[1], brightness);
                Color tinted = blendWithWhite(fullColor, SATURATION_LEVELS[row]);
                colorMatrix[row][col] = tinted;
            }
        }
    }
    
    /**
     * Blend a color with white based on factor (1.0 = original, 0.0 = white)
     */
    private Color blendWithWhite(Color color, float factor) {
        int r = (int) (color.getRed() + (255 - color.getRed()) * (1 - factor));
        int g = (int) (color.getGreen() + (255 - color.getGreen()) * (1 - factor));
        int b = (int) (color.getBlue() + (255 - color.getBlue()) * (1 - factor));
        return new Color(r, g, b);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw color cells
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int x = PADDING + col * CELL_SIZE;
                int y = PADDING + row * CELL_SIZE;
                
                // Draw color cell
                g2d.setColor(colorMatrix[row][col]);
                g2d.fillRect(x, y, CELL_SIZE, CELL_SIZE);
                
                // Draw selection indicator
                if (row == selectedRow && col == selectedCol) {
                    g2d.setColor(Color.WHITE);
                    g2d.setStroke(new BasicStroke(2));
                    g2d.drawRect(x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2);
                    g2d.setColor(Color.BLACK);
                    g2d.setStroke(new BasicStroke(1));
                    g2d.drawRect(x + 3, y + 3, CELL_SIZE - 6, CELL_SIZE - 6);
                }
            }
        }
        
        // Draw note labels centered on the top row cells
        g2d.setFont(FontManager.getBold(9));
        g2d.setColor(Color.BLACK);
        FontMetrics fm = g2d.getFontMetrics();
        
        for (int col = 0; col < COLS; col++) {
            String noteName = NOTE_NAMES[col];
            int textWidth = fm.stringWidth(noteName);
            int x = PADDING + col * CELL_SIZE + (CELL_SIZE - textWidth) / 2;
            int y = PADDING + (CELL_SIZE + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(noteName, x, y);
        }
        
    }
}

