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
    private int metronomeBeatIndex = 0;
    
    // Track management
    private TrackManager trackManager;
    
    // MIDI sequencer
    private MidiSequencerPanel midiSequencer;
    private Timer playheadTimer;
    private boolean isMidiPlaying = false;
    
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
                // Look up element by ID to use current properties
                Track.NoteEvent playEvent = event;
                if (event.elementId != null && canvas != null) {
                    DrawableElement element = canvas.getElementById(event.elementId);
                    if (element != null) {
                        // Use current element properties
                        int currentColorRGB = element.getColor().getRGB();
                        float currentVelocity = element.getOpacity();
                        int currentHeight = element.getBounds().height;
                        
                        playEvent = new Track.NoteEvent(
                            event.timestampMs,
                            event.instrumentType,
                            event.midiNote,
                            event.drumKey,
                            currentVelocity,
                            event.durationMs,
                            currentColorRGB,
                            currentHeight,
                            event.elementId
                        );
                    }
                }
                SoundManager.getInstance().playNoteEvent(playEvent);
            }

            @Override
            public void onRecordingAutoStopped(String reason) {
                // 메트로놈 끄기 + UI/모드 정리
                stopMetronome();
                isCountingIn = false; // 혹시 남아있으면 정리

                if (canvas != null) {
                    canvas.setInteractionMode(SketchCanvas.InteractionMode.OBJECT);
                    canvas.repaint();
                }

                repaint();
                System.out.println("Recording auto-stopped: " + reason);
            }
            
            @Override
            public void onLoopRestart() {
                // Reset metronome to downbeat when loop restarts
                resetMetronomeToDownbeat();
                System.out.println("Loop restart: metronome synced to downbeat");
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
                        trackManager.setBpm(bpm);
                        updateMetronomeBpm();
                        repaint();
                    }
                    else if (x >= TRACK_WIDTH - BPM_BUTTON_SIZE && x < TRACK_WIDTH) {
                        bpm = Math.min(MAX_BPM, bpm + 5);
                        trackManager.setBpm(bpm);
                        updateMetronomeBpm();
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
                // Check both TrackManager playing AND MIDI playing
                if (!trackManager.isPlaying() && !isMidiPlaying) {
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
                // Check both TrackManager recording AND MIDI recording
                if (!trackManager.isRecording() && !isRecordingToMidi) {
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
        if (midiSequencer != null) {
            midiSequencer.getSequence().setLooping(loopEnabled);
        }
    }
    
    private void updateLoopBeats() {
        int beats = BEAT_OPTIONS[selectedBeatIndex];
        trackManager.setLoopBeats(beats);
        if (midiSequencer != null) {
            midiSequencer.getSequence().setLoopBeats(beats);
        }
        System.out.println("Loop beats set to: " + beats);
    }
    
    public int getSelectedBeats() {
        return BEAT_OPTIONS[selectedBeatIndex];
    }
    
    private void startPlaying() {
        // Use MIDI sequence for playback instead of TrackManager
        if (midiSequencer != null) {
            startMidiPlayback();
        } else {
            trackManager.startPlayback();
        }
        // Switch to instrument/preview mode
        if (canvas != null) {
            canvas.setInteractionMode(SketchCanvas.InteractionMode.PLAY);
            canvas.repaint();
        }
        System.out.println("Preview mode started");
    }
    
    private void stopPlaying() {
        stopMidiPlayback();
        trackManager.stopPlayback();
        // Return to edit mode
        if (canvas != null) {
            canvas.setInteractionMode(SketchCanvas.InteractionMode.OBJECT);
            canvas.repaint();
        }
        System.out.println("Preview mode stopped");
    }
    
    private long playbackStartTime;
    private double lastPlayedBeat = -1;
    
    private void startMidiPlayback() {
        if (midiSequencer == null) return;
        
        isMidiPlaying = true;
        MidiSequence seq = midiSequencer.getSequence();
        midiSequencer.setPlaying(true);
        playbackStartTime = System.currentTimeMillis();
        lastPlayedBeat = -1;
        
        // Start playhead timer (5ms for tighter timing)
        if (playheadTimer != null) playheadTimer.stop();
        playheadTimer = new Timer(5, e -> {
            long elapsed = System.currentTimeMillis() - playbackStartTime;
            double currentBeat = (elapsed * bpm) / 60000.0;
            
            // Loop handling
            if (loopEnabled && seq.getLoopBeats() > 0) {
                if (currentBeat >= seq.getLoopBeats()) {
                    playbackStartTime = System.currentTimeMillis();
                    currentBeat = 0;
                    lastPlayedBeat = -1;
                }
            }
            
            // Play notes FIRST (before updating visual playhead)
            for (MidiNote note : seq.getNotes()) {
                // Per-note compensation: only for Piano/Guitar using specific SF2s
                double triggerBeat = note.getStartBeat() - getNoteCompensationBeats(note);
                if (triggerBeat > lastPlayedBeat && triggerBeat <= currentBeat) {
                    playMidiNote(note);
                }
            }
            
            lastPlayedBeat = currentBeat;
            
            // Update playhead visual AFTER playing notes
            midiSequencer.setPlayheadBeat(currentBeat);
        });
        playheadTimer.start();
    }
    
    private void stopMidiPlayback() {
        isMidiPlaying = false;
        if (playheadTimer != null) {
            playheadTimer.stop();
            playheadTimer = null;
        }
        if (midiSequencer != null) {
            midiSequencer.setPlaying(false);
            midiSequencer.setPlayheadBeat(0);
        }
    }
    
    private void playMidiNote(MidiNote note) {
        // Default to stored values
        int colorRGB = note.getColorRGB();
        int heightPx = note.getHeightPx();
        float velocity = note.getVelocity();
        int midiNoteNum = note.getMidiNote();
        int drumKey = note.getDrumKey();
        String instrumentType = note.getInstrumentType();
        
        // Look up element by ID to use CURRENT properties (reflects element edits)
        if (note.getElementId() != null && canvas != null) {
            DrawableElement element = canvas.getElementById(note.getElementId());
            if (element != null) {
                // Update from current element state
                colorRGB = element.getColor().getRGB();
                velocity = element.getOpacity();
                heightPx = element.getBounds().height;
                
                // Recalculate midiNote based on current color for Piano/Guitar
                if ("Piano".equals(instrumentType) || "Guitar".equals(instrumentType)) {
                    Color color = element.getColor();
                    int noteIndex = getNoteIndexFromColor(color);
                    String mapped = element.getMappedValue();
                    int octave = parseOctaveFromMapped(mapped, instrumentType);
                    midiNoteNum = (octave + 1) * 12 + noteIndex;
                }
                
                // For drums, recalculate drumKey based on current size
                if ("Drum".equals(instrumentType) || "Snare".equals(instrumentType)) {
                    String mapped = element.getMappedValue();
                    if ("Drum".equals(instrumentType)) {
                        drumKey = mapDrumToMidi(mapped);
                    } else {
                        drumKey = mapSnareToMidi(mapped);
                    }
                }
            }
        }
        
        // Use note duration from MIDI sequencer (respects user edits)
        int durationMs = note.getDurationMs(bpm);
        // Minimum duration to ensure sound plays
        if (durationMs < 100) durationMs = 100;
        
        // Create a NoteEvent for playback
        Track.NoteEvent event = new Track.NoteEvent(
            note.getStartMs(bpm),
            instrumentType,
            midiNoteNum,
            drumKey,
            velocity,
            durationMs,
            colorRGB,
            heightPx,
            note.getElementId()
        );
        
        SoundManager.getInstance().playNoteEvent(event);
    }
    
    // Helper methods for recalculating note properties from current element state
    private int getNoteIndexFromColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        // Use Math.round to match SoundManager calculation
        return (int)Math.round(hsb[0] * 12) % 12;
    }
    
    private int parseOctaveFromMapped(String mapped, String instrumentType) {
        try {
            if ("Piano".equals(instrumentType)) {
                // Format: "Octave X" - add 1 to match instrument mode calculation
                return Integer.parseInt(mapped.split(" ")[1]) + 1;
            } else if ("Guitar".equals(instrumentType)) {
                // Format: "Octave X, Duration Y"
                return Integer.parseInt(mapped.split(",")[0].split(" ")[1]);
            }
        } catch (Exception e) {
            // Default octave
        }
        return 3;
    }
    
    private int mapDrumToMidi(String drumType) {
        return switch (drumType) {
            case "High Tom" -> 50;
            case "Mid Tom" -> 47;
            case "Floor Tom" -> 43;
            case "Bass Drum" -> 36;
            default -> 36;
        };
    }
    
    private int mapSnareToMidi(String snareType) {
        return switch (snareType) {
            case "Rim Shot" -> 37;
            case "Middle Shot" -> 38;
            default -> 38;
        };
    }
    
    /**
     * Get per-note SF2 latency compensation in beats.
     * Returns 125ms worth of beats ONLY for:
     * - Piano notes when Piano SF2 is 8bitsf.SF2 or contains "distortion"
     * - Guitar notes when Guitar SF2 is Distortion_Guitar.sf2 or contains "8bit"
     */
    private double getNoteCompensationBeats(MidiNote note) {
        String type = note.getInstrumentType();
        SoundManager sm = SoundManager.getInstance();
        
        // Check Piano notes against Piano SF2
        if ("Piano".equals(type)) {
            String sf2 = sm.getSelectedPianoSF2();
            if (sf2 != null) {
                String lower = sf2.toLowerCase();
                if (lower.contains("8bit") || lower.contains("distortion")) {
                    return 125.0 * bpm / 60000.0;
                }
            }
        }
        
        // Check Guitar notes against Guitar SF2
        if ("Guitar".equals(type)) {
            String sf2 = sm.getSelectedGuitarSF2();
            if (sf2 != null) {
                String lower = sf2.toLowerCase();
                if (lower.contains("8bit") || lower.contains("distortion")) {
                    return 125.0 * bpm / 60000.0;
                }
            }
        }
        
        // No compensation for drums or other SF2s
        return 0.0;
    }
    
    private boolean isCountingIn = false;
    private Timer countInTimer;
    
    private boolean isRecordingToMidi = false;
    private long recordingStartTime;
    private int currentMidiTrackIndex = 0;
    
    // Track colors (same as TrackManager)
    private static final java.awt.Color[] TRACK_COLORS = {
        new java.awt.Color(0x00, 0xBF, 0xFF), // Track 1 - Cyan/Blue
        new java.awt.Color(0xFF, 0x00, 0x00), // Track 2 - Red
        new java.awt.Color(0xFF, 0x8C, 0x00), // Track 3 - Orange
        new java.awt.Color(0xFF, 0xD7, 0x00), // Track 4 - Gold/Yellow
        new java.awt.Color(0x32, 0xCD, 0x32), // Track 5 - Lime Green
        new java.awt.Color(0x00, 0xCE, 0xD1), // Track 6 - Dark Turquoise
        new java.awt.Color(0x94, 0x00, 0xD3), // Track 7 - Dark Violet
    };
    
    private void startRecording() {
        // Start 4-beat count-in before actual recording
        startCountIn(() -> {
            if (midiSequencer != null) {
                // Use MIDI sequencer for recording
                isRecordingToMidi = true;
                recordingStartTime = System.currentTimeMillis();
                
                // Set track index to next available (after existing tracks)
                MidiSequence seq = midiSequencer.getSequence();
                int maxExisting = -1;
                for (MidiNote note : seq.getNotes()) {
                    maxExisting = Math.max(maxExisting, note.getTrackIndex());
                }
                currentMidiTrackIndex = maxExisting + 1;
                
                midiSequencer.setPlaying(true);
                startMidiRecordingPlayback();
            } else {
                trackManager.startRecording();
            }
            startMetronome(); // Metronome only in record mode
            // Switch to instrument/preview mode for recording
            if (canvas != null) {
                canvas.setInteractionMode(SketchCanvas.InteractionMode.PLAY);
                canvas.repaint();
            }
            System.out.println("Record mode started");
        });
    }
    
    private void startMidiRecordingPlayback() {
        if (midiSequencer == null) return;
        
        MidiSequence seq = midiSequencer.getSequence();
        playbackStartTime = System.currentTimeMillis();
        lastPlayedBeat = -1;
        
        // Start playhead timer for recording (5ms for tighter timing)
        if (playheadTimer != null) playheadTimer.stop();
        playheadTimer = new Timer(5, e -> {
            long elapsed = System.currentTimeMillis() - playbackStartTime;
            double currentBeat = (elapsed * bpm) / 60000.0;
            
            // Loop handling during recording
            if (loopEnabled && seq.getLoopBeats() > 0) {
                if (currentBeat >= seq.getLoopBeats()) {
                    playbackStartTime = System.currentTimeMillis();
                    recordingStartTime = System.currentTimeMillis();
                    currentBeat = 0;
                    lastPlayedBeat = -1;
                    resetMetronomeToDownbeat();
                    // Increment track index for new loop (new "layer")
                    currentMidiTrackIndex++;
                }
            }
            
            // Play existing notes FIRST
            for (MidiNote note : seq.getNotes()) {
                // Per-note compensation: only for Piano/Guitar using specific SF2s
                double triggerBeat = note.getStartBeat() - getNoteCompensationBeats(note);
                if (triggerBeat > lastPlayedBeat && triggerBeat <= currentBeat) {
                    playMidiNote(note);
                }
            }
            
            lastPlayedBeat = currentBeat;
            
            // Update playhead visual AFTER
            midiSequencer.setPlayheadBeat(currentBeat);
        });
        playheadTimer.start();
    }
    
    private void startCountIn(Runnable onComplete) {
        isCountingIn = true;
        int intervalMs = 60000 / bpm;
        final int[] clickCount = {0};
        
        // Play first click immediately
        SoundManager.getInstance().playMetronomeClick(true);
        clickCount[0]++;
        System.out.println("Count-in: " + clickCount[0] + "/4");
        repaint();
        
        countInTimer = new Timer(intervalMs, e -> {
            clickCount[0]++;
            if (clickCount[0] <= 4) {
                SoundManager.getInstance().playMetronomeClick();
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
        isRecordingToMidi = false;
        stopMidiPlayback();
        trackManager.stopRecording();
        stopMetronome();
        // Return to edit mode
        if (canvas != null) {
            canvas.setInteractionMode(SketchCanvas.InteractionMode.OBJECT);
            canvas.repaint();
        }
        System.out.println("Record mode stopped");
    }
    
    /**
     * Record a note to the MIDI sequence (called from SketchCanvas)
     */
    public void recordMidiNote(String instrumentType, int midiNote, int drumKey, 
                               float velocity, int durationMs, int colorRGB, 
                               int heightPx, String elementId) {
        if (!isRecordingToMidi || midiSequencer == null) return;
        
        long elapsed = System.currentTimeMillis() - recordingStartTime;
        double startBeat = (elapsed * bpm) / 60000.0;
        
        // Use fixed duration for Piano (1 beat), otherwise calculate from durationMs
        double durationBeats;
        if ("Piano".equals(instrumentType)) {
            durationBeats = 1.0; // Fixed 1 beat for piano
        } else {
            durationBeats = (durationMs * bpm) / 60000.0;
            if (durationBeats < 0.125) durationBeats = 0.25;
        }
        
        // Snap to grid using floor (snap to earlier beat point for natural feel)
        double snapBeat = midiSequencer.getSnapBeat();
        startBeat = Math.floor(startBeat / snapBeat) * snapBeat;
        
        MidiNote note = new MidiNote(
            startBeat, durationBeats, instrumentType,
            midiNote, drumKey, velocity,
            colorRGB, heightPx, elementId
        );
        
        // Set track color
        note.setTrackIndex(currentMidiTrackIndex);
        int trackColorIdx = currentMidiTrackIndex % TRACK_COLORS.length;
        note.setTrackColorRGB(TRACK_COLORS[trackColorIdx].getRGB());
        
        midiSequencer.getSequence().addNote(note);
    }
    
    public boolean isRecordingToMidi() {
        return isRecordingToMidi;
    }
    
    private void startMetronome() {
        stopMetronome();

        int intervalMs = 60000 / bpm;

        metronomeBeatIndex = 0; // 메트로놈 시작 시 초기화
        metronomeStartTime = System.currentTimeMillis();

        metronomeTimer = new Timer(intervalMs, e -> {
            boolean downbeat = (metronomeBeatIndex == 0);
            SoundManager.getInstance().playMetronomeClick(downbeat);
            metronomeBeatIndex = (metronomeBeatIndex + 1) % 4;
        });

        // First beat comes after the interval (count-in already played beat 4)
        metronomeTimer.setInitialDelay(intervalMs);
        metronomeTimer.start();
    }
    
    private long metronomeStartTime = 0;
    
    /**
     * Reset metronome to downbeat (called when loop restarts)
     */
    public void resetMetronomeToDownbeat() {
        metronomeBeatIndex = 0;
        metronomeStartTime = System.currentTimeMillis();
        
        // Restart the timer to sync with the new loop
        if (metronomeTimer != null && metronomeTimer.isRunning()) {
            metronomeTimer.stop();
            int intervalMs = 60000 / bpm;
            metronomeTimer.setInitialDelay(intervalMs);
            metronomeTimer.start();
        }
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
    
    public void setMidiSequencer(MidiSequencerPanel midiSequencer) {
        this.midiSequencer = midiSequencer;
        // Sync sequence settings
        if (midiSequencer != null) {
            midiSequencer.getSequence().setBpm(bpm);
            midiSequencer.getSequence().setLoopBeats(getSelectedBeats());
            midiSequencer.getSequence().setLooping(loopEnabled);
        }
    }
    
    public MidiSequencerPanel getMidiSequencer() {
        return midiSequencer;
    }
    
    public boolean isPlaying() {
        return trackManager.isPlaying() || isMidiPlaying;
    }
    
    public boolean isRecording() {
        return trackManager.isRecording() || isCountingIn || isRecordingToMidi;
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
        if (midiSequencer != null) {
            // Delete all notes from this MIDI track
            MidiSequence seq = midiSequencer.getSequence();
            java.util.List<MidiNote> toRemove = new java.util.ArrayList<>();
            for (MidiNote note : seq.getNotes()) {
                if (note.getTrackIndex() == trackIndex) {
                    toRemove.add(note);
                }
            }
            for (MidiNote note : toRemove) {
                seq.removeNote(note);
            }
            
            // If deleting current track, stay on same index
            // If deleting a track before current, adjust currentMidiTrackIndex
            if (trackIndex < currentMidiTrackIndex) {
                currentMidiTrackIndex--;
            }
            
            // Re-index remaining tracks
            for (MidiNote note : seq.getNotes()) {
                if (note.getTrackIndex() > trackIndex) {
                    note.setTrackIndex(note.getTrackIndex() - 1);
                    // Update track color
                    int newIdx = note.getTrackIndex() % TRACK_COLORS.length;
                    note.setTrackColorRGB(TRACK_COLORS[newIdx].getRGB());
                }
            }
            
            midiSequencer.repaint();
            repaint();
            System.out.println("MIDI Track " + (trackIndex + 1) + " deleted");
        } else {
            trackManager.deleteTrack(trackIndex);
            System.out.println("Track " + (trackIndex + 1) + " deleted");
        }
    }
    
    private void loadIcons() {
        try {
            File symbolsDir = ResourceLoader.getSymbolsDir();
            playIcon = ImageIO.read(new File(symbolsDir, "play.png"));
            recordIcon = ImageIO.read(new File(symbolsDir, "record.png"));
            loopIcon = ImageIO.read(new File(symbolsDir, "loop.png"));
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
        
        // Draw tracks (y = TRACKS_Y) - show MIDI tracks
        g2d.setFont(FontManager.getRegular(18));
        
        if (midiSequencer != null) {
            // Show MIDI track layers
            drawMidiTracks(g2d);
        } else {
            // Fallback to TrackManager tracks
            java.util.List<Track> tracks = trackManager.getTracks();
            Track currentRecording = trackManager.getCurrentRecordingTrack();
            
            int trackIndex = 0;
            for (Track track : tracks) {
                if (trackIndex < NUM_TRACKS) {
                    drawTrack(g2d, track, trackIndex, TRACKS_Y + trackIndex * TRACK_HEIGHT);
                }
                trackIndex++;
            }
            
            if (currentRecording != null && trackIndex < NUM_TRACKS) {
                drawTrack(g2d, currentRecording, trackIndex, TRACKS_Y + trackIndex * TRACK_HEIGHT);
                g2d.setColor(new Color(255, 0, 0, 100));
                g2d.fillRect(0, TRACKS_Y + trackIndex * TRACK_HEIGHT, TRACK_WIDTH, TRACK_HEIGHT);
            }
        }
    }
    
    private void drawMidiTracks(Graphics2D g2d) {
        MidiSequence seq = midiSequencer.getSequence();
        
        // Count notes per track
        java.util.Map<Integer, Integer> notesPerTrack = new java.util.HashMap<>();
        for (MidiNote note : seq.getNotes()) {
            int ti = note.getTrackIndex();
            notesPerTrack.put(ti, notesPerTrack.getOrDefault(ti, 0) + 1);
        }
        
        // Determine how many tracks to show
        // Only show current track if actively recording, otherwise just show tracks with notes
        int maxTrack = -1;
        if (isRecordingToMidi) {
            maxTrack = currentMidiTrackIndex;
        }
        for (int ti : notesPerTrack.keySet()) {
            maxTrack = Math.max(maxTrack, ti);
        }
        
        // Draw each track layer
        for (int i = 0; i <= maxTrack && i < NUM_TRACKS; i++) {
            int y = TRACKS_Y + i * TRACK_HEIGHT;
            Color trackColor = TRACK_COLORS[i % TRACK_COLORS.length];
            
            // Background
            g2d.setColor(trackColor);
            g2d.fillRect(0, y, TRACK_WIDTH, TRACK_HEIGHT);
            
            // Hover effect
            if (hoveredTrack == i && hoveredTrackX != i) {
                g2d.setColor(new Color(255, 255, 255, 50));
                g2d.fillRect(0, y, TRACK_WIDTH - 50, TRACK_HEIGHT);
            }
            
            // Track label
            g2d.setColor(Color.WHITE);
            g2d.setFont(FontManager.getRegular(18));
            String label = "TRACK " + (i + 1);
            FontMetrics fm = g2d.getFontMetrics();
            int textY = y + (TRACK_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(label, 15, textY);
            
            // Note count
            int noteCount = notesPerTrack.getOrDefault(i, 0);
            if (noteCount > 0) {
                g2d.setFont(FontManager.getRegular(10));
                g2d.drawString(noteCount + " notes", 15, y + 15);
            }
            
            // Recording indicator for current track
            if (isRecordingToMidi && i == currentMidiTrackIndex) {
                g2d.setColor(new Color(255, 0, 0, 100));
                g2d.fillRect(0, y, TRACK_WIDTH, TRACK_HEIGHT);
            }
            
            // X button for clearing track
            if (hoveredTrackX == i) {
                g2d.setColor(new Color(0, 0, 0, 50));
                g2d.fillRect(TRACK_WIDTH - 50, y, 50, TRACK_HEIGHT);
            }
            
            // X icon
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2));
            int xCenter = TRACK_WIDTH - 25;
            int yCenter = y + TRACK_HEIGHT / 2;
            int xSize = 8;
            g2d.drawLine(xCenter - xSize, yCenter - xSize, xCenter + xSize, yCenter + xSize);
            g2d.drawLine(xCenter - xSize, yCenter + xSize, xCenter + xSize, yCenter - xSize);
        }
        
        // Show message if no tracks yet
        if (maxTrack < 0) {
            int y = TRACKS_Y;
            g2d.setColor(new Color(0x50, 0x50, 0x50));
            g2d.fillRect(0, y, TRACK_WIDTH, TRACK_HEIGHT);
            g2d.setColor(new Color(0xAA, 0xAA, 0xAA));
            g2d.setFont(FontManager.getRegular(14));
            g2d.drawString("No tracks yet", 15, y + 30);
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
        trackManager.setBpm(this.bpm);
        if (midiSequencer != null) {
            midiSequencer.getSequence().setBpm(this.bpm);
        }
        updateMetronomeBpm();
        repaint();
    }
    
    /**
     * Reset track index (called when creating new file)
     */
    public void resetMidiTrackIndex() {
        currentMidiTrackIndex = 0;
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

