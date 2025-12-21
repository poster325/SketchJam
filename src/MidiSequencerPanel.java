import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * Visual MIDI sequencer / piano roll editor
 */
public class MidiSequencerPanel extends JPanel implements MidiSequence.SequenceListener {
    
    // Layout constants - aligned to 25px grid
    private static final int HEADER_HEIGHT = 25;
    private static final int ROW_LABEL_WIDTH = 100;
    private static final int ROW_HEIGHT = 25;  // Match 25px grid
    private static final int MIN_ROWS = 1;
    private static final int SCROLLBAR_WIDTH = 12;
    
    // Reference to canvas for element-based rows
    private SketchCanvas canvas;
    
    // Colors
    private static final Color BG_COLOR = new Color(0x2A, 0x2A, 0x2A);
    private static final Color GRID_COLOR = new Color(0x3A, 0x3A, 0x3A);
    private static final Color GRID_BEAT_COLOR = new Color(0x4A, 0x4A, 0x4A);
    private static final Color GRID_BAR_COLOR = new Color(0x5A, 0x5A, 0x5A);
    private static final Color HEADER_BG = new Color(0x38, 0x38, 0x38);
    private static final Color HEADER_TEXT = new Color(0xAA, 0xAA, 0xAA);
    private static final Color ROW_LABEL_BG = new Color(0x33, 0x33, 0x33);
    private static final Color ROW_LABEL_TEXT = new Color(0xCC, 0xCC, 0xCC);
    private static final Color NOTE_COLOR = new Color(0xE0, 0x40, 0x40);  // Red
    private static final Color NOTE_SELECTED_COLOR = new Color(0x00, 0xBF, 0xFF);  // Cyan
    private static final Color NOTE_BORDER = new Color(0x80, 0x20, 0x20);
    private static final Color PLAYHEAD_COLOR = new Color(0x00, 0xFF, 0x80);
    private static final Color SELECTION_RECT_COLOR = new Color(0x00, 0xBF, 0xFF, 80);
    
    // State
    private MidiSequence sequence;
    private double pixelsPerBeat = 50.0;
    private double scrollOffsetBeats = 0;
    private int scrollOffsetRows = 0;
    private double playheadBeat = 0;
    private boolean isPlaying = false;
    
    // Row management
    private List<RowInfo> rows = new ArrayList<>();
    
    // Mouse interaction
    private enum DragMode { NONE, SELECT_BOX, MOVE_NOTE, RESIZE_NOTE, PAN }
    private DragMode dragMode = DragMode.NONE;
    private Point dragStart;
    private Point dragCurrent;
    private MidiNote dragNote;
    private double dragNoteStartBeat;
    private double dragNoteDuration;
    private boolean isDuplicating = false;
    private boolean hasDuplicated = false;
    private java.util.Map<MidiNote, Double> originalNotePositions = new java.util.HashMap<>();
    
    // Quasi-mode for duration adjustment (S key)
    private boolean isDurationAdjustMode = false;
    private java.util.Map<MidiNote, Double> originalNoteDurations = new java.util.HashMap<>();
    
    // Grid snap
    private double snapBeat = 0.25; // Snap to 16th notes by default
    
    // Row info for display
    private static class RowInfo {
        String key;
        String label;
        Color color;
        
        RowInfo(String key, String label, Color color) {
            this.key = key;
            this.label = label;
            this.color = color;
        }
    }
    
    public MidiSequencerPanel() {
        this.sequence = new MidiSequence();
        sequence.addListener(this);
        
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(800, 200));
        
        setupMouseHandlers();
        setupKeyBindings();
        
