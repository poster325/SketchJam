import java.awt.Color;
import java.util.*;

/**
 * Manages all MIDI notes in the sequencer
 */
public class MidiSequence {
    
    private List<MidiNote> notes;
    private int bpm;
    private int loopBeats;
    private boolean looping;
    
    // Track metadata
    private int trackCount;
    private static final Color[] TRACK_COLORS = {
        new Color(0x00, 0xBF, 0xFF), // Track 1 - Cyan/Blue
        Color.RED,                    // Track 2 - Red
        new Color(0xFF, 0x8C, 0x00), // Track 3 - Orange
        new Color(0xFF, 0xD7, 0x00), // Track 4 - Gold/Yellow
        new Color(0x32, 0xCD, 0x32), // Track 5 - Lime Green
        new Color(0x00, 0xCE, 0xD1), // Track 6 - Dark Turquoise
        new Color(0x94, 0x00, 0xD3), // Track 7 - Dark Violet
    };
    
    // Listeners
    private List<SequenceListener> listeners;
    
    public interface SequenceListener {
        void onSequenceChanged();
        void onSelectionChanged();
    }
    
    public MidiSequence() {
        this.notes = new ArrayList<>();
        this.bpm = 120;
        this.loopBeats = 16;
        this.looping = true;
        this.trackCount = 0;
        this.listeners = new ArrayList<>();
    }
    
    public void addListener(SequenceListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(SequenceListener listener) {
        listeners.remove(listener);
    }
    
    private void notifySequenceChanged() {
        for (SequenceListener l : listeners) {
            l.onSequenceChanged();
        }
    }
    
    private void notifySelectionChanged() {
        for (SequenceListener l : listeners) {
            l.onSelectionChanged();
        }
    }
    
    // Add a note
    public void addNote(MidiNote note) {
        notes.add(note);
        assignRowIndex(note);
        notifySequenceChanged();
    }
    
    // Remove a note
    public void removeNote(MidiNote note) {
        notes.remove(note);
        notifySequenceChanged();
    }
    
    // Remove selected notes
    public void removeSelectedNotes() {
        notes.removeIf(MidiNote::isSelected);
        notifySequenceChanged();
    }
    
    // Clear all notes
    public void clear() {
        notes.clear();
        trackCount = 0;
        notifySequenceChanged();
    }
    
    // Get all notes
    public List<MidiNote> getNotes() {
        return new ArrayList<>(notes);
    }
    
    // Get notes sorted by start time
    public List<MidiNote> getNotesSorted() {
        List<MidiNote> sorted = new ArrayList<>(notes);
        sorted.sort(Comparator.comparingDouble(MidiNote::getStartBeat));
        return sorted;
    }
    
    // Get notes in a time range (for playback)
    public List<MidiNote> getNotesInRange(double startBeat, double endBeat) {
        List<MidiNote> result = new ArrayList<>();
        for (MidiNote note : notes) {
            if (note.getStartBeat() >= startBeat && note.getStartBeat() < endBeat) {
                result.add(note);
            }
        }
        return result;
    }
    
    // Get selected notes
    public List<MidiNote> getSelectedNotes() {
        List<MidiNote> selected = new ArrayList<>();
        for (MidiNote note : notes) {
            if (note.isSelected()) {
                selected.add(note);
            }
        }
        return selected;
    }
    
    // Select note
    public void selectNote(MidiNote note, boolean addToSelection) {
        if (!addToSelection) {
            clearSelection();
        }
        note.setSelected(true);
        notifySelectionChanged();
    }
    
    // Clear selection
    public void clearSelection() {
        for (MidiNote note : notes) {
            note.setSelected(false);
        }
        notifySelectionChanged();
    }
    
    // Select notes in rectangle (for box selection)
    public void selectNotesInRect(double startBeat, double endBeat, int startRow, int endRow, boolean addToSelection) {
        if (!addToSelection) {
            clearSelection();
        }
        
        double minBeat = Math.min(startBeat, endBeat);
        double maxBeat = Math.max(startBeat, endBeat);
        int minRow = Math.min(startRow, endRow);
        int maxRow = Math.max(startRow, endRow);
        
        for (MidiNote note : notes) {
            if (note.getStartBeat() < maxBeat && note.getEndBeat() > minBeat &&
                note.getRowIndex() >= minRow && note.getRowIndex() <= maxRow) {
                note.setSelected(true);
            }
        }
        notifySelectionChanged();
    }
    
    // Get unique row keys (for left panel)
    public List<String> getRowKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (MidiNote note : notes) {
            keys.add(note.getRowKey());
        }
        return new ArrayList<>(keys);
    }
    
