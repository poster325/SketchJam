import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.util.List;

/**
 * Manages file operations for SketchJam projects
 */
public class FileManager {
    
    private static FileManager instance;
    private File currentFile = null;
    private boolean hasUnsavedChanges = false;
    
    // References to app components
    private SketchCanvas canvas;
    private RecordPanel recordPanel;
    private ColorPalette colorPalette;
    private ScaleSelector scaleSelector;
    private SF2Manager sf2Manager;
    private EQPanel eqPanel;
    
    private FileManager() {}
    
    public static FileManager getInstance() {
        if (instance == null) {
            instance = new FileManager();
        }
        return instance;
    }
    
    public void setComponents(SketchCanvas canvas, RecordPanel recordPanel, 
                              ColorPalette colorPalette, ScaleSelector scaleSelector,
                              SF2Manager sf2Manager, EQPanel eqPanel) {
        this.canvas = canvas;
        this.recordPanel = recordPanel;
        this.colorPalette = colorPalette;
        this.scaleSelector = scaleSelector;
        this.sf2Manager = sf2Manager;
        this.eqPanel = eqPanel;
    }
    
    public void markUnsaved() {
        hasUnsavedChanges = true;
    }
    
    public void markSaved() {
        hasUnsavedChanges = false;
    }
    
    public boolean hasUnsavedChanges() {
        return hasUnsavedChanges;
    }
    
    public File getCurrentFile() {
        return currentFile;
    }
    
