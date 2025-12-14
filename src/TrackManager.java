import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;

/**
 * Manages track recording and playback for the loopstation
 */
public class TrackManager {
    
    private static final int MAX_TRACKS = 7;
    
    // Track colors
    private static final Color[] TRACK_COLORS = {
        new Color(0x00, 0xBF, 0xFF), // Track 1 - Cyan/Blue
        Color.RED,                    // Track 2 - Red
        new Color(0xFF, 0x8C, 0x00), // Track 3 - Orange
        new Color(0xFF, 0xD7, 0x00), // Track 4 - Gold/Yellow
        new Color(0x32, 0xCD, 0x32), // Track 5 - Lime Green
        new Color(0x00, 0xCE, 0xD1), // Track 6 - Dark Turquoise
        new Color(0x94, 0x00, 0xD3), // Track 7 - Dark Violet
    };
    
    private List<Track> tracks;
    private Track currentRecordingTrack;
    private long recordingStartTime;
    private int currentBpm;
    private boolean isRecording;
    private boolean isPlaying;
    private boolean isLooping;
    
    // Playback
    private Timer playbackTimer;
    private long playbackStartTime;
    private int currentPlaybackIndex;
    
    // Loop timing - separate timers for recording and playback
    private Timer recordingLoopTimer;
    private Timer playbackLoopTimer;
    private long loopDurationMs;
    
    // Listener for UI updates
    private TrackUpdateListener listener;
    
    public interface TrackUpdateListener {
        void onTracksUpdated();
        void onPlayNote(Track.NoteEvent event);
    }
    
    private int loopBeats = 16; // Default to 16 beats
    
    public TrackManager() {
        tracks = new ArrayList<>();
        isRecording = false;
        isPlaying = false;
        isLooping = false;
        currentBpm = 120;
        updateLoopDuration();
    }
    
    public void setListener(TrackUpdateListener listener) {
        this.listener = listener;
    }
    
    public void setBpm(int bpm) {
        this.currentBpm = bpm;
        updateLoopDuration();
    }
    
    public void setLoopBeats(int beats) {
        this.loopBeats = beats;
        updateLoopDuration();
    }
    
    private void updateLoopDuration() {
        // Calculate loop duration: beats * (60000 / bpm) ms
        this.loopDurationMs = (loopBeats * 60000L) / currentBpm;
    }
    
    public void setLooping(boolean looping) {
        this.isLooping = looping;
    }
    
    public int getLoopBeats() {
        return loopBeats;
    }
    
    /**
     * Start recording a new track
     */
    public void startRecording() {
        if (tracks.size() >= MAX_TRACKS) {
            System.out.println("Maximum tracks reached");
            return;
        }
        
        // If already recording, finalize current track
        if (isRecording && currentRecordingTrack != null) {
            finalizeRecording();
        }
        
        // Start playing existing tracks
        if (!tracks.isEmpty() && !isPlaying) {
            startPlayback();
        }
        
        // Create new track
        int trackNum = tracks.size() + 1;
        Color trackColor = TRACK_COLORS[(trackNum - 1) % TRACK_COLORS.length];
        currentRecordingTrack = new Track("TRACK " + trackNum, trackColor, currentBpm);
        
        recordingStartTime = System.currentTimeMillis();
        isRecording = true;
        
        // If looping, set up auto-finalize at 16 beats
        if (isLooping) {
            setupLoopRecordingTimer();
        }
        
        System.out.println("Recording started: " + currentRecordingTrack.getName());
        notifyTracksUpdated();
    }
    
    /**
     * Stop recording
     */
    public void stopRecording() {
        if (isRecording && currentRecordingTrack != null) {
            finalizeRecording();
        }
        isRecording = false;
        
        if (recordingLoopTimer != null) {
            recordingLoopTimer.stop();
            recordingLoopTimer = null;
        }
        
        // Also stop playback when recording stops
        stopPlayback();
        
        System.out.println("Recording stopped");
    }
    
