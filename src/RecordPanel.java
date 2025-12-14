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
    private static final int NUM_TRACKS = 7;
    
    // Layout offsets
    private static final int BPM_Y = 25;  // 25px from top
    private static final int BPM_HEIGHT = 25;
    private static final int BPM_BUTTON_SIZE = 25;
    private static final int TOP_PADDING = 50;  // BPM ends at 50
    private static final int ICONS_Y = TOP_PADDING + 25;  // y=75
    private static final int BEATS_Y = ICONS_Y + ICON_SIZE + 25;  // y=150 (after icons + 25px spacing)
    private static final int BEATS_HEIGHT = 25;
    private static final int TRACKS_Y = BEATS_Y + BEATS_HEIGHT; // y=175
    
    // Loop beat options
    private static final int[] BEAT_OPTIONS = {4, 8, 16, 32};
    private int selectedBeatIndex = 2; // Default to 16 beats
    
    // Icon images
    private BufferedImage playIcon;
    private BufferedImage recordIcon;
    private BufferedImage loopIcon;
    
    // BPM
    private int bpm = 120;
    private static final int MIN_BPM = 40;
    private static final int MAX_BPM = 240;
    
    // Metronome
    private Timer metronomeTimer;
    private SketchCanvas canvas;
    
    // Track management
    private TrackManager trackManager;
    
    // Button states
    private int hoveredButton = -1; // 0=play, 1=record, 2=loop
    private int hoveredTrack = -1;
    private int hoveredTrackX = -1; // Is X button hovered
    private boolean hoveredBpmMinus = false;
    private boolean hoveredBpmPlus = false;
    private int hoveredBeat = -1; // 0-3 for beat options
    
    public RecordPanel() {
        // Width: 200, Height: BPM(25) + padding(25) + icons(50) + padding(25) + tracks(7*50) = 500
        setPreferredSize(new Dimension(TRACK_WIDTH, BPM_HEIGHT + TOP_PADDING + 25 + ICON_SIZE + 25 + NUM_TRACKS * TRACK_HEIGHT));
        setBackground(new Color(0x38, 0x38, 0x38)); // #383838 - match window background
        
        // Initialize track manager
        trackManager = new TrackManager();
        trackManager.setBpm(bpm);
        trackManager.setListener(new TrackManager.TrackUpdateListener() {
            @Override
            public void onTracksUpdated() {
                repaint();
            }
            
            @Override
            public void onPlayNote(Track.NoteEvent event) {
                // Play the note through SoundManager
                SoundManager.getInstance().playNoteEvent(event);
            }
        });
        
        // Load icons from symbols folder
        loadIcons();
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int x = e.getX();
                int y = e.getY();
                
                // Check BPM controls (only when not recording)
                if (!isRecording() && y >= BPM_Y && y < BPM_Y + BPM_BUTTON_SIZE) {
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
                
                // Check beat selector clicks (y = BEATS_Y to BEATS_Y + 25)
                if (y >= BEATS_Y && y < BEATS_Y + BEATS_HEIGHT) {
                    int beatButtonWidth = TRACK_WIDTH / 4;
                    int beatIndex = x / beatButtonWidth;
                    if (beatIndex >= 0 && beatIndex < 4) {
                        selectedBeatIndex = beatIndex;
                        updateLoopBeats();
                        repaint();
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
                hoveredBeat = -1;
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
                int newHoveredBeat = -1;
                
                // Check BPM hover (only when not recording)
                if (!isRecording() && y >= BPM_Y && y < BPM_Y + BPM_BUTTON_SIZE) {
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
                
                // Check beat selector hover
                if (y >= BEATS_Y && y < BEATS_Y + BEATS_HEIGHT) {
                    int beatButtonWidth = TRACK_WIDTH / 4;
                    newHoveredBeat = x / beatButtonWidth;
                    if (newHoveredBeat >= 4) newHoveredBeat = -1;
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
                    newHoveredBpmPlus != hoveredBpmPlus || newHoveredBeat != hoveredBeat) {
                    hoveredButton = newHoveredButton;
                    hoveredTrack = newHoveredTrack;
                    hoveredTrackX = newHoveredTrackX;
                    hoveredBeat = newHoveredBeat;
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
                if (!trackManager.isPlaying()) {
                    startPlaying();
                } else {
                    stopPlaying();
                }
                break;
            case 1: // Record
                // Cancel count-in if clicking during count-in
                if (isCountingIn) {
                    cancelCountIn();
                    break;
                }
                if (!trackManager.isRecording()) {
                    startRecording();
                } else {
                    stopRecording();
                }
                break;
            case 2: // Loop
                toggleLoop();
                System.out.println("Loop mode: " + (loopEnabled ? "ON" : "OFF"));
                break;
        }
        repaint();
    }
    
    private void cancelCountIn() {
        if (countInTimer != null) {
            countInTimer.stop();
            countInTimer = null;
        }
        isCountingIn = false;
        System.out.println("Count-in cancelled");
    }
    
    private boolean isLoopingEnabled() {
        // Track loop state locally since TrackManager doesn't expose it directly as toggle
        return loopEnabled;
    }
    
    private boolean loopEnabled = false;
    
    private void toggleLoop() {
        loopEnabled = !loopEnabled;
        trackManager.setLooping(loopEnabled);
    }
    
    private void updateLoopBeats() {
        int beats = BEAT_OPTIONS[selectedBeatIndex];
        trackManager.setLoopBeats(beats);
        System.out.println("Loop beats set to: " + beats);
    }
    
    public int getSelectedBeats() {
        return BEAT_OPTIONS[selectedBeatIndex];
    }
    
    private void startPlaying() {
        trackManager.startPlayback();
        // Switch to instrument/preview mode
        if (canvas != null) {
            canvas.setInteractionMode(SketchCanvas.InteractionMode.PLAY);
            canvas.repaint();
        }
        System.out.println("Preview mode started");
    }
    
    private void stopPlaying() {
        trackManager.stopPlayback();
        // Return to edit mode
        if (canvas != null) {
            canvas.setInteractionMode(SketchCanvas.InteractionMode.OBJECT);
            canvas.repaint();
        }
        System.out.println("Preview mode stopped");
    }
    
    private boolean isCountingIn = false;
    private Timer countInTimer;
    
    private void startRecording() {
        // Start 4-beat count-in before actual recording
        startCountIn(() -> {
            trackManager.startRecording();
            startMetronome(); // Metronome only in record mode
            // Switch to instrument/preview mode for recording
            if (canvas != null) {
                canvas.setInteractionMode(SketchCanvas.InteractionMode.PLAY);
                canvas.repaint();
            }
            System.out.println("Record mode started");
        });
    }
    
    private void startCountIn(Runnable onComplete) {
        isCountingIn = true;
        int intervalMs = 60000 / bpm;
        final int[] clickCount = {0};
        
        // Play first click immediately
        SoundManager.getInstance().playMetronomeClick();
        clickCount[0]++;
        System.out.println("Count-in: " + clickCount[0] + "/4");
        repaint();
        
        countInTimer = new Timer(intervalMs, e -> {
            clickCount[0]++;
            if (clickCount[0] <= 4) {
                SoundManager.getInstance().playMetronomeClick();
                System.out.println("Count-in: " + clickCount[0] + "/4");
                repaint();
            }
            
            if (clickCount[0] >= 4) {
                countInTimer.stop();
                countInTimer = null;
                isCountingIn = false;
                onComplete.run();
            }
        });
        countInTimer.setRepeats(true);
        countInTimer.start();
    }
    
    private void stopRecording() {
        trackManager.stopRecording();
        stopMetronome();
        // Return to edit mode
        if (canvas != null) {
            canvas.setInteractionMode(SketchCanvas.InteractionMode.OBJECT);
            canvas.repaint();
        }
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
        return trackManager.isPlaying();
    }
    
    public boolean isRecording() {
        return trackManager.isRecording() || isCountingIn;
    }
    
    public boolean isCountingIn() {
        return isCountingIn;
    }
    
    public boolean isLooping() {
        return loopEnabled;
    }
    
    public TrackManager getTrackManager() {
        return trackManager;
    }
    
    private void handleTrackClick(int trackIndex) {
        System.out.println("Track " + (trackIndex + 1) + " clicked");
        // Could be used to solo/mute tracks in the future
    }
    
    private void handleTrackDelete(int trackIndex) {
        trackManager.deleteTrack(trackIndex);
        System.out.println("Track " + (trackIndex + 1) + " deleted");
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
        drawIcon(g2d, playIcon, startX, ICONS_Y, hoveredButton == 0, isPlaying());
        drawIcon(g2d, recordIcon, startX + ICON_SIZE, ICONS_Y, hoveredButton == 1, isRecording());
        drawIcon(g2d, loopIcon, startX + ICON_SIZE * 2, ICONS_Y, hoveredButton == 2, loopEnabled);
        
        // Draw beat selector (y = BEATS_Y)
        drawBeatSelector(g2d);
        
        // Draw tracks (y = TRACKS_Y) - dynamic from TrackManager
        g2d.setFont(FontManager.getRegular(18));
        
        java.util.List<Track> tracks = trackManager.getTracks();
        Track currentRecording = trackManager.getCurrentRecordingTrack();
        
        int trackIndex = 0;
        
        // Draw existing tracks
        for (Track track : tracks) {
            drawTrack(g2d, track, trackIndex, TRACKS_Y + trackIndex * TRACK_HEIGHT);
            trackIndex++;
        }
        
        // Draw current recording track (if any)
        if (currentRecording != null && trackIndex < NUM_TRACKS) {
            drawTrack(g2d, currentRecording, trackIndex, TRACKS_Y + trackIndex * TRACK_HEIGHT);
            // Add recording indicator
            g2d.setColor(new Color(255, 0, 0, 100));
            g2d.fillRect(0, TRACKS_Y + trackIndex * TRACK_HEIGHT, TRACK_WIDTH, TRACK_HEIGHT);
        }
    }
    
    private void drawTrack(Graphics2D g2d, Track track, int index, int y) {
        // Track background
        g2d.setColor(track.getColor());
        g2d.fillRect(0, y, TRACK_WIDTH, TRACK_HEIGHT);
        
        // Hover effect
        if (hoveredTrack == index && hoveredTrackX != index) {
            g2d.setColor(new Color(255, 255, 255, 50));
            g2d.fillRect(0, y, TRACK_WIDTH - 50, TRACK_HEIGHT);
        }
        
        // Track label (white text)
        g2d.setColor(Color.WHITE);
        g2d.setFont(FontManager.getRegular(18));
        String label = track.getName();
        FontMetrics fm = g2d.getFontMetrics();
        int textY = y + (TRACK_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
        g2d.drawString(label, 15, textY);
        
        // Event count indicator
        if (track.getEventCount() > 0) {
            g2d.setFont(FontManager.getRegular(10));
            String countText = track.getEventCount() + " notes";
            g2d.drawString(countText, 15, y + 15);
        }
        
        // X button area
        if (hoveredTrackX == index) {
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.fillRect(TRACK_WIDTH - 50, y, 50, TRACK_HEIGHT);
        }
        
        // X icon (white)
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(2));
        int xCenter = TRACK_WIDTH - 25;
        int yCenter = y + TRACK_HEIGHT / 2;
        int xSize = 8;
        g2d.drawLine(xCenter - xSize, yCenter - xSize, xCenter + xSize, yCenter + xSize);
        g2d.drawLine(xCenter - xSize, yCenter + xSize, xCenter + xSize, yCenter - xSize);
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
    
    private void drawBeatSelector(Graphics2D g2d) {
        int buttonWidth = TRACK_WIDTH / 4;
        
        g2d.setFont(FontManager.getBold(12));
        FontMetrics fm = g2d.getFontMetrics();
        
        for (int i = 0; i < 4; i++) {
            int x = i * buttonWidth;
            boolean isSelected = (i == selectedBeatIndex);
            boolean isHovered = (i == hoveredBeat);
            
            // Background
            if (isSelected) {
                g2d.setColor(new Color(0x60, 0x60, 0x60));
            } else if (isHovered) {
                g2d.setColor(new Color(0x50, 0x50, 0x50));
            } else {
                g2d.setColor(new Color(0x40, 0x40, 0x40));
            }
            g2d.fillRect(x, BEATS_Y, buttonWidth, BEATS_HEIGHT);
            
            // Border
            g2d.setColor(new Color(0x30, 0x30, 0x30));
            g2d.drawRect(x, BEATS_Y, buttonWidth, BEATS_HEIGHT);
            
            // Text
            String label = BEAT_OPTIONS[i] + "";
            int textWidth = fm.stringWidth(label);
            int textX = x + (buttonWidth - textWidth) / 2;
            int textY = BEATS_Y + (BEATS_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            
            g2d.setColor(isSelected ? Color.WHITE : new Color(0xAA, 0xAA, 0xAA));
            g2d.drawString(label, textX, textY);
        }
    }
    
    private void drawBpmSelector(Graphics2D g2d) {
        boolean isDisabled = isRecording(); // Only disabled during recording
        
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
        trackManager.setBpm(bpm);
        repaint();
    }
    
    public int getLoopBeats() {
        return trackManager.getLoopBeats();
    }
    
    public void setLoopBeats(int beats) {
        trackManager.setLoopBeats(beats);
        // Update UI selection
        for (int i = 0; i < BEAT_OPTIONS.length; i++) {
            if (BEAT_OPTIONS[i] == beats) {
                selectedBeatIndex = i;
                break;
            }
        }
        repaint();
    }
}

