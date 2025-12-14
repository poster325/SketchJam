import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SketchCanvas extends JPanel {
    
    private static final int GRID_SIZE = 25;
    private static final int SNAP_SIZE = 5;
    private static final Color GRID_COLOR = new Color(255, 255, 255, 25); // 10% white
    private static final Color BACKGROUND_COLOR = new Color(0x32, 0x32, 0x32); // #323232
    
    // Drawing modes
    public enum DrawMode {
        NONE, DRUM, SNARE_DRUM, PIANO, GUITAR
    }
    
    // Interaction modes
    public enum InteractionMode {
        OBJECT,  // Select, move, resize, color change
        PLAY     // Only play sounds, no editing
    }
    
    private DrawMode currentDrawMode = DrawMode.NONE;
    private InteractionMode interactionMode = InteractionMode.OBJECT;
    private Color currentColor = Color.RED;
    private UndoRedoManager undoRedoManager;
    private RecordPanel recordPanel;
    private List<DrawableElement> elements = new ArrayList<>();
    
    // Selection state (supports multiple selection)
    private DrawableElement selectedElement = null;
    private List<DrawableElement> selectedElements = new ArrayList<>();
    private int activeHandle = DrawableElement.HANDLE_NONE;
    
    // Drawing/dragging state
    private Point dragStart = null;
    private Point dragCurrent = null;
    private boolean isDrawing = false;
    private boolean isDragging = false;
    private boolean isResizing = false;
    private boolean isMarqueeSelecting = false;
    private boolean isPlayDragging = false;
    private Point elementStartPos = null;
    private Dimension elementStartSize = null;
    
    // Track the last element the mouse was over during drag (for re-entry detection)
    private DrawableElement lastElementDuringDrag = null;
    
    // Track currently held piano element for sustain behavior
    private DrawableElement heldPianoElement = null;
    
    // Glow effect tracking: element -> glow intensity (1.0 = full, 0.0 = none)
    private Map<DrawableElement, Float> glowingElements = new HashMap<>();
    private Timer glowFadeTimer;
    private static final float GLOW_FADE_RATE = 0.05f; // How much to fade per tick
    private static final int GLOW_FADE_INTERVAL = 30; // ms between fade ticks
    
    public SketchCanvas(UndoRedoManager undoRedoManager) {
        this.undoRedoManager = undoRedoManager;
        setBackground(BACKGROUND_COLOR);
        setFocusable(true);
        setDoubleBuffered(true); // Prevent flickering
        setOpaque(true); // Optimize painting
        
        setupMouseListeners();
        setupKeyBindings();
        setupGlowFadeTimer();
    }
    
    private void setupGlowFadeTimer() {
        glowFadeTimer = new Timer(GLOW_FADE_INTERVAL, e -> {
            if (glowingElements.isEmpty()) return;
            
            // Fade all glowing elements
            Iterator<Map.Entry<DrawableElement, Float>> it = glowingElements.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<DrawableElement, Float> entry = it.next();
                float newIntensity = entry.getValue() - GLOW_FADE_RATE;
                if (newIntensity <= 0) {
                    it.remove();
                } else {
                    entry.setValue(newIntensity);
                }
            }
            repaint();
        });
        glowFadeTimer.start();
    }
    
    /**
     * Trigger glow effect on an element (called when played)
     */
    private void triggerGlow(DrawableElement element) {
        glowingElements.put(element, 1.0f);
    }
    
    @Override
    public void update(Graphics g) {
        // Override to prevent flickering - paint directly without clearing
        paint(g);
    }
    
    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Point p = e.getPoint();
                
                // PLAY MODE: Different behavior for each instrument type
                if (interactionMode == InteractionMode.PLAY) {
                    DrawableElement clicked = getElementAt(p);
                    if (clicked != null) {
                        String type = clicked.getElementType();
                        
                        if (type.equals("Piano")) {
                            // Piano: Start sustain note (will stop on release)
                            String mapped = clicked.getMappedValue();
                            int octave = Integer.parseInt(mapped.split(" ")[1]) + 1;
                            SoundManager.getInstance().startPianoNote(clicked.getColor(), octave, clicked.getOpacity());
                            heldPianoElement = clicked;
                            triggerGlow(clicked);
                        } else if (type.equals("Guitar")) {
                            // Guitar: Play with duration based on height
                            String mapped = clicked.getMappedValue();
                            int octave = Integer.parseInt(mapped.split(",")[0].split(" ")[1]);
                            int height = clicked.getBounds().height;
                            SoundManager.getInstance().playGuitarWithDuration(clicked.getColor(), octave, clicked.getOpacity(), height);
                            triggerGlow(clicked);
                            lastElementDuringDrag = clicked;
                        } else {
                            // Drums/Snare: Click to play
                            SoundManager.getInstance().playElement(clicked);
                            triggerGlow(clicked);
                        }
                    } else {
                        lastElementDuringDrag = null;
                    }
                    isPlayDragging = true;
                    dragStart = p;
                    repaint();
                    return;
                }
                
                // OBJECT MODE: Full editing capabilities
                
                // Check if clicking on a handle of single selected element
                if (selectedElement != null && selectedElements.size() <= 1) {
                    int handle = selectedElement.getHandleAtPoint(p);
                    if (handle != DrawableElement.HANDLE_NONE) {
                        activeHandle = handle;
                        isResizing = true;
                        dragStart = p;
                        elementStartPos = selectedElement.getPosition();
                        elementStartSize = selectedElement.getSize();
                        undoRedoManager.saveState(elements);
                        return;
                    }
                }
                
                // Check if clicking on an element to select it
                DrawableElement clicked = getElementAt(p);
                if (clicked != null && currentDrawMode == DrawMode.NONE) {
                    // Clear multiple selection and select single element
                    selectedElements.clear();
                    selectedElement = clicked;
                    isDragging = true;
                    dragStart = p;
                    elementStartPos = selectedElement.getPosition();
                    undoRedoManager.saveState(elements);
                    SoundManager.getInstance().playElement(clicked);
                    repaint();
                    return;
                }
                
                // Start drawing new element
                if (currentDrawMode != DrawMode.NONE) {
                    selectedElement = null;
                    selectedElements.clear();
                    isDrawing = true;
                    dragStart = snapToGridStart(p);
                    dragCurrent = dragStart;
                    repaint();
                    return;
                }
                
                // Clicked on empty space - start marquee selection for multiple elements
                if (currentDrawMode == DrawMode.NONE && clicked == null) {
                    selectedElement = null;
                    selectedElements.clear();
                    isMarqueeSelecting = true;
                    dragStart = p;
                    dragCurrent = p;
                    repaint();
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                // PLAY MODE release
                if (interactionMode == InteractionMode.PLAY) {
                    // Stop any held piano note
                    if (heldPianoElement != null) {
                        SoundManager.getInstance().stopPianoNote();
                        heldPianoElement = null;
                    }
                    isPlayDragging = false;
                    lastElementDuringDrag = null;
                    dragStart = null;
                    repaint();
                    return;
                }
                
                // OBJECT MODE release
                if (isDrawing && dragStart != null) {
                    Point end = snapToGrid(e.getPoint());
                    DrawableElement newElement = createElementFromDrag(dragStart, end);
                    if (newElement != null) {
                        undoRedoManager.saveState(elements);
                        elements.add(newElement);
                        selectedElement = newElement;
                        selectedElements.clear();
                    }
                    currentDrawMode = DrawMode.NONE;
                }
                
                // Handle marquee selection - select ALL elements inside
                if (isMarqueeSelecting && dragStart != null && dragCurrent != null) {
                    Rectangle marquee = getMarqueeRect();
                    selectedElements.clear();
                    selectedElement = null;
                    
                    for (DrawableElement element : elements) {
                        Rectangle bounds = element.getBounds();
                        Point center = new Point(bounds.x + bounds.width/2, bounds.y + bounds.height/2);
                        if (marquee.contains(center)) {
                            selectedElements.add(element);
                        }
                    }
                    
                    // If only one selected, also set selectedElement
                    if (selectedElements.size() == 1) {
                        selectedElement = selectedElements.get(0);
                    }
                }
                
                isDrawing = false;
                isDragging = false;
                isResizing = false;
                isMarqueeSelecting = false;
                isPlayDragging = false;
                activeHandle = DrawableElement.HANDLE_NONE;
                dragStart = null;
                dragCurrent = null;
                elementStartPos = null;
                elementStartSize = null;
                repaint();
            }
        });
        
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                Point p = e.getPoint();
                
                // PLAY MODE: Different drag behavior for each instrument
                if (interactionMode == InteractionMode.PLAY && isPlayDragging) {
                    DrawableElement elementAtPoint = getElementAt(p);
                    
                    // If holding a piano and moved off it, stop the note
                    if (heldPianoElement != null && elementAtPoint != heldPianoElement) {
                        SoundManager.getInstance().stopPianoNote();
                        heldPianoElement = null;
                    }
                    
                    // Check if entering a new element
                    if (elementAtPoint != null && elementAtPoint != lastElementDuringDrag) {
                        String type = elementAtPoint.getElementType();
                        
                        if (type.equals("Guitar")) {
                            // Guitar: Drag-to-play with duration based on height
                            String mapped = elementAtPoint.getMappedValue();
                            int octave = Integer.parseInt(mapped.split(",")[0].split(" ")[1]);
                            int height = elementAtPoint.getBounds().height;
                            SoundManager.getInstance().playGuitarWithDuration(elementAtPoint.getColor(), octave, elementAtPoint.getOpacity(), height);
                            triggerGlow(elementAtPoint);
                        } else if (type.equals("Piano")) {
                            // Piano: Start sustain on entry
                            String mapped = elementAtPoint.getMappedValue();
                            int octave = Integer.parseInt(mapped.split(" ")[1]) + 1;
                            SoundManager.getInstance().startPianoNote(elementAtPoint.getColor(), octave, elementAtPoint.getOpacity());
                            heldPianoElement = elementAtPoint;
                            triggerGlow(elementAtPoint);
                        } else {
                            // Drums/Snare: Play on entry
                            SoundManager.getInstance().playElement(elementAtPoint);
                            triggerGlow(elementAtPoint);
                        }
                    }
                    
                    lastElementDuringDrag = elementAtPoint;
                    paintImmediately(0, 0, getWidth(), getHeight());
                    return;
                }
                
                // OBJECT MODE dragging
                if (isResizing && selectedElement != null) {
                    handleResize(p);
                } else if (isDragging && selectedElement != null) {
                    handleDrag(p);
                } else if (isDrawing) {
                    dragCurrent = snapToGrid(p);
                } else if (isMarqueeSelecting) {
                    dragCurrent = p;
                }
                paintImmediately(0, 0, getWidth(), getHeight());
            }
        });
    }
    
    private void handleDrag(Point current) {
        if (dragStart == null || elementStartPos == null) return;
        
        int dx = current.x - dragStart.x;
        int dy = current.y - dragStart.y;
        
        int newX = snapValue(elementStartPos.x + dx);
        int newY = snapValue(elementStartPos.y + dy);
        
        selectedElement.setPosition(newX, newY);
    }
    
    private void handleResize(Point current) {
        if (elementStartPos == null || elementStartSize == null) return;
        
        int dx = current.x - dragStart.x;
        int dy = current.y - dragStart.y;
        
        int rawWidth, rawHeight;
        
        switch (activeHandle) {
            case DrawableElement.HANDLE_TOP:
                // Drag top edge - change height, anchor bottom edge
                int anchorBottomY = elementStartPos.y + elementStartSize.height;
                rawHeight = elementStartSize.height - dy;
                if (rawHeight > 0) {
                    selectedElement.setSize(elementStartSize.width, rawHeight);
                    int snappedHeight = selectedElement.getSize().height;
                    selectedElement.setPosition(elementStartPos.x, anchorBottomY - snappedHeight);
                }
                break;
                
            case DrawableElement.HANDLE_BOTTOM:
                // Drag bottom edge - change height, anchor top edge
                rawHeight = elementStartSize.height + dy;
                if (rawHeight > 0) {
                    selectedElement.setSize(elementStartSize.width, rawHeight);
                    selectedElement.setPosition(elementStartPos.x, elementStartPos.y);
                }
                break;
                
            case DrawableElement.HANDLE_LEFT:
                // Drag left edge - change width, anchor right edge
                int anchorRightX = elementStartPos.x + elementStartSize.width;
                rawWidth = elementStartSize.width - dx;
                if (rawWidth > 0) {
                    selectedElement.setSize(rawWidth, elementStartSize.height);
                    int snappedWidth = selectedElement.getSize().width;
                    selectedElement.setPosition(anchorRightX - snappedWidth, elementStartPos.y);
                }
                break;
                
            case DrawableElement.HANDLE_RIGHT:
                // Drag right edge - change width, anchor left edge
                rawWidth = elementStartSize.width + dx;
                if (rawWidth > 0) {
                    selectedElement.setSize(rawWidth, elementStartSize.height);
                    selectedElement.setPosition(elementStartPos.x, elementStartPos.y);
                }
                break;
                
            // Corner handles for drums (diagonal resize, 1:1 ratio)
            case 10: // TOP_LEFT corner
                {
                    int anchorX = elementStartPos.x + elementStartSize.width;
                    int anchorY = elementStartPos.y + elementStartSize.height;
                    int delta = Math.max(-dx, -dy); // Use larger movement
                    int rawSize = elementStartSize.width + delta;
                    if (rawSize > 0) {
                        selectedElement.setSize(rawSize, rawSize);
                        Dimension snapped = selectedElement.getSize();
                        selectedElement.setPosition(anchorX - snapped.width, anchorY - snapped.height);
                    }
                }
                break;
                
            case 11: // TOP_RIGHT corner
                {
                    int anchorX = elementStartPos.x;
                    int anchorY = elementStartPos.y + elementStartSize.height;
                    int delta = Math.max(dx, -dy);
                    int rawSize = elementStartSize.width + delta;
                    if (rawSize > 0) {
                        selectedElement.setSize(rawSize, rawSize);
                        int snappedHeight = selectedElement.getSize().height;
                        selectedElement.setPosition(anchorX, anchorY - snappedHeight);
                    }
                }
                break;
                
            case 12: // BOTTOM_LEFT corner
                {
                    int anchorX = elementStartPos.x + elementStartSize.width;
                    int anchorY = elementStartPos.y;
                    int delta = Math.max(-dx, dy);
                    int rawSize = elementStartSize.width + delta;
                    if (rawSize > 0) {
                        selectedElement.setSize(rawSize, rawSize);
                        int snappedWidth = selectedElement.getSize().width;
                        selectedElement.setPosition(anchorX - snappedWidth, anchorY);
                    }
                }
                break;
                
            case 13: // BOTTOM_RIGHT corner
                {
                    int anchorX = elementStartPos.x;
                    int anchorY = elementStartPos.y;
                    int delta = Math.max(dx, dy);
                    int rawSize = elementStartSize.width + delta;
                    if (rawSize > 0) {
                        selectedElement.setSize(rawSize, rawSize);
                        selectedElement.setPosition(anchorX, anchorY);
                    }
                }
                break;
        }
    }
    
    private DrawableElement createElementFromDrag(Point start, Point end) {
        int width = Math.abs(end.x - start.x);
        int height = Math.abs(end.y - start.y);
        int x = Math.min(start.x, end.x);
        int y = Math.min(start.y, end.y);
        
        // Minimum size check
        if (width < 10 && height < 10) {
            // Set default sizes
            width = 100;
            height = 100;
        }
        
        switch (currentDrawMode) {
            case DRUM:
                int drumSize = Math.max(width, height);
                return new DrumElement(x, y, drumSize, currentColor);
            case SNARE_DRUM:
                int snareSize = Math.max(width, height);
                return new SnareDrumElement(x, y, snareSize, currentColor);
            case PIANO:
                return new PianoElement(x, y, height, currentColor);
            case GUITAR:
                return new GuitarElement(x, y, width, height, currentColor);
            default:
                return null;
        }
    }
    
    private DrawableElement getElementAt(Point p) {
        // Search from top (last added) to bottom, using rotation-aware hit detection
        for (int i = elements.size() - 1; i >= 0; i--) {
            DrawableElement element = elements.get(i);
            if (element.containsPoint(p)) {
                return element;
            }
        }
        return null;
    }
    
    private Rectangle getMarqueeRect() {
        if (dragStart == null || dragCurrent == null) return null;
        int x = Math.min(dragStart.x, dragCurrent.x);
        int y = Math.min(dragStart.y, dragCurrent.y);
        int w = Math.abs(dragCurrent.x - dragStart.x);
        int h = Math.abs(dragCurrent.y - dragStart.y);
        return new Rectangle(x, y, w, h);
    }
    
    private void setupKeyBindings() {
        // D - Drum mode (hold to draw, release to select)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, false), "drumModeOn");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true), "drumModeOff");
        getActionMap().put("drumModeOn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactionMode == InteractionMode.OBJECT && currentDrawMode == DrawMode.NONE) {
                    currentDrawMode = DrawMode.DRUM;
                    selectedElement = null;
                    repaint();
                }
            }
        });
        getActionMap().put("drumModeOff", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentDrawMode == DrawMode.DRUM && !isDrawing) {
                    currentDrawMode = DrawMode.NONE;
                    repaint();
                }
            }
        });
        
        // Ctrl+D - Snare Drum mode (hold to draw, release to select)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK, false), "snareDrumModeOn");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK, true), "snareDrumModeOff");
        getActionMap().put("snareDrumModeOn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactionMode == InteractionMode.OBJECT && currentDrawMode == DrawMode.NONE) {
                    currentDrawMode = DrawMode.SNARE_DRUM;
                    selectedElement = null;
                    repaint();
                }
            }
        });
        getActionMap().put("snareDrumModeOff", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentDrawMode == DrawMode.SNARE_DRUM && !isDrawing) {
                    currentDrawMode = DrawMode.NONE;
                    repaint();
                }
            }
        });
        
        // F - Piano mode (hold to draw, release to select)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, false), "pianoModeOn");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, true), "pianoModeOff");
        getActionMap().put("pianoModeOn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactionMode == InteractionMode.OBJECT && currentDrawMode == DrawMode.NONE) {
                    currentDrawMode = DrawMode.PIANO;
                    selectedElement = null;
                    repaint();
                }
            }
        });
        getActionMap().put("pianoModeOff", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentDrawMode == DrawMode.PIANO && !isDrawing) {
                    currentDrawMode = DrawMode.NONE;
                    repaint();
                }
            }
        });
        
        // G - Guitar mode (hold to draw, release to select)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_G, 0, false), "guitarModeOn");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_G, 0, true), "guitarModeOff");
        getActionMap().put("guitarModeOn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactionMode == InteractionMode.OBJECT && currentDrawMode == DrawMode.NONE) {
                    currentDrawMode = DrawMode.GUITAR;
                    selectedElement = null;
                    repaint();
                }
            }
        });
        getActionMap().put("guitarModeOff", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (currentDrawMode == DrawMode.GUITAR && !isDrawing) {
                    currentDrawMode = DrawMode.NONE;
                    repaint();
                }
            }
        });
        
        // R - Rotate selected element (Object Mode only)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('R'), "rotate");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke('r'), "rotate");
        getActionMap().put("rotate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactionMode == InteractionMode.OBJECT && selectedElement != null) {
                    undoRedoManager.saveState(elements);
                    selectedElement.rotate90();
                    repaint();
                }
            }
        });
        
        // Escape - Exit draw mode
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exitMode");
        getActionMap().put("exitMode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                currentDrawMode = DrawMode.NONE;
                selectedElement = null;
                repaint();
            }
        });
        
        // Delete - Remove selected element(s) (Object Mode only)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete");
        getActionMap().put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactionMode != InteractionMode.OBJECT) return;
                if (!selectedElements.isEmpty()) {
                    undoRedoManager.saveState(elements);
                    elements.removeAll(selectedElements);
                    selectedElements.clear();
                    selectedElement = null;
                    repaint();
                } else if (selectedElement != null) {
                    undoRedoManager.saveState(elements);
                    elements.remove(selectedElement);
                    selectedElement = null;
                    repaint();
                }
            }
        });
        
        // O - Object Mode (editing mode)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_O, 0), "objectMode");
        getActionMap().put("objectMode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                interactionMode = InteractionMode.OBJECT;
                repaint();
            }
        });
        
        // P - Play Mode (only playing sounds)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0), "playMode");
        getActionMap().put("playMode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                interactionMode = InteractionMode.PLAY;
                selectedElement = null;
                selectedElements.clear();
                repaint();
            }
        });
        
        // [ - Decrease opacity by 20% (Object Mode only)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, 0), "decreaseOpacity");
        getActionMap().put("decreaseOpacity", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactionMode != InteractionMode.OBJECT) return;
                if (!selectedElements.isEmpty()) {
                    undoRedoManager.saveState(elements);
                    for (DrawableElement elem : selectedElements) {
                        elem.setOpacity(elem.getOpacity() - 0.2f);
                    }
                    repaint();
                } else if (selectedElement != null) {
                    undoRedoManager.saveState(elements);
                    selectedElement.setOpacity(selectedElement.getOpacity() - 0.2f);
                    repaint();
                }
            }
        });
        
        // ] - Increase opacity by 20% (Object Mode only)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, 0), "increaseOpacity");
        getActionMap().put("increaseOpacity", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactionMode != InteractionMode.OBJECT) return;
                if (!selectedElements.isEmpty()) {
                    undoRedoManager.saveState(elements);
                    for (DrawableElement elem : selectedElements) {
                        elem.setOpacity(elem.getOpacity() + 0.2f);
                    }
                    repaint();
                } else if (selectedElement != null) {
                    undoRedoManager.saveState(elements);
                    selectedElement.setOpacity(selectedElement.getOpacity() + 0.2f);
                    repaint();
                }
            }
        });
    }
    
    /**
     * Snap a point to the nearest snap grid (5 pixels) for movement/resize
     */
    public static Point snapToGrid(Point p) {
        int x = Math.round((float) p.x / SNAP_SIZE) * SNAP_SIZE;
        int y = Math.round((float) p.y / SNAP_SIZE) * SNAP_SIZE;
        return new Point(x, y);
    }
    
    /**
     * Snap a point to the 25×25 grid for initial drawing position
     */
    public static Point snapToGridStart(Point p) {
        int x = Math.round((float) p.x / GRID_SIZE) * GRID_SIZE;
        int y = Math.round((float) p.y / GRID_SIZE) * GRID_SIZE;
        return new Point(x, y);
    }
    
    /**
     * Snap a value to the nearest snap grid
     */
    public static int snapValue(int value) {
        return Math.round((float) value / SNAP_SIZE) * SNAP_SIZE;
    }
    
    public void setCurrentColor(Color color) {
        this.currentColor = color;
        
        // Handle multiple selection
        if (!selectedElements.isEmpty()) {
            undoRedoManager.saveState(elements);
            for (DrawableElement elem : selectedElements) {
                String type = elem.getElementType();
                if (!type.equals("Drum") && !type.equals("Snare Drum")) {
                    elem.setColor(color);
                }
            }
            repaint();
            return;
        }
        
        // Handle single selection
        if (selectedElement != null) {
            String type = selectedElement.getElementType();
            if (!type.equals("Drum") && !type.equals("Snare Drum")) {
                undoRedoManager.saveState(elements);
                selectedElement.setColor(color);
                repaint();
            }
        }
    }
    
    public Color getCurrentColor() {
        return currentColor;
    }
    
    public void setRecordPanel(RecordPanel recordPanel) {
        this.recordPanel = recordPanel;
    }
    
    public DrawableElement getSelectedElement() {
        return selectedElement;
    }
    
    public DrawMode getCurrentMode() {
        return currentDrawMode;
    }
    
    public List<DrawableElement> getElements() {
        return elements;
    }
    
    public void setElements(List<DrawableElement> elements) {
        this.elements = new ArrayList<>(elements);
        // Clear selection state when elements are restored (undo/redo)
        selectedElement = null;
        selectedElements.clear();
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw grid
        drawGrid(g2d);
        
        // Draw all elements
        for (DrawableElement element : elements) {
            element.draw(g2d);
        }
        
        // Draw glow effects for played elements (Play mode)
        if (!glowingElements.isEmpty()) {
            for (Map.Entry<DrawableElement, Float> entry : glowingElements.entrySet()) {
                drawGlowEffect(g2d, entry.getKey(), entry.getValue());
            }
        }
        
        // Draw selection for elements (only in Object mode)
        if (interactionMode == InteractionMode.OBJECT) {
            // Multiple selection: just bounding boxes, no handles
            if (selectedElements.size() > 1) {
                for (DrawableElement elem : selectedElements) {
                    drawMultiSelectBox(g2d, elem);
                }
            }
            // Single selection: full handles
            else if (selectedElement != null) {
                selectedElement.drawSelectionHandles(g2d);
            }
        }
        
        // Draw preview while drawing
        if (isDrawing && dragStart != null && dragCurrent != null) {
            drawPreview(g2d);
        }
        
        // Draw marquee selection box
        if (isMarqueeSelecting && dragStart != null && dragCurrent != null) {
            drawMarquee(g2d);
        }
        
        // Draw mode indicator
        drawModeIndicator(g2d);
    }
    
    private void drawMultiSelectBox(Graphics2D g2d, DrawableElement elem) {
        Rectangle bounds = elem.getBounds();
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1));
        
        // Apply rotation if element is rotated
        if (elem.getRotation() != 0) {
            Graphics2D g2 = (Graphics2D) g2d.create();
            g2.rotate(Math.toRadians(elem.getRotation()), bounds.x, bounds.y);
            g2.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            g2.dispose();
        } else {
            g2d.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }
    }
    
    private void drawGlowEffect(Graphics2D g2d, DrawableElement elem, float intensity) {
        Rectangle bounds = elem.getBounds();
        Graphics2D g2 = (Graphics2D) g2d.create();
        
        // Apply rotation if element is rotated
        if (elem.getRotation() != 0) {
            g2.rotate(Math.toRadians(elem.getRotation()), bounds.x, bounds.y);
        }
        
        // Get element color for glow (white for drums, element color for others)
        Color glowColor = elem.getColor();
        if (glowColor.equals(Color.WHITE)) {
            glowColor = new Color(200, 200, 255); // Slight blue tint for white elements
        }
        
        // Draw multiple expanding glow layers
        int glowLayers = 4;
        int maxExpand = (int)(15 * intensity);
        
        for (int i = glowLayers; i >= 1; i--) {
            int expand = (maxExpand * i) / glowLayers;
            float layerAlpha = intensity * (0.3f / i);
            
            g2.setColor(new Color(
                glowColor.getRed(),
                glowColor.getGreen(),
                glowColor.getBlue(),
                (int)(layerAlpha * 255)
            ));
            
            String type = elem.getElementType();
            if (type.equals("Drum") || type.equals("Snare Drum")) {
                // Draw oval glow for drums
                g2.fillOval(
                    bounds.x - expand,
                    bounds.y - expand,
                    bounds.width + expand * 2,
                    bounds.height + expand * 2
                );
            } else {
                // Draw rounded rectangle glow for others
                g2.fillRoundRect(
                    bounds.x - expand,
                    bounds.y - expand,
                    bounds.width + expand * 2,
                    bounds.height + expand * 2,
                    expand * 2,
                    expand * 2
                );
            }
        }
        
        g2.dispose();
    }
    
    private void drawMarquee(Graphics2D g2d) {
        Rectangle rect = getMarqueeRect();
        if (rect == null) return;
        
        // Dashed black stroke
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 4}, 0));
        g2d.drawRect(rect.x, rect.y, rect.width, rect.height);
    }
    
    private void drawPreview(Graphics2D g2d) {
        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{5}, 0));
        
        int rawWidth = Math.abs(dragCurrent.x - dragStart.x);
        int rawHeight = Math.abs(dragCurrent.y - dragStart.y);
        
        // Apply element-specific snapping to preview
        switch (currentDrawMode) {
            case DRUM:
                g2d.setColor(new Color(255, 255, 255, 128)); // White for drums
                int drumSize = DrumElement.snapSize(Math.max(rawWidth, rawHeight));
                g2d.drawOval(dragStart.x, dragStart.y, drumSize, drumSize);
                drawSizeLabel(g2d, dragStart.x, dragStart.y + drumSize + 15, drumSize + "×" + drumSize);
                break;
            case SNARE_DRUM:
                g2d.setColor(new Color(255, 255, 255, 128)); // White for snare drums
                int snareSize = SnareDrumElement.snapSize(Math.max(rawWidth, rawHeight));
                g2d.drawOval(dragStart.x, dragStart.y, snareSize, snareSize);
                int innerSize = snareSize / 2;
                int innerX = dragStart.x + (snareSize - innerSize) / 2;
                int innerY = dragStart.y + (snareSize - innerSize) / 2;
                g2d.drawOval(innerX, innerY, innerSize, innerSize);
                drawSizeLabel(g2d, dragStart.x, dragStart.y + snareSize + 15, snareSize + "×" + snareSize);
                break;
            case PIANO:
                g2d.setColor(new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 128));
                int pianoHeight = PianoElement.snapHeight(rawHeight);
                g2d.drawRect(dragStart.x, dragStart.y, PianoElement.FIXED_WIDTH, pianoHeight);
                drawSizeLabel(g2d, dragStart.x, dragStart.y + pianoHeight + 15, PianoElement.FIXED_WIDTH + "×" + pianoHeight);
                break;
            case GUITAR:
                g2d.setColor(new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 128));
                int guitarWidth = GuitarElement.snapWidth(rawWidth);
                int guitarHeight = GuitarElement.snapHeight(rawHeight);
                g2d.drawRect(dragStart.x, dragStart.y, guitarWidth, guitarHeight);
                drawSizeLabel(g2d, dragStart.x, dragStart.y + guitarHeight + 15, guitarWidth + "×" + guitarHeight);
                break;
        }
    }
    
    private void drawSizeLabel(Graphics2D g2d, int x, int y, String text) {
        g2d.setFont(FontManager.getBold(12));
        // Draw background for visibility
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(x - 2, y - textHeight + 4, textWidth + 4, textHeight);
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, x, y);
    }
    
    private void drawModeIndicator(Graphics2D g2d) {
        g2d.setFont(FontManager.getBold(14));
        FontMetrics fm = g2d.getFontMetrics();
        
        // Grid-aligned constants
        final int BOX_HEIGHT = 25;  // Match grid size
        final int BOX_X = 0;        // Align with grid
        int currentY = 0;           // Start at grid origin
        int textY;
        
        // Show play mode from RecordPanel (playback with metronome)
        if (recordPanel != null && recordPanel.isPlaying()) {
            String playText = "PLAY MODE";
            int playWidth = fm.stringWidth(playText);
            g2d.setColor(new Color(0, 128, 0, 200)); // Green
            g2d.fillRect(BOX_X, currentY, playWidth + 20, BOX_HEIGHT);
            g2d.setColor(Color.WHITE);
            textY = currentY + (BOX_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(playText, BOX_X + 10, textY);
            currentY += BOX_HEIGHT;
        }
        
        // Show record mode from RecordPanel
        if (recordPanel != null && recordPanel.isRecording()) {
            String recText = "RECORD MODE";
            int recWidth = fm.stringWidth(recText);
            g2d.setColor(new Color(200, 0, 0, 200)); // Red
            g2d.fillRect(BOX_X, currentY, recWidth + 20, BOX_HEIGHT);
            g2d.setColor(Color.WHITE);
            textY = currentY + (BOX_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(recText, BOX_X + 10, textY);
            currentY += BOX_HEIGHT;
        }
        
        // Always show interaction mode (edit vs instrument play)
        String interactionText = interactionMode == InteractionMode.PLAY ? "INSTRUMENT MODE (P)" : "EDIT MODE (O)";
        Color bgColor = interactionMode == InteractionMode.PLAY ? new Color(0, 100, 128, 200) : new Color(0, 0, 0, 180);
        
        int textWidth = fm.stringWidth(interactionText);
        g2d.setColor(bgColor);
        g2d.fillRect(BOX_X, currentY, textWidth + 20, BOX_HEIGHT);
        g2d.setColor(Color.WHITE);
        textY = currentY + (BOX_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
        g2d.drawString(interactionText, BOX_X + 10, textY);
        currentY += BOX_HEIGHT;
        
        // Show draw mode if active
        if (currentDrawMode != DrawMode.NONE) {
            String drawText = "Drawing: " + currentDrawMode.name();
            int drawWidth = fm.stringWidth(drawText);
            g2d.setColor(new Color(0, 0, 128, 200));
            g2d.fillRect(BOX_X, currentY, drawWidth + 20, BOX_HEIGHT);
            g2d.setColor(Color.WHITE);
            textY = currentY + (BOX_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(drawText, BOX_X + 10, textY);
            currentY += BOX_HEIGHT;
        }
        
        // Show selection count if multiple
        if (selectedElements.size() > 1) {
            String selText = selectedElements.size() + " selected";
            int selWidth = fm.stringWidth(selText);
            g2d.setColor(new Color(128, 0, 128, 200));
            g2d.fillRect(BOX_X, currentY, selWidth + 20, BOX_HEIGHT);
            g2d.setColor(Color.WHITE);
            textY = currentY + (BOX_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(selText, BOX_X + 10, textY);
        }
    }
    
    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(GRID_COLOR);
        g2d.setStroke(new BasicStroke(1));
        
        int width = getWidth();
        int height = getHeight();
        
        // Draw vertical lines (use width-1 to ensure rightmost line is visible)
        for (int x = 0; x < width; x += GRID_SIZE) {
            g2d.drawLine(x, 0, x, height);
        }
        // Draw final vertical line 1 pixel from edge to ensure visibility
        g2d.drawLine(width - 1, 0, width - 1, height);
        
        // Draw horizontal lines (use height-1 to ensure bottom line is visible)
        for (int y = 0; y < height; y += GRID_SIZE) {
            g2d.drawLine(0, y, width, y);
        }
        // Draw final horizontal line 1 pixel from edge to ensure visibility
        g2d.drawLine(0, height - 1, width, height - 1);
    }
    
    public UndoRedoManager getUndoRedoManager() {
        return undoRedoManager;
    }
}
