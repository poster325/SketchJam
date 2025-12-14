import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Scale preset panel showing 7 color panels for quick note selection
 * Each panel is 50×50, total width 350 (matches color palette)
 * Colors and notes change based on selected scale
 */
public class ScalePreset extends JPanel {
    
    private static final int CELL_SIZE = 50;
    private static final int COLS = 7;
    
    // All 12 colors for all notes
    private static final Color[] ALL_COLORS = {
        Color.decode("#FF0000"), // C  - Red
        Color.decode("#FF8000"), // C# - Orange
        Color.decode("#FFFF00"), // D  - Yellow
        Color.decode("#80FF00"), // D# - Yellow-Green
        Color.decode("#00FF00"), // E  - Green
        Color.decode("#00FF80"), // F  - Green-Cyan
        Color.decode("#00FFFF"), // F# - Cyan
        Color.decode("#0080FF"), // G  - Cyan-Blue
        Color.decode("#0000FF"), // G# - Blue
        Color.decode("#8000FF"), // A  - Purple
        Color.decode("#FF00FF"), // A# - Magenta
        Color.decode("#FF0080"), // B  - Pink-Red
    };
    
    private static final String[] ALL_NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    
    // Current scale note indices (0-11 for each of 7 notes)
    private int[] scaleNoteIndices = {0, 2, 4, 5, 7, 9, 11}; // Default: C Major
    // Octave offsets for each note (0 = base octave, 1 = one octave up when scale wraps)
    private int[] octaveOffsets = {0, 0, 0, 0, 0, 0, 0};
    
    private static final int BASE_OCTAVE = 3; // Base octave for playback
    
    private SketchCanvas canvas;
    private ColorPalette colorPalette; // Reference to clear selection when preset is clicked
    private int selectedIndex = -1;
    
    public ScalePreset(SketchCanvas canvas) {
        this.canvas = canvas;
        setPreferredSize(new Dimension(COLS * CELL_SIZE, CELL_SIZE));
        setBackground(new Color(0x38, 0x38, 0x38));
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int col = e.getX() / CELL_SIZE;
                
                if (col >= 0 && col < COLS) {
                    selectedIndex = col;
                    int noteIndex = scaleNoteIndices[col];
                    int octave = BASE_OCTAVE + octaveOffsets[col];
                    canvas.setCurrentColor(ALL_COLORS[noteIndex]);
                    
                    // Clear selection in color palette
                    if (colorPalette != null) {
                        colorPalette.clearSelection();
                    }
                    
                    // Play the note with correct octave
                    SoundManager.getInstance().playPianoByIndex(noteIndex, octave, 1.0f);
                    
                    repaint();
                }
            }
        });
    }
    
    /**
     * Set the scale by providing 7 note indices (0-11)
     * Automatically calculates octave offsets when scale wraps around
     */
    public void setScale(int[] noteIndices) {
        if (noteIndices != null && noteIndices.length == 7) {
            this.scaleNoteIndices = noteIndices.clone();
            
            // Calculate octave offsets - when a note is lower than the previous, it's in the next octave
            octaveOffsets = new int[7];
            octaveOffsets[0] = 0; // First note is always base octave
            
            for (int i = 1; i < 7; i++) {
                // If this note index is less than or equal to the first note, it wrapped around
                if (noteIndices[i] < noteIndices[0]) {
                    octaveOffsets[i] = 1;
                } else {
                    octaveOffsets[i] = 0;
                }
            }
            
            selectedIndex = -1; // Clear selection when scale changes
            repaint();
        }
    }
    
    /**
     * Get the current scale note indices
     */
    public int[] getScaleNoteIndices() {
        return scaleNoteIndices.clone();
    }
    
    /**
     * Set reference to color palette for mutual selection clearing
     */
    public void setColorPalette(ColorPalette palette) {
        this.colorPalette = palette;
    }
    
    /**
     * Clear selection (called when color palette is clicked)
     */
    public void clearSelection() {
        selectedIndex = -1;
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw color cells
        for (int col = 0; col < COLS; col++) {
            int x = col * CELL_SIZE;
            int y = 0;
            int noteIndex = scaleNoteIndices[col];
            
            // Draw color cell
            g2d.setColor(ALL_COLORS[noteIndex]);
            g2d.fillRect(x, y, CELL_SIZE, CELL_SIZE);
            
            // Draw selection indicator
            if (col == selectedIndex) {
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRect(x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2);
                g2d.setColor(Color.BLACK);
                g2d.setStroke(new BasicStroke(1));
                g2d.drawRect(x + 3, y + 3, CELL_SIZE - 6, CELL_SIZE - 6);
            }
        }
        
        // Draw note labels centered on cells
        g2d.setFont(FontManager.getBold(12));
        g2d.setColor(Color.BLACK);
        FontMetrics fm = g2d.getFontMetrics();
        
        for (int col = 0; col < COLS; col++) {
            int noteIndex = scaleNoteIndices[col];
            String noteName = ALL_NOTE_NAMES[noteIndex];
            int textWidth = fm.stringWidth(noteName);
            int x = col * CELL_SIZE + (CELL_SIZE - textWidth) / 2;
            int y = (CELL_SIZE + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(noteName, x, y);
        }
    }
}
