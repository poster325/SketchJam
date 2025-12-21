import java.awt.*;
import java.util.UUID;

/**
 * Base abstract class for all drawable elements
 */
public abstract class AbstractElement implements DrawableElement {
    
    protected int x, y;
    protected int width, height;
    protected Color color;
    protected int rotation = 0; // 0, 90, 180, 270
    protected float opacity = 1.0f; // 0.0 to 1.0, maps to volume
    protected String elementId; // Unique identifier for tracking
    
    public AbstractElement(int x, int y, int width, int height, Color color) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.color = color;
        this.elementId = UUID.randomUUID().toString();
    }
    
    @Override
    public String getElementId() {
        return elementId;
    }
    
    @Override
    public void setElementId(String id) {
        this.elementId = id;
    }
    
    @Override
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }
    
    @Override
    public boolean containsPoint(Point p) {
        // Transform point to local coordinates if rotated
        Point local = p;
        if (rotation != 0) {
            double rad = -Math.toRadians(rotation);
            int dx = p.x - x;
            int dy = p.y - y;
            int newX = (int)(dx * Math.cos(rad) - dy * Math.sin(rad)) + x;
            int newY = (int)(dx * Math.sin(rad) + dy * Math.cos(rad)) + y;
            local = new Point(newX, newY);
        }
        return getBounds().contains(local);
    }
    
    @Override
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    @Override
    public Point getPosition() {
        return new Point(x, y);
    }
    
    @Override
    public Dimension getSize() {
        return new Dimension(width, height);
    }
    
    @Override
    public Color getColor() {
        return color;
    }
    
    @Override
    public void setColor(Color color) {
        this.color = color;
    }
    
    @Override
    public void rotate90() {
        rotation = (rotation + 90) % 360;
    }
    
    @Override
    public int getRotation() {
        return rotation;
    }
    
    @Override
    public float getOpacity() {
        return opacity;
    }
    
    @Override
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0.2f, Math.min(1.0f, opacity)); // Clamp between 0.2 and 1.0
    }
    
    @Override
    public void drawSelectionHandles(Graphics2D g) {
        int stroke = DrawableElement.SELECTION_STROKE;
        int hs = DrawableElement.HANDLE_SIZE;
        
        Graphics2D g2d = (Graphics2D) g.create();
        
        // Apply rotation around top-left corner (same as element)
        if (rotation != 0) {
            g2d.rotate(Math.toRadians(rotation), x, y);
        }
        
        // Draw selection box with 1px black stroke
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(stroke));
        g2d.drawRect(x, y, width, height);
        
        // Draw handles on sides (black filled rectangles)
        g2d.fillRect(x + width/2 - hs/2, y - hs/2, hs, hs);           // TOP
        g2d.fillRect(x + width/2 - hs/2, y + height - hs/2, hs, hs);  // BOTTOM
        g2d.fillRect(x - hs/2, y + height/2 - hs/2, hs, hs);          // LEFT
        g2d.fillRect(x + width - hs/2, y + height/2 - hs/2, hs, hs);  // RIGHT
        
        g2d.dispose();
    }
    
    protected Rectangle[] getHandleRects() {
        int hs = DrawableElement.HANDLE_SIZE;  // 6×6
        // Handles on sides, centered on midpoints of each edge
        return new Rectangle[] {
            new Rectangle(x + width/2 - hs/2, y - hs/2, hs, hs),           // TOP - centered on top edge midpoint
            new Rectangle(x + width/2 - hs/2, y + height - hs/2, hs, hs),  // BOTTOM - centered on bottom edge midpoint
            new Rectangle(x - hs/2, y + height/2 - hs/2, hs, hs),          // LEFT - centered on left edge midpoint
            new Rectangle(x + width - hs/2, y + height/2 - hs/2, hs, hs)   // RIGHT - centered on right edge midpoint
        };
    }
    
    @Override
    public int getHandleAtPoint(Point p) {
        // Transform point to local coordinates if rotated
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
    
    protected void setRotation(int rotation) {
        this.rotation = rotation;
    }
}

