import javax.sound.sampled.*;
import java.awt.Color;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages sound playback for SketchJam instruments.
 * Notes are determined by color, octaves by element size.
 */
public class SoundManager {
    
    private static SoundManager instance;
    private Map<String, byte[]> generatedTones = new HashMap<>();
    
    // Note names matching ColorPalette order
    public static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    
    // Base frequencies for all 12 notes in octave 4
    private static final double[] NOTE_FREQUENCIES_OCTAVE_4 = {
        261.63,  // C4
        277.18,  // C#4
        293.66,  // D4
        311.13,  // D#4
        329.63,  // E4
        349.23,  // F4
        369.99,  // F#4
        392.00,  // G4
        415.30,  // G#4
        440.00,  // A4
        466.16,  // A#4
        493.88,  // B4
    };
    
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
    
    // Drum frequencies (lower = bigger drum)
    private static final double[] DRUM_FREQUENCIES = {
        200.0,  // High Tom
        150.0,  // Mid Tom
        100.0,  // Floor Tom
        60.0,   // Bass Drum
    };
    
    // Snare frequencies
    private static final double[] SNARE_FREQUENCIES = {
        300.0,  // Rim Shot
        200.0,  // Middle Shot
    };
    
    private SoundManager() {
        generateDefaultTones();
    }
    
    public static SoundManager getInstance() {
        if (instance == null) {
            instance = new SoundManager();
        }
        return instance;
    }
    
    private void generateDefaultTones() {
        // Generate all 12 notes × 5 octaves for piano (shifted 1 octave up)
        for (int octave = 1; octave <= 5; octave++) {
            for (int note = 0; note < 12; note++) {
                String key = "piano_" + NOTE_NAMES[note] + "_" + octave;
                double freq = getNoteFrequency(note, octave + 1); // +1 octave shift up
                generatedTones.put(key, generateTone(freq, 0.5, false));
            }
        }
        
        // Generate all 12 notes × 5 octaves for guitar (shifted 1 octave down)
        for (int octave = 1; octave <= 5; octave++) {
            for (int note = 0; note < 12; note++) {
                String key = "guitar_" + NOTE_NAMES[note] + "_" + octave;
                double freq = getNoteFrequency(note, octave - 1); // -1 octave shift down
                generatedTones.put(key, generateGuitarTone(freq, 0.4));
            }
        }
        
        // Generate drum tones
        String[] drumTypes = {"high_tom", "mid_tom", "floor_tom", "bass_drum"};
        for (int i = 0; i < drumTypes.length; i++) {
            generatedTones.put("drum_" + drumTypes[i], generateDrumTone(DRUM_FREQUENCIES[i], 0.3));
        }
        
        // Generate snare tones
        String[] snareTypes = {"rim_shot", "middle_shot"};
        for (int i = 0; i < snareTypes.length; i++) {
            generatedTones.put("snare_" + snareTypes[i], generateSnareTone(SNARE_FREQUENCIES[i], 0.2));
        }
    }
    
    /**
     * Get frequency for a note index (0-11) at a given octave
     */
    private double getNoteFrequency(int noteIndex, int octave) {
        double baseFreq = NOTE_FREQUENCIES_OCTAVE_4[noteIndex];
        int octaveDiff = octave - 4;
        return baseFreq * Math.pow(2, octaveDiff);
    }
    
