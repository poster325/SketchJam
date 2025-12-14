import java.awt.*;
import java.awt.AlphaComposite;

/**
 * Piano element - Rectangle with fixed width 100, height snaps to 100-500 (octaves 5-1)
 */
public class PianoElement extends AbstractElement {
    
    public static final int FIXED_WIDTH = 100;
    public static final int MIN_HEIGHT = 100;
    public static final int MAX_HEIGHT = 500;
    public static final int HEIGHT_SNAP = 100;
    
    public PianoElement(int x, int y, int height, Color color) {
        super(x, y, FIXED_WIDTH, snapHeight(height), color);
    }
    
    private PianoElement(int x, int y, int width, int height, Color color, int rotation, float opacity) {
        super(x, y, width, height, color);
        this.rotation = rotation;
        this.opacity = opacity;
    }
    
    public static int snapHeight(int height) {
        // Snap to nearest 100, clamp between 100-500
        int snapped = Math.round((float) height / HEIGHT_SNAP) * HEIGHT_SNAP;
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, snapped));
    }
    
    @Override
    public void setSize(int width, int height) {
        // Width is fixed, only height changes
        this.width = FIXED_WIDTH;
        this.height = snapHeight(height);
    }
    
    @Override
    public void draw(Graphics2D g) {
        Graphics2D g2d = (Graphics2D) g.create();
        
        if (rotation != 0) {
            g2d.rotate(Math.toRadians(rotation), x, y);
        }
        
        // Apply opacity
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        
        // Solid fill, no stroke
        g2d.setColor(color);
        g2d.fillRect(x, y, width, height);
        
        g2d.dispose();
    }
    
    @Override
    public String getElementType() {
        return "Piano";
    }
    
    @Override
    public String getMappedValue() {
        // Height 100 = Octave 5 (high), 500 = Octave 1 (low)
        // Short = high octave, Tall = low octave
        int octave = 6 - (height / HEIGHT_SNAP);
        return "Octave " + octave;
    }
    
    @Override
    public DrawableElement copy() {
        return new PianoElement(x, y, width, height, new Color(color.getRGB()), rotation, opacity);
    }
    
    // Piano only has top and bottom handles (no width handles since width is fixed)
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
        
        // Only draw TOP and BOTTOM handles (no left/right for piano)
        g2d.setColor(Color.BLACK);
        g2d.fillRect(x + width/2 - hs/2, y - hs/2, hs, hs);           // TOP
        g2d.fillRect(x + width/2 - hs/2, y + height - hs/2, hs, hs);  // BOTTOM
        
        g2d.dispose();
    }
    
    @Override
    protected Rectangle[] getHandleRects() {
        int hs = DrawableElement.HANDLE_SIZE;
        // Only TOP and BOTTOM handles for piano
        // Note: These are in local coordinates, hit detection needs rotation consideration
        return new Rectangle[] {
            new Rectangle(x + width/2 - hs/2, y - hs/2, hs, hs),           // TOP
            new Rectangle(x + width/2 - hs/2, y + height - hs/2, hs, hs),  // BOTTOM
            null,  // No LEFT handle
            null   // No RIGHT handle
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
                return i;
            }
        }
        return DrawableElement.HANDLE_NONE;
    }
}