    // Assign row index to a note based on its key
    private void assignRowIndex(MidiNote note) {
        List<String> rowKeys = getRowKeys();
        String key = note.getRowKey();
        int index = rowKeys.indexOf(key);
        if (index >= 0) {
            note.setRowIndex(index);
        } else {
            note.setRowIndex(rowKeys.size());
        }
    }
    
    // Reassign all row indices (after changes)
    public void reassignRowIndices() {
        List<String> rowKeys = getRowKeys();
        for (MidiNote note : notes) {
            int index = rowKeys.indexOf(note.getRowKey());
            note.setRowIndex(Math.max(0, index));
        }
    }
    
    // Move selected notes
    public void moveSelectedNotes(double deltaBeat, int deltaRow) {
        for (MidiNote note : notes) {
            if (note.isSelected()) {
                note.move(deltaBeat);
                note.setRowIndex(Math.max(0, note.getRowIndex() + deltaRow));
            }
        }
        notifySequenceChanged();
    }
    
    // Duplicate selected notes
    public List<MidiNote> duplicateSelectedNotes() {
        List<MidiNote> duplicates = new ArrayList<>();
        for (MidiNote note : notes) {
            if (note.isSelected()) {
                MidiNote copy = new MidiNote(note);
                copy.move(1); // Offset by 1 beat
                duplicates.add(copy);
            }
        }
        for (MidiNote dup : duplicates) {
            notes.add(dup);
        }
        // Select only the duplicates
        clearSelection();
        for (MidiNote dup : duplicates) {
            dup.setSelected(true);
        }
        notifySequenceChanged();
        return duplicates;
    }
    
    // Quantize selected notes to grid
    public void quantizeSelectedNotes(double gridSize) {
        for (MidiNote note : notes) {
            if (note.isSelected()) {
                double quantized = Math.round(note.getStartBeat() / gridSize) * gridSize;
                note.setStartBeat(quantized);
            }
        }
        notifySequenceChanged();
    }
    
    // Convert from old Track format
    public void importFromTracks(List<Track> tracks) {
        clear();
        for (Track track : tracks) {
            for (Track.NoteEvent event : track.getEvents()) {
                // Convert timestamp to beats
                double startBeat = (event.timestampMs * bpm) / 60000.0;
                double durationBeats = (event.durationMs * bpm) / 60000.0;
                if (durationBeats < 0.125) durationBeats = 0.25; // Minimum duration
                
                MidiNote note = new MidiNote(
                    startBeat,
                    durationBeats,
                    event.instrumentType,
                    event.midiNote,
                    event.drumKey,
                    event.velocity,
                    event.colorRGB,
                    event.heightPx,
                    event.elementId
                );
                notes.add(note);
            }
        }
        reassignRowIndices();
        notifySequenceChanged();
    }
    
    // Convert to Track format (for compatibility)
    public List<Track> exportToTracks() {
        // Group notes by track (we can use color or just make one track)
        Track track = new Track("TRACK 1", TRACK_COLORS[0], bpm);
        
        for (MidiNote note : getNotesSorted()) {
            Track.NoteEvent event = new Track.NoteEvent(
                note.getStartMs(bpm),
                note.getInstrumentType(),
                note.getMidiNote(),
                note.getDrumKey(),
                note.getVelocity(),
                note.getDurationMs(bpm),
                note.getColorRGB(),
                note.getHeightPx(),
                note.getElementId()
            );
            track.addEvent(event);
        }
        
        List<Track> result = new ArrayList<>();
        if (!track.isEmpty()) {
            result.add(track);
        }
        return result;
    }
    
    // Getters/Setters
    public int getBpm() { return bpm; }
    public void setBpm(int bpm) { 
        this.bpm = bpm; 
        notifySequenceChanged();
    }
    
    public int getLoopBeats() { return loopBeats; }
    public void setLoopBeats(int loopBeats) { 
        this.loopBeats = loopBeats; 
        notifySequenceChanged();
    }
    
    public boolean isLooping() { return looping; }
    public void setLooping(boolean looping) { this.looping = looping; }
    
    public long getLoopDurationMs() {
        return (loopBeats * 60000L) / bpm;
    }
    
    public int getNoteCount() {
        return notes.size();
    }
    
    public boolean isEmpty() {
        return notes.isEmpty();
    }
}

