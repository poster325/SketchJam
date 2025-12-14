import java.awt.*;
import java.awt.AlphaComposite;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;

/**
 * Snare Drum element - Hollow circle with 50% inner circle subtracted
 * Snaps to: 100 (rim shot), 150 (middle shot)
 */
public class SnareDrumElement extends AbstractElement {
    
    public static final int[] SNAP_SIZES = {100, 150};
    public static final String[] SNARE_TYPES = {"Rim Shot", "Middle Shot"};
    
    public SnareDrumElement(int x, int y, int size, Color color) {
        super(x, y, snapSize(size), snapSize(size), Color.WHITE); // Always white
    }
    
    private SnareDrumElement(int x, int y, int size, Color color, int rotation, float opacity) {
        super(x, y, size, size, Color.WHITE); // Always white
        this.rotation = rotation;
        this.opacity = opacity;
    }
    
    @Override
    public void setColor(Color color) {
        // Snare drums are always black, ignore color changes
    }
    
    public static int snapSize(int size) {
        // Find nearest snap size
        int closest = SNAP_SIZES[0];
        int minDiff = Math.abs(size - closest);
        
        for (int snapSize : SNAP_SIZES) {
            int diff = Math.abs(size - snapSize);
            if (diff < minDiff) {
                minDiff = diff;
                closest = snapSize;
            }
        }
        return closest;
    }
    
    @Override
    public void setSize(int width, int height) {
        // Use the larger dimension, keep 1:1 ratio
        int size = Math.max(width, height);
        int snapped = snapSize(size);
        this.width = snapped;
        this.height = snapped;
    }
    
    @Override
    public void draw(Graphics2D g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (rotation != 0) {
            g2d.rotate(Math.toRadians(rotation), x, y);
        }
        
        // Apply opacity
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        
        // Create outer circle
        Ellipse2D outer = new Ellipse2D.Double(x, y, width, height);
        
        // Create inner circle (50% size, centered)
        int innerSize = width / 2;
        int innerX = x + (width - innerSize) / 2;
        int innerY = y + (height - innerSize) / 2;
        Ellipse2D inner = new Ellipse2D.Double(innerX, innerY, innerSize, innerSize);
        
        // Subtract inner from outer - solid fill, no stroke
        Area ring = new Area(outer);
        ring.subtract(new Area(inner));
        
        g2d.setColor(color);
        g2d.fill(ring);
        
        g2d.dispose();
    }
    
    @Override
    public String getElementType() {
        return "Snare Drum";
    }
    
    @Override
    public String getMappedValue() {
        for (int i = 0; i < SNAP_SIZES.length; i++) {
            if (width == SNAP_SIZES[i]) {
                return SNARE_TYPES[i];
            }
        }
        return "Unknown";
    }
    
    @Override
    public DrawableElement copy() {
        return new SnareDrumElement(x, y, width, new Color(color.getRGB()), rotation, opacity);
    }
    
    // Snare drums have corner handles for diagonal scaling (1:1 ratio)
    @Override
    public void drawSelectionHandles(Graphics2D g) {
        int stroke = DrawableElement.SELECTION_STROKE;
        int hs = DrawableElement.HANDLE_SIZE;
        
        Graphics2D g2d = (Graphics2D) g.create();
        if (rotation != 0) {
            g2d.rotate(Math.toRadians(rotation), x, y);
        }
        
        // Draw selection box with 1px black stroke
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(stroke));
        g2d.drawRect(x, y, width, height);
        
        // Draw corner handles
        g2d.fillRect(x - hs/2, y - hs/2, hs, hs);                     // TOP_LEFT
        g2d.fillRect(x + width - hs/2, y - hs/2, hs, hs);             // TOP_RIGHT
        g2d.fillRect(x - hs/2, y + height - hs/2, hs, hs);            // BOTTOM_LEFT
        g2d.fillRect(x + width - hs/2, y + height - hs/2, hs, hs);    // BOTTOM_RIGHT
        
        g2d.dispose();
    }
    
    @Override
    protected Rectangle[] getHandleRects() {
        int hs = DrawableElement.HANDLE_SIZE;
        // Corner handles for diagonal scaling
        return new Rectangle[] {
            new Rectangle(x - hs/2, y - hs/2, hs, hs),                     // TOP_LEFT (index 0)
            new Rectangle(x + width - hs/2, y - hs/2, hs, hs),             // TOP_RIGHT (index 1)
            new Rectangle(x - hs/2, y + height - hs/2, hs, hs),            // BOTTOM_LEFT (index 2)
            new Rectangle(x + width - hs/2, y + height - hs/2, hs, hs)     // BOTTOM_RIGHT (index 3)
        };
    }
    
    @Override
    public int getHandleAtPoint(Point p) {
        // Transform point if rotated
        Point transformed = p;
        if (rotation != 0) {
            double rad = -Math.toRadians(rotation);
            int dx = p.x - x;
            int dy = p.y - y;
            int newX = (int)(dx * Math.cos(rad) - dy * Math.sin(rad)) + x;
            int newY = (int)(dx * Math.sin(rad) + dy * Math.cos(rad)) + y;
            transformed = new Point(newX, newY);
        }
        
        Rectangle[] handles = getHandleRects();
        for (int i = 0; i < handles.length; i++) {
            if (handles[i] != null && handles[i].contains(transformed)) {
                // Map corner indices to special drum handle indices (offset by 10 to distinguish)
                return 10 + i;  // 10=TL, 11=TR, 12=BL, 13=BR
            }
        }
        return DrawableElement.HANDLE_NONE;
    }
}

