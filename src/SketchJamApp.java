import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class SketchJamApp extends JFrame {
    
    public static final int WINDOW_WIDTH = 1900;
    public static final int WINDOW_HEIGHT = 1050;
    public static final int CANVAS_X = 200;
    public static final int CANVAS_Y = 0;
    public static final int CANVAS_WIDTH = 1350;
    public static final int CANVAS_HEIGHT = 1050;
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
    private UndoRedoManager undoRedoManager;
    
    public SketchJamApp() {
        setTitle("SketchJam");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);
        
        // Create content panel with fixed size and double buffering
        JPanel contentPanel = new JPanel(null);
        contentPanel.setPreferredSize(new Dimension(WINDOW_WIDTH, WINDOW_HEIGHT));
        contentPanel.setBackground(new Color(0x38, 0x38, 0x38)); // #383838
        contentPanel.setDoubleBuffered(true);
        setContentPane(contentPanel);
        
        // Initialize undo/redo manager
        undoRedoManager = new UndoRedoManager(20);
        
        // Create canvas
        canvas = new SketchCanvas(undoRedoManager);
        canvas.setBounds(CANVAS_X, CANVAS_Y, CANVAS_WIDTH, CANVAS_HEIGHT);
        contentPanel.add(canvas);
        
        // Connect undo manager to canvas
        undoRedoManager.setCanvas(canvas);
        
        // Create file panel (top-left, at position 0, 0)
        // Width: 200px (4 buttons * 50px), Height: 50px (no padding)
        filePanel = new FilePanel();
        filePanel.setBounds(0, 0, 200, 50);
        filePanel.setCanvas(canvas);
        contentPanel.add(filePanel);
        
        // Create record panel (left side, below file panel)
        // Width: 200px, Height: BPM + icons + beats + tracks (7*50=350) = 525
        recordPanel = new RecordPanel();
        recordPanel.setBounds(0, 50, 200, 525);  // Start at y=50 (below FilePanel)
        recordPanel.setCanvas(canvas);
        canvas.setRecordPanel(recordPanel);
        contentPanel.add(recordPanel);
        
        // Create color palette (positioned at 1575, 25 per design spec)
        // Colorbox: 350×150 at position 1550, 25 (25px padding around 300×100 color matrix)
        int paletteWidth = 350;
        int paletteHeight = 150;
        int paletteX = 1550;  // Right edge at window edge (1550 + 350 = 1900)
        int paletteY = 0;     // Top edge at window edge
        
        colorPalette = new ColorPalette(canvas);
        colorPalette.setBounds(paletteX, paletteY, paletteWidth, paletteHeight);
        contentPanel.add(colorPalette);
        
        // Create scale preset (right below color palette)
        // 7 cells × 50px = 350px wide, 50px tall
        int scalePresetWidth = 350;
        int scalePresetHeight = 50;
        int scalePresetX = paletteX;  // Same x as color palette
        int scalePresetY = paletteY + paletteHeight;  // Right below color palette
        
        scalePreset = new ScalePreset(canvas);
        scalePreset.setBounds(scalePresetX, scalePresetY, scalePresetWidth, scalePresetHeight);
        contentPanel.add(scalePreset);
        
        // Create scale selector (right below scale preset)
        // Height: 75px (25 for label + 25 for root notes + 25 for major/minor)
        int scaleSelectorWidth = 350;
        int scaleSelectorHeight = 75;
        int scaleSelectorX = paletteX;
        int scaleSelectorY = scalePresetY + scalePresetHeight;
        
        scaleSelector = new ScaleSelector(scalePreset);
        scaleSelector.setBounds(scaleSelectorX, scaleSelectorY, scaleSelectorWidth, scaleSelectorHeight);
        contentPanel.add(scaleSelector);
        
        // Connect color palette and scale preset for mutual selection clearing
        colorPalette.setScalePreset(scalePreset);
        scalePreset.setColorPalette(colorPalette);
        
        // Create SF2 Manager (below scale selector with 25px spacing)
        // Height: 100px (25 for label + 25 for piano + 25 for guitar + 25 for drums)
        int sf2ManagerWidth = 350;
        int sf2ManagerHeight = 100;
        int sf2ManagerX = paletteX;
        int sf2ManagerY = scaleSelectorY + scaleSelectorHeight + 25; // 25px spacing
        
        sf2Manager = new SF2Manager();
        sf2Manager.setBounds(sf2ManagerX, sf2ManagerY, sf2ManagerWidth, sf2ManagerHeight);
        contentPanel.add(sf2Manager);
        
        // Create EQ Panel (below SF2 Manager with 25px spacing)
        int eqPanelWidth = 350;
        int eqPanelHeight = 75;
        int eqPanelX = paletteX;
        int eqPanelY = sf2ManagerY + sf2ManagerHeight + 25; // 25px spacing
        
        eqPanel = new EQPanel(canvas);
        eqPanel.setBounds(eqPanelX, eqPanelY, eqPanelWidth, eqPanelHeight);
        contentPanel.add(eqPanel);
        
        // Create Logo Panel (bottom right corner)
        // Position: 1550, 950 (window height 1050 - 100 height = 950)
        int logoPanelWidth = 350;
        int logoPanelHeight = 100;
        int logoPanelX = paletteX;
        int logoPanelY = WINDOW_HEIGHT - logoPanelHeight;
        
        LogoPanel logoPanel = new LogoPanel();
        logoPanel.setBounds(logoPanelX, logoPanelY, logoPanelWidth, logoPanelHeight);
        contentPanel.add(logoPanel);
        
        // Initialize FileManager with all components
        FileManager.getInstance().setComponents(canvas, recordPanel, colorPalette, 
            scaleSelector, sf2Manager, eqPanel);
        
        // Add keyboard shortcuts for undo/redo
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
    
    private void setupKeyBindings() {
        // Undo: Ctrl+Z
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("control Z"), "undo");
        getRootPane().getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                undoRedoManager.undo();
                canvas.repaint();
            }
        });
        
        // Redo: Ctrl+Y
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("control Y"), "redo");
        getRootPane().getActionMap().put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
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