    /**
     * Finalize the current recording and add to tracks
     */
    private void finalizeRecording() {
        if (currentRecordingTrack != null) {
            // Set duration to loop duration if looping, otherwise actual duration
            if (isLooping) {
                currentRecordingTrack.setDurationMs(loopDurationMs);
            } else {
                // Set duration to time since recording started
                long duration = System.currentTimeMillis() - recordingStartTime;
                currentRecordingTrack.setDurationMs(duration);
            }
            
            tracks.add(currentRecordingTrack);
            System.out.println("Track finalized: " + currentRecordingTrack.getName() + 
                " with " + currentRecordingTrack.getEventCount() + " events, duration: " + 
                currentRecordingTrack.getDurationMs() + "ms");
            currentRecordingTrack = null;
            notifyTracksUpdated();
            
            // Mark project as having unsaved changes
            FileManager.getInstance().markUnsaved();
        } else {
            System.out.println("No track to finalize");
        }
    }
    
    /**
     * Setup timer for loop-based recording (auto-finalize at 16 beats)
     */
    private void setupLoopRecordingTimer() {
        if (recordingLoopTimer != null) {
            recordingLoopTimer.stop();
        }
        
        recordingLoopTimer = new Timer((int) loopDurationMs, e -> {
            if (isRecording && isLooping) {
                // Finalize current track and start new one
                finalizeRecording();
                
                // Start or restart playback to play all recorded tracks
                if (!tracks.isEmpty()) {
                    if (!isPlaying) {
                        // Start playback for the first time (after Track 1 is saved)
                        startPlaybackForRecording();
                        System.out.println("Loop sync: playback started for recorded tracks");
                    } else {
                        // Reset playback to sync with new loop
                        playbackStartTime = System.currentTimeMillis();
                        lastPlayedTime = -1;
                        System.out.println("Loop sync: playback restarted");
                    }
                }
                
                if (tracks.size() < MAX_TRACKS) {
                    // Start a new track
                    int trackNum = tracks.size() + 1;
                    Color trackColor = TRACK_COLORS[(trackNum - 1) % TRACK_COLORS.length];
                    currentRecordingTrack = new Track("TRACK " + trackNum, trackColor, currentBpm);
                    recordingStartTime = System.currentTimeMillis();
                    System.out.println("Loop recording: new track " + trackNum);
                } else {
                    // Max tracks reached, stop recording
                    stopRecording();
                }
            }
        });
        recordingLoopTimer.setRepeats(true);
        recordingLoopTimer.start();
    }
    
    /**
     * Start playback specifically during recording (no loop timer conflict)
     */
    private void startPlaybackForRecording() {
        isPlaying = true;
        playbackStartTime = System.currentTimeMillis();
        lastPlayedTime = -1;
        
        // Start the playback timer to check for events
        if (playbackTimer != null) {
            playbackTimer.stop();
        }
        
        playbackTimer = new Timer(5, ev -> {
            playEventsAtCurrentTime();
        });
        playbackTimer.start();
    }
    
    /**
     * Record a note event
     */
    public void recordEvent(String instrumentType, int noteIndex, int octave, float velocity, int durationMs) {
        if (!isRecording || currentRecordingTrack == null) {
            System.out.println("Cannot record: isRecording=" + isRecording + ", hasTrack=" + (currentRecordingTrack != null));
            return;
        }
        
        long timestamp = System.currentTimeMillis() - recordingStartTime;
        
        // In loop mode, wrap timestamp within loop duration
        if (isLooping) {
            timestamp = timestamp % loopDurationMs;
        }
        
        Track.NoteEvent event = new Track.NoteEvent(
            timestamp, instrumentType, noteIndex, octave, velocity, durationMs
        );
        currentRecordingTrack.addEvent(event);
    }
    