        // Initialize default rows
        updateRows();
    }
    
    public void setSequence(MidiSequence sequence) {
        if (this.sequence != null) {
            this.sequence.removeListener(this);
        }
        this.sequence = sequence;
        if (sequence != null) {
            sequence.addListener(this);
        }
        updateRows();
        repaint();
    }
    
    public MidiSequence getSequence() {
        return sequence;
    }
    
    public void setCanvas(SketchCanvas canvas) {
        this.canvas = canvas;
        updateRows();
        repaint();
    }
    
    /**
     * Refresh rows from canvas elements (call when elements change)
     */
    public void refreshFromCanvas() {
        updateRows();
        repaint();
    }
    
    private void setupMouseHandlers() {
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePressed(e);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouseReleased(e);
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseDragged(e);
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleDoubleClick(e);
                }
            }
            
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                handleMouseWheel(e);
            }
        };
        
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
        addMouseWheelListener(mouseHandler);
    }
    
    private void setupKeyBindings() {
        setFocusable(true);
        
        // Delete selected notes
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "delete");
        getActionMap().put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sequence.saveState();
                sequence.removeSelectedNotes();
            }
        });
        
        // Duplicate with Ctrl+D
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK), "duplicate");
        getActionMap().put("duplicate", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sequence.saveState();
                sequence.duplicateSelectedNotes();
            }
        });
        
        // Undo with Ctrl+Z
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sequence.undo();
            }
        });
        
        // Redo with Ctrl+Y
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        getActionMap().put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sequence.redo();
            }
        });
        
        // Select all with Ctrl+A
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), "selectAll");
        getActionMap().put("selectAll", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                for (MidiNote note : sequence.getNotes()) {
                    note.setSelected(true);
                }
                repaint();
            }
        });
        
        // Quantize with Q
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0), "quantize");
        getActionMap().put("quantize", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sequence.saveState();
                sequence.quantizeSelectedNotes(snapBeat);
            }
        });
        
        // Move selected notes left with Left Arrow
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "moveLeft");
        getActionMap().put("moveLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sequence.saveState();
                moveSelectedNotes(-snapBeat);
            }
        });
        
        // Move selected notes right with Right Arrow
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "moveRight");
        getActionMap().put("moveRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sequence.saveState();
                moveSelectedNotes(snapBeat);
            }
        });
        
        // Move selected notes up with Up Arrow (change row)
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "moveUp");
        getActionMap().put("moveUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sequence.saveState();
                moveSelectedNotesVertically(-1);
            }
        });
        
        // Move selected notes down with Down Arrow (change row)
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "moveDown");
        getActionMap().put("moveDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sequence.saveState();
                moveSelectedNotesVertically(1);
            }
        });
        
        // S pressed - enable duration adjustment mode
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, false), "sPressed");
        getActionMap().put("sPressed", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isDurationAdjustMode = true;
                // Save state before duration adjustment
                if (!sequence.getSelectedNotes().isEmpty()) {
                    sequence.saveState();
                }
                // Store original durations of selected notes
                originalNoteDurations.clear();
                for (MidiNote note : sequence.getSelectedNotes()) {
                    originalNoteDurations.put(note, note.getDurationBeats());
                }
                setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            }
        });
        
        // S released - disable duration adjustment mode
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true), "sReleased");
        getActionMap().put("sReleased", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                isDurationAdjustMode = false;
                originalNoteDurations.clear();
                setCursor(Cursor.getDefaultCursor());
            }
        });
    }
    
    private void handleMousePressed(MouseEvent e) {
        requestFocusInWindow();
        dragStart = e.getPoint();
        dragCurrent = e.getPoint();
        
        // Check if clicking on a note
        MidiNote clickedNote = getNoteAt(e.getPoint());
        
        if (clickedNote != null) {
            // Check if clicking on resize handle (right edge)
            // Only use resize if note is wide enough (>20px) and clicking on right 6px
            Rectangle noteRect = getNoteRect(clickedNote);
            int resizeZone = Math.min(6, noteRect.width / 4); // Smaller zone for short notes
            boolean isResizeArea = noteRect != null && noteRect.width > 15 && 
                                   e.getX() > noteRect.x + noteRect.width - resizeZone;
            
            if (isResizeArea) {
                sequence.saveState(); // Save state before resize
                dragMode = DragMode.RESIZE_NOTE;
                dragNote = clickedNote;
                dragNoteDuration = clickedNote.getDurationBeats();
                isDuplicating = false;
                hasDuplicated = false;
                if (!clickedNote.isSelected()) {
                    sequence.selectNote(clickedNote, e.isShiftDown());
                }
            } else {
                // Moving note(s) - Alt+drag to duplicate
                sequence.saveState(); // Save state before move
                dragMode = DragMode.MOVE_NOTE;
                dragNote = clickedNote;
                dragNoteStartBeat = clickedNote.getStartBeat();
                isDuplicating = e.isAltDown();
                hasDuplicated = false;
                
                // Store original positions of all selected notes
                originalNotePositions.clear();
                if (!clickedNote.isSelected()) {
                    sequence.selectNote(clickedNote, e.isShiftDown());
                }
                for (MidiNote note : sequence.getSelectedNotes()) {
                    originalNotePositions.put(note, note.getStartBeat());
                }
                if (!clickedNote.isSelected()) {
                    sequence.selectNote(clickedNote, e.isShiftDown());
                }
            }
        } else if (SwingUtilities.isMiddleMouseButton(e)) {
            // Pan with middle mouse only (Alt+drag on note = duplicate)
            dragMode = DragMode.PAN;
            isDuplicating = false;
        } else {
            // Box selection
            dragMode = DragMode.SELECT_BOX;
            if (!e.isShiftDown()) {
                sequence.clearSelection();
            }
        }
        
        repaint();
    }
    
    private void handleMouseDragged(MouseEvent e) {
        dragCurrent = e.getPoint();
        
        // S+drag: Adjust duration of selected notes - cursor sets the end position
        if (isDurationAdjustMode && !originalNoteDurations.isEmpty()) {
            double cursorBeat = snapToGrid(xToBeat(dragCurrent.x));
            
            for (MidiNote note : sequence.getSelectedNotes()) {
                // The note's end should follow the cursor
                double newEndBeat = cursorBeat;
                double newDuration = newEndBeat - note.getStartBeat();
                // Ensure minimum duration
                newDuration = Math.max(snapBeat, newDuration);
                note.setDurationBeats(newDuration);
            }
            repaint();
            return;
        }
        
        switch (dragMode) {
            case MOVE_NOTE:
                if (dragNote != null) {
                    // Alt+drag to duplicate (only once at start of drag)
                    if (isDuplicating && !hasDuplicated) {
                        duplicateSelectedNotesInPlace();
                        hasDuplicated = true;
                        // Update original positions for the new duplicates
                        originalNotePositions.clear();
                        for (MidiNote note : sequence.getSelectedNotes()) {
                            originalNotePositions.put(note, note.getStartBeat());
                        }
                    }
                    
                    // Calculate delta from original drag start position
                    double deltaBeat = xToBeat(dragCurrent.x) - xToBeat(dragStart.x);
                    
                    // Move all selected notes based on their original positions
                    for (MidiNote note : sequence.getSelectedNotes()) {
                        Double originalBeat = originalNotePositions.get(note);
                        if (originalBeat != null) {
                            double newBeat = snapToGrid(originalBeat + deltaBeat);
                            note.setStartBeat(newBeat);
                        }
                    }
                }
                break;
                
            case RESIZE_NOTE:
                if (dragNote != null) {
                    double endBeat = xToBeat(dragCurrent.x);
                    endBeat = snapToGrid(endBeat);
                    double newDuration = Math.max(snapBeat, endBeat - dragNote.getStartBeat());
                    dragNote.setDurationBeats(newDuration);
                }
                break;
                
            case PAN:
                double dx = dragStart.x - dragCurrent.x;
                scrollOffsetBeats += dx / pixelsPerBeat;
                scrollOffsetBeats = Math.max(0, scrollOffsetBeats);
                dragStart = dragCurrent;
                break;
                
            case SELECT_BOX:
                // Box selection handled in paint
                break;
        }
        
        repaint();
    }
    
    private void handleMouseReleased(MouseEvent e) {
        if (dragMode == DragMode.SELECT_BOX && dragStart != null && dragCurrent != null) {
            // Finalize box selection
            double startBeat = xToBeat(Math.min(dragStart.x, dragCurrent.x));
            double endBeat = xToBeat(Math.max(dragStart.x, dragCurrent.x));
            int startRow = yToRow(Math.min(dragStart.y, dragCurrent.y));
            int endRow = yToRow(Math.max(dragStart.y, dragCurrent.y));
            
            sequence.selectNotesInRect(startBeat, endBeat, startRow, endRow, e.isShiftDown());
        }
        
        dragMode = DragMode.NONE;
        dragStart = null;
        dragCurrent = null;
        dragNote = null;
        isDuplicating = false;
        hasDuplicated = false;
        originalNotePositions.clear();
        
        repaint();
    }
    
    private void handleDoubleClick(MouseEvent e) {
        // Double-click in grid area creates a new note
        if (e.getX() > ROW_LABEL_WIDTH && e.getY() > HEADER_HEIGHT) {
            double beat = snapToGrid(xToBeat(e.getX()));
            int row = yToRow(e.getY());
            
            if (row >= 0 && row < rows.size()) {
                RowInfo rowInfo = rows.get(row);
                
                // Create a new note with default properties
                MidiNote note = createNoteForRow(rowInfo, beat, snapBeat * 4);
                if (note != null) {
                    sequence.saveState(); // Save state before creating note
                    note.setRowIndex(row);
                    sequence.addNote(note);
                    sequence.selectNote(note, false);
                }
            }
        }
    }
    
    private MidiNote createNoteForRow(RowInfo rowInfo, double startBeat, double duration) {
        // Parse row key to create appropriate note
        String[] parts = rowInfo.key.split("_");
        if (parts.length < 2) return null;
        
        String instType = parts[0];
        int noteOrKey = 60;
        try {
            noteOrKey = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {}
        
        int midiNote = 60;
        int drumKey = 0;
        
        if ("DRUM".equals(instType) || "SNARE".equals(instType)) {
            drumKey = noteOrKey;
        } else {
            midiNote = noteOrKey;
        }
        
        return new MidiNote(
            startBeat, duration, instType,
            midiNote, drumKey, 1.0f,
            rowInfo.color.getRGB(), 50, null
        );
    }
    
    private void handleMouseWheel(MouseWheelEvent e) {
        if (e.isControlDown()) {
            // Zoom horizontal
            double oldPpb = pixelsPerBeat;
            pixelsPerBeat *= e.getWheelRotation() < 0 ? 1.2 : 0.8;
            pixelsPerBeat = Math.max(20, Math.min(200, pixelsPerBeat));
            
            // Adjust scroll to keep mouse position stable
            double beatUnderMouse = xToBeat(e.getX());
            scrollOffsetBeats += beatUnderMouse * (1 - pixelsPerBeat / oldPpb);
            scrollOffsetBeats = Math.max(0, scrollOffsetBeats);
        } else if (e.isAltDown()) {
            // Alt+scroll: Scroll vertical (through rows)
            scrollOffsetRows += e.getWheelRotation();
            scrollOffsetRows = Math.max(0, Math.min(rows.size() - 1, scrollOffsetRows));
        } else {
            // Scroll horizontal
            scrollOffsetBeats += e.getWheelRotation() * 0.5;
            scrollOffsetBeats = Math.max(0, scrollOffsetBeats);
        }
        repaint();
    }
    
    private double snapToGrid(double beat) {
        return Math.round(beat / snapBeat) * snapBeat;
    }
    
    private double xToBeat(int x) {
        return (x - ROW_LABEL_WIDTH) / pixelsPerBeat + scrollOffsetBeats;
    }
    
    private int beatToX(double beat) {
        return (int) ((beat - scrollOffsetBeats) * pixelsPerBeat + ROW_LABEL_WIDTH);
    }
    
    private int yToRow(int y) {
        return (y - HEADER_HEIGHT) / ROW_HEIGHT + scrollOffsetRows;
    }
    
    private int rowToY(int row) {
        return (row - scrollOffsetRows) * ROW_HEIGHT + HEADER_HEIGHT;
    }
    
    private MidiNote getNoteAt(Point p) {
        for (MidiNote note : sequence.getNotes()) {
            Rectangle rect = getNoteRect(note);
            if (rect != null && rect.contains(p)) {
                return note;
            }
        }
        return null;
    }
    
    private Rectangle getNoteRect(MidiNote note) {
        int x = beatToX(note.getStartBeat());
        int y = rowToY(note.getRowIndex());
        int width = (int) (note.getDurationBeats() * pixelsPerBeat);
        int height = ROW_HEIGHT - 2;
        
        // Check if visible
        if (x + width < ROW_LABEL_WIDTH || x > getWidth() || 
            y + height < HEADER_HEIGHT || y > getHeight()) {
            return null;
        }
        
        return new Rectangle(x, y + 1, Math.max(4, width), height);
    }
    
    private void updateRows() {
        rows.clear();
        
        // Build rows from canvas elements
        if (canvas != null) {
            List<DrawableElement> elements = canvas.getElements();
            for (int i = 0; i < elements.size(); i++) {
                DrawableElement elem = elements.get(i);
                String elementId = elem.getElementId();
                String type = elem.getElementType();
                String label = type + " " + (i + 1);
                Color color = elem.getColor();
                
                // Use element ID as the row key for unique identification
                rows.add(new RowInfo(elementId, label, color));
            }
        }
        
        // If no elements, show empty message row
        if (rows.isEmpty()) {
            rows.add(new RowInfo("_empty_", "(No elements)", new Color(0x60, 0x60, 0x60)));
        }
        
        // Update row indices on notes based on element ID
        for (MidiNote note : sequence.getNotes()) {
            String noteElementId = note.getElementId();
            boolean found = false;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).key.equals(noteElementId)) {
                    note.setRowIndex(i);
                    found = true;
                    break;
                }
            }
            // If element was deleted, assign to first row
            if (!found && !rows.isEmpty()) {
                note.setRowIndex(0);
            }
        }
    }
    
    private String formatRowLabel(String key) {
        String[] parts = key.split("_");
        if (parts.length < 2) return key;
        
        String type = parts[0];
        String value = parts[1];
        
        switch (type) {
            case "PIANO":
                try {
                    int midi = Integer.parseInt(value);
                    return "Piano " + getNoteNameFromMidi(midi);
                } catch (NumberFormatException e) {
                    return "Piano " + value;
                }
            case "GUITAR":
                try {
                    int midi = Integer.parseInt(value);
                    return "Guitar " + getNoteNameFromMidi(midi);
                } catch (NumberFormatException e) {
                    return "Guitar " + value;
                }
            case "DRUM":
                switch (value) {
                    case "0": return "Kick";
                    case "1": return "Snare";
                    case "2": return "Tom";
                    default: return "Drum " + value;
                }
            case "SNARE":
                switch (value) {
                    case "0": return "Snare Lo";
                    case "1": return "Snare Mid";
                    case "2": return "Snare Hi";
                    default: return "Snare " + value;
                }
            default:
                return type + " " + value;
        }
    }
    
    private String getNoteNameFromMidi(int midi) {
        String[] notes = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int octave = (midi / 12) - 1;
        int noteIdx = midi % 12;
        return notes[noteIdx] + octave;
    }
    
    private Color getRowColor(String key, int index) {
        String[] parts = key.split("_");
        String type = parts[0];
        
        switch (type) {
            case "PIANO": return new Color(0x40, 0x80, 0xC0);
            case "GUITAR": return new Color(0xC0, 0x80, 0x40);
            case "DRUM": return new Color(0xC0, 0x40, 0x40);
            case "SNARE": return new Color(0x80, 0xC0, 0x40);
            default: return new Color(0x80, 0x80, 0x80);
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int width = getWidth();
        int height = getHeight();
        int gridWidth = width - ROW_LABEL_WIDTH;
        int gridHeight = height - HEADER_HEIGHT;
        
        // Draw grid background
        drawGrid(g2d, gridWidth, gridHeight);
        
        // Draw notes
        drawNotes(g2d);
        
        // Draw row labels (left panel)
        drawRowLabels(g2d, height);
        
        // Draw timeline header
        drawHeader(g2d, width);
        
        // Draw playhead
        drawPlayhead(g2d, height);
        
        // Draw selection rectangle
        if (dragMode == DragMode.SELECT_BOX && dragStart != null && dragCurrent != null) {
            drawSelectionRect(g2d);
        }
        
        // Draw loop region indicator
        drawLoopRegion(g2d, height);
        
        g2d.dispose();
    }
    
    private void drawGrid(Graphics2D g2d, int gridWidth, int gridHeight) {
        g2d.setColor(BG_COLOR);
        g2d.fillRect(ROW_LABEL_WIDTH, HEADER_HEIGHT, gridWidth, gridHeight);
        
        // Draw horizontal row lines
        g2d.setColor(GRID_COLOR);
        for (int row = 0; row < rows.size() + 1; row++) {
            int y = rowToY(row);
            if (y >= HEADER_HEIGHT && y <= getHeight()) {
                g2d.drawLine(ROW_LABEL_WIDTH, y, getWidth(), y);
            }
        }
        
        // Draw vertical beat lines
        double startBeat = Math.floor(scrollOffsetBeats);
        double endBeat = startBeat + gridWidth / pixelsPerBeat + 1;
        
        for (double beat = startBeat; beat <= endBeat; beat += 0.25) {
            int x = beatToX(beat);
            if (x < ROW_LABEL_WIDTH) continue;
            
            if (beat % 4 == 0) {
                // Bar line
                g2d.setColor(GRID_BAR_COLOR);
                g2d.setStroke(new BasicStroke(1.5f));
            } else if (beat % 1 == 0) {
                // Beat line
                g2d.setColor(GRID_BEAT_COLOR);
                g2d.setStroke(new BasicStroke(1f));
            } else {
                // Sub-beat line
                g2d.setColor(GRID_COLOR);
                g2d.setStroke(new BasicStroke(0.5f));
            }
            
            g2d.drawLine(x, HEADER_HEIGHT, x, getHeight());
        }
        
        g2d.setStroke(new BasicStroke(1f));
    }
    
    private void drawNotes(Graphics2D g2d) {
        for (MidiNote note : sequence.getNotes()) {
            Rectangle rect = getNoteRect(note);
            if (rect == null) continue;
            
            // Note fill - use track color
            Color baseColor = note.getTrackColor();
            g2d.setColor(baseColor);
            g2d.fill(new RoundRectangle2D.Double(rect.x, rect.y, rect.width, rect.height, 4, 4));
            
            // If selected, add whitening highlight overlay
            if (note.isSelected()) {
                g2d.setColor(new Color(255, 255, 255, 120)); // Semi-transparent white overlay
                g2d.fill(new RoundRectangle2D.Double(rect.x, rect.y, rect.width, rect.height, 4, 4));
                
                // Add a bright white border for selected notes
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(2f));
                g2d.draw(new RoundRectangle2D.Double(rect.x, rect.y, rect.width, rect.height, 4, 4));
                g2d.setStroke(new BasicStroke(1f));
            } else {
                // Note border - darker version of track color
                g2d.setColor(baseColor.darker());
                g2d.draw(new RoundRectangle2D.Double(rect.x, rect.y, rect.width, rect.height, 4, 4));
            }
            
            // Resize handle indicator
            if (rect.width > 15) {
                g2d.setColor(new Color(255, 255, 255, 60));
                g2d.fillRect(rect.x + rect.width - 4, rect.y + 2, 2, rect.height - 4);
            }
        }
    }
    
    private void drawRowLabels(Graphics2D g2d, int height) {
        // Background
        g2d.setColor(ROW_LABEL_BG);
        g2d.fillRect(0, HEADER_HEIGHT, ROW_LABEL_WIDTH, height - HEADER_HEIGHT);
        
        // Border
        g2d.setColor(GRID_COLOR);
        g2d.drawLine(ROW_LABEL_WIDTH, HEADER_HEIGHT, ROW_LABEL_WIDTH, height);
        
        // Labels
        Font labelFont = FontManager.getRegular(10f);
        g2d.setFont(labelFont);
        FontMetrics fm = g2d.getFontMetrics();
        
        for (int i = 0; i < rows.size(); i++) {
            int y = rowToY(i);
            if (y < HEADER_HEIGHT || y > height) continue;
            
            RowInfo row = rows.get(i);
            
            // Row background with slight color tint
            g2d.setColor(new Color(row.color.getRed(), row.color.getGreen(), row.color.getBlue(), 30));
            g2d.fillRect(0, y, ROW_LABEL_WIDTH, ROW_HEIGHT);
            
            // Label text
            g2d.setColor(ROW_LABEL_TEXT);
            int textY = y + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(row.label, 5, textY);
            
            // Row separator
            g2d.setColor(GRID_COLOR);
            g2d.drawLine(0, y + ROW_HEIGHT, ROW_LABEL_WIDTH, y + ROW_HEIGHT);
        }
    }
    
    private void drawHeader(Graphics2D g2d, int width) {
        // Background
        g2d.setColor(HEADER_BG);
        g2d.fillRect(0, 0, width, HEADER_HEIGHT);
        
        // Border
        g2d.setColor(GRID_COLOR);
        g2d.drawLine(0, HEADER_HEIGHT, width, HEADER_HEIGHT);
        
        // Beat/Bar labels
        Font headerFont = FontManager.getRegular(9f);
        g2d.setFont(headerFont);
        g2d.setColor(HEADER_TEXT);
        
        double startBeat = Math.floor(scrollOffsetBeats);
        double endBeat = startBeat + (width - ROW_LABEL_WIDTH) / pixelsPerBeat + 1;
        
        for (double beat = startBeat; beat <= endBeat; beat += 1) {
            int x = beatToX(beat);
            if (x < ROW_LABEL_WIDTH) continue;
            
            int bar = (int)(beat / 4) + 1;
            int beatInBar = (int)(beat % 4) + 1;
            
            String label;
            if (beat % 4 == 0) {
                label = String.valueOf(bar);
                g2d.setColor(HEADER_TEXT);
            } else {
                label = bar + "." + beatInBar;
                g2d.setColor(new Color(HEADER_TEXT.getRed(), HEADER_TEXT.getGreen(), HEADER_TEXT.getBlue(), 150));
            }
            
            g2d.drawString(label, x + 2, HEADER_HEIGHT - 8);
            
            // Tick mark
            g2d.setColor(GRID_COLOR);
            g2d.drawLine(x, HEADER_HEIGHT - 5, x, HEADER_HEIGHT);
        }
        
        // Corner box
        g2d.setColor(ROW_LABEL_BG);
        g2d.fillRect(0, 0, ROW_LABEL_WIDTH, HEADER_HEIGHT);
        g2d.setColor(GRID_COLOR);
        g2d.drawRect(0, 0, ROW_LABEL_WIDTH, HEADER_HEIGHT);
        
        // "MIDI" label in corner
        g2d.setColor(new Color(0x00, 0xBF, 0xFF));
        g2d.setFont(FontManager.getBold(11f));
        g2d.drawString("MIDI", 5, 17);
    }
    
    private void drawPlayhead(Graphics2D g2d, int height) {
        int x = beatToX(playheadBeat);
        if (x >= ROW_LABEL_WIDTH && x <= getWidth()) {
            g2d.setColor(PLAYHEAD_COLOR);
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawLine(x, HEADER_HEIGHT, x, height);
            
            // Playhead triangle
            int[] xPoints = {x - 6, x + 6, x};
            int[] yPoints = {0, 0, 8};
            g2d.fillPolygon(xPoints, yPoints, 3);
            
            g2d.setStroke(new BasicStroke(1f));
        }
    }
    
    private void drawSelectionRect(Graphics2D g2d) {
        int x = Math.min(dragStart.x, dragCurrent.x);
        int y = Math.min(dragStart.y, dragCurrent.y);
        int w = Math.abs(dragCurrent.x - dragStart.x);
        int h = Math.abs(dragCurrent.y - dragStart.y);
        
        g2d.setColor(SELECTION_RECT_COLOR);
        g2d.fillRect(x, y, w, h);
        g2d.setColor(NOTE_SELECTED_COLOR);
        g2d.drawRect(x, y, w, h);
    }
    
    private void drawLoopRegion(Graphics2D g2d, int height) {
        if (sequence.isLooping()) {
            int loopEndX = beatToX(sequence.getLoopBeats());
            
            // Draw loop end line
            g2d.setColor(new Color(0xFF, 0x80, 0x00, 150));
            g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 5}, 0));
            g2d.drawLine(loopEndX, HEADER_HEIGHT, loopEndX, height);
            g2d.setStroke(new BasicStroke(1f));
            
            // Loop marker at top
            g2d.setColor(new Color(0xFF, 0x80, 0x00));
            g2d.fillRect(loopEndX - 2, 0, 4, HEADER_HEIGHT);
        }
    }
    
    // Playback control
    public void setPlayheadBeat(double beat) {
        this.playheadBeat = beat;
        repaint();
    }
    
    public double getPlayheadBeat() {
        return playheadBeat;
    }
    
    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
    }
    
    /**
     * Move selected notes horizontally by delta beats
     */
    private void moveSelectedNotes(double deltaBeat) {
        List<MidiNote> selected = sequence.getSelectedNotes();
        if (selected.isEmpty()) return;
        
        for (MidiNote note : selected) {
            double newStart = note.getStartBeat() + deltaBeat;
            note.setStartBeat(Math.max(0, newStart));
        }
        repaint();
        FileManager.getInstance().markUnsaved();
    }
    
    /**
     * Duplicate selected notes in place (for Alt+drag)
     * Creates copies at the same position, then the drag moves them
     */
    private void duplicateSelectedNotesInPlace() {
        List<MidiNote> selected = sequence.getSelectedNotes();
        if (selected.isEmpty()) return;
        
        List<MidiNote> duplicates = new ArrayList<>();
        for (MidiNote note : selected) {
            MidiNote copy = new MidiNote(note);
            duplicates.add(copy);
        }
        
        // Deselect originals (they stay in place)
        for (MidiNote note : selected) {
            note.setSelected(false);
        }
        
        // Add and select duplicates (they will be moved)
        for (MidiNote dup : duplicates) {
            dup.setSelected(true);
            sequence.addNote(dup);
        }
        
        // Update drag note reference to the duplicate
        if (dragNote != null) {
            for (int i = 0; i < selected.size(); i++) {
                if (selected.get(i) == dragNote && i < duplicates.size()) {
                    dragNote = duplicates.get(i);
                    break;
                }
            }
        }
        
        FileManager.getInstance().markUnsaved();
    }
    
    /**
     * Move selected notes vertically by delta rows
     */
    private void moveSelectedNotesVertically(int deltaRow) {
        List<MidiNote> selected = sequence.getSelectedNotes();
        if (selected.isEmpty() || rows.isEmpty()) return;
        
        for (MidiNote note : selected) {
            int newRow = note.getRowIndex() + deltaRow;
            newRow = Math.max(0, Math.min(rows.size() - 1, newRow));
            note.setRowIndex(newRow);
            
            // Update element ID to match new row
            if (newRow < rows.size()) {
                String newElementId = rows.get(newRow).key;
                // Note: This changes which element the note is assigned to
                // Only do this if the key is actually an element ID
                if (!newElementId.startsWith("_")) {
                    // We can't change elementId directly, but row index is enough for display
                }
            }
        }
        repaint();
        FileManager.getInstance().markUnsaved();
    }
    
    // Snap grid control
    public void setSnapBeat(double snap) {
        this.snapBeat = snap;
    }
    
    public double getSnapBeat() {
        return snapBeat;
    }
    
    // Zoom control
    public void setPixelsPerBeat(double ppb) {
        this.pixelsPerBeat = Math.max(20, Math.min(200, ppb));
        repaint();
    }
    
    public double getPixelsPerBeat() {
        return pixelsPerBeat;
    }
    
    // Scroll to beat
    public void scrollToBeat(double beat) {
        this.scrollOffsetBeats = Math.max(0, beat - 2);
        repaint();
    }
    
    // SequenceListener implementation
    @Override
    public void onSequenceChanged() {
        updateRows();
        repaint();
        FileManager.getInstance().markUnsaved();
    }
    
    @Override
    public void onSelectionChanged() {
        repaint();
    }
}

