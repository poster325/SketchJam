import javax.sound.midi.*;
import java.awt.Color;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages sound playback for SketchJam instruments using SF2 SoundFonts.
 * Loads multiple soundfonts for different instruments.
 */
public class SoundManager {
    
    private static SoundManager instance;
    
    // MIDI synthesizer for SF2 playback
    private Synthesizer synthesizer;
    private MidiChannel[] channels;
    private boolean soundfontsLoaded = false;
    
    // Channel assignments
    private static final int PIANO_CHANNEL = 0;
    private static final int GUITAR_CHANNEL = 1;
    private static final int DRUM_CHANNEL = 9;  // Standard MIDI drum channel
    
    // Note names matching ColorPalette order
    public static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    
    // Colors matching the palette (for note detection from element color)
    private static final Color[] NOTE_COLORS = {
        Color.decode("#FF0000"), // C  - Red
        Color.decode("#FF8000"), // C# - Orange
        Color.decode("#FFFF00"), // D  - Yellow
        Color.decode("#80FF00"), // D# - Yellow-Green
        Color.decode("#00FF00"), // E  - Green
        Color.decode("#00FF80"), // F  - Green-Cyan
        Color.decode("#00FFFF"), // F# - Cyan
        Color.decode("#0080FF"), // G  - Cyan-Blue
        Color.decode("#0000FF"), // G# - Blue
        Color.decode("#8000FF"), // A  - Purple
        Color.decode("#FF00FF"), // A# - Magenta
        Color.decode("#FF0080"), // B  - Pink-Red
    };
    
    // Drum MIDI note numbers
    private static final int HIGH_TOM = 50;
    private static final int MID_TOM = 47;
    private static final int FLOOR_TOM = 43;
    private static final int BASS_DRUM = 36;
    private static final int SNARE_RIM = 37;
    private static final int SNARE_CENTER = 38;
    
    private SoundManager() {
        initializeSynthesizer();
    }
    