    /**
     * Check for unsaved changes and prompt user
     * Returns true if okay to proceed, false to cancel
     */
    public boolean checkUnsavedChanges(Component parent) {
        if (!hasUnsavedChanges) {
            return true;
        }
        
        int result = JOptionPane.showConfirmDialog(
            parent,
            "You have unsaved changes. Do you want to save before continuing?",
            "Unsaved Changes",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        
        if (result == JOptionPane.YES_OPTION) {
            return saveFile(parent);
        } else if (result == JOptionPane.NO_OPTION) {
            return true;
        } else {
            return false; // Cancel
        }
    }
    
    /**
     * Create a new empty project
     */
    public void newFile(Component parent) {
        if (!checkUnsavedChanges(parent)) {
            return;
        }
        
        // Clear all data
        if (canvas != null) {
            canvas.clearElements();
        }
        if (recordPanel != null) {
            recordPanel.getTrackManager().clearTracks();
            recordPanel.setBpm(120);
            recordPanel.setLoopBeats(16);
        }
        if (colorPalette != null) {
            colorPalette.clearSelection();
        }
        if (scaleSelector != null) {
            scaleSelector.setScale(0, true); // C Major
        }
        if (eqPanel != null) {
            eqPanel.setBrightnessLevel(5);
        }
        
        currentFile = null;
        hasUnsavedChanges = false;
        
        System.out.println("New project created");
    }
    
    /**
     * Open a .sjam file
     */
    public void openFile(Component parent) {
        if (!checkUnsavedChanges(parent)) {
            return;
        }
        
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open SketchJam Project");
        chooser.setFileFilter(new FileNameExtensionFilter("SketchJam Files (*.sjam)", "sjam"));
        
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                loadProject(file);
                currentFile = file;
                hasUnsavedChanges = false;
                System.out.println("Opened: " + file.getAbsolutePath());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(parent, 
                    "Failed to open file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Save to current file or prompt for new file
     */
    public boolean saveFile(Component parent) {
        if (currentFile == null) {
            return saveFileAs(parent);
        }
        
        try {
            saveProject(currentFile);
            hasUnsavedChanges = false;
            System.out.println("Saved: " + currentFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent, 
                "Failed to save file: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Save to a new file
     */
    public boolean saveFileAs(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save SketchJam Project");
        chooser.setFileFilter(new FileNameExtensionFilter("SketchJam Files (*.sjam)", "sjam"));
        
        if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            
            // Add .sjam extension if not present
            if (!file.getName().toLowerCase().endsWith(".sjam")) {
                file = new File(file.getAbsolutePath() + ".sjam");
            }
            
            try {
                saveProject(file);
                currentFile = file;
                hasUnsavedChanges = false;
                System.out.println("Saved: " + file.getAbsolutePath());
                return true;
            } catch (Exception e) {
                JOptionPane.showMessageDialog(parent, 
                    "Failed to save file: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }
    
    /**
     * Export project as WAV file
     */
    public void exportWav(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export as WAV");
        chooser.setFileFilter(new FileNameExtensionFilter("WAV Audio Files (*.wav)", "wav"));
        
        if (chooser.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            
            // Add .wav extension if not present
            if (!file.getName().toLowerCase().endsWith(".wav")) {
                file = new File(file.getAbsolutePath() + ".wav");
            }
            
            try {
                renderToWav(file);
                JOptionPane.showMessageDialog(parent, 
                    "Exported to: " + file.getName(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                System.out.println("Exported: " + file.getAbsolutePath());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(parent, 
                    "Failed to export: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Collect current project data
     */
    private ProjectData collectProjectData() {
        ProjectData data = new ProjectData();
        
        // Collect elements
        if (canvas != null) {
            for (DrawableElement element : canvas.getElements()) {
                data.elements.add(new ProjectData.ElementData(element));
            }
        }
        
        // Collect tracks
        if (recordPanel != null) {
            TrackManager tm = recordPanel.getTrackManager();
            for (Track track : tm.getTracks()) {
                data.tracks.add(new ProjectData.TrackData(track));
            }
            data.bpm = recordPanel.getBpm();
            data.loopBeats = recordPanel.getLoopBeats();
        }
        
        // Collect settings
        if (scaleSelector != null) {
            data.scaleRootNote = scaleSelector.getRootNote();
            data.scaleMajor = scaleSelector.isMajor();
        }
        
        if (eqPanel != null) {
            data.eqBrightnessLevel = eqPanel.getBrightnessLevel();
        }
        
        if (colorPalette != null) {
            data.selectedColorCol = colorPalette.getSelectedCol();
            data.selectedColorRow = colorPalette.getSelectedRow();
        }
        
        if (sf2Manager != null) {
            data.pianoSoundfont = sf2Manager.getSelectedPiano();
            data.guitarSoundfont = sf2Manager.getSelectedGuitar();
            data.drumsSoundfont = sf2Manager.getSelectedDrums();
        }
        
        return data;
    }
    
    /**
     * Save project to file
     */
    private void saveProject(File file) throws IOException {
        ProjectData data = collectProjectData();
        data.saveToFile(file);
    }
    
    /**
     * Load project from file
     */
    private void loadProject(File file) throws IOException, ClassNotFoundException {
        ProjectData data = ProjectData.loadFromFile(file);
        
        // Load elements
        if (canvas != null) {
            canvas.clearElements();
            for (ProjectData.ElementData elementData : data.elements) {
                DrawableElement element = elementData.toElement();
                if (element != null) {
                    canvas.addElement(element);
                }
            }
        }
        
        // Load tracks
        if (recordPanel != null) {
            TrackManager tm = recordPanel.getTrackManager();
            tm.clearTracks();
            for (ProjectData.TrackData trackData : data.tracks) {
                tm.addTrack(trackData.toTrack());
            }
            recordPanel.setBpm(data.bpm);
            recordPanel.setLoopBeats(data.loopBeats);

            tm.normalizeTrackMetaAfterLoad();
        }
        
        // Load settings
        if (scaleSelector != null) {
            scaleSelector.setScale(data.scaleRootNote, data.scaleMajor);
        }
        
        if (eqPanel != null) {
            eqPanel.setBrightnessLevel(data.eqBrightnessLevel);
        }
        
        if (colorPalette != null && data.selectedColorCol >= 0) {
            colorPalette.setSelection(data.selectedColorCol, data.selectedColorRow);
        }
        
        if (sf2Manager != null) {
            if (data.pianoSoundfont != null) sf2Manager.setPiano(data.pianoSoundfont);
            if (data.guitarSoundfont != null) sf2Manager.setGuitar(data.guitarSoundfont);
            if (data.drumsSoundfont != null) sf2Manager.setDrums(data.drumsSoundfont);
        }
    }
    
    /**
     * Render all tracks to WAV file
     */
    private void renderToWav(File file) throws Exception {
        if (recordPanel == null) {
            throw new Exception("No tracks to export");
        }
        
        TrackManager tm = recordPanel.getTrackManager();
        List<Track> tracks = tm.getTracks();
        
        if (tracks.isEmpty()) {
            throw new Exception("No tracks to export");
        }
        
        // Calculate total duration
        long maxDuration = 0;
        for (Track track : tracks) {
            for (Track.NoteEvent event : track.getEvents()) {
                long endTime = event.timestampMs + event.durationMs;
                if (endTime > maxDuration) {
                    maxDuration = endTime;
                }
            }
        }
        
        // Add some padding
        maxDuration += 1000; // 1 second padding
        
        // Audio format: 44100 Hz, 16-bit, stereo
        float sampleRate = 44100;
        int channels = 2;
        int sampleSizeInBits = 16;
        boolean signed = true;
        boolean bigEndian = false;
        
        AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
        int numSamples = (int)(sampleRate * maxDuration / 1000);
        byte[] audioData = new byte[numSamples * channels * 2]; // 2 bytes per sample
        
        // Render each track
        // Note: This is a simplified render - in reality you'd need to
        // capture the synthesizer output while playing each note
        
        // For now, we'll create a simple tone-based render
        for (Track track : tracks) {
            for (Track.NoteEvent event : track.getEvents()) {
                renderNoteToBuffer(audioData, format, event);
            }
        }
        
        // Write to WAV file
        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        AudioInputStream ais = new AudioInputStream(bais, format, numSamples);
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
    }
    
    /**
     * Render a single note to the audio buffer
    */
    private void renderNoteToBuffer(byte[] buffer, AudioFormat format, Track.NoteEvent event) {
        float sampleRate = format.getSampleRate();
        int startSample = (int)(sampleRate * event.timestampMs / 1000.0);
        int durationSamples = (int)(sampleRate * Math.max(100, event.durationMs) / 1000.0);

        // ✅ event에서 바로 MIDI를 가져옴 (octave/noteIndex 계산 삭제)
        int midiNote = event.midiNote;

        double frequency = 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0);
        
        // Generate simple sine wave
        double amplitude = event.velocity * 0.3; // Scale down to avoid clipping
        
        for (int i = 0; i < durationSamples; i++) {
            int sampleIndex = startSample + i;
            if (sampleIndex * 4 + 3 >= buffer.length) break;
            
            // Envelope: attack-decay
            double envelope = 1.0;
            if (i < 1000) {
                envelope = i / 1000.0; // Attack
            } else if (i > durationSamples - 2000) {
                envelope = (durationSamples - i) / 2000.0; // Release
            }
            envelope = Math.max(0, Math.min(1, envelope));
            
            double sample = Math.sin(2.0 * Math.PI * frequency * i / sampleRate) * amplitude * envelope;
            short sampleValue = (short)(sample * 32767);
            
            // Mix with existing audio (add and clip)
            int byteIndex = sampleIndex * 4;
            int existingL = (buffer[byteIndex] & 0xFF) | (buffer[byteIndex + 1] << 8);
            int existingR = (buffer[byteIndex + 2] & 0xFF) | (buffer[byteIndex + 3] << 8);
            
            int newL = Math.max(-32768, Math.min(32767, existingL + sampleValue));
            int newR = Math.max(-32768, Math.min(32767, existingR + sampleValue));
            
            buffer[byteIndex] = (byte)(newL & 0xFF);
            buffer[byteIndex + 1] = (byte)((newL >> 8) & 0xFF);
            buffer[byteIndex + 2] = (byte)(newR & 0xFF);
            buffer[byteIndex + 3] = (byte)((newR >> 8) & 0xFF);
        }
    }
}

