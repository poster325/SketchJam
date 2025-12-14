import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a recorded track with timestamped note events
 */
public class Track {
    
    /**
     * A single note event in the track
     */
    public static class NoteEvent {
        public final long timestampMs;  // Time from track start in milliseconds
        public final String instrumentType;  // "Piano", "Guitar", "Drum", "Snare Drum"
        public final int noteIndex;  // 0-11 for chromatic notes (C=0, C#=1, etc.)
        public final int octave;  // 1-5
        public final float velocity;  // 0.0-1.0 (opacity)
        public final int durationMs;  // Duration for guitar notes
        
        public NoteEvent(long timestampMs, String instrumentType, int noteIndex, int octave, float velocity, int durationMs) {
            this.timestampMs = timestampMs;
            this.instrumentType = instrumentType;
            this.noteIndex = noteIndex;
            this.octave = octave;
            this.velocity = velocity;
            this.durationMs = durationMs;
        }
    }
    
    private String name;
    private Color color;
    private List<NoteEvent> events;
    private int bpm;
    private long durationMs;  // Total duration of the track
    
    public Track(String name, Color color, int bpm) {
        this.name = name;
        this.color = color;
        this.bpm = bpm;
        this.events = new ArrayList<>();
        this.durationMs = 0;
    }
    
    public void addEvent(NoteEvent event) {
        events.add(event);
        // Update duration to include this event
        if (event.timestampMs + event.durationMs > durationMs) {
            durationMs = event.timestampMs + event.durationMs;
        }
    }
    
    public List<NoteEvent> getEvents() {
        return events;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Color getColor() {
        return color;
    }
    
    public int getBpm() {
        return bpm;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
    
    public int getEventCount() {
        return events.size();
    }
    
    public boolean isEmpty() {
        return events.isEmpty();
    }
    
    /**
     * Calculate the duration of 16 beats at current BPM
     */
    public static long get16BeatDurationMs(int bpm) {
        // 1 beat = 60000 / bpm milliseconds
        // 16 beats = 16 * 60000 / bpm
        return (16 * 60000L) / bpm;
    }
}