    /**
     * Detect which note (0-11) a color represents by finding closest match
     */
    public static int getNoteIndexFromColor(Color color) {
        int bestMatch = 0;
        int minDistance = Integer.MAX_VALUE;
        
        for (int i = 0; i < NOTE_COLORS.length; i++) {
            Color noteColor = NOTE_COLORS[i];
            // Calculate color distance (simple RGB distance)
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
     * Generate a sine wave tone with harmonics
     */
    private byte[] generateTone(double frequency, double duration, boolean harmonics) {
        int sampleRate = 44100;
        int numSamples = (int)(duration * sampleRate);
        byte[] buffer = new byte[numSamples * 2];
        
        for (int i = 0; i < numSamples; i++) {
            double time = i / (double) sampleRate;
            double envelope = Math.exp(-3.0 * time / duration);
            
            double sample = Math.sin(2 * Math.PI * frequency * time);
            
            if (harmonics) {
                sample += 0.5 * Math.sin(4 * Math.PI * frequency * time);
                sample += 0.25 * Math.sin(6 * Math.PI * frequency * time);
                sample /= 1.75;
            }
            
            sample *= envelope;
            
            short value = (short)(sample * 32767 * 0.8);
            buffer[i * 2] = (byte)(value & 0xFF);
            buffer[i * 2 + 1] = (byte)((value >> 8) & 0xFF);
        }
        
        return buffer;
    }
    
    /**
     * Generate a guitar-like plucked string tone
     */
    private byte[] generateGuitarTone(double frequency, double duration) {
        int sampleRate = 44100;
        int numSamples = (int)(duration * sampleRate);
        byte[] buffer = new byte[numSamples * 2];
        
        for (int i = 0; i < numSamples; i++) {
            double time = i / (double) sampleRate;
            double envelope = Math.exp(-4.0 * time / duration);
            
            // Guitar has strong harmonics
            double sample = Math.sin(2 * Math.PI * frequency * time);
            sample += 0.6 * Math.sin(4 * Math.PI * frequency * time);
            sample += 0.3 * Math.sin(6 * Math.PI * frequency * time);
            sample += 0.15 * Math.sin(8 * Math.PI * frequency * time);
            sample /= 2.05;
            
            sample *= envelope;
            
            short value = (short)(sample * 32767 * 0.8);
            buffer[i * 2] = (byte)(value & 0xFF);
            buffer[i * 2 + 1] = (byte)((value >> 8) & 0xFF);
        }
        
        return buffer;
    }
    
    /**
     * Generate a drum-like tone
     */
    private byte[] generateDrumTone(double frequency, double duration) {
        int sampleRate = 44100;
        int numSamples = (int)(duration * sampleRate);
        byte[] buffer = new byte[numSamples * 2];
        
        for (int i = 0; i < numSamples; i++) {
            double time = i / (double) sampleRate;
            double envelope = Math.exp(-8.0 * time / duration);
            
            double currentFreq = frequency * (1.0 + 2.0 * Math.exp(-20 * time));
            double sample = Math.sin(2 * Math.PI * currentFreq * time);
            sample += 0.1 * (Math.random() * 2 - 1) * envelope;
            sample *= envelope;
            
            short value = (short)(sample * 32767 * 0.9);
            buffer[i * 2] = (byte)(value & 0xFF);
            buffer[i * 2 + 1] = (byte)((value >> 8) & 0xFF);
        }
        
        return buffer;
    }
    
    /**
     * Generate a snare-like tone with noise
     */
    private byte[] generateSnareTone(double frequency, double duration) {
        int sampleRate = 44100;
        int numSamples = (int)(duration * sampleRate);
        byte[] buffer = new byte[numSamples * 2];
        
        for (int i = 0; i < numSamples; i++) {
            double time = i / (double) sampleRate;
            double envelope = Math.exp(-10.0 * time / duration);
            
            double sample = 0.5 * Math.sin(2 * Math.PI * frequency * time);
            sample += 0.5 * (Math.random() * 2 - 1);
            sample *= envelope;
            
            short value = (short)(sample * 32767 * 0.8);
            buffer[i * 2] = (byte)(value & 0xFF);
            buffer[i * 2 + 1] = (byte)((value >> 8) & 0xFF);
        }
        
        return buffer;
    }
    
    /**
     * Play a sound by key
     */
    public void play(String key) {
        play(key, 1.0f);
    }
    
    /**
     * Play a sound by key with volume
     */
    public void play(String key, float volume) {
        if (generatedTones.containsKey(key)) {
            playGeneratedTone(generatedTones.get(key), volume);
        }
    }
    
    private void playGeneratedTone(byte[] data) {
        playGeneratedTone(data, 1.0f);
    }
    
    private void playGeneratedTone(byte[] data, float volume) {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            Clip clip = (Clip) AudioSystem.getLine(info);
            clip.open(format, data, 0, data.length);
            
            // Apply volume control
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                // Convert volume (0-1) to decibels
                float dB = (float) (Math.log(Math.max(0.01, volume)) / Math.log(10.0) * 20.0);
                dB = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), dB));
                gainControl.setValue(dB);
            }
            
            clip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Play piano note based on color, octave, and volume
     */
    public void playPiano(Color color, int octave, float volume) {
        String noteName = getNoteNameFromColor(color);
        String key = "piano_" + noteName + "_" + octave;
        play(key, volume);
    }
    
    /**
     * Play piano note by note index (0-11 for C through B), octave, and volume
     */
    public void playPianoByIndex(int noteIndex, int octave, float volume) {
        if (noteIndex >= 0 && noteIndex < NOTE_NAMES.length) {
            String key = "piano_" + NOTE_NAMES[noteIndex] + "_" + octave;
            play(key, volume);
        }
    }
    
    /**
     * Play guitar note based on color, octave, and volume
     */
    public void playGuitar(Color color, int octave, float volume) {
        String noteName = getNoteNameFromColor(color);
        String key = "guitar_" + noteName + "_" + octave;
        play(key, volume);
    }
    
    /**
     * Play drum sound with volume
     */
    public void playDrum(String type, float volume) {
        String key = "drum_" + type.toLowerCase().replace(" ", "_");
        play(key, volume);
    }
    
    /**
     * Play snare sound with volume
     */
    public void playSnare(String type, float volume) {
        String key = "snare_" + type.toLowerCase().replace(" ", "_");
        play(key, volume);
    }
    
    /**
     * Play sound for a drawable element based on its color, properties, and opacity (volume)
     */
    public void playElement(DrawableElement element) {
        String type = element.getElementType();
        String mapped = element.getMappedValue();
        Color color = element.getColor();
        float volume = element.getOpacity(); // Opacity maps to volume
        
        switch (type) {
            case "Piano":
                // Extract octave number from "Octave X"
                int pianoOctave = Integer.parseInt(mapped.split(" ")[1]);
                playPiano(color, pianoOctave, volume);
                break;
            case "Drum":
                playDrum(mapped, volume);
                break;
            case "Snare Drum":
                playSnare(mapped, volume);
                break;
            case "Guitar":
                // Extract octave from "Octave X, Duration Y"
                int guitarOctave = Integer.parseInt(mapped.split(",")[0].split(" ")[1]);
                playGuitar(color, guitarOctave, volume);
                break;
        }
    }
}
