import java.awt.Color;

/**
 * Represents a single MIDI note in the sequencer
 */
public class MidiNote {
    
    // Timing (in beats, not milliseconds for easier grid alignment)
    private double startBeat;
    private double durationBeats;
    
    // Musical properties
    private String instrumentType;  // "PIANO", "GUITAR", "DRUM", "SNARE"
    private int midiNote;          // MIDI note number (0-127)
    private int drumKey;           // Drum key (0=small, 1=mid, 2=big)
    private float velocity;        // 0.0 to 1.0
    
    // Visual/Sound properties from source element
    private int colorRGB;
    private int heightPx;
    private String elementId;
    
    // Track info for display color
    private int trackIndex;
    private int trackColorRGB;
    
    // Selection state (for UI)
    private boolean selected;
    
    // Row index in the sequencer (for display)
    private int rowIndex;
    
    public MidiNote(double startBeat, double durationBeats, String instrumentType,
                    int midiNote, int drumKey, float velocity,
                    int colorRGB, int heightPx, String elementId) {
        this.startBeat = startBeat;
        this.durationBeats = durationBeats;
        this.instrumentType = instrumentType;
        this.midiNote = midiNote;
        this.drumKey = drumKey;
        this.velocity = velocity;
        this.colorRGB = colorRGB;
        this.heightPx = heightPx;
        this.elementId = elementId;
        this.selected = false;
        this.rowIndex = 0;
    }
    
    // Copy constructor
    public MidiNote(MidiNote other) {
        this.startBeat = other.startBeat;
        this.durationBeats = other.durationBeats;
        this.instrumentType = other.instrumentType;
        this.midiNote = other.midiNote;
        this.drumKey = other.drumKey;
        this.velocity = other.velocity;
        this.colorRGB = other.colorRGB;
        this.heightPx = other.heightPx;
        this.elementId = other.elementId;
        this.trackIndex = other.trackIndex;
        this.trackColorRGB = other.trackColorRGB;
        this.selected = false;
        this.rowIndex = other.rowIndex;
    }
    
    // Getters
    public double getStartBeat() { return startBeat; }
    public double getDurationBeats() { return durationBeats; }
    public double getEndBeat() { return startBeat + durationBeats; }
    public String getInstrumentType() { return instrumentType; }
    public int getMidiNote() { return midiNote; }
    public int getDrumKey() { return drumKey; }
    public float getVelocity() { return velocity; }
    public int getColorRGB() { return colorRGB; }
    public int getHeightPx() { return heightPx; }
    public String getElementId() { return elementId; }
    public boolean isSelected() { return selected; }
    public int getRowIndex() { return rowIndex; }
    public int getTrackIndex() { return trackIndex; }
    public int getTrackColorRGB() { return trackColorRGB; }
    
    // Setters
    public void setStartBeat(double startBeat) { this.startBeat = Math.max(0, startBeat); }
    public void setDurationBeats(double durationBeats) { this.durationBeats = Math.max(0.125, durationBeats); }
    public void setVelocity(float velocity) { this.velocity = Math.max(0, Math.min(1, velocity)); }
    public void setSelected(boolean selected) { this.selected = selected; }
    public void setRowIndex(int rowIndex) { this.rowIndex = rowIndex; }
    public void setColorRGB(int colorRGB) { this.colorRGB = colorRGB; }
    public void setHeightPx(int heightPx) { this.heightPx = heightPx; }
    public void setTrackIndex(int trackIndex) { this.trackIndex = trackIndex; }
    public void setTrackColorRGB(int trackColorRGB) { this.trackColorRGB = trackColorRGB; }
    
    // Get track color for display
    public Color getTrackColor() {
        if (trackColorRGB != 0) {
            return new Color(trackColorRGB);
        }
        // Fallback to default track colors
        return getDefaultTrackColor(trackIndex);
    }
    
    private static final Color[] TRACK_COLORS = {
        new Color(0x00, 0xBF, 0xFF), // Track 1 - Cyan/Blue
        new Color(0xFF, 0x00, 0x00), // Track 2 - Red
        new Color(0xFF, 0x8C, 0x00), // Track 3 - Orange
        new Color(0xFF, 0xD7, 0x00), // Track 4 - Gold/Yellow
        new Color(0x32, 0xCD, 0x32), // Track 5 - Lime Green
        new Color(0x00, 0xCE, 0xD1), // Track 6 - Dark Turquoise
        new Color(0x94, 0x00, 0xD3), // Track 7 - Dark Violet
    };
    
    private static Color getDefaultTrackColor(int trackIndex) {
        if (trackIndex >= 0 && trackIndex < TRACK_COLORS.length) {
            return TRACK_COLORS[trackIndex];
        }
        return TRACK_COLORS[0];
    }
    
    // Move note by delta beats
    public void move(double deltaBeats) {
        this.startBeat = Math.max(0, this.startBeat + deltaBeats);
    }
    
    // Resize note (change duration)
    public void resize(double newDuration) {
        this.durationBeats = Math.max(0.125, newDuration);
    }
    
    // Convert to milliseconds at given BPM
    public long getStartMs(int bpm) {
        return (long) (startBeat * 60000.0 / bpm);
    }
    
    public int getDurationMs(int bpm) {
        return (int) (durationBeats * 60000.0 / bpm);
    }
    
    // Get display color
    public Color getDisplayColor() {
        return new Color(colorRGB);
    }
    
    // Get a unique key for grouping (instrument + note)
    public String getRowKey() {
        if ("DRUM".equals(instrumentType) || "SNARE".equals(instrumentType)) {
            return instrumentType + "_" + drumKey;
        } else {
            return instrumentType + "_" + midiNote;
        }
    }
    
    @Override
    public String toString() {
        return String.format("MidiNote[%s, beat=%.2f, dur=%.2f, note=%d]", 
            instrumentType, startBeat, durationBeats, midiNote);
    }
}

