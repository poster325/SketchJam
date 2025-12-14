import java.awt.*;

/**
 * Base interface for all drawable elements on the canvas
 */
public interface DrawableElement {
    void draw(Graphics2D g);
    void drawSelectionHandles(Graphics2D g);
    Rectangle getBounds();
    boolean containsPoint(Point p); // Check if point is inside element (accounts for rotation)
    void setPosition(int x, int y);
    void setSize(int width, int height);
    Point getPosition();
    Dimension getSize();
    Color getColor();
    void setColor(Color color);
    void rotate90();
    int getRotation();
    float getOpacity();
    void setOpacity(float opacity);
    String getElementType();
    String getMappedValue(); // Returns what this element maps to (e.g., "high tom", "octave 3")
    DrawableElement copy(); // Use copy() instead of clone() to avoid conflicts with Object.clone()
    
    // Selection handle hit detection - handles on sides for separate width/height control
    int getHandleAtPoint(Point p); // Returns handle index or -1
    static final int HANDLE_NONE = -1;
    static final int HANDLE_TOP = 0;     // Changes height (top)
    static final int HANDLE_BOTTOM = 1;  // Changes height (bottom)
    static final int HANDLE_LEFT = 2;    // Changes width (left)
    static final int HANDLE_RIGHT = 3;   // Changes width (right)
    static final int HANDLE_SIZE = 6;
    static final int SELECTION_STROKE = 1; // 1px stroke
}

