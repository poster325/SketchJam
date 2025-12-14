import javax.swing.*;
import java.awt.*;

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
    private UndoRedoManager undoRedoManager;
    
    public SketchJamApp() {
        setTitle("SketchJam");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
        
        // Create color palette (positioned at 1575, 25 per design spec)
        // Colorbox: 300×115 at position 1575, 25 (100 for colors + 15 for labels)
        int paletteWidth = 300;
        int paletteHeight = 115;
        int paletteX = 1575;
        int paletteY = 25;
        
        colorPalette = new ColorPalette(canvas);
        colorPalette.setBounds(paletteX, paletteY, paletteWidth, paletteHeight);
        contentPanel.add(colorPalette);
        
        // Add keyboard shortcuts for undo/redo
        setupKeyBindings();
        
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
    }
}

