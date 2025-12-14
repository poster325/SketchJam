import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Recording UI panel with play/record/pause buttons and track list
 */
public class RecordPanel extends JPanel {
    
    private static final int ICON_SIZE = 50;
    private static final int TRACK_WIDTH = 200;
    private static final int TRACK_HEIGHT = 50;
    private static final int NUM_TRACKS = 5;
    
    // Layout offsets
    private static final int BPM_Y = 25;  // 25px from top
    private static final int BPM_HEIGHT = 25;
    private static final int BPM_BUTTON_SIZE = 25;
    private static final int TOP_PADDING = 50;  // BPM ends at 50
    private static final int ICONS_Y = TOP_PADDING + 25;  // y=75
    private static final int TRACKS_Y = TOP_PADDING + 100; // y=150
    
    // Track colors
    private static final Color[] TRACK_COLORS = {
        new Color(0x00, 0xBF, 0xFF), // Track 1 - Cyan/Blue
        Color.RED,                    // Track 2 - Red
        new Color(0xFF, 0x8C, 0x00), // Track 3 - Orange
        new Color(0xFF, 0xD7, 0x00), // Track 4 - Gold/Yellow
        new Color(0x32, 0xCD, 0x32), // Track 5 - Lime Green
    };
    
    // Icon images
    private BufferedImage playIcon;
    private BufferedImage recordIcon;
    private BufferedImage loopIcon;
    
    // BPM
    private int bpm = 120;
    private static final int MIN_BPM = 40;
    private static final int MAX_BPM = 240;
    
    // Play/Record/Loop mode states
    private boolean isPlaying = false;
    private boolean isRecording = false;
    private boolean isLooping = false;
    
    // Metronome
    private Timer metronomeTimer;
    private SketchCanvas canvas;
    
    // Button states
    private int hoveredButton = -1; // 0=play, 1=record, 2=pause
    private int hoveredTrack = -1;
    private int hoveredTrackX = -1; // Is X button hovered
    private boolean hoveredBpmMinus = false;
    private boolean hoveredBpmPlus = false;
    
