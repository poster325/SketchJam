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
    
    // Track data
    public List<TrackData> tracks = new ArrayList<>();
    
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
        private static final long serialVersionUID = 1L;
        
        public String type; // "Piano", "Guitar", "Drum", "Snare Drum"
        public int x, y, width, height;
        public int colorRGB;
        public float opacity;
        public int rotationDegrees;
        
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
            Track track = new Track(name, Color.WHITE, 120);
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
        private static final long serialVersionUID = 1L;
        
        public long timestampMs;
        public String instrumentType;
        public int midiNote;
        public int drumKey;
        public float velocity;
        public int durationMs;

        public NoteEventData(Track.NoteEvent event) {
            timestampMs = event.timestampMs;
            instrumentType = event.instrumentType;
            midiNote = event.midiNote;
            drumKey = event.drumKey;
            velocity = event.velocity;
            durationMs = event.durationMs;
        }

        public Track.NoteEvent toNoteEvent() {
            return new Track.NoteEvent(timestampMs, instrumentType, midiNote, drumKey, velocity, durationMs);
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

