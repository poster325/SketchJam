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
    private Color gridColor = new Color(255, 255, 255, 25); // 10% white (changes with brightness)
    private Color canvasBackground = new Color(0x32, 0x32, 0x32); // #323232 (changes with brightness)
    private Color drumColor = Color.WHITE; // Drum/snare color (changes with brightness)
    
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
    private MidiSequencerPanel midiSequencer;
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
    private boolean isDuplicating = false; // Alt+drag to duplicate
    private Point elementStartPos = null;
    private Dimension elementStartSize = null;
    private java.util.Map<DrawableElement, Point> multiSelectStartPositions = new java.util.HashMap<>();
    
    // Track the last element the mouse was over during drag (for re-entry detection)
    private DrawableElement lastElementDuringDrag = null;
    
    // Track currently held piano element for sustain behavior
    private DrawableElement heldPianoElement = null;
    
    // Track drag path for interpolation (fast drag detection)
    private Point lastDragPoint = null;
    private java.util.Set<DrawableElement> playedDuringThisDrag = new java.util.HashSet<>();
    
    // Glow effect tracking: element -> glow intensity (1.0 = full, 0.0 = none)
    private Map<DrawableElement, Float> glowingElements = new HashMap<>();
    private Timer glowFadeTimer;
    private static final float GLOW_FADE_RATE = 0.05f; // How much to fade per tick
    private static final int GLOW_FADE_INTERVAL = 30; // ms between fade ticks
    
    // Zoom and pan state
    private double zoomLevel = 1.0;
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 4.0;
    private static final double ZOOM_STEP = 0.25;
    private int panOffsetX = 0;
    private int panOffsetY = 0;
    private boolean isSpacePressed = false;
    private boolean isPanning = false;
    private Point panStart = null;
    
    // Quasi-mode keys for scaling
    private boolean isHeightScaleMode = false;  // 'S' key - height scaling
    private boolean isWidthScaleMode = false;   // 'A' key - width scaling (guitar)
    private boolean isCKeyPressed = false;      // 'C' key - select same color
    
    public SketchCanvas(UndoRedoManager undoRedoManager) {
        this.undoRedoManager = undoRedoManager;
        setBackground(canvasBackground);
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
    
    /**
     * Convert screen coordinates to canvas coordinates (accounting for zoom and pan)
     */
    private Point screenToCanvas(Point screenPoint) {
        int x = (int)((screenPoint.x - panOffsetX) / zoomLevel);
        int y = (int)((screenPoint.y - panOffsetY) / zoomLevel);
        return new Point(x, y);
    }
    
    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                
                // Handle panning with spacebar
                if (isSpacePressed) {
                    isPanning = true;
                    panStart = e.getPoint();
                    return;
                }
                
                Point p = screenToCanvas(e.getPoint());
                
                // PLAY MODE: Different behavior for each instrument type
                if (interactionMode == InteractionMode.PLAY) {
                    DrawableElement clicked = getElementAt(p);
                    if (clicked != null) {
                        String type = clicked.getElementType();
                        
                        if (type.equals("Piano")) {
                            // Piano: Start sustain note (will stop on release)
                            String mapped = clicked.getMappedValue();
                            int octave = safeParseIntFromMapped(mapped, "piano", 3) + 1;
                            SoundManager.getInstance().startPianoNote(clicked.getColor(), octave, clicked.getOpacity());
                            heldPianoElement = clicked;
                            triggerGlow(clicked);
                            recordElement(clicked, 500); // Record with default duration
                        } else if (type.equals("Guitar")) {
                            // Guitar: Play with duration based on height
                            String mapped = clicked.getMappedValue();
                            int octave = safeParseIntFromMapped(mapped, "guitar", 3);
                            int height = clicked.getBounds().height;
                            int durationMs = Math.max(100, height * 2);
                            SoundManager.getInstance().playGuitarWithDuration(clicked.getColor(), octave, clicked.getOpacity(), height);
                            triggerGlow(clicked);
                            lastElementDuringDrag = clicked;
                            recordElement(clicked, durationMs);
                        } else {
                            // Drums/Snare: Click to play
                            SoundManager.getInstance().playElement(clicked);
                            triggerGlow(clicked);
                            recordElement(clicked, 100);
                        }
                    } else {
                        lastElementDuringDrag = null;
                    }
                    isPlayDragging = true;
                    dragStart = p;
                    lastDragPoint = p;
                    playedDuringThisDrag.clear();
                    if (clicked != null) playedDuringThisDrag.add(clicked);
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
                    // C+click: Select all elements with the same color
                    if (isCKeyPressed) {
                        selectedElements.clear();
                        Color targetColor = clicked.getColor();
                        for (DrawableElement elem : elements) {
                            if (colorsMatch(elem.getColor(), targetColor)) {
                                selectedElements.add(elem);
                            }
                        }
                        selectedElement = clicked;
                        multiSelectStartPositions.clear();
                        for (DrawableElement elem : selectedElements) {
                            multiSelectStartPositions.put(elem, elem.getPosition());
                        }
                        SoundManager.getInstance().playElement(clicked);
                        repaint();
                        return;
                    }
                    
                    // Check if clicking on an element that's part of multi-selection
                    if (selectedElements.contains(clicked) && selectedElements.size() > 1) {
                        // Clicking on element in multi-selection - start dragging all
                        undoRedoManager.saveState(elements);
                        
                        // Alt+click to duplicate all selected elements
                        if (e.isAltDown()) {
                            List<DrawableElement> duplicates = new ArrayList<>();
                            for (DrawableElement elem : selectedElements) {
                                DrawableElement dup = elem.copy();
                                elements.add(dup);
                                duplicates.add(dup);
                            }
                            selectedElements.clear();
                            selectedElements.addAll(duplicates);
                            selectedElement = duplicates.get(0);
                            isDuplicating = true;
                            notifyMidiSequencer();
                        }
                        
                        // Store start positions for all selected elements
                        multiSelectStartPositions.clear();
                        for (DrawableElement elem : selectedElements) {
                            multiSelectStartPositions.put(elem, elem.getPosition());
                        }
                        
                        isDragging = true;
                        dragStart = p;
                        SoundManager.getInstance().playElement(clicked);
                        repaint();
                        return;
                    }
                    
                    // Clear multiple selection and select single element
                    selectedElements.clear();
                    multiSelectStartPositions.clear();
                    
                    // Alt+click to duplicate
                    if (e.isAltDown()) {
                        undoRedoManager.saveState(elements);
                        DrawableElement duplicate = clicked.copy();
                        elements.add(duplicate);
                        selectedElement = duplicate;
                        isDuplicating = true;
                        notifyMidiSequencer();
                    } else {
                        selectedElement = clicked;
                        undoRedoManager.saveState(elements);
                    }
                    
                    isDragging = true;
                    dragStart = p;
                    elementStartPos = selectedElement.getPosition();
                    elementStartSize = selectedElement.getSize();
                    SoundManager.getInstance().playElement(selectedElement);
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
                // Handle panning release
                if (isPanning) {
                    isPanning = false;
                    panStart = null;
                    return;
                }
                
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
                    Point end = snapToGrid(screenToCanvas(e.getPoint()));
                    DrawableElement newElement = createElementFromDrag(dragStart, end);
                    if (newElement != null) {
                        undoRedoManager.saveState(elements);
                        elements.add(newElement);
                        selectedElement = newElement;
                        selectedElements.clear();
                        notifyMidiSequencer();
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
                isDuplicating = false;
                activeHandle = DrawableElement.HANDLE_NONE;
                dragStart = null;
                dragCurrent = null;
                elementStartPos = null;
                elementStartSize = null;
                multiSelectStartPositions.clear();
                repaint();
            }
        });
        
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // Handle panning with spacebar
                if (isPanning && panStart != null) {
                    int dx = e.getX() - panStart.x;
                    int dy = e.getY() - panStart.y;
                    panOffsetX += dx;
                    panOffsetY += dy;
                    panStart = e.getPoint();
                    repaint();
                    return;
                }
                
                Point p = screenToCanvas(e.getPoint());
                
                // PLAY MODE: Different drag behavior for each instrument
                if (interactionMode == InteractionMode.PLAY && isPlayDragging) {
                    // Check all elements along the drag path (interpolate to catch fast drags)
                    java.util.List<DrawableElement> elementsInPath = getElementsAlongPath(lastDragPoint, p);
                    lastDragPoint = p;
                    
                    // If holding a piano and moved off it, check if still on it
                    DrawableElement elementAtPoint = getElementAt(p);
                    if (heldPianoElement != null && elementAtPoint != heldPianoElement) {
                        SoundManager.getInstance().stopPianoNote();
                        heldPianoElement = null;
                    }
                    
                    // Process all elements hit during the drag
                    for (DrawableElement element : elementsInPath) {
                        if (element != null && !playedDuringThisDrag.contains(element)) {
                            playedDuringThisDrag.add(element);
                            String type = element.getElementType();
                            
                            if (type.equals("Guitar")) {
                                String mapped = element.getMappedValue();
                                int octave = safeParseIntFromMapped(mapped, "guitar", 3);
                                int height = element.getBounds().height;
                                SoundManager.getInstance().playGuitarWithDuration(element.getColor(), octave, element.getOpacity(), height);
                                triggerGlow(element);
                                recordElement(element, height * 2);
                            } else if (type.equals("Piano")) {
                                String mapped = element.getMappedValue();
                                int octave = safeParseIntFromMapped(mapped, "piano", 3) + 1;
                                SoundManager.getInstance().startPianoNote(element.getColor(), octave, element.getOpacity());
                                heldPianoElement = element;
                                triggerGlow(element);
                                recordElement(element, 500);
                            } else {
                                SoundManager.getInstance().playElement(element);
                                triggerGlow(element);
                                recordElement(element, 100);
                            }
                        }
                    }
                    
                    lastElementDuringDrag = elementAtPoint;
                    paintImmediately(0, 0, getWidth(), getHeight());
                    return;
                }
                
                // OBJECT MODE dragging
                
                // Quasi-mode scaling with S or A keys
                if ((isHeightScaleMode || isWidthScaleMode) && selectedElement != null && dragStart != null) {
                    handleQuasiModeScaling(p);
                } else if (isResizing && selectedElement != null) {
                    handleResize(p);
                } else if (isDragging && (selectedElement != null || !multiSelectStartPositions.isEmpty())) {
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
        if (dragStart == null) return;
        
        int dx = current.x - dragStart.x;
        int dy = current.y - dragStart.y;
        
        // Handle multi-selection drag
        if (!multiSelectStartPositions.isEmpty()) {
            for (java.util.Map.Entry<DrawableElement, Point> entry : multiSelectStartPositions.entrySet()) {
                DrawableElement elem = entry.getKey();
                Point startPos = entry.getValue();
                int newX = snapValue(startPos.x + dx);
                int newY = snapValue(startPos.y + dy);
                elem.setPosition(newX, newY);
            }
            return;
        }
        
        // Handle single element drag
        if (elementStartPos == null) return;
        
        int newX = snapValue(elementStartPos.x + dx);
        int newY = snapValue(elementStartPos.y + dy);
        
        selectedElement.setPosition(newX, newY);
    }
    
    /**
     * Handle quasi-mode scaling with S (height) or A (width) keys
     */
    private void handleQuasiModeScaling(Point current) {
        if (selectedElement == null || elementStartSize == null) return;
        
        int dy = current.y - dragStart.y;
        int dx = current.x - dragStart.x;
        
        String type = selectedElement.getElementType();
        
        if (isHeightScaleMode) {
            // S key: Scale height (all instruments)
            int newHeight = Math.max(GRID_SIZE, elementStartSize.height + dy);
            
            if ("Piano".equals(type)) {
                // Piano: height determines octave (100-500 range, snaps to 100)
                selectedElement.setSize(PianoElement.FIXED_WIDTH, newHeight);
            } else if ("Guitar".equals(type)) {
                // Guitar: height determines ring time (100-500 range, snaps to 25)
                selectedElement.setSize(elementStartSize.width, newHeight);
            } else if ("Drum".equals(type)) {
                // Drum: size (100-200, snaps to 100/125/150/200)
                selectedElement.setSize(newHeight, newHeight);
            } else if ("Snare Drum".equals(type)) {
                // Snare: size (100-150, snaps to 100/150)
                selectedElement.setSize(newHeight, newHeight);
            }
        }
        
        if (isWidthScaleMode && "Guitar".equals(type)) {
            // A key: Scale width (guitar only - string thickness)
            // Width snaps to: 5, 10, 15, 20, 25 (octaves 5-1)
            int newWidth = Math.max(5, elementStartSize.width + dx);
            selectedElement.setSize(newWidth, elementStartSize.height);
        }
        
        repaint();
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
                return new DrumElement(x, y, drumSize, drumColor);
            case SNARE_DRUM:
                int snareSize = Math.max(width, height);
                return new SnareDrumElement(x, y, snareSize, drumColor);
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
    
    /**
     * Get all elements along a line path (for fast drag detection)
     * Uses Bresenham-style sampling to catch elements between mouse events
     */
    private java.util.List<DrawableElement> getElementsAlongPath(Point from, Point to) {
        java.util.List<DrawableElement> result = new java.util.ArrayList<>();
        if (from == null || to == null) {
            DrawableElement atTo = getElementAt(to);
            if (atTo != null) result.add(atTo);
            return result;
        }
        
        // Calculate distance and number of samples
        int dx = to.x - from.x;
        int dy = to.y - from.y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        // Sample every 5 pixels along the path
        int samples = Math.max(1, (int)(distance / 5));
        
        java.util.Set<DrawableElement> seen = new java.util.HashSet<>();
        for (int i = 0; i <= samples; i++) {
            double t = (double)i / samples;
            int x = (int)(from.x + dx * t);
            int y = (int)(from.y + dy * t);
            
            DrawableElement element = getElementAt(new Point(x, y));
            if (element != null && !seen.contains(element)) {
                seen.add(element);
                result.add(element);
            }
        }
        
        return result;
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
                    notifyMidiSequencer();
                    repaint();
                } else if (selectedElement != null) {
                    undoRedoManager.saveState(elements);
                    elements.remove(selectedElement);
                    selectedElement = null;
                    notifyMidiSequencer();
                    repaint();
                }
            }
        });
        
        // O - Object Mode (editing mode)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_O, 0), "objectMode");
        getActionMap().put("objectMode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Don't allow switching to Edit mode while recording or playing
                if (recordPanel != null && (recordPanel.isRecording() || recordPanel.isPlaying())) {
                    return;
                }
                interactionMode = InteractionMode.OBJECT;
                repaint();
            }
        });
        
        // P - Play Mode (only playing sounds)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0), "playMode");
        getActionMap().put("playMode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Don't allow switching to Edit mode while recording or playing
                if (recordPanel != null && (recordPanel.isRecording() || recordPanel.isPlaying())) {
                    return;
                }
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
        
        // Ctrl + (=) - Zoom in
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK), "zoomIn");
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK), "zoomIn");
        getActionMap().put("zoomIn", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                zoomLevel = Math.min(MAX_ZOOM, zoomLevel + ZOOM_STEP);
                System.out.println("Zoom: " + (int)(zoomLevel * 100) + "%");
                repaint();
            }
        });
        
        // Ctrl - - Zoom out
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK), "zoomOut");
        getActionMap().put("zoomOut", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                zoomLevel = Math.max(MIN_ZOOM, zoomLevel - ZOOM_STEP);
                System.out.println("Zoom: " + (int)(zoomLevel * 100) + "%");
                repaint();
            }
        });
        
        // 1 - Reset zoom and pan to original
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0), "resetView");
        getActionMap().put("resetView", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                zoomLevel = 1.0;
                panOffsetX = 0;
                panOffsetY = 0;
                System.out.println("View reset to original");
                repaint();
            }
        });
        
        // Space pressed - enable panning mode
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), "spacePressed");
        getActionMap().put("spacePressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isSpacePressed = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
        });
        
        // Space released - disable panning mode
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, true), "spaceReleased");
        getActionMap().put("spaceReleased", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isSpacePressed = false;
                isPanning = false;
                panStart = null;
                setCursor(Cursor.getDefaultCursor());
            }
        });
        
        // 'S' pressed - enable height scaling mode
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, false), "sPressed");
        getActionMap().put("sPressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactionMode == InteractionMode.OBJECT) {
                    isHeightScaleMode = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                }
            }
        });
        
        // 'S' released - disable height scaling mode
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true), "sReleased");
        getActionMap().put("sReleased", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isHeightScaleMode = false;
                setCursor(Cursor.getDefaultCursor());
            }
        });
        
        // 'A' pressed - enable width scaling mode (for guitar)
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, false), "aPressed");
        getActionMap().put("aPressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (interactionMode == InteractionMode.OBJECT) {
                    isWidthScaleMode = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                }
            }
        });
        
        // 'A' released - disable width scaling mode
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true), "aReleased");
        getActionMap().put("aReleased", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isWidthScaleMode = false;
                setCursor(Cursor.getDefaultCursor());
            }
        });
        
        // 'C' pressed - enable select same color mode
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0, false), "cPressed");
        getActionMap().put("cPressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isCKeyPressed = true;
            }
        });
        
        // 'C' released - disable select same color mode
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0, true), "cReleased");
        getActionMap().put("cReleased", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isCKeyPressed = false;
            }
        });
        
        // T, Y, U, I - Trigger play on element under mouse (in Instrument/Play mode only)
        // All four keys work as mouse clicks for better keyboard playing experience
        int[] triggerKeys = {KeyEvent.VK_T, KeyEvent.VK_Y, KeyEvent.VK_U, KeyEvent.VK_I};
        for (int key : triggerKeys) {
            String keyName = KeyEvent.getKeyText(key);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, false), "triggerPlay" + keyName);
            getActionMap().put("triggerPlay" + keyName, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (interactionMode != InteractionMode.PLAY) return;
                    
                    // Get mouse position and find element under it
                    Point mousePos = getMousePosition();
                    if (mousePos == null) return;
                    
                    Point canvasPos = screenToCanvas(mousePos);
                    DrawableElement element = getElementAt(canvasPos);
                    
                    if (element != null) {
                        playElementByType(element);
                        triggerGlow(element);
                    }
                }
            });
            
            // Key released - stop piano note if held
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, true), "triggerPlayReleased" + keyName);
            getActionMap().put("triggerPlayReleased" + keyName, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (interactionMode != InteractionMode.PLAY) return;
                    
                    // Stop any held piano note
                    if (heldPianoElement != null) {
                        SoundManager.getInstance().stopPianoNote();
                        heldPianoElement = null;
                    }
                }
            });
        }
    }
    
    /**
     * Play an element based on its type (used by both mouse and T key)
     */
    private void playElementByType(DrawableElement element) {
        String type = element.getElementType();
        
        if (type.equals("Piano")) {
            // Piano: Start sustain
            String mapped = element.getMappedValue();
            int octave = safeParseIntFromMapped(mapped, "piano", 3) + 1;
            SoundManager.getInstance().startPianoNote(element.getColor(), octave, element.getOpacity());
            heldPianoElement = element;
            recordElement(element, 0);
        } else if (type.equals("Guitar")) {
            // Guitar: Play with duration based on height
            String mapped = element.getMappedValue();
            int octave = safeParseIntFromMapped(mapped, "guitar", 3);
            int height = element.getBounds().height;
            SoundManager.getInstance().playGuitarWithDuration(element.getColor(), octave, element.getOpacity(), height);
            recordElement(element, height * 2);
        } else {
            // Drums/Snare: Just play
            SoundManager.getInstance().playElement(element);
            recordElement(element, 100);
        }
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
    
    public void setMidiSequencer(MidiSequencerPanel midiSequencer) {
        this.midiSequencer = midiSequencer;
    }
    
    /**
     * Notify MIDI sequencer that elements have changed
     */
    private void notifyMidiSequencer() {
        if (midiSequencer != null) {
            midiSequencer.refreshFromCanvas();
        }
    }
    
    /**
     * Set the interaction mode (called from RecordPanel when play/record is toggled)
     */
    public void setInteractionMode(InteractionMode mode) {
        this.interactionMode = mode;
        repaint();
    }
    
    public InteractionMode getInteractionMode() {
        return interactionMode;
    }
    

    private int getVariantFromElement(DrawableElement element) {
        String type = element.getElementType();

        if (type.equals("Drum") || type.equals("Snare Drum")) {
            int size = element.getBounds().width; // 원형이면 width==height
            if (size < 40) return 0;
            if (size < 70) return 1;
            return 2;
        }

        if (type.equals("Piano")) {
            // ❌ octave 변수가 없으니 직접 쓰면 안 됨
            // ✅ 기존 함수로 octave를 구해서 variant로 쓰기
            return getOctaveFromElement(element);
        }

        if (type.equals("Guitar")) {
            // 기타는 원하면 높이를 variant로 저장 가능
            return element.getBounds().height;
        }

        return 0;
    }


    private int getUiOctaveFromElement(DrawableElement element) {
        String mapped = element.getMappedValue();
        if (mapped == null) return 3;

        try {
            // 네 mapped 포맷에 맞춰 여기만 정확히 파싱하면 됨
            // 예: "C 3, ..." 혹은 "C 3"
            String[] parts = mapped.split(",")[0].trim().split(" ");
            return Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return 3;
        }
    }

    private int getDrumKeyFromSize(DrawableElement element) {
        Rectangle b = element.getBounds();
        int size = Math.max(b.width, b.height);

        // 너희 DrumElement의 스냅 규칙이 있으면 거기에 맞춰 구간을 잡는 게 좋음.
        // 일단 예시:
        if (size < 50) return 0;      // small
        if (size < 100) return 1;     // mid
        return 2;                     // big
    }

    private int drumKeyFromMapped(String mapped) {
        if (mapped == null) return 0;
        String m = mapped.toLowerCase().replace(" ", "_");
        switch (m) {
            case "high_tom":  return 0;
            case "mid_tom":   return 1;
            case "floor_tom": return 2;
            case "bass_drum": return 3;
            default:          return 3;
        }
    }

    
    private int snareKeyFromMapped(String mapped) {
        if (mapped == null) return 1;
        String m = mapped.toLowerCase().replace(" ", "_");
        switch (m) {
            case "snare_rim":
            case "rim":
            case "rimshot":
                return 0;
            case "snare_center":
            case "center":
            default:
                return 1;
        }
    }


    private static int packGuitarParams(float saturation, int heightPx) {
        int satMilli = Math.max(0, Math.min(65535, (int)(saturation * 1000f)));
        int h = Math.max(0, Math.min(65535, heightPx));
        return (satMilli << 16) | (h & 0xFFFF);
    }
    private int mapDrumToMidi(String mapped) {
        if (mapped == null) return 36; // default bass drum

        String s = mapped.trim().toLowerCase();

        // 너가 올려준 상수 기준:
        // HIGH_TOM=50, MID_TOM=47, FLOOR_TOM=43, BASS_DRUM=36
        if (s.contains("high") && s.contains("tom")) return 50;
        if (s.contains("mid") && s.contains("tom")) return 47;
        if (s.contains("floor") && s.contains("tom")) return 43;

        // "bass drum" 혹은 그 외 기본
        return 36;
    }

    private int mapSnareToMidi(String mapped) {
        if (mapped == null) return 38; // default snare center

        String s = mapped.trim().toLowerCase();

        // 너가 올려준 상수 기준:
        // SNARE_RIM=37, SNARE_CENTER=38
        // mapped가 어떤 문자열을 주는지에 따라 조건을 조금 넓게 잡음
        if (s.contains("rim")) return 37;
        if (s.contains("center")) return 38;

        // 혹시 "snare rim"/"snare center" 말고 다른 표현이면 기본 center
        return 38;
    }

    private int safeParseIntFromMapped(String mapped, String kind, int fallback) {
        try {
            if (mapped == null) return fallback;

            if ("piano".equals(kind)) {
                // 예: "Octave 3" 같은 형태를 기대
                // 네 코드가 mapped.split(" ")[1]을 쓰는 형태였음
                String[] sp = mapped.trim().split("\\s+");
                if (sp.length >= 2) return Integer.parseInt(sp[1]);
                return fallback;
            }

            if ("guitar".equals(kind)) {
                // 예: "Octave 3, something" 같은 형태를 기대
                String left = mapped.split(",")[0];
                String[] sp = left.trim().split("\\s+");
                if (sp.length >= 2) return Integer.parseInt(sp[1]);
                return fallback;
            }

            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    
    /**
     * Record an element event if recording is active
     */
    private void recordElement(DrawableElement element, int durationMs) {
        if (recordPanel == null || !recordPanel.isRecording()) return;

        String type = element.getElementType();
        float velocity = element.getOpacity();
        String elementId = element.getElementId();
        int colorRGB = element.getColor().getRGB();
        int midiNote = 0;
        int drumKey = 0;
        int heightPx = 0;

        if ("Piano".equals(type)) {
            String mapped = element.getMappedValue();
            int octaveParam = safeParseIntFromMapped(mapped, "piano", 0) + 1;
            int noteIndex = getNoteIndexFromColor(element.getColor());
            midiNote = (octaveParam + 1) * 12 + noteIndex;
            heightPx = 0;
        } else if ("Guitar".equals(type)) {
            String mapped = element.getMappedValue();
            int octaveParam = safeParseIntFromMapped(mapped, "guitar", 0);
            int noteIndex = getNoteIndexFromColor(element.getColor());
            midiNote = (octaveParam + 1) * 12 + noteIndex;
            heightPx = element.getBounds().height;
        } else if ("Drum".equals(type)) {
            String mapped = element.getMappedValue();
            drumKey = mapDrumToMidi(mapped);
            type = "Drum";
        } else if ("Snare Drum".equals(type)) {
            String mapped = element.getMappedValue();
            drumKey = mapSnareToMidi(mapped);
            type = "Snare";
        } else {
            return;
        }

        // Record to MIDI sequencer if recording to MIDI
        if (recordPanel.isRecordingToMidi()) {
            recordPanel.recordMidiNote(type, midiNote, drumKey, velocity, durationMs, colorRGB, heightPx, elementId);
        } else {
            // Legacy: Record to TrackManager
            TrackManager tm = recordPanel.getTrackManager();
            if (tm != null) {
                tm.recordEvent(type, midiNote, drumKey, velocity, durationMs, colorRGB, heightPx, elementId);
            }
        }
    }
    
    private int getNoteIndexFromColor(Color color) {
        // Find closest matching note color (0-11 for C to B)
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        // Map hue (0-1) to note index (0-11)
        return (int)(hsb[0] * 12) % 12;
    }
    
    /**
     * Check if two colors match (same RGB values)
     */
    private boolean colorsMatch(Color c1, Color c2) {
        if (c1 == null || c2 == null) return false;
        return c1.getRed() == c2.getRed() && 
               c1.getGreen() == c2.getGreen() && 
               c1.getBlue() == c2.getBlue();
    }
    
    private int getOctaveFromElement(DrawableElement element) {
        String mapped = element.getMappedValue();
        if (mapped != null && mapped.contains("Octave")) {
            try {
                String[] parts = mapped.split(",")[0].split(" ");
                return Integer.parseInt(parts[1]) +1;
            } catch (Exception e) {
                return 3; // Default octave
            }
        }
        return 3;
    }
    
    public DrawableElement getSelectedElement() {
        return selectedElement;
    }
    
    /**
     * Find an element by its unique ID
     */
    public DrawableElement getElementById(String elementId) {
        if (elementId == null) return null;
        for (DrawableElement element : elements) {
            if (elementId.equals(element.getElementId())) {
                return element;
            }
        }
        return null;
    }
    
    public DrawMode getCurrentMode() {
        return currentDrawMode;
    }
    
    public List<DrawableElement> getElements() {
        return elements;
    }
    
    public void clearElements() {
        elements.clear();
        selectedElement = null;
        selectedElements.clear();
        notifyMidiSequencer();
        repaint();
    }
    
    public void addElement(DrawableElement element) {
        elements.add(element);
        notifyMidiSequencer();
        repaint();
    }
    
    public void setElements(List<DrawableElement> elements) {
        this.elements = new ArrayList<>(elements);
        // Clear selection state when elements are restored (undo/redo)
        selectedElement = null;
        selectedElements.clear();
        notifyMidiSequencer();
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Save original transform
        java.awt.geom.AffineTransform originalTransform = g2d.getTransform();
        
        // Apply zoom and pan transformation
        g2d.translate(panOffsetX, panOffsetY);
        g2d.scale(zoomLevel, zoomLevel);
        
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
        
        // Restore original transform for UI elements (mode indicator stays fixed)
        g2d.setTransform(originalTransform);
        
        // Draw mode indicator (fixed position, not affected by zoom/pan)
        drawModeIndicator(g2d);
        
        // Draw zoom level indicator
        drawZoomIndicator(g2d);
    }
    
    private void drawZoomIndicator(Graphics2D g2d) {
        if (zoomLevel != 1.0 || panOffsetX != 0 || panOffsetY != 0) {
            String zoomText = (int)(zoomLevel * 100) + "%";
            g2d.setFont(FontManager.getRegular(12));
            g2d.setColor(new Color(0, 0, 0, 180));
            // Position at bottom left of canvas
            int y = getHeight() - 25;
            g2d.fillRect(0, y, 75, 25);
            g2d.setColor(Color.WHITE);
            g2d.drawString(zoomText, 10, y + 17);
        }
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
        
        // Show preview mode from RecordPanel (playback without metronome)
        if (recordPanel != null && recordPanel.isPlaying()) {
            String playText = "PREVIEW MODE";
            int playWidth = fm.stringWidth(playText);
            g2d.setColor(new Color(0, 128, 128, 200)); // Teal
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
        g2d.setColor(gridColor);
        g2d.setStroke(new BasicStroke(1));
        
        // Calculate visible area in canvas coordinates (accounting for zoom and pan)
        // The transform is already applied, so we need to find what screen area maps to
        int screenWidth = getWidth();
        int screenHeight = getHeight();
        
        // Calculate the visible canvas area bounds
        int startX = (int)((-panOffsetX / zoomLevel) - GRID_SIZE);
        int startY = (int)((-panOffsetY / zoomLevel) - GRID_SIZE);
        int endX = (int)((screenWidth - panOffsetX) / zoomLevel) + GRID_SIZE;
        int endY = (int)((screenHeight - panOffsetY) / zoomLevel) + GRID_SIZE;
        
        // Snap to grid
        startX = (startX / GRID_SIZE) * GRID_SIZE;
        startY = (startY / GRID_SIZE) * GRID_SIZE;
        
        // Draw vertical lines across the extended visible area
        for (int x = startX; x <= endX; x += GRID_SIZE) {
            g2d.drawLine(x, startY, x, endY);
        }
        
        // Draw horizontal lines across the extended visible area
        for (int y = startY; y <= endY; y += GRID_SIZE) {
            g2d.drawLine(startX, y, endX, y);
        }
    }
    
    /**
     * Set canvas background color (called from EQ panel)
     */
    public void setCanvasBackground(Color color) {
        this.canvasBackground = color;
        setBackground(color);
        repaint();
    }
    
    /**
     * Set element colors based on brightness (grid and drums)
     * @param useDark true for dark elements (light background), false for light elements (dark background)
     */
    public void setElementColors(boolean useDark) {
        if (useDark) {
            gridColor = new Color(0, 0, 0, 25); // 10% black
            drumColor = Color.BLACK;
        } else {
            gridColor = new Color(255, 255, 255, 25); // 10% white
            drumColor = Color.WHITE;
        }
        
        // Update existing drum elements
        for (DrawableElement element : elements) {
            if (element instanceof DrumElement || element instanceof SnareDrumElement) {
                element.setColor(drumColor);
            }
        }
        
        repaint();
    }
    
    public Color getDrumColor() {
        return drumColor;
    }
    
    public UndoRedoManager getUndoRedoManager() {
        return undoRedoManager;
    }
}
