import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * EQ Settings Panel with brightness control
 * Maps brightness to EQ: dark = bass heavy, light = treble heavy
 */
public class EQPanel extends JPanel {
    
    private static final int PANEL_WIDTH = 350;
    private static final int PANEL_HEIGHT = 75;
    private static final int SLIDER_HEIGHT = 25;
    private static final int PADDING = 25;
    
    // 12 brightness levels from white to black
    private static final int NUM_LEVELS = 12;
    private int selectedLevel = 6; // Start at middle (neutral EQ)
    
    private SketchCanvas canvas;
    
    // EQ parameters
    private float bassGain = 1.0f;   // 0.5 to 1.5
    private float trebleGain = 1.0f; // 0.5 to 1.5
    
    public EQPanel(SketchCanvas canvas) {
        this.canvas = canvas;
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(new Color(0x383838));
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });
        
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });
        
        // Apply initial settings
        updateSettings();
    }
    
    private void handleClick(int x, int y) {
        // Check if clicking on the brightness bar
        int barY = 25;
        int barHeight = SLIDER_HEIGHT;
        int cellWidth = (PANEL_WIDTH - 2 * PADDING) / NUM_LEVELS;
        int barX = PADDING;
        
        if (y >= barY && y < barY + barHeight) {
            int level = (x - barX) / cellWidth;
            if (level >= 0 && level < NUM_LEVELS) {
                selectedLevel = level;
                updateSettings();
                repaint();
            }
        }
    }
    
    private void updateSettings() {
        // Calculate brightness (0 = white, 11 = black)
        float brightness = 1.0f - (selectedLevel / (float)(NUM_LEVELS - 1));
        
        // Update canvas background color
        int gray = (int)(brightness * 255);
        Color bgColor = new Color(gray, gray, gray);
        canvas.setCanvasBackground(bgColor);
        
        // Update grid and drum colors based on brightness midpoint
        boolean useDarkElements = brightness > 0.5f; // Light background = dark elements
        canvas.setElementColors(useDarkElements);
        
        // Calculate EQ settings
        // Dark (level 11) = bass 1.5, treble 0.5
        // Light (level 0) = bass 0.5, treble 1.5
        float t = selectedLevel / (float)(NUM_LEVELS - 1); // 0 to 1
        bassGain = 0.5f + t * 1.0f;   // 0.5 to 1.5
        trebleGain = 1.5f - t * 1.0f; // 1.5 to 0.5
        
        // Apply to sound manager
        SoundManager.getInstance().setEQ(bassGain, trebleGain);
        
        System.out.println("EQ: brightness=" + (int)(brightness*100) + "%, bass=" + 
            String.format("%.1f", bassGain) + ", treble=" + String.format("%.1f", trebleGain));
    }
    
    public float getBassGain() { return bassGain; }
    public float getTrebleGain() { return trebleGain; }
    
    public int getBrightnessLevel() {
        return selectedLevel;
    }
    
    public void setBrightnessLevel(int level) {
        if (level >= 0 && level < NUM_LEVELS) {
            selectedLevel = level;
            updateSettings();
            repaint();
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw title centered (at y=0, height=25)
        g2d.setFont(FontManager.getBold(11));
        g2d.setColor(Color.WHITE);
        String title = "EQ SETTINGS";
        FontMetrics fm = g2d.getFontMetrics();
        int titleWidth = fm.stringWidth(title);
        g2d.drawString(title, (PANEL_WIDTH - titleWidth) / 2, 17);
        
        // Draw brightness bar (at y=25, height=25)
        int cellWidth = (PANEL_WIDTH - 2 * PADDING) / NUM_LEVELS;
        int barY = 25;
        
        for (int i = 0; i < NUM_LEVELS; i++) {
            // Calculate color for this level (white to black)
            int gray = 255 - (i * 255 / (NUM_LEVELS - 1));
            g2d.setColor(new Color(gray, gray, gray));
            g2d.fillRect(PADDING + i * cellWidth, barY, cellWidth, SLIDER_HEIGHT);
        }
        
        // Draw selection indicator
        g2d.setColor(new Color(0, 200, 255)); // Cyan selection
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRect(PADDING + selectedLevel * cellWidth, barY, cellWidth, SLIDER_HEIGHT);
        
        // Draw EQ labels (at y=50, height=25)
        g2d.setFont(FontManager.getRegular(9));
        g2d.setColor(Color.GRAY);
        int labelY = 50 + 17; // Centered in the 50-75 row
        g2d.drawString("TREBLE", PADDING, labelY);
        g2d.drawString("BASS", PANEL_WIDTH - PADDING - 25, labelY);
    }
}

