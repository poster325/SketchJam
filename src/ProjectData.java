import java.awt.Color;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds all saveable project data for .sjam files
 */
public class ProjectData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Element data
    public List<ElementData> elements = new ArrayList<>();
    
    // Track data (legacy)
    public List<TrackData> tracks = new ArrayList<>();
    
    // MIDI sequence data (new format)
    public List<MidiNoteData> midiNotes = new ArrayList<>();
    
    // Settings
    public int bpm = 120;
    public int loopBeats = 16;
    public int scaleRootNote = 0; // 0-11 for C-B
    public boolean scaleMajor = true;
    public int eqBrightnessLevel = 5; // 0-11
    
    // SF2 assignments (filenames)
    public String pianoSoundfont = null;
    public String guitarSoundfont = null;
    public String drumsSoundfont = null;
    
    // Selected color
    public int selectedColorCol = -1;
    public int selectedColorRow = -1;
    
    /**
     * Element data for serialization
     */
    public static class ElementData implements Serializable {
        private static final long serialVersionUID = 2L;
        
        public String type; // "Piano", "Guitar", "Drum", "Snare Drum"
        public int x, y, width, height;
        public int colorRGB;
        public float opacity;
        public int rotationDegrees;
        public String elementId; // Unique ID for tracking across save/load
        
        public ElementData() {}
        
        public ElementData(DrawableElement element) {
            this.type = element.getElementType();
            java.awt.Point pos = element.getPosition();
            java.awt.Dimension size = element.getSize();
            this.x = pos.x;
            this.y = pos.y;
            this.width = size.width;
            this.height = size.height;
            this.colorRGB = element.getColor().getRGB();
            this.opacity = element.getOpacity();
            this.rotationDegrees = element.getRotation();
            this.elementId = element.getElementId();
        }
        
        public DrawableElement toElement() {
            Color color = new Color(colorRGB);
            AbstractElement element;
            
            switch (type) {
                case "Piano":
                    // PianoElement(x, y, height, color) - fixed width
                    element = new PianoElement(x, y, height, color);
                    break;
                case "Guitar":
                    // GuitarElement(x, y, width, height, color)
                    element = new GuitarElement(x, y, width, height, color);
                    break;
                case "Drum":
                    // DrumElement(x, y, size, color)
                    element = new DrumElement(x, y, width, color);
                    break;
                case "Snare Drum":
                    // SnareDrumElement(x, y, size, color)
                    element = new SnareDrumElement(x, y, width, color);
                    break;
                default:
                    return null;
            }
            
            element.setOpacity(opacity);
            element.rotation = rotationDegrees;
            if (elementId != null) {
                element.setElementId(elementId);
            }
            return element;
        }
    }
    
    /**
     * Track data for serialization
     */
    public static class TrackData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public String name;
        public List<NoteEventData> events = new ArrayList<>();
        
        public TrackData() {}
        
        public TrackData(Track track) {
            this.name = track.getName();
            for (Track.NoteEvent event : track.getEvents()) {
                events.add(new NoteEventData(event));
            }
        }
        
        public Track toTrack() {
            Track track = new Track(name, Color.GRAY, 120);
            for (NoteEventData eventData : events) {
                track.addEvent(eventData.toNoteEvent());
            }
            return track;
        }
    }
    
    /**
     * Note event data for serialization
     */
    public static class NoteEventData implements Serializable {
        private static final long serialVersionUID = 2L;
        
        public long timestampMs;
        public String instrumentType;
        public int midiNote;
        public int drumKey;
        public float velocity;
        public int durationMs;
        public int colorRGB;
        public int heightPx;
        public String elementId;

        public NoteEventData() {} // Required for deserialization
        
        public NoteEventData(Track.NoteEvent e) {
            timestampMs = e.timestampMs;
            instrumentType = e.instrumentType;
            midiNote = e.midiNote;
            drumKey = e.drumKey;
            velocity = e.velocity;
            durationMs = e.durationMs;
            colorRGB = e.colorRGB;
            heightPx = e.heightPx;
            elementId = e.elementId;
        }

        public Track.NoteEvent toNoteEvent() {
            return new Track.NoteEvent(timestampMs, instrumentType, midiNote, drumKey, velocity, durationMs, colorRGB, heightPx, elementId);
        }
    }
    
    /**
     * MIDI note data for serialization
     */
    public static class MidiNoteData implements Serializable {
        private static final long serialVersionUID = 2L;
        
        public double startBeat;
        public double durationBeats;
        public String instrumentType;
        public int midiNote;
        public int drumKey;
        public float velocity;
        public int colorRGB;
        public int heightPx;
        public String elementId;
        public int rowIndex;
        public int trackIndex;
        public int trackColorRGB;
        
        public MidiNoteData() {}
        
        public MidiNoteData(MidiNote note) {
            this.startBeat = note.getStartBeat();
            this.durationBeats = note.getDurationBeats();
            this.instrumentType = note.getInstrumentType();
            this.midiNote = note.getMidiNote();
            this.drumKey = note.getDrumKey();
            this.velocity = note.getVelocity();
            this.colorRGB = note.getColorRGB();
            this.heightPx = note.getHeightPx();
            this.elementId = note.getElementId();
            this.rowIndex = note.getRowIndex();
            this.trackIndex = note.getTrackIndex();
            this.trackColorRGB = note.getTrackColorRGB();
        }
        
        public MidiNote toMidiNote() {
            MidiNote note = new MidiNote(
                startBeat, durationBeats, instrumentType,
                midiNote, drumKey, velocity,
                colorRGB, heightPx, elementId
            );
            note.setRowIndex(rowIndex);
            note.setTrackIndex(trackIndex);
            note.setTrackColorRGB(trackColorRGB);
            return note;
        }
    }
    
    /**
     * Save project to file
     */
    public void saveToFile(File file) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(this);
        }
    }
    
    /**
     * Load project from file
     */
    public static ProjectData loadFromFile(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (ProjectData) ois.readObject();
        }
    }
}

