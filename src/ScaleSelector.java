import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Scale selector panel for choosing root note (C-B) and scale type (Major/Minor)
 * Row 1: Label "SCALE PRESET"
 * Row 2: 12 root notes (25px each = 300px) + padding
 * Row 3: MAJOR and MINOR buttons (175px each = 350px)
 */
public class ScaleSelector extends JPanel {
    
    private static final int ROOT_CELL_SIZE = 25;
    private static final int ROW_HEIGHT = 25;
    private static final int LABEL_HEIGHT = 25;
    private static final int TYPE_BUTTON_WIDTH = 175; // Half of 350
    
    public static final String[] ROOT_NOTES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    
    // Scale intervals (semitones from root)
    public static final int[] MAJOR_INTERVALS = {0, 2, 4, 5, 7, 9, 11};  // W-W-H-W-W-W-H
    public static final int[] MINOR_INTERVALS = {0, 2, 3, 5, 7, 8, 10};  // W-H-W-W-H-W-W
    
    private int selectedRoot = 0;  // Default to C
    private int selectedType = 0;  // Default to Major
    
    private ScalePreset scalePreset;
    
    public ScaleSelector(ScalePreset scalePreset) {
        this.scalePreset = scalePreset;
        // Height: Label (25) + Root notes row (25) + Type row (25) = 75
        setPreferredSize(new Dimension(350, LABEL_HEIGHT + ROW_HEIGHT + ROW_HEIGHT));
        setBackground(new Color(0x38, 0x38, 0x38));
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                
                // Row 2: Root notes (y = 25 to 50)
                if (y >= LABEL_HEIGHT && y < LABEL_HEIGHT + ROW_HEIGHT) {
                    int rootStartX = 25; // Same offset as in paintComponent
                    int relativeX = x - rootStartX;
                    if (relativeX >= 0 && relativeX < 12 * ROOT_CELL_SIZE) {
                        int col = relativeX / ROOT_CELL_SIZE;
                        if (col >= 0 && col < 12) {
                            selectedRoot = col;
                            updateScalePreset();
                            repaint();
                        }
                    }
                }
                // Row 3: Major/Minor (y = 50 to 75)
                else if (y >= LABEL_HEIGHT + ROW_HEIGHT && y < LABEL_HEIGHT + ROW_HEIGHT * 2) {
                    if (x < TYPE_BUTTON_WIDTH) {
                        selectedType = 0; // Major
                    } else {
                        selectedType = 1; // Minor
                    }
                    updateScalePreset();
                    repaint();
                }
            }
        });
        
        // Initialize preset with default scale
        updateScalePreset();
    }
    
    private void updateScalePreset() {
        int[] intervals = (selectedType == 0) ? MAJOR_INTERVALS : MINOR_INTERVALS;
        int[] noteIndices = new int[7];
        
        for (int i = 0; i < 7; i++) {
            noteIndices[i] = (selectedRoot + intervals[i]) % 12;
        }
        
        scalePreset.setScale(noteIndices);
    }
    
    public int getSelectedRoot() {
        return selectedRoot;
    }
    
    public int getSelectedType() {
        return selectedType;
    }
    
    public int getRootNote() {
        return selectedRoot;
    }
    
    public boolean isMajor() {
        return selectedType == 0;
    }
    
    public void setScale(int rootNote, boolean major) {
        this.selectedRoot = rootNote % 12;
        this.selectedType = major ? 0 : 1;
        updateScalePreset();
        repaint();
    }
    
    public String getScaleName() {
        return ROOT_NOTES[selectedRoot] + " " + (selectedType == 0 ? "Major" : "Minor");
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        FontMetrics fm;
        
        // Row 1: Label "SCALE PRESET"
        g2d.setFont(FontManager.getBold(11));
        fm = g2d.getFontMetrics();
        g2d.setColor(Color.WHITE);
        String label = "SCALE PRESET";
        int labelWidth = fm.stringWidth(label);
        g2d.drawString(label, (350 - labelWidth) / 2, (LABEL_HEIGHT + fm.getAscent() - fm.getDescent()) / 2);
        
        // Row 2: Root note cells (12 * 25 = 300px, centered with 25px padding on each side)
        g2d.setFont(FontManager.getBold(10));
        fm = g2d.getFontMetrics();
        int rootStartX = 25; // Center the 300px row in 350px width
        
        for (int i = 0; i < 12; i++) {
            int x = rootStartX + i * ROOT_CELL_SIZE;
            int y = LABEL_HEIGHT;
            
            // Background
            if (i == selectedRoot) {
                g2d.setColor(new Color(0x60, 0x60, 0x60));
            } else {
                g2d.setColor(new Color(0x48, 0x48, 0x48));
            }
            g2d.fillRect(x, y, ROOT_CELL_SIZE, ROW_HEIGHT);
            
            // Border
            g2d.setColor(new Color(0x30, 0x30, 0x30));
            g2d.drawRect(x, y, ROOT_CELL_SIZE, ROW_HEIGHT);
            
            // Text
            String note = ROOT_NOTES[i];
            int textWidth = fm.stringWidth(note);
            int textX = x + (ROOT_CELL_SIZE - textWidth) / 2;
            int textY = y + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            
            g2d.setColor(i == selectedRoot ? Color.WHITE : new Color(0xAA, 0xAA, 0xAA));
            g2d.drawString(note, textX, textY);
        }
        
        // Row 3: Major/Minor buttons (175px each)
        g2d.setFont(FontManager.getBold(10));
        fm = g2d.getFontMetrics();
        
        String[] typeLabels = {"MAJOR", "MINOR"};
        for (int i = 0; i < 2; i++) {
            int x = i * TYPE_BUTTON_WIDTH;
            int y = LABEL_HEIGHT + ROW_HEIGHT;
            
            // Background
            if (i == selectedType) {
                g2d.setColor(new Color(0x60, 0x60, 0x60));
            } else {
                g2d.setColor(new Color(0x48, 0x48, 0x48));
            }
            g2d.fillRect(x, y, TYPE_BUTTON_WIDTH, ROW_HEIGHT);
            
            // Border
            g2d.setColor(new Color(0x30, 0x30, 0x30));
            g2d.drawRect(x, y, TYPE_BUTTON_WIDTH, ROW_HEIGHT);
            
            // Text
            String typeLabel = typeLabels[i];
            int textWidth = fm.stringWidth(typeLabel);
            int textX = x + (TYPE_BUTTON_WIDTH - textWidth) / 2;
            int textY = y + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            
            g2d.setColor(i == selectedType ? Color.WHITE : new Color(0xAA, 0xAA, 0xAA));
            g2d.drawString(typeLabel, textX, textY);
        }
    }
}
