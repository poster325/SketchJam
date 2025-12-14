import java.awt.*;
import java.awt.AlphaComposite;

/**
 * Guitar string element - Rectangle
 * Width snaps to: 5, 10, 15, 20, 25 (octaves 5-1)
 * Height snaps to: multiples of 25, range 100-500 (duration)
 */
public class GuitarElement extends AbstractElement {
    
    public static final int[] WIDTH_SNAPS = {5, 10, 15, 20, 25};
    public static final int HEIGHT_SNAP = 25;
    public static final int MIN_HEIGHT = 100;
    public static final int MAX_HEIGHT = 500;
    
    public GuitarElement(int x, int y, int width, int height, Color color) {
        super(x, y, snapWidth(width), snapHeight(height), color);
    }
    
    private GuitarElement(int x, int y, int width, int height, Color color, int rotation, float opacity) {
        super(x, y, width, height, color);
        this.rotation = rotation;
        this.opacity = opacity;
    }
    
    public static int snapWidth(int width) {
        // Find nearest snap width
        int closest = WIDTH_SNAPS[0];
        int minDiff = Math.abs(width - closest);
        
        for (int snapWidth : WIDTH_SNAPS) {
            int diff = Math.abs(width - snapWidth);
            if (diff < minDiff) {
                minDiff = diff;
                closest = snapWidth;
            }
        }
        return closest;
    }
    
    public static int snapHeight(int height) {
        // Snap to nearest 25, clamp between 100-500
        int snapped = Math.round((float) height / HEIGHT_SNAP) * HEIGHT_SNAP;
        return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, snapped));
    }
    
    @Override
    public void setSize(int width, int height) {
        this.width = snapWidth(width);
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
        return "Guitar";
    }
    
    @Override
    public String getMappedValue() {
        // Width determines octave: 5=oct5 (thin/high), 25=oct1 (thick/low)
        int widthIndex = java.util.Arrays.binarySearch(WIDTH_SNAPS, width);
        int octave = 5 - widthIndex; // 5 for thin (index 0), 1 for thick (index 4)
        // Height determines duration
        int duration = height / HEIGHT_SNAP;
        return "Octave " + octave + ", Duration " + duration;
    }
    
    @Override
    public DrawableElement copy() {
        return new GuitarElement(x, y, width, height, new Color(color.getRGB()), rotation, opacity);
    }
}