    public RecordPanel() {
        // Width: 200, Height: BPM(25) + padding(25) + icons(50) + padding(25) + tracks(5*50) = 400
        setPreferredSize(new Dimension(TRACK_WIDTH, BPM_HEIGHT + TOP_PADDING + 25 + ICON_SIZE + 25 + NUM_TRACKS * TRACK_HEIGHT));
        setBackground(new Color(0x38, 0x38, 0x38)); // #383838 - match window background
        
        // Load icons from symbols folder
        loadIcons();
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                
                // Check BPM controls (only when metronome is not playing)
                if (!isPlaying && !isRecording && y >= BPM_Y && y < BPM_Y + BPM_BUTTON_SIZE) {
                    // Minus button (left side, 25x25)
                    if (x >= 0 && x < BPM_BUTTON_SIZE) {
                        bpm = Math.max(MIN_BPM, bpm - 5);
                        repaint();
                    }
                    // Plus button (right side, 25x25)
                    else if (x >= TRACK_WIDTH - BPM_BUTTON_SIZE && x < TRACK_WIDTH) {
                        bpm = Math.min(MAX_BPM, bpm + 5);
                        repaint();
                    }
                }
                
                // Check button clicks (y = ICONS_Y to ICONS_Y + 50)
                if (y >= ICONS_Y && y < ICONS_Y + ICON_SIZE) {
                    int startX = (TRACK_WIDTH - ICON_SIZE * 3) / 2;
                    int relativeX = x - startX;
                    if (relativeX >= 0 && relativeX < ICON_SIZE * 3) {
                        int buttonIndex = relativeX / ICON_SIZE;
                        if (buttonIndex >= 0 && buttonIndex < 3) {
                            handleButtonClick(buttonIndex);
                        }
                    }
                }
                
                // Check track clicks (y = TRACKS_Y onwards)
                if (y >= TRACKS_Y) {
                    int trackIndex = (y - TRACKS_Y) / TRACK_HEIGHT;
                    if (trackIndex >= 0 && trackIndex < NUM_TRACKS) {
                        // Check if X button was clicked (right side)
                        if (x >= TRACK_WIDTH - 50) {
                            handleTrackDelete(trackIndex);
                        } else {
                            handleTrackClick(trackIndex);
                        }
                    }
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredButton = -1;
                hoveredTrack = -1;
                hoveredTrackX = -1;
                hoveredBpmMinus = false;
                hoveredBpmPlus = false;
                repaint();
            }
        });
        
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                
                int newHoveredButton = -1;
                int newHoveredTrack = -1;
                int newHoveredTrackX = -1;
                boolean newHoveredBpmMinus = false;
                boolean newHoveredBpmPlus = false;
                
                // Check BPM hover (only when metronome is not playing)
                if (!isPlaying && !isRecording && y >= BPM_Y && y < BPM_Y + BPM_BUTTON_SIZE) {
                    if (x >= 0 && x < BPM_BUTTON_SIZE) {
                        newHoveredBpmMinus = true;
                    } else if (x >= TRACK_WIDTH - BPM_BUTTON_SIZE && x < TRACK_WIDTH) {
                        newHoveredBpmPlus = true;
                    }
                }
                
                // Check button hover
                if (y >= ICONS_Y && y < ICONS_Y + ICON_SIZE) {
                    int startX = (TRACK_WIDTH - ICON_SIZE * 3) / 2;
                    int relativeX = x - startX;
                    if (relativeX >= 0 && relativeX < ICON_SIZE * 3) {
                        int buttonIndex = relativeX / ICON_SIZE;
                        if (buttonIndex >= 0 && buttonIndex < 3) {
                            newHoveredButton = buttonIndex;
                        }
                    }
                }
                
                // Check track hover
                if (y >= TRACKS_Y) {
                    int trackIndex = (y - TRACKS_Y) / TRACK_HEIGHT;
                    if (trackIndex >= 0 && trackIndex < NUM_TRACKS) {
                        newHoveredTrack = trackIndex;
                        if (x >= TRACK_WIDTH - 50) {
                            newHoveredTrackX = trackIndex;
                        }
                    }
                }
                
                if (newHoveredButton != hoveredButton || newHoveredTrack != hoveredTrack || 
                    newHoveredTrackX != hoveredTrackX || newHoveredBpmMinus != hoveredBpmMinus ||
                    newHoveredBpmPlus != hoveredBpmPlus) {
                    hoveredButton = newHoveredButton;
                    hoveredTrack = newHoveredTrack;
                    hoveredTrackX = newHoveredTrackX;
                    hoveredBpmMinus = newHoveredBpmMinus;
                    hoveredBpmPlus = newHoveredBpmPlus;
                    repaint();
                }
            }
        });
    }
    
    private void handleButtonClick(int buttonIndex) {
        switch (buttonIndex) {
            case 0: // Play
                if (!isPlaying) {
                    startPlaying();
                } else {
                    stopPlaying();
                }
                break;
            case 1: // Record
                if (!isRecording) {
                    startRecording();
                } else {
                    stopRecording();
                }
                break;
            case 2: // Loop
                isLooping = !isLooping;
                System.out.println("Loop mode: " + (isLooping ? "ON" : "OFF"));
                break;
        }
        repaint();
    }
    
    private void startPlaying() {
        isPlaying = true;
        isRecording = false;
        startMetronome();
        if (canvas != null) canvas.repaint();
        System.out.println("Play mode started");
    }
    
    private void stopPlaying() {
        isPlaying = false;
        stopMetronome();
        if (canvas != null) canvas.repaint();
        System.out.println("Play mode stopped");
    }
    
    private void startRecording() {
        isRecording = true;
        isPlaying = false;
        startMetronome();
        if (canvas != null) canvas.repaint();
        System.out.println("Record mode started");
    }
    
    private void stopRecording() {
        isRecording = false;
        stopMetronome();
        if (canvas != null) canvas.repaint();
        System.out.println("Record mode stopped");
    }
    
    private void startMetronome() {
        stopMetronome(); // Stop any existing metronome
        
        int intervalMs = 60000 / bpm; // Convert BPM to milliseconds
        metronomeTimer = new Timer(intervalMs, e -> {
            SoundManager.getInstance().playMetronomeClick();
        });
        metronomeTimer.setInitialDelay(intervalMs); // Wait one interval before first click
        metronomeTimer.start();
    }
    
    private void stopMetronome() {
        if (metronomeTimer != null) {
            metronomeTimer.stop();
            metronomeTimer = null;
        }
    }
    
    private void updateMetronomeBpm() {
        // Restart metronome with new BPM if currently running
        if (metronomeTimer != null && metronomeTimer.isRunning()) {
            startMetronome();
        }
    }
    
    public void setCanvas(SketchCanvas canvas) {
        this.canvas = canvas;
    }
    
    public boolean isPlaying() {
        return isPlaying;
    }
    
    public boolean isRecording() {
        return isRecording;
    }
    
    public boolean isLooping() {
        return isLooping;
    }
    
    private void handleTrackClick(int trackIndex) {
        System.out.println("Track " + (trackIndex + 1) + " clicked");
    }
    
    private void handleTrackDelete(int trackIndex) {
        System.out.println("Track " + (trackIndex + 1) + " delete clicked");
    }
    
    private void loadIcons() {
        try {
            playIcon = ImageIO.read(new File("symbols/play.png"));
            recordIcon = ImageIO.read(new File("symbols/record.png"));
            loopIcon = ImageIO.read(new File("symbols/loop.png"));
            System.out.println("Loaded icons from symbols folder");
        } catch (Exception e) {
            System.err.println("Failed to load icons: " + e.getMessage());
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        
        // Draw BPM selector (y = 0)
        drawBpmSelector(g2d);
        
        // Draw buttons (y = ICONS_Y), centered horizontally
        int totalIconsWidth = ICON_SIZE * 3;
        int startX = (TRACK_WIDTH - totalIconsWidth) / 2;  // Center the 3 icons
        drawIcon(g2d, playIcon, startX, ICONS_Y, hoveredButton == 0, isPlaying);
        drawIcon(g2d, recordIcon, startX + ICON_SIZE, ICONS_Y, hoveredButton == 1, isRecording);
        drawIcon(g2d, loopIcon, startX + ICON_SIZE * 2, ICONS_Y, hoveredButton == 2, isLooping);
        
        // Draw tracks (y = TRACKS_Y)
        g2d.setFont(FontManager.getRegular(18));
        
        for (int i = 0; i < NUM_TRACKS; i++) {
            int y = TRACKS_Y + i * TRACK_HEIGHT;
            
            // Track background
            g2d.setColor(TRACK_COLORS[i]);
            g2d.fillRect(0, y, TRACK_WIDTH, TRACK_HEIGHT);
            
            // Hover effect
            if (hoveredTrack == i && hoveredTrackX != i) {
                g2d.setColor(new Color(255, 255, 255, 50));
                g2d.fillRect(0, y, TRACK_WIDTH - 50, TRACK_HEIGHT);
            }
            
            // Track label
            g2d.setColor(Color.WHITE);
            String label = "TRACK " + (i + 1);
            FontMetrics fm = g2d.getFontMetrics();
            int textY = y + (TRACK_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(label, 15, textY);
            
            // X button area
            if (hoveredTrackX == i) {
                g2d.setColor(new Color(0, 0, 0, 50));
                g2d.fillRect(TRACK_WIDTH - 50, y, 50, TRACK_HEIGHT);
            }
            
            // X icon
            g2d.setColor(new Color(255, 255, 255, 180));
            g2d.setStroke(new BasicStroke(2));
            int xCenter = TRACK_WIDTH - 25;
            int yCenter = y + TRACK_HEIGHT / 2;
            int xSize = 8;
            g2d.drawLine(xCenter - xSize, yCenter - xSize, xCenter + xSize, yCenter + xSize);
            g2d.drawLine(xCenter - xSize, yCenter + xSize, xCenter + xSize, yCenter - xSize);
        }
    }
    
    private void drawIcon(Graphics2D g2d, BufferedImage icon, int x, int y, boolean hovered, boolean active) {
        if (icon != null) {
            // Set opacity: 100% when active, 30% when inactive
            float opacity = active ? 1.0f : 0.3f;
            // Slightly brighter on hover when inactive
            if (hovered && !active) {
                opacity = 0.5f;
            }
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            g2d.drawImage(icon, x, y, ICON_SIZE, ICON_SIZE, null);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } else {
            // Fallback: draw placeholder circle
            float opacity = active ? 1.0f : 0.3f;
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            g2d.setColor(Color.BLACK);
            g2d.fillOval(x + 5, y + 5, ICON_SIZE - 10, ICON_SIZE - 10);
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        }
    }
    
    private void drawBpmSelector(Graphics2D g2d) {
        boolean isDisabled = isPlaying || isRecording;
        
        // Background for the middle area
        g2d.setColor(isDisabled ? new Color(0x30, 0x30, 0x30) : new Color(0x48, 0x48, 0x48));
        g2d.fillRect(BPM_BUTTON_SIZE, BPM_Y, TRACK_WIDTH - BPM_BUTTON_SIZE * 2, BPM_BUTTON_SIZE);
        
        // Minus button (25x25, left)
        if (isDisabled) {
            g2d.setColor(new Color(0x30, 0x30, 0x30));
        } else {
            g2d.setColor(hoveredBpmMinus ? new Color(0x60, 0x60, 0x60) : new Color(0x50, 0x50, 0x50));
        }
        g2d.fillRect(0, BPM_Y, BPM_BUTTON_SIZE, BPM_BUTTON_SIZE);
        g2d.setColor(isDisabled ? new Color(0x60, 0x60, 0x60) : Color.WHITE);
        g2d.setFont(FontManager.getBold(16));
        FontMetrics fmBtn = g2d.getFontMetrics();
        String minusStr = "-";
        int minusX = (BPM_BUTTON_SIZE - fmBtn.stringWidth(minusStr)) / 2;
        int minusY = BPM_Y + (BPM_BUTTON_SIZE + fmBtn.getAscent() - fmBtn.getDescent()) / 2;
        g2d.drawString(minusStr, minusX, minusY);
        
        // Plus button (25x25, right)
        if (isDisabled) {
            g2d.setColor(new Color(0x30, 0x30, 0x30));
        } else {
            g2d.setColor(hoveredBpmPlus ? new Color(0x60, 0x60, 0x60) : new Color(0x50, 0x50, 0x50));
        }
        g2d.fillRect(TRACK_WIDTH - BPM_BUTTON_SIZE, BPM_Y, BPM_BUTTON_SIZE, BPM_BUTTON_SIZE);
        g2d.setColor(isDisabled ? new Color(0x60, 0x60, 0x60) : Color.WHITE);
        String plusStr = "+";
        int plusX = TRACK_WIDTH - BPM_BUTTON_SIZE + (BPM_BUTTON_SIZE - fmBtn.stringWidth(plusStr)) / 2;
        int plusY = BPM_Y + (BPM_BUTTON_SIZE + fmBtn.getAscent() - fmBtn.getDescent()) / 2;
        g2d.drawString(plusStr, plusX, plusY);
        
        // BPM text (centered in middle area)
        g2d.setFont(FontManager.getRegular(12));
        String bpmText = bpm + " BPM";
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(bpmText);
        int textX = (TRACK_WIDTH - textWidth) / 2;
        int textY = BPM_Y + (BPM_BUTTON_SIZE + fm.getAscent() - fm.getDescent()) / 2;
        g2d.setColor(isDisabled ? new Color(0x60, 0x60, 0x60) : Color.WHITE);
        g2d.drawString(bpmText, textX, textY);
    }
    
    public int getBpm() {
        return bpm;
    }
    
    public void setBpm(int bpm) {
        this.bpm = Math.max(MIN_BPM, Math.min(MAX_BPM, bpm));
        repaint();
    }
}

