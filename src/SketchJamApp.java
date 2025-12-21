import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class SketchJamApp extends JFrame {
    
    public static final int DEFAULT_WIDTH = 1900;
    public static final int DEFAULT_HEIGHT = 1050;
    public static final int MIN_WIDTH = 1200;
    public static final int MIN_HEIGHT = 700;
    public static final int LEFT_PANEL_WIDTH = 200;
    public static final int RIGHT_PANEL_WIDTH = 350;
    public static final int MIDI_PANEL_HEIGHT = 200;
    public static final int GRID_SIZE = 25;
    public static final int SNAP_SIZE = 5;
    
    private SketchCanvas canvas;
    private ColorPalette colorPalette;
    private ScalePreset scalePreset;
    private ScaleSelector scaleSelector;
    private SF2Manager sf2Manager;
    private EQPanel eqPanel;
    private FilePanel filePanel;
    private RecordPanel recordPanel;
    private LogoPanel logoPanel;
    private MidiSequencerPanel midiPanel;
    private UndoRedoManager undoRedoManager;
    private JPanel contentPanel;
    
    public SketchJamApp() {
        setTitle("SketchJam");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        
        // Create content panel with null layout for absolute positioning
        contentPanel = new JPanel(null);
        contentPanel.setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        contentPanel.setBackground(new Color(0x38, 0x38, 0x38)); // #383838
        contentPanel.setDoubleBuffered(true);
        setContentPane(contentPanel);
        
        // Initialize undo/redo manager
        undoRedoManager = new UndoRedoManager(20);
        
        // Create canvas (will be sized in updateLayout)
        canvas = new SketchCanvas(undoRedoManager);
        contentPanel.add(canvas);
        
        // Connect undo manager to canvas
        undoRedoManager.setCanvas(canvas);
        
        // Create file panel (top-left)
        filePanel = new FilePanel();
        filePanel.setCanvas(canvas);
        contentPanel.add(filePanel);
        
        // Create record panel (left side, below file panel)
        recordPanel = new RecordPanel();
        recordPanel.setCanvas(canvas);
        canvas.setRecordPanel(recordPanel);
        contentPanel.add(recordPanel);
        
        // Create color palette (right side)
        colorPalette = new ColorPalette(canvas);
        contentPanel.add(colorPalette);
        
        // Create scale preset (below color palette)
        scalePreset = new ScalePreset(canvas);
        contentPanel.add(scalePreset);
        
        // Create scale selector (below scale preset)
        scaleSelector = new ScaleSelector(scalePreset);
        contentPanel.add(scaleSelector);
        
        // Connect color palette and scale preset for mutual selection clearing
        colorPalette.setScalePreset(scalePreset);
        scalePreset.setColorPalette(colorPalette);
        
        // Create SF2 Manager (below scale selector)
        sf2Manager = new SF2Manager();
        contentPanel.add(sf2Manager);
        
        // Create EQ Panel (below SF2 Manager)
        eqPanel = new EQPanel(canvas);
        contentPanel.add(eqPanel);
        
        // Create Logo Panel (bottom right corner)
        logoPanel = new LogoPanel();
        contentPanel.add(logoPanel);
        
        // Create MIDI Sequencer Panel (top of canvas area)
        midiPanel = new MidiSequencerPanel();
        midiPanel.setCanvas(canvas);  // Connect to canvas for element-based rows
        contentPanel.add(midiPanel);
        
        // Connect MIDI panel to record panel and canvas
        recordPanel.setMidiSequencer(midiPanel);
        canvas.setMidiSequencer(midiPanel);  // For refreshing when elements change
        
        // Initial layout
        updateLayout(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        
        // Add resize listener
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Dimension size = getContentPane().getSize();
                updateLayout(size.width, size.height);
            }
        });
        
        // Initialize FileManager with all components
        FileManager.getInstance().setComponents(canvas, recordPanel, colorPalette, 
            scaleSelector, sf2Manager, eqPanel, midiPanel);
        
        // Add keyboard shortcuts
        setupKeyBindings();
        
        // Add window close listener for unsaved changes warning
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (FileManager.getInstance().checkUnsavedChanges(SketchJamApp.this)) {
                    dispose();
                    System.exit(0);
                }
            }
        });
        
        // Pack to fit content + window decorations
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    /**
     * Update component positions based on window size
     */
    private void updateLayout(int width, int height) {
        // Left panel - fixed width
        filePanel.setBounds(0, 0, LEFT_PANEL_WIDTH, 50);
        recordPanel.setBounds(0, 50, LEFT_PANEL_WIDTH, 525);
        
        // Right panel - fixed width, anchored to right edge
        int rightX = width - RIGHT_PANEL_WIDTH;
        
        colorPalette.setBounds(rightX, 0, RIGHT_PANEL_WIDTH, 150);
        scalePreset.setBounds(rightX, 150, RIGHT_PANEL_WIDTH, 50);
        scaleSelector.setBounds(rightX, 200, RIGHT_PANEL_WIDTH, 75);
        sf2Manager.setBounds(rightX, 300, RIGHT_PANEL_WIDTH, 100);
        eqPanel.setBounds(rightX, 425, RIGHT_PANEL_WIDTH, 75);
        logoPanel.setBounds(rightX, height - 100, RIGHT_PANEL_WIDTH, 100);
        
        // MIDI Sequencer Panel - top of canvas area
        int canvasX = LEFT_PANEL_WIDTH;
        int canvasWidth = width - LEFT_PANEL_WIDTH - RIGHT_PANEL_WIDTH;
        midiPanel.setBounds(canvasX, 0, canvasWidth, MIDI_PANEL_HEIGHT);
        
        // Canvas - fills the space between left and right panels, below MIDI panel
        int canvasHeight = height - MIDI_PANEL_HEIGHT;
        canvas.setBounds(canvasX, MIDI_PANEL_HEIGHT, canvasWidth, canvasHeight);
        
        // Repaint all
        contentPanel.revalidate();
        contentPanel.repaint();
    }
    
    private void setupKeyBindings() {
        // Undo: Ctrl+Z (skip if MIDI panel is focused - it has its own undo)
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("control Z"), "undo");
        getRootPane().getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                // Skip if MIDI panel is focused (it handles its own undo)
                if (midiPanel.isFocusOwner()) {
                    return;
                }
                undoRedoManager.undo();
                canvas.repaint();
            }
        });
        
        // Redo: Ctrl+Y (skip if MIDI panel is focused - it has its own redo)
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("control Y"), "redo");
        getRootPane().getActionMap().put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                // Skip if MIDI panel is focused (it handles its own redo)
                if (midiPanel.isFocusOwner()) {
                    return;
                }
                undoRedoManager.redo();
                canvas.repaint();
            }
        });
        
        // Save: Ctrl+S
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("control S"), "save");
        getRootPane().getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                filePanel.handleSave();
            }
        });
        
        // New: Ctrl+N
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("control N"), "new");
        getRootPane().getActionMap().put("new", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                filePanel.handleNew();
            }
        });
        
        // Open: Ctrl+O
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("control O"), "open");
        getRootPane().getActionMap().put("open", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                filePanel.handleOpen();
            }
        });
    }
}

