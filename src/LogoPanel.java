import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Panel displaying the SketchJam logo at bottom right
 */
public class LogoPanel extends JPanel {
    
    private BufferedImage logoImage;
    
    public LogoPanel() {
        setPreferredSize(new Dimension(350, 100));
        setBackground(new Color(0x38, 0x38, 0x38));
        setOpaque(true);
        
        // Load logo image
        try {
            File symbolsDir = ResourceLoader.getSymbolsDir();
            logoImage = ImageIO.read(new File(symbolsDir, "bottomlogo.png"));
        } catch (Exception e) {
            System.err.println("Could not load bottomlogo.png: " + e.getMessage());
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (logoImage != null) {
            // Calculate centered position
            int imgWidth = logoImage.getWidth();
            int imgHeight = logoImage.getHeight();
            
            // Scale to fit while maintaining aspect ratio
            double scaleX = (double) getWidth() / imgWidth;
            double scaleY = (double) getHeight() / imgHeight;
            double scale = Math.min(scaleX, scaleY);
            
            int scaledWidth = (int) (imgWidth * scale);
            int scaledHeight = (int) (imgHeight * scale);
            
            int x = (getWidth() - scaledWidth) / 2;
            int y = (getHeight() - scaledHeight) / 2;
            
            g2d.drawImage(logoImage, x, y, scaledWidth, scaledHeight, null);
        } else {
            // Fallback: draw text
            g2d.setColor(new Color(0x60, 0x60, 0x60));
            g2d.setFont(FontManager.getBold(24));
            FontMetrics fm = g2d.getFontMetrics();
            String text = "SketchJam";
            int textX = (getWidth() - fm.stringWidth(text)) / 2;
            int textY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(text, textX, textY);
        }
    }
}

