import javax.sound.midi.*;
import javax.sound.sampled.*;
import java.awt.Color;
import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages sound playback for SketchJam instruments using SF2 SoundFonts.
 * Loads multiple soundfonts for different instruments.
 * Includes real-time EQ DSP using audio streaming.
 */
public class SoundManager {
    
    private static SoundManager instance;
    
    // MIDI synthesizer for SF2 playback (clean sounds)
    private Synthesizer synthesizer;
    private MidiChannel[] channels;
    private boolean soundfontsLoaded = false;
    
    // Separate synthesizer for distortion sounds
    private Synthesizer distortionSynth;
    private MidiChannel[] distortionChannels;
    
    // Audio streaming for EQ DSP
    private AudioInputStream audioStream;
    private SourceDataLine audioLine;
    private Thread audioThread;
    private volatile boolean audioRunning = false;
    
    // EQ parameters (set from EQ panel)
    private volatile float bassGain = 1.0f;   // 0.5 to 1.5
    private volatile float trebleGain = 1.0f; // 0.5 to 1.5
    // Bass filter state
    private float bassYL = 0, bassYR = 0;
    
    // MIDI channel assignments
    private static final int PIANO_CHANNEL = 0;
    private static final int GUITAR_CHANNEL = 1;
    private static final int DRUM_CHANNEL = 9;  // Standard MIDI drum channel
    
    // Distortion soundfont programs (bank + program)
    private int distortionPianoBank = -1;
    private int distortionPianoProgram = -1;
    private int distortionGuitarBank = -1;
    private int distortionGuitarProgram = -1;
    private boolean distortionLoaded = false;
    
    // Currently selected SF2 filenames (for latency compensation check)
    private String selectedPianoSF2 = null;
    private String selectedGuitarSF2 = null;
    
    // Note names matching ColorPalette order
    public static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    
    
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
    
    /**
     * Check if currently selected Piano or Guitar SF2 needs latency compensation.
     * Returns true ONLY for specific SF2 files: 8bitsf.SF2, Distortion_Guitar.sf2
     */
    public boolean needsLatencyCompensation() {
        return needsCompensation(selectedPianoSF2) || needsCompensation(selectedGuitarSF2);
    }
    
    private boolean needsCompensation(String sf2Name) {
        if (sf2Name == null) return false;
        String lower = sf2Name.toLowerCase();
        // Only these specific files need compensation
        return lower.contains("8bit") || lower.contains("distortion");
    }
    
    public String getSelectedPianoSF2() { return selectedPianoSF2; }
    public String getSelectedGuitarSF2() { return selectedGuitarSF2; }
    
    /**
     * Check if distortion SF2 files (8bitsf.SF2, Distortion_Guitar.sf2) are loaded.
     * These are from the distortion/ folder and have internal latency.
     */
    public boolean isDistortionLoaded() {
        return distortionLoaded;
    }
    
    private void initializeSynthesizer() {
        try {
            synthesizer = MidiSystem.getSynthesizer();
            
            // Try to set up audio streaming with EQ
            if (!trySetupAudioStreaming()) {
                // Fallback to regular output
                synthesizer.open();
                System.out.println("MIDI Synthesizer initialized (no EQ DSP)");
            }
            
            // Load all available soundfonts
            loadAllSoundFonts();
            
            channels = synthesizer.getChannels();
            
            // Set up instrument programs based on loaded soundfonts
            setupInstruments();
            
        } catch (MidiUnavailableException e) {
            System.err.println("MIDI Synthesizer not available: " + e.getMessage());
        }
    }
    