    /**
     * Start playback of all tracks
     */
    public void startPlayback() {
        if (tracks.isEmpty()) {
            System.out.println("No tracks to play");
            return;
        }
        
        isPlaying = true;
        playbackStartTime = System.currentTimeMillis();
        lastPlayedTime = -1; // Reset for new playback
        
        // Set up playback loop
        if (isLooping) {
            setupLoopPlaybackTimer();
        }
        
        // Start a timer that checks for events to play
        if (playbackTimer != null) {
            playbackTimer.stop();
        }
        
        playbackTimer = new Timer(5, e -> {
            playEventsAtCurrentTime();
        });
        playbackTimer.start();
        
        System.out.println("Playback started");
    }
    
    /**
     * Stop playback
     */
    public void stopPlayback() {
        isPlaying = false;
        
        if (playbackTimer != null) {
            playbackTimer.stop();
            playbackTimer = null;
        }
        
        if (playbackLoopTimer != null) {
            playbackLoopTimer.stop();
            playbackLoopTimer = null;
        }
        
        System.out.println("Playback stopped");
    }
    
    /**
     * Setup timer for loop playback (restart at 16 beats)
     */
    private void setupLoopPlaybackTimer() {
        if (playbackLoopTimer != null) {
            playbackLoopTimer.stop();
        }
        
        playbackLoopTimer = new Timer((int) loopDurationMs, e -> {
            if (isPlaying && isLooping && !isRecording) {
                // Reset playback start time for looping (only when not recording - recording timer handles sync)
                playbackStartTime = System.currentTimeMillis();
                lastPlayedTime = -1;
                System.out.println("Loop playback: restarting");
            }
        });
        playbackLoopTimer.setRepeats(true);
        playbackLoopTimer.start();
    }
    
    // Track last played timestamp to avoid double-triggering
    private long lastPlayedTime = -1;
    
    /**
     * Play events that should occur at the current time
     */
    private void playEventsAtCurrentTime() {
        if (!isPlaying) return;
        
        long currentTime = System.currentTimeMillis() - playbackStartTime;
        
        // In loop mode, wrap current time
        if (isLooping && loopDurationMs > 0) {
            currentTime = currentTime % loopDurationMs;
            // Reset lastPlayedTime when loop restarts
            if (currentTime < lastPlayedTime) {
                lastPlayedTime = -1;
            }
        }
        
        for (Track track : tracks) {
            for (Track.NoteEvent event : track.getEvents()) {
                // Check if this event should be played now
                // Play if: event timestamp is between lastPlayedTime and currentTime
                if (event.timestampMs > lastPlayedTime && event.timestampMs <= currentTime) {
                    if (listener != null) {
                        listener.onPlayNote(event);
                    }
                }
            }
        }
        
        lastPlayedTime = currentTime;
    }
    
    /**
     * Delete a track by index
     */
    public void deleteTrack(int index) {
        if (index >= 0 && index < tracks.size()) {
            tracks.remove(index);
            // Rename remaining tracks
            for (int i = 0; i < tracks.size(); i++) {
                tracks.get(i).setName("TRACK " + (i + 1));
            }
            notifyTracksUpdated();
        }
    }
    
    /**
     * Get all tracks
     */
    public List<Track> getTracks() {
        return tracks;
    }
    
    /**
     * Clear all tracks
     */
    public void clearTracks() {
        tracks.clear();
        currentRecordingTrack = null;
        isRecording = false;
        isPlaying = false;
    }
    
    /**
     * Add a track (used when loading from file)
     */
    public void addTrack(Track track) {
        tracks.add(track);
    }
    
    /**
     * Get track count including current recording
     */
    public int getTrackCount() {
        int count = tracks.size();
        if (currentRecordingTrack != null) {
            count++;
        }
        return count;
    }
    
    /**
     * Check if currently recording
     */
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * Check if currently playing
     */
    public boolean isPlaying() {
        return isPlaying;
    }
    
    /**
     * Get current recording track (for display)
     */
    public Track getCurrentRecordingTrack() {
        return currentRecordingTrack;
    }
    
    private void notifyTracksUpdated() {
        if (listener != null) {
            listener.onTracksUpdated();
        }
    }
}

