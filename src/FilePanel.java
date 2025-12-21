import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * File operations panel with New, Open, Save, Export buttons
 */
public class FilePanel extends JPanel {
    
    private static final int BUTTON_SIZE = 50;
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 50; // No padding, just buttons
    
    // Icon images
    private BufferedImage newIcon;
    private BufferedImage openIcon;
    private BufferedImage saveIcon;
    private BufferedImage exportIcon;
    
    // Button states
    private int hoveredButton = -1; // 0=new, 1=open, 2=save, 3=export
    
    // Reference to canvas for future file operations
    private SketchCanvas canvas;
    
    public FilePanel() {
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setBackground(new Color(0x38, 0x38, 0x38));
        
        // Load icons
        loadIcons();
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                
                // Check button clicks (y = 0 to 50, no padding)
                if (y >= 0 && y < BUTTON_SIZE) {
                    int buttonIndex = x / BUTTON_SIZE;
                    if (buttonIndex >= 0 && buttonIndex < 4) {
                        handleButtonClick(buttonIndex);
                    }
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredButton = -1;
                repaint();
            }
        });
        
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                
                int newHoveredButton = -1;
                
                if (y >= 0 && y < BUTTON_SIZE) {
                    int buttonIndex = x / BUTTON_SIZE;
                    if (buttonIndex >= 0 && buttonIndex < 4) {
                        newHoveredButton = buttonIndex;
                    }
                }
                
                if (newHoveredButton != hoveredButton) {
                    hoveredButton = newHoveredButton;
                    repaint();
                }
            }
        });
    }
    
    private void loadIcons() {
        File symbolsDir = ResourceLoader.getSymbolsDir();
        try {
            newIcon = ImageIO.read(new File(symbolsDir, "new.png"));
        } catch (Exception e) { }
        try {
            openIcon = ImageIO.read(new File(symbolsDir, "open.png"));
        } catch (Exception e) { }
        try {
            saveIcon = ImageIO.read(new File(symbolsDir, "save.png"));
        } catch (Exception e) { }
        try {
            exportIcon = ImageIO.read(new File(symbolsDir, "export.png"));
        } catch (Exception e) { }
    }
    
    private void handleButtonClick(int buttonIndex) {
        switch (buttonIndex) {
            case 0:
                handleNew();
                break;
            case 1:
                handleOpen();
                break;
            case 2:
                handleSave();
                break;
            case 3:
                handleExport();
                break;
        }
    }
    
    public void handleNew() {
        System.out.println("New file clicked");
        FileManager.getInstance().newFile(this);
    }
    
    public void handleOpen() {
        System.out.println("Open file clicked");
        FileManager.getInstance().openFile(this);
    }
    
    public void handleSave() {
        System.out.println("Save file clicked");
        FileManager.getInstance().saveFile(this);
    }
    
    public void handleExport() {
        System.out.println("Export file clicked");
        FileManager.getInstance().exportWav(this);
    }
    
    public void setCanvas(SketchCanvas canvas) {
        this.canvas = canvas;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        // Draw 4 buttons at top-left corner, no spacing
        drawIcon(g2d, newIcon, "NEW", 0, 0, hoveredButton == 0);
        drawIcon(g2d, openIcon, "OPEN", BUTTON_SIZE, 0, hoveredButton == 1);
        drawIcon(g2d, saveIcon, "SAVE", BUTTON_SIZE * 2, 0, hoveredButton == 2);
        drawIcon(g2d, exportIcon, "EXP", BUTTON_SIZE * 3, 0, hoveredButton == 3);
    }
    
    private void drawIcon(Graphics2D g2d, BufferedImage icon, String fallbackText, int x, int y, boolean hovered) {
        if (icon != null) {
            // Draw icon with hover effect
            float opacity = hovered ? 1.0f : 0.5f;
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            g2d.drawImage(icon, x, y, BUTTON_SIZE, BUTTON_SIZE, null);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } else {
            // Fallback: draw text button
            g2d.setColor(hovered ? new Color(0x60, 0x60, 0x60) : new Color(0x48, 0x48, 0x48));
            g2d.fillRect(x, y, BUTTON_SIZE, BUTTON_SIZE);
            
            g2d.setColor(hovered ? Color.WHITE : new Color(0xAA, 0xAA, 0xAA));
            g2d.setFont(FontManager.getBold(10));
            FontMetrics fm = g2d.getFontMetrics();
            int textX = x + (BUTTON_SIZE - fm.stringWidth(fallbackText)) / 2;
            int textY = y + (BUTTON_SIZE + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(fallbackText, textX, textY);
        }
    }
}