    /**
     * Try to set up audio streaming using reflection to access SoftSynthesizer.openStream()
     */
    private boolean trySetupAudioStreaming() {
        try {
            // Use reflection to call openStream on SoftSynthesizer
            Method openStreamMethod = synthesizer.getClass().getMethod("openStream", 
                AudioFormat.class, java.util.Map.class);
            
            AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
            audioStream = (AudioInputStream) openStreamMethod.invoke(synthesizer, format, null);
            
            // Open audio output
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(format, 8192);
            audioLine.start();
            
            // Start audio processing thread
            audioRunning = true;
            audioThread = new Thread(this::audioProcessingLoop, "EQAudio");
            audioThread.setDaemon(true);
            audioThread.start();
            
            System.out.println("Audio streaming with EQ DSP enabled");
            return true;
            
        } catch (Exception e) {
            System.out.println("Could not enable EQ DSP: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Audio processing loop - applies EQ
     */
    private void audioProcessingLoop() {
        byte[] buffer = new byte[2048];
        
        while (audioRunning) {
            try {
                int bytesRead = audioStream.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    // Apply EQ DSP
                    applyEQ(buffer, bytesRead);
                    audioLine.write(buffer, 0, bytesRead);
                }
            } catch (Exception e) {
                if (audioRunning) {
                    System.err.println("Audio error: " + e.getMessage());
                }
            }
        }
    }
    
    // Mid-frequency filter state
    private float midLpL1 = 0, midLpR1 = 0;  // First lowpass (cuts highs)
    private float midHpL1 = 0, midHpR1 = 0;  // Then highpass (cuts lows) = bandpass for mids
    
    /**
     * Apply EQ to audio buffer
     */
    private void applyEQ(byte[] buffer, int length) {
        // Filter coefficients for extracting frequency bands
        float bassAlpha = 0.97f;   // ~200Hz lowpass for bass
        float midLpAlpha = 0.85f;  // ~2kHz lowpass
        float midHpAlpha = 0.95f;  // ~400Hz highpass (combined = mid bandpass)
        
        for (int i = 0; i < length; i += 4) {
            // Read stereo samples (16-bit little-endian)
            int loL = buffer[i] & 0xFF;
            int hiL = buffer[i + 1];
            short sL = (short) ((hiL << 8) | loL);
            
            int loR = buffer[i + 2] & 0xFF;
            int hiR = buffer[i + 3];
            short sR = (short) ((hiR << 8) | loR);
            
            // Convert to float
            float xL = sL / 32768.0f;
            float xR = sR / 32768.0f;
            
            // === EQ PROCESSING ===
            // Extract bass (lowpass ~200Hz)
            bassYL = bassAlpha * bassYL + (1 - bassAlpha) * xL;
            bassYR = bassAlpha * bassYR + (1 - bassAlpha) * xR;
            
            // Extract mids (bandpass ~400Hz-2kHz)
            // First lowpass to cut highs
            midLpL1 = midLpAlpha * midLpL1 + (1 - midLpAlpha) * xL;
            midLpR1 = midLpAlpha * midLpR1 + (1 - midLpAlpha) * xR;
            // Then highpass to cut lows (highpass = input - lowpass)
            float midHpInL = midLpL1;
            float midHpInR = midLpR1;
            midHpL1 = midHpAlpha * midHpL1 + (1 - midHpAlpha) * midHpInL;
            midHpR1 = midHpAlpha * midHpR1 + (1 - midHpAlpha) * midHpInR;
            float midL = midHpInL - midHpL1;  // Mid frequencies
            float midR = midHpInR - midHpR1;
            
            // Apply EQ:
            // bassGain: 0.5-1.5 (dark=1.5 bass boost, light=0.5 bass cut)
            // trebleGain: 0.5-1.5 (light=1.5 mid boost, dark=0.5 mid cut)
            float bassBoostL = bassYL * (bassGain - 1.0f) * 2.5f;
            float bassBoostR = bassYR * (bassGain - 1.0f) * 2.5f;
            float midBoostL = midL * (trebleGain - 1.0f) * 4.0f;
            float midBoostR = midR * (trebleGain - 1.0f) * 4.0f;
            
            xL = xL + bassBoostL + midBoostL;
            xR = xR + bassBoostR + midBoostR;
            
            // Clamp and convert back
            xL = Math.max(-0.999f, Math.min(0.999f, xL));
            xR = Math.max(-0.999f, Math.min(0.999f, xR));
            
            short outL = (short) Math.round(xL * 32767.0f);
            short outR = (short) Math.round(xR * 32767.0f);
            
            buffer[i] = (byte) (outL & 0xFF);
            buffer[i + 1] = (byte) ((outL >> 8) & 0xFF);
            buffer[i + 2] = (byte) (outR & 0xFF);
            buffer[i + 3] = (byte) ((outR >> 8) & 0xFF);
        }
    }
    
    /**
     * Set EQ parameters (bass and treble gain)
     * @param bass Bass gain (0.5 = -6dB, 1.0 = 0dB, 1.5 = +6dB)
     * @param treble Treble gain (0.5 = -6dB, 1.0 = 0dB, 1.5 = +6dB)
     */
    public void setEQ(float bass, float treble) {
        this.bassGain = Math.max(0.5f, Math.min(1.5f, bass));
        this.trebleGain = Math.max(0.5f, Math.min(1.5f, treble));
    }
    
    public float getBassGain() { return bassGain; }
    public float getTrebleGain() { return trebleGain; }
    
    private void loadAllSoundFonts() {
        File soundfontsDir = ResourceLoader.getSoundfontsDir();
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
    
    /**
     * Load distortion soundfonts from distortion/ folder into separate synthesizer
     * First file with "guitar" = guitar distortion
     * Other files = piano/pad distortion
     */
    private void loadDistortionSoundfonts() {
        File distortionDir = ResourceLoader.getDistortionDir();
        if (!distortionDir.exists() || !distortionDir.isDirectory()) {
            System.out.println("No distortion folder found - distortion mixing disabled");
            return;
        }
        
        File[] sf2Files = distortionDir.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".sf2") || name.toLowerCase().endsWith(".sf3"));
        
        if (sf2Files == null || sf2Files.length == 0) {
            System.out.println("No distortion SF2 files found");
            return;
        }
        
        // Create separate synthesizer for distortion
        try {
            distortionSynth = MidiSystem.getSynthesizer();
            distortionSynth.open();
            distortionChannels = distortionSynth.getChannels();
        } catch (Exception e) {
            System.err.println("Failed to create distortion synthesizer: " + e.getMessage());
            return;
        }
        
        for (File sf2File : sf2Files) {
            try {
                Soundbank soundbank = MidiSystem.getSoundbank(sf2File);
                if (distortionSynth.isSoundbankSupported(soundbank)) {
                    distortionSynth.loadAllInstruments(soundbank);
                    String fileName = sf2File.getName().toLowerCase();
                    
                    // Find first instrument in this soundfont
                    Instrument[] instruments = soundbank.getInstruments();
                    if (instruments.length > 0) {
                        Patch patch = instruments[0].getPatch();
                        int bank = patch.getBank();
                        int program = patch.getProgram();
                        
                        System.out.println("Loaded distortion: " + sf2File.getName() + 
                            " -> " + instruments[0].getName() + " (bank " + bank + ", program " + program + ")");
                        
                        // Assign based on filename - "guitar" goes to guitar, everything else to piano
                        if (fileName.contains("guitar") && distortionGuitarProgram < 0) {
                            distortionGuitarBank = bank;
                            distortionGuitarProgram = program;
                            System.out.println("  -> Assigned to GUITAR distortion");
                        } else if (distortionPianoProgram < 0) {
                            distortionPianoBank = bank;
                            distortionPianoProgram = program;
                            System.out.println("  -> Assigned to PIANO distortion");
                        }
                    }
                    distortionLoaded = true;
                }
            } catch (Exception e) {
                System.err.println("Failed to load distortion " + sf2File.getName() + ": " + e.getMessage());
            }
        }
        
        // Set up distortion channels on the SEPARATE synthesizer
        if (distortionChannels != null && distortionLoaded) {
            if (distortionPianoProgram >= 0) {
                distortionChannels[PIANO_CHANNEL].controlChange(0, distortionPianoBank >> 7);
                distortionChannels[PIANO_CHANNEL].controlChange(32, distortionPianoBank & 0x7F);
                distortionChannels[PIANO_CHANNEL].programChange(distortionPianoProgram);
                System.out.println("Distortion piano (separate synth) channel " + PIANO_CHANNEL + 
                    " set to bank " + distortionPianoBank + ", program " + distortionPianoProgram);
            }
            if (distortionGuitarProgram >= 0) {
                distortionChannels[GUITAR_CHANNEL].controlChange(0, distortionGuitarBank >> 7);
                distortionChannels[GUITAR_CHANNEL].controlChange(32, distortionGuitarBank & 0x7F);
                distortionChannels[GUITAR_CHANNEL].programChange(distortionGuitarProgram);
                System.out.println("Distortion guitar (separate synth) channel " + GUITAR_CHANNEL + 
                    " set to bank " + distortionGuitarBank + ", program " + distortionGuitarProgram);
            }
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
        
        // Set up clean channels with found programs
        channels[PIANO_CHANNEL].programChange(pianoProgram);
        channels[GUITAR_CHANNEL].programChange(guitarProgram);
        
        System.out.println("Piano channel " + PIANO_CHANNEL + " set to program " + pianoProgram);
        System.out.println("Guitar channel " + GUITAR_CHANNEL + " set to program " + guitarProgram);
        
        // Load distortion soundfonts
        loadDistortionSoundfonts();
    }
    
    /**
     * Get saturation (0.0 to 1.0) from a color
     */
    public static float getSaturationFromColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return hsb[1]; // hsb[0]=hue, hsb[1]=saturation, hsb[2]=brightness
    }
    
    /**
     * Calculate clean/distortion mix based on saturation
     * Returns [cleanVelocityRatio, distortionVelocityRatio]
     * 100% sat: 0/100, 80% sat: 40/80, 60% sat: 80/40, 40% sat: 100/0
     * Middle values use total 120% to maintain volume when mixing
     */
    private float[] getMixRatios(float saturation) {
        // Exact ratios as specified:
        // sat 1.0 (100%) -> clean 0%, dist 100% (total 100)
        // sat 0.8 (80%)  -> clean 40%, dist 80% (total 120 - boosted)
        // sat 0.6 (60%)  -> clean 80%, dist 40% (total 120 - boosted)
        // sat 0.4 (40%)  -> clean 100%, dist 0% (total 100)
        
        if (saturation >= 0.95f) {
            return new float[] {0.0f, 1.0f};        // 100%: 0/100
        } else if (saturation >= 0.75f) {
            return new float[] {0.4f, 0.8f};        // 80%: 40/80 (total 120)
        } else if (saturation >= 0.55f) {
            return new float[] {0.8f, 0.4f};        // 60%: 80/40 (total 120)
        } else {
            return new float[] {1.0f, 0.0f};        // 40%: 100/0
        }
    }
    
    /**
     * Set piano soundfont by filename
     */
    public void setPianoSoundfont(String filename) {
        if (channels == null) return;
        
        selectedPianoSF2 = filename; // Track selected SF2 for compensation check
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
        
        selectedGuitarSF2 = filename; // Track selected SF2 for compensation check
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
                File sf2File = new File(ResourceLoader.getSoundfontsDir(), filename);
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
            File sf2File = new File(ResourceLoader.getSoundfontsDir(), filename);
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
     * Detect which note (0-11) a color represents by matching HUE
     * This works correctly for desaturated colors (pink maps to red/C, etc.)
     */
    public static int getNoteIndexFromColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float hue = hsb[0]; // 0.0 to 1.0
        
        // Map hue to note index (0-11)
        // Hue 0.0 = Red = C, Hue 0.0833 = Orange = C#, etc.
        // Each note spans 1/12 of the hue wheel (0.0833)
        int noteIndex = (int)Math.round(hue * 12) % 12;
        return noteIndex;
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
     * Uses saturation-based mixing between clean and distortion
     */
    public void playPiano(Color color, int octave, float volume) {
        if (channels == null) return;
        
        int noteIndex = getNoteIndexFromColor(color);
        int midiNote = toMidiNote(noteIndex, octave);
        int baseVelocity = Math.max(1, Math.min(127, (int)(volume * 100)));
        
        // Get saturation and calculate mix
        float saturation = getSaturationFromColor(color);
        float[] mix = getMixRatios(saturation);
        int cleanVelocity = (int)(baseVelocity * mix[0]);
        int distVelocity = (int)(baseVelocity * mix[1]);
        
        // Play clean sound on main synthesizer
        if (cleanVelocity > 0) {
            playNote(PIANO_CHANNEL, midiNote, cleanVelocity, 500);
        }
        
        // Play distortion sound on separate synthesizer
        if (distVelocity > 0 && distortionLoaded && distortionChannels != null && distortionPianoProgram >= 0) {
            final int note = midiNote;
            final int vel = distVelocity;
            new Thread(() -> {
                try {
                    distortionChannels[PIANO_CHANNEL].noteOn(note, vel);
                    Thread.sleep(500);
                    distortionChannels[PIANO_CHANNEL].noteOff(note);
                } catch (InterruptedException e) {
                    distortionChannels[PIANO_CHANNEL].noteOff(note);
                }
            }).start();
        }
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
        stopPianoNoteInternal();
        
        int noteIndex = getNoteIndexFromColor(color);
        currentPianoNote = toMidiNote(noteIndex, octave);
        int baseVelocity = Math.max(1, Math.min(127, (int)(volume * 100)));
        
        // Get saturation and calculate mix
        float saturation = getSaturationFromColor(color);
        float[] mix = getMixRatios(saturation);
        int cleanVelocity = (int)(baseVelocity * mix[0]);
        int distVelocity = (int)(baseVelocity * mix[1]);
        
        // Play clean sound on main synthesizer
        if (cleanVelocity > 0) {
            channels[PIANO_CHANNEL].noteOn(currentPianoNote, cleanVelocity);
        }
        
        // Play distortion sound on separate synthesizer
        if (distVelocity > 0 && distortionLoaded && distortionChannels != null && distortionPianoProgram >= 0) {
            distortionChannels[PIANO_CHANNEL].noteOn(currentPianoNote, distVelocity);
        }
        
    }
    
    private void stopPianoNoteInternal() {
        if (currentPianoNote >= 0) {
            if (channels != null) {
                channels[PIANO_CHANNEL].noteOff(currentPianoNote);
            }
            if (distortionLoaded && distortionChannels != null && distortionPianoProgram >= 0) {
                distortionChannels[PIANO_CHANNEL].noteOff(currentPianoNote);
            }
        }
    }
    
    /**
     * Stop the currently playing piano note
     */
    public void stopPianoNote() {
        if (channels == null || currentPianoNote < 0) return;
        
        stopPianoNoteInternal();
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
        final int baseVelocity = Math.max(1, Math.min(127, (int)(volume * 100)));
        final MidiChannel cleanCh = channels[GUITAR_CHANNEL];
        final MidiChannel distCh = (distortionLoaded && distortionChannels != null && distortionGuitarProgram >= 0) ? 
            distortionChannels[GUITAR_CHANNEL] : null;
        
        // Get saturation and calculate mix
        final float saturation = getSaturationFromColor(color);
        final float[] mix = getMixRatios(saturation);
        final int cleanVelocity = (int)(baseVelocity * mix[0]);
        final int distVelocity = (int)(baseVelocity * mix[1]);
        
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
        cleanCh.noteOff(midiNote);
        if (distCh != null) distCh.noteOff(midiNote);
        
        // Small delay to ensure noteOff is processed
        try { Thread.sleep(5); } catch (InterruptedException e) {}
        
        // Play clean on main synthesizer, distortion on separate synthesizer
        if (cleanVelocity > 0) {
            cleanCh.noteOn(midiNote, cleanVelocity);
        }
        if (distVelocity > 0 && distCh != null) {
            distCh.noteOn(midiNote, distVelocity);
        }

        // Schedule noteOff for both channels
        new Thread(() -> {
            try {
                Thread.sleep(ringTime);
                Long currentVersion = guitarNoteVersions.get(midiNote);
                if (currentVersion != null && currentVersion == thisVersion) {
                    cleanCh.noteOff(midiNote);
                    if (distCh != null) distCh.noteOff(midiNote);
                }
            } catch (InterruptedException e) {}
        }).start();
    }
    
    /**
     * Play guitar note based on color, octave, and volume
     * Uses overdrive based on color saturation
     */
    public void playGuitar(Color color, int octave, float volume) {
        // Use playGuitarWithDuration with default height for overdrive
        playGuitarWithDuration(color, octave, volume, 200);
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
    // public void playMetronomeClick() {
    //     new Thread(() -> {
    //         try {
    //             // Generate a short beep using AudioSystem
    //             float sampleRate = 44100;
    //             int durationMs = 50;
    //             int numSamples = (int)(sampleRate * durationMs / 1000);
    //             byte[] buffer = new byte[numSamples * 2];
                
    //             double frequency = 880.0; // A5 - typical metronome beep frequency
                
    //             for (int i = 0; i < numSamples; i++) {
    //                 double angle = 2.0 * Math.PI * i * frequency / sampleRate;
    //                 // Apply envelope for click sound
    //                 double envelope = 1.0 - ((double)i / numSamples);
    //                 short sample = (short)(Math.sin(angle) * 32767 * 0.5 * envelope);
    //                 buffer[i * 2] = (byte)(sample & 0xFF);
    //                 buffer[i * 2 + 1] = (byte)((sample >> 8) & 0xFF);
    //             }
                
    //             javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(
    //                 sampleRate, 16, 1, true, false);
    //             javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(
    //                 javax.sound.sampled.SourceDataLine.class, format);
    //             javax.sound.sampled.SourceDataLine line = 
    //                 (javax.sound.sampled.SourceDataLine) javax.sound.sampled.AudioSystem.getLine(info);
    //             line.open(format);
    //             line.start();
    //             line.write(buffer, 0, buffer.length);
    //             line.drain();
    //             line.close();
    //         } catch (Exception e) {
    //             System.err.println("Metronome beep error: " + e.getMessage());
    //         }
    //     }).start();
    // }

    public void playMetronomeClick(boolean isDownbeat) {
        new Thread(() -> {
            try {
                float sampleRate = 44100;
                int durationMs = 50;
                int numSamples = (int)(sampleRate * durationMs / 1000);
                byte[] buffer = new byte[numSamples * 2];

                double frequency = isDownbeat ? 440.0 : 880.0; // A4 vs A5
                double gain = 0.5; 

                for (int i = 0; i < numSamples; i++) {
                    double angle = 2.0 * Math.PI * i * frequency / sampleRate;
                    double envelope = 1.0 - ((double)i / numSamples);
                    short sample = (short)(Math.sin(angle) * 32767 * gain * envelope);
                    buffer[i * 2] = (byte)(sample & 0xFF);
                    buffer[i * 2 + 1] = (byte)((sample >> 8) & 0xFF);
                }

                AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
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

    public void playMetronomeClick() {
        playMetronomeClick(false);
    }

    private static float unpackSaturation(int packed) {
        int satMilli = (packed >>> 16) & 0xFFFF;
        return satMilli / 1000f;
    }

    private static int unpackHeight(int packed) {
        return packed & 0xFFFF;
    }


    private void playGuitarFromEvent(int midiNote, float volume, float saturation, int heightPx) {
        if (channels == null) return;

        final int baseVelocity = Math.max(1, Math.min(127, (int)(volume * 100)));
        final MidiChannel cleanCh = channels[GUITAR_CHANNEL];
        final MidiChannel distCh =
            (distortionLoaded && distortionChannels != null && distortionGuitarProgram >= 0)
                ? distortionChannels[GUITAR_CHANNEL]
                : null;

        final float[] mix = getMixRatios(saturation);
        final int cleanVelocity = (int)(baseVelocity * mix[0]);
        final int distVelocity  = (int)(baseVelocity * mix[1]);

        long now = System.currentTimeMillis();
        Long lastPlay = guitarLastPlayTime.get(midiNote);
        if (lastPlay != null && (now - lastPlay) < MIN_RETRIGGER_INTERVAL) return;
        guitarLastPlayTime.put(midiNote, now);

        final int ringTime = (int)(50 * Math.pow(40, (heightPx - 100) / 400.0));
        final long thisVersion = ++guitarNoteCounter;
        guitarNoteVersions.put(midiNote, thisVersion);

        cleanCh.noteOff(midiNote);
        if (distCh != null) distCh.noteOff(midiNote);
        try { Thread.sleep(5); } catch (InterruptedException ignored) {}

        if (cleanVelocity > 0) cleanCh.noteOn(midiNote, cleanVelocity);
        if (distVelocity > 0 && distCh != null) distCh.noteOn(midiNote, distVelocity);

        new Thread(() -> {
            try {
                Thread.sleep(ringTime);
                Long currentVersion = guitarNoteVersions.get(midiNote);
                if (currentVersion != null && currentVersion == thisVersion) {
                    cleanCh.noteOff(midiNote);
                    if (distCh != null) distCh.noteOff(midiNote);
                }
            } catch (InterruptedException ignored) {}
        }).start();
    }


    public synchronized void playGuitarMidiWithDuration(int midiNote, Color color, float volume, int heightPx) {
        if (channels == null) return;

        final int baseVelocity = Math.max(1, Math.min(127, (int)(volume * 100)));
        final MidiChannel cleanCh = channels[GUITAR_CHANNEL];
        final MidiChannel distCh = (distortionLoaded && distortionChannels != null && distortionGuitarProgram >= 0)
                ? distortionChannels[GUITAR_CHANNEL] : null;

        // ✅ color 기반 saturation 그대로 사용
        final float saturation = getSaturationFromColor(color);
        final float[] mix = getMixRatios(saturation);
        final int cleanVelocity = (int)(baseVelocity * mix[0]);
        final int distVelocity  = (int)(baseVelocity * mix[1]);

        // 리트리거 제한 그대로
        long now = System.currentTimeMillis();
        Long lastPlay = guitarLastPlayTime.get(midiNote);
        if (lastPlay != null && (now - lastPlay) < MIN_RETRIGGER_INTERVAL) return;
        guitarLastPlayTime.put(midiNote, now);

        // height 기반 링타임 그대로
        final int ringTime = (int)(50 * Math.pow(40, (heightPx - 100) / 400.0));

        final long thisVersion = ++guitarNoteCounter;
        guitarNoteVersions.put(midiNote, thisVersion);

        cleanCh.noteOff(midiNote);
        if (distCh != null) distCh.noteOff(midiNote);
        try { Thread.sleep(5); } catch (InterruptedException e) {}

        if (cleanVelocity > 0) cleanCh.noteOn(midiNote, cleanVelocity);
        if (distVelocity > 0 && distCh != null) distCh.noteOn(midiNote, distVelocity);

        new Thread(() -> {
            try {
                Thread.sleep(ringTime);
                Long currentVersion = guitarNoteVersions.get(midiNote);
                if (currentVersion != null && currentVersion == thisVersion) {
                    cleanCh.noteOff(midiNote);
                    if (distCh != null) distCh.noteOff(midiNote);
                }
            } catch (InterruptedException e) {}
        }).start();
    }


    private void playLayeredNote(int channelIndex, int midiNote, int baseVel, int durationMs, Color color) {
        if (channels == null || channelIndex < 0 || channelIndex >= channels.length) return;

        // saturation -> (clean/dist) mix
        float saturation = getSaturationFromColor(color);
        float[] mix = getMixRatios(saturation);

        int cleanVel = Math.max(0, Math.min(127, (int)(baseVel * mix[0])));
        int distVel  = Math.max(0, Math.min(127, (int)(baseVel * mix[1])));

        MidiChannel cleanCh = channels[channelIndex];
        MidiChannel distCh  = (distortionLoaded && distortionChannels != null
                && channelIndex < distortionChannels.length
                && distortionChannels[channelIndex] != null)
                ? distortionChannels[channelIndex]
                : null;

        if (cleanVel > 0) cleanCh.noteOn(midiNote, cleanVel);
        if (distVel > 0 && distCh != null) distCh.noteOn(midiNote, distVel);

        final int dur = (durationMs > 0) ? durationMs : 500;
        new Thread(() -> {
            try {
                Thread.sleep(dur);
                cleanCh.noteOff(midiNote);
                if (distCh != null) distCh.noteOff(midiNote);
            } catch (InterruptedException ignored) {}
        }).start();
    }

    
    /**
     * Play a note event from a recorded track
     */
    public void playNoteEvent(Track.NoteEvent event) {
        if (channels == null) return;

        String type = event.instrumentType;
        int midiNote = event.midiNote;
        float volume = event.velocity;
        int durationMs = event.durationMs;
        int vel = 0;
        int dur = 0;

        Color color = new Color(event.colorRGB, true);

        switch (type) {
            case "Piano":
                // Use volume * 100 to match instrument mode (startPianoNote)
                vel = Math.max(1, Math.min(127, (int)(volume * 100)));
                dur = (durationMs > 0) ? durationMs : 500;

                Color c = new Color(event.colorRGB, true);
                playLayeredNote(PIANO_CHANNEL, midiNote, vel, dur, c);
                break;

            case "Guitar":
                int h = (event.heightPx > 0) ? event.heightPx : 200;
                playGuitarMidiWithDuration(midiNote, color, volume, h);
                break;

            case "Drum":
            case "Snare":
            case "Snare Drum":
                // ✅ drumKey를 그대로 사용 (여러 스네어/톰 지원 가능)
                vel = Math.max(1, (int)(volume * 127));
                channels[DRUM_CHANNEL].noteOn(event.drumKey, vel);
                break;
        }
    }

    private int mapDrumKeyToMidi(String type, int drumKey) {
        // 예시: 킥/스네어 각각에 대해 size variant를 다른 MIDI note로 매핑
        if (type.equals("Snare Drum")) {
            // snare variants
            return switch (drumKey) {
                case 0 -> SNARE_CENTER;  // Snare
                case 1 -> 40;  // Electric Snare
                default -> SNARE_RIM; // Side Stick 등
            };
        } else {
            // kick variants
            return switch (drumKey) {
                case 0 -> BASS_DRUM;  // Bass Drum 1
                case 1 -> 35;  // Acoustic Bass Drum
                default -> 41; // Low Floor Tom 같은 다른 저역 타격으로 대체 가능
            };
        }
    }

    public static int uiPitchToMidi(int uiOctave, int noteIndex) {
        int midiOctave = uiOctave; 
        return (midiOctave + 1) * 12 + noteIndex; // C4=60 컨벤션이라면 이렇게
    }
    
    /**
     * Clean up resources
     */
    public void close() {
        if (synthesizer != null && synthesizer.isOpen()) {
            synthesizer.close();
        }
        if (distortionSynth != null && distortionSynth.isOpen()) {
            distortionSynth.close();
        }
    }
}