    public static SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }
    
    private void initializeSynthesizer() {
        try {
            synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();
            
            // Load all available soundfonts
            loadAllSoundFonts();
            
            channels = synthesizer.getChannels();
            
            // Set up instrument programs based on loaded soundfonts
            setupInstruments();
            
            System.out.println("MIDI Synthesizer initialized");
            
        } catch (MidiUnavailableException e) {
            System.err.println("MIDI Synthesizer not available: " + e.getMessage());
        }
    }
    
    private void loadAllSoundFonts() {
        File soundfontsDir = new File("soundfonts");
        if (!soundfontsDir.exists() || !soundfontsDir.isDirectory()) {
            System.out.println("No soundfonts folder found, using default sounds");
            loadDefaultSoundbank();
            return;
        }
        
        File[] sf2Files = soundfontsDir.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".sf2") || name.toLowerCase().endsWith(".sf3"));
        
        if (sf2Files == null || sf2Files.length == 0) {
            System.out.println("No SF2 files found, using default sounds");
            loadDefaultSoundbank();
            return;
        }
        
        List<String> loadedFonts = new ArrayList<>();
        
        for (File sf2File : sf2Files) {
            try {
                Soundbank soundbank = MidiSystem.getSoundbank(sf2File);
                if (synthesizer.isSoundbankSupported(soundbank)) {
                    synthesizer.loadAllInstruments(soundbank);
                    loadedFonts.add(sf2File.getName());
                    soundfontsLoaded = true;
                }
            } catch (Exception e) {
                System.err.println("Failed to load " + sf2File.getName() + ": " + e.getMessage());
            }
        }
        
        if (!loadedFonts.isEmpty()) {
            System.out.println("Loaded SoundFonts: " + String.join(", ", loadedFonts));
        }
        
        if (!soundfontsLoaded) {
            loadDefaultSoundbank();
        }
    }
    
    private void loadDefaultSoundbank() {
        Soundbank defaultSoundbank = synthesizer.getDefaultSoundbank();
        if (defaultSoundbank != null) {
            synthesizer.loadAllInstruments(defaultSoundbank);
        }
    }
    
    private void setupInstruments() {
        if (channels == null || channels.length == 0) return;
        
        Instrument[] instruments = synthesizer.getLoadedInstruments();
        
        // Find best piano instrument (look for "piano" or "electric piano")
        int pianoProgram = 0;
        int guitarProgram = 25;
        
        for (Instrument inst : instruments) {
            String name = inst.getName().toLowerCase();
            Patch patch = inst.getPatch();
            
            // Look for electric piano (from Galaxy_Electric_Pianos.sf2)
            if (name.contains("electric") && name.contains("piano")) {
                pianoProgram = patch.getProgram();
                System.out.println("Using piano: " + inst.getName() + " (program " + pianoProgram + ")");
            }
            // Look for any piano if no electric piano found yet
            else if (name.contains("piano") && pianoProgram == 0) {
                pianoProgram = patch.getProgram();
            }
            
            // Look for clean or jazz guitar
            if (name.contains("clean") && name.contains("guitar")) {
                guitarProgram = patch.getProgram();
                System.out.println("Using guitar: " + inst.getName() + " (program " + guitarProgram + ")");
            }
            else if (name.contains("jazz") && name.contains("guitar") && guitarProgram == 25) {
                guitarProgram = patch.getProgram();
            }
            else if (name.contains("guitar") && guitarProgram == 25) {
                guitarProgram = patch.getProgram();
            }
        }
        
        // Set up channels with found programs
        channels[PIANO_CHANNEL].programChange(pianoProgram);
        channels[GUITAR_CHANNEL].programChange(guitarProgram);
        
        System.out.println("Piano channel " + PIANO_CHANNEL + " set to program " + pianoProgram);
        System.out.println("Guitar channel " + GUITAR_CHANNEL + " set to program " + guitarProgram);
    }
    
    /**
     * Set piano soundfont by filename
     */
    public void setPianoSoundfont(String filename) {
        if (channels == null) return;
        
        int program = loadAndFindProgram(filename, "piano");
        if (program >= 0) {
            channels[PIANO_CHANNEL].programChange(program);
            System.out.println("Piano set to program " + program + " from " + (filename != null ? filename : "default"));
        }
    }
    
    /**
     * Set guitar soundfont by filename
     */
    public void setGuitarSoundfont(String filename) {
        if (channels == null) return;
        
        int program = loadAndFindProgram(filename, "guitar");
        if (program >= 0) {
            channels[GUITAR_CHANNEL].programChange(program);
            System.out.println("Guitar set to program " + program + " from " + (filename != null ? filename : "default"));
        }
    }
    
    /**
     * Set drums soundfont by filename
     */
    public void setDrumsSoundfont(String filename) {
        if (filename != null) {
            try {
                File sf2File = new File("soundfonts/" + filename);
                if (sf2File.exists()) {
                    Soundbank soundbank = MidiSystem.getSoundbank(sf2File);
                    if (synthesizer.isSoundbankSupported(soundbank)) {
                        synthesizer.loadAllInstruments(soundbank);
                        System.out.println("Drums updated from " + filename);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to load drum soundfont: " + e.getMessage());
            }
        }
    }
    
    /**
     * Load a soundfont and find a suitable program number
     */
    private int loadAndFindProgram(String filename, String instrumentType) {
        if (filename == null) {
            // Return default program
            return instrumentType.equals("piano") ? 0 : 25;
        }
        
        try {
            File sf2File = new File("soundfonts/" + filename);
            if (!sf2File.exists()) {
                System.err.println("SF2 file not found: " + filename);
                return -1;
            }
            
            Soundbank soundbank = MidiSystem.getSoundbank(sf2File);
            
            // Load all instruments from this soundfont
            if (synthesizer.isSoundbankSupported(soundbank)) {
                synthesizer.loadAllInstruments(soundbank);
                System.out.println("Loaded soundfont: " + filename);
            } else {
                System.err.println("Soundfont not supported: " + filename);
                return -1;
            }
            
            Instrument[] instruments = soundbank.getInstruments();
            
            // Find first instrument (most SF2s have their main sound at program 0)
            if (instruments.length > 0) {
                Patch patch = instruments[0].getPatch();
                System.out.println("Found instrument: " + instruments[0].getName() + " at program " + patch.getProgram());
                return patch.getProgram();
            }
            
            // Fallback: search by name
            for (Instrument inst : instruments) {
                String name = inst.getName().toLowerCase();
                Patch patch = inst.getPatch();
                
                // Return first matching instrument
                if (instrumentType.equals("piano") && 
                    (name.contains("piano") || name.contains("key") || name.contains("ep"))) {
                    return patch.getProgram();
                }
                if (instrumentType.equals("guitar") && name.contains("guitar")) {
                    return patch.getProgram();
                }
            }
            
            // If no match found, return first instrument
            if (instruments.length > 0) {
                return instruments[0].getPatch().getProgram();
            }
        } catch (Exception e) {
            System.err.println("Error finding program: " + e.getMessage());
        }
        
        return -1;
    }
    
    /**
     * Convert note index (0-11) and octave to MIDI note number
     * MIDI note 60 = C4 (middle C)
     */
    private int toMidiNote(int noteIndex, int octave) {
        return 12 * (octave + 1) + noteIndex;
    }
    
    /**
     * Detect which note (0-11) a color represents by finding closest match
     */
    public static int getNoteIndexFromColor(Color color) {
        int bestMatch = 0;
        int minDistance = Integer.MAX_VALUE;
        
        for (int i = 0; i < NOTE_COLORS.length; i++) {
            Color noteColor = NOTE_COLORS[i];
            int dr = color.getRed() - noteColor.getRed();
            int dg = color.getGreen() - noteColor.getGreen();
            int db = color.getBlue() - noteColor.getBlue();
            int distance = dr*dr + dg*dg + db*db;
            
            if (distance < minDistance) {
                minDistance = distance;
                bestMatch = i;
            }
        }
        return bestMatch;
    }
    
    /**
     * Get note name from color
     */
    public static String getNoteNameFromColor(Color color) {
        int index = getNoteIndexFromColor(color);
        return NOTE_NAMES[index];
    }
    
    /**
     * Play a note on the specified channel
     */
    private void playNote(int channel, int midiNote, int velocity, int durationMs) {
        if (channels == null || channel >= channels.length) return;
        
        final MidiChannel ch = channels[channel];
        final int note = Math.max(0, Math.min(127, midiNote)); // Clamp to valid MIDI range
        final int vel = Math.max(1, Math.min(127, velocity));
        
        // Play note in a separate thread to not block
        new Thread(() -> {
            try {
                ch.noteOn(note, vel);
                Thread.sleep(durationMs);
                ch.noteOff(note);
            } catch (InterruptedException e) {
                ch.noteOff(note);
            }
        }).start();
    }
    
    /**
     * Play piano note by note index (0-11 for C through B), octave, and volume
     */
    public void playPianoByIndex(int noteIndex, int octave, float volume) {
        int midiNote = toMidiNote(noteIndex, octave);
        int velocity = (int)(volume * 100);
        playNote(PIANO_CHANNEL, midiNote, velocity, 500);
    }
    
    /**
     * Play piano note based on color, octave, and volume
     */
    public void playPiano(Color color, int octave, float volume) {
        int noteIndex = getNoteIndexFromColor(color);
        playPianoByIndex(noteIndex, octave, volume);
    }
    
    // Track currently held piano note for sustain
    private int currentPianoNote = -1;
    
    /**
     * Start playing a piano note (for press-and-hold behavior)
     * Call stopPianoNote() to release
     */
    public void startPianoNote(Color color, int octave, float volume) {
        if (channels == null) return;
        
        // Stop any currently playing note first
        if (currentPianoNote >= 0) {
            channels[PIANO_CHANNEL].noteOff(currentPianoNote);
        }
        
        int noteIndex = getNoteIndexFromColor(color);
        currentPianoNote = toMidiNote(noteIndex, octave);
        int velocity = Math.max(1, Math.min(127, (int)(volume * 100)));
        
        MidiChannel ch = channels[PIANO_CHANNEL];
        ch.noteOn(currentPianoNote, velocity);
        System.out.println("Piano note ON: " + currentPianoNote + " velocity: " + velocity);
    }
    
    /**
     * Stop the currently playing piano note
     */
    public void stopPianoNote() {
        if (channels == null || currentPianoNote < 0) return;
        
        MidiChannel ch = channels[PIANO_CHANNEL];
        ch.noteOff(currentPianoNote);
        System.out.println("Piano note OFF: " + currentPianoNote);
        currentPianoNote = -1;
    }
    
    /**
     * Play guitar note by note index
     */
    public void playGuitarByIndex(int noteIndex, int octave, float volume) {
        int midiNote = toMidiNote(noteIndex, octave);
        int velocity = (int)(volume * 100);
        playNote(GUITAR_CHANNEL, midiNote, velocity, 400);
    }
    
    // Track guitar note versions and last play time
    private java.util.Map<Integer, Long> guitarNoteVersions = new java.util.HashMap<>();
    private java.util.Map<Integer, Long> guitarLastPlayTime = new java.util.HashMap<>();
    private long guitarNoteCounter = 0;
    private static final long MIN_RETRIGGER_INTERVAL = 30; // Minimum ms between re-triggers of same note
    
    /**
     * Play guitar note with duration based on element height
     * Short elements = quick damping (like palm muting)
     * Tall elements = let it ring naturally
     * Height 100px = 50ms (damped), Height 500px = 2000ms (ring out)
     */
    public synchronized void playGuitarWithDuration(Color color, int octave, float volume, int heightPx) {
        if (channels == null) return;
        
        int noteIndex = getNoteIndexFromColor(color);
        final int midiNote = toMidiNote(noteIndex, octave);
        final int velocity = Math.max(1, Math.min(127, (int)(volume * 100)));
        final MidiChannel ch = channels[GUITAR_CHANNEL];
        
        // Check if we're re-triggering too fast
        long now = System.currentTimeMillis();
        Long lastPlay = guitarLastPlayTime.get(midiNote);
        if (lastPlay != null && (now - lastPlay) < MIN_RETRIGGER_INTERVAL) {
            return; // Skip this trigger, too fast
        }
        guitarLastPlayTime.put(midiNote, now);
        
        // Map height to ring time before damping
        // Height 100 = 50ms (very short, muted), Height 500 = 2000ms (full ring)
        final int ringTime = (int)(50 * Math.pow(40, (heightPx - 100) / 400.0)); // 50ms to 2000ms
        
        // Increment version for this note - previous noteOffs will be ignored
        final long thisVersion = ++guitarNoteCounter;
        guitarNoteVersions.put(midiNote, thisVersion);
        
        // Send noteOff first to reset the note (allows clean re-trigger)
        ch.noteOff(midiNote);
        
        // Small delay to ensure noteOff is processed
        try { Thread.sleep(5); } catch (InterruptedException e) {}
        
        // Start the note
        ch.noteOn(midiNote, velocity);
        
        // Schedule noteOff in background
        new Thread(() -> {
            try {
                Thread.sleep(ringTime);
                // Only send noteOff if this is still the latest trigger for this note
                Long currentVersion = guitarNoteVersions.get(midiNote);
                if (currentVersion != null && currentVersion == thisVersion) {
                    ch.noteOff(midiNote);
                }
            } catch (InterruptedException e) {
                // Interrupted - don't send noteOff
            }
        }).start();
    }
    
    /**
     * Play guitar note based on color, octave, and volume
     */
    public void playGuitar(Color color, int octave, float volume) {
        int noteIndex = getNoteIndexFromColor(color);
        playGuitarByIndex(noteIndex, octave, volume);
    }
    
    /**
     * Play drum sound with volume
     */
    public void playDrum(String type, float volume) {
        if (channels == null || DRUM_CHANNEL >= channels.length) return;
        
        int drumNote;
        switch (type.toLowerCase()) {
            case "high tom":
            case "high_tom":
                drumNote = HIGH_TOM;
                break;
            case "mid tom":
            case "mid_tom":
                drumNote = MID_TOM;
                break;
            case "floor tom":
            case "floor_tom":
                drumNote = FLOOR_TOM;
                break;
            case "bass drum":
            case "bass_drum":
            default:
                drumNote = BASS_DRUM;
                break;
        }
        
        int velocity = (int)(volume * 100);
        playNote(DRUM_CHANNEL, drumNote, velocity, 300);
    }
    
    /**
     * Play snare sound with volume
     */
    public void playSnare(String type, float volume) {
        if (channels == null || DRUM_CHANNEL >= channels.length) return;
        
        int snareNote;
        switch (type.toLowerCase()) {
            case "rim shot":
            case "rim_shot":
                snareNote = SNARE_RIM;
                break;
            case "middle shot":
            case "middle_shot":
            default:
                snareNote = SNARE_CENTER;
                break;
        }
        
        int velocity = (int)(volume * 100);
        playNote(DRUM_CHANNEL, snareNote, velocity, 200);
    }
    
    /**
     * Play an element based on its type and properties
     */
    public void playElement(DrawableElement element) {
        String type = element.getElementType();
        String mapped = element.getMappedValue();
        Color color = element.getColor();
        float volume = element.getOpacity();
        
        switch (type) {
            case "Piano":
                int pianoOctave = Integer.parseInt(mapped.split(" ")[1]);
                playPiano(color, pianoOctave + 1, volume);
                break;
            case "Guitar":
                int guitarOctave = Integer.parseInt(mapped.split(",")[0].split(" ")[1]);
                playGuitar(color, guitarOctave, volume);
                break;
            case "Drum":
                playDrum(mapped, volume);
                break;
            case "Snare Drum":
                playSnare(mapped, volume);
                break;
        }
    }
    
    /**
     * Play a metronome beep sound
     * Generates a short sine wave beep
     */
    public void playMetronomeClick() {
        new Thread(() -> {
            try {
                // Generate a short beep using AudioSystem
                float sampleRate = 44100;
                int durationMs = 50;
                int numSamples = (int)(sampleRate * durationMs / 1000);
                byte[] buffer = new byte[numSamples * 2];
                
                double frequency = 880.0; // A5 - typical metronome beep frequency
                
                for (int i = 0; i < numSamples; i++) {
                    double angle = 2.0 * Math.PI * i * frequency / sampleRate;
                    // Apply envelope for click sound
                    double envelope = 1.0 - ((double)i / numSamples);
                    short sample = (short)(Math.sin(angle) * 32767 * 0.5 * envelope);
                    buffer[i * 2] = (byte)(sample & 0xFF);
                    buffer[i * 2 + 1] = (byte)((sample >> 8) & 0xFF);
                }
                
                javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
                    sampleRate, 16, 1, true, false);
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
                    javax.sound.sampled.SourceDataLine.class, format);
                javax.sound.sampled.SourceDataLine line = 
                    (javax.sound.sampled.SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
                line.open(format);
                line.start();
                line.write(buffer, 0, buffer.length);
                line.drain();
                line.close();
            } catch (Exception e) {
                System.err.println("Metronome beep error: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Clean up resources
     */
    public void close() {
        if (synthesizer != null && synthesizer.isOpen()) {
            synthesizer.close();
        }
    }
}
