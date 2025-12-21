# SketchJam Codebase Structure Evaluation & Refactoring Proposal

## Current Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          SketchJamApp (Main Window)                      │
│  ┌──────────────┬───────────────┬──────────────┬──────────────────────┐ │
│  │ RecordPanel  │ SketchCanvas  │   EQPanel    │ MidiSequencerPanel   │ │
│  │  (Controls)  │   (Drawing)   │   (Audio)    │   (MIDI Editor)      │ │
│  └──────────────┴───────────────┴──────────────┴──────────────────────┘ │
│         │               │               │               │               │
│         v               v               v               v               │
│  ┌──────────────┬───────────────┬──────────────┬──────────────────────┐ │
│  │ TrackManager │ DrawableElement│SoundManager │   MidiSequence       │ │
│  │  (Legacy?)   │   (Elements)  │  (Audio)     │   (MIDI Data)        │ │
│  └──────────────┴───────────────┴──────────────┴──────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## File Summary (29 Source Files)

| File | Lines | Responsibility | Complexity |
|------|-------|----------------|------------|
| `SoundManager.java` | 1215 | Audio synthesis, SF2 loading, EQ | **HIGH** |
| `SketchCanvas.java` | ~1800 | Drawing, interaction, elements | **HIGH** |
| `RecordPanel.java` | 1190 | Recording, playback, UI | **HIGH** |
| `MidiSequencerPanel.java` | 1042 | MIDI piano roll editor | **MEDIUM** |
| `TrackManager.java` | 476 | Legacy track management | **LOW** |
| `MidiSequence.java` | ~300 | MIDI note collection | **LOW** |
| `MidiNote.java` | ~200 | Single MIDI note | **LOW** |
| `AbstractElement.java` | 163 | Base element class | **LOW** |
| `PianoElement.java` | ~150 | Piano element | **LOW** |
| `GuitarElement.java` | ~150 | Guitar element | **LOW** |
| `DrumElement.java` | ~150 | Drum element | **LOW** |
| `SnareDrumElement.java` | ~150 | Snare element | **LOW** |
| Others | ~50-200 | Various utilities | **LOW** |

---

## Identified Issues

### 1. **GOD CLASSES** (Too Many Responsibilities)

#### `SoundManager.java` (1215 lines) - **Urgent Refactor**
Does too much:
- SF2 soundfont loading
- EQ DSP processing  
- MIDI channel management
- Clean/distortion mixing logic
- Metronome beep generation
- All instrument playback methods
- Note retrigger rate limiting

#### `SketchCanvas.java` (~1800 lines) - **Urgent Refactor**
Does too much:
- Element drawing
- Mouse interaction (selection, resize, move)
- Keyboard shortcuts
- Zoom/pan
- Glow effects
- Quasi-mode scaling
- Color selection mode
- Play mode interaction

#### `RecordPanel.java` (1190 lines) - **Moderate Refactor**
Does too much:
- Recording logic
- MIDI playback coordination
- Metronome management
- UI rendering
- Track display
- BPM control
- Loop beat selection
- Latency compensation

### 2. **DUPLICATE/PARALLEL SYSTEMS**

```
TrackManager ←──────── LEGACY (time-based)
     ↓
MidiSequence ←──────── CURRENT (beat-based)
```

`TrackManager` and `Track` are still present but mostly bypassed. The codebase has migrated to `MidiSequence`/`MidiNote` but hasn't removed the old system.

### 3. **INCONSISTENT NOTE CALCULATIONS**

The same color-to-note calculation exists in 3+ places:
```java
// SoundManager.java
int noteIndex = (int)Math.round(hue * 12) % 12;

// RecordPanel.java  
int noteIndex = (int)Math.round(hsb[0] * 12) % 12;

// SketchCanvas.java
int noteIndex = Math.round(hue * 12) % 12;
```

This has caused bugs (notes playing wrong pitch) and should be centralized.

### 4. **TIGHT COUPLING**

- `RecordPanel` directly calls into `SoundManager`, `MidiSequencerPanel`, `SketchCanvas`
- `MidiSequencerPanel` needs `SketchCanvas` reference for row updates
- `SketchCanvas` needs `RecordPanel` for recording, `MidiSequencerPanel` for updates

### 5. **MISSING ABSTRACTIONS**

No clear separation between:
- **Audio Engine** (playing sounds)
- **Sequencer** (timing/triggering)
- **UI** (visual representation)

---

## Proposed Refactoring

### Phase 1: Extract Services (Low Risk)

#### 1.1 Create `AudioEngine` from `SoundManager`
```
SoundManager (1215 lines) → Split into:
├── AudioEngine.java (~400 lines)
│   ├── Synthesizer management
│   ├── Channel assignment
│   └── Note playback primitives
├── EQProcessor.java (~150 lines)
│   └── Real-time EQ DSP
├── SoundfontLoader.java (~200 lines)
│   └── SF2 loading and instrument setup
└── SoundManager.java (~300 lines) - Facade
    └── Coordinates above components
```

#### 1.2 Create `NoteUtils` Utility Class
Centralize all note calculations:
```java
public class NoteUtils {
    public static final String[] NOTE_NAMES = {"C", "C#", ...};
    
    public static int colorToNoteIndex(Color color);
    public static int toMidiNote(int noteIndex, int octave);
    public static String getNoteNameFromMidi(int midi);
    public static float getSaturation(Color color);
    public static float[] getCleanDistortionMix(float saturation);
}
```

### Phase 2: Decouple Components (Medium Risk)

#### 2.1 Event-Based Communication
Replace direct method calls with events:

```java
public interface PlaybackEvent {
    void onPlayheadMoved(double beat);
    void onNoteTriggered(MidiNote note);
    void onRecordingStarted();
    void onRecordingStopped();
    void onLoopRestarted();
}

// Components subscribe instead of direct coupling
recordPanel.addPlaybackListener(midiSequencer);
recordPanel.addPlaybackListener(canvas);
```

#### 2.2 Remove `TrackManager` (Deprecated)
Migration path:
1. Ensure all functionality is in `MidiSequence`
2. Remove `TrackManager` references from `RecordPanel`
3. Delete `TrackManager.java` and `Track.java`

### Phase 3: Split God Classes (Higher Risk)

#### 3.1 Split `SketchCanvas`
```
SketchCanvas (~1800 lines) → Split into:
├── SketchCanvas.java (~500 lines)
│   └── Core drawing and element management
├── CanvasInteractionHandler.java (~400 lines)
│   └── Mouse/keyboard input handling
├── CanvasSelectionManager.java (~200 lines)
│   └── Selection logic, multi-select, marquee
├── CanvasZoomPanController.java (~150 lines)
│   └── Zoom, pan, coordinate transforms
└── CanvasPlayModeHandler.java (~200 lines)
    └── Instrument mode interaction
```

#### 3.2 Split `RecordPanel`
```
RecordPanel (1190 lines) → Split into:
├── RecordPanel.java (~300 lines)
│   └── UI rendering only
├── PlaybackController.java (~400 lines)
│   └── Play/record/loop logic
├── MetronomeManager.java (~100 lines)
│   └── Metronome timing
└── MidiPlaybackEngine.java (~300 lines)
    └── MIDI note triggering, compensation
```

### Phase 4: Improve Consistency

#### 4.1 Standardize Element Creation
```java
// Current: Each element class has its own copy() method
// Proposed: Factory pattern
public class ElementFactory {
    public static DrawableElement create(String type, int x, int y, Color color);
    public static DrawableElement copy(DrawableElement source);
    public static DrawableElement deserialize(Map<String, Object> data);
}
```

#### 4.2 Standardize MIDI Note Operations
```java
// Current: Scattered calculations in RecordPanel, SoundManager
// Proposed: MidiNote knows how to calculate its own properties
public class MidiNote {
    public int getPlaybackMidiNote(DrawableElement element);
    public int getDrumKey(DrawableElement element);
    public double getCompensatedTriggerBeat(int bpm, boolean distortionLoaded);
}
```

---

## Recommended File Structure (Post-Refactor)

```
src/
├── Main.java                    # Entry point
├── SketchJamApp.java            # Main window
│
├── audio/                       # Audio subsystem
│   ├── AudioEngine.java         # Core synthesis
│   ├── EQProcessor.java         # Real-time EQ
│   ├── SoundfontLoader.java     # SF2 loading
│   ├── MetronomeGenerator.java  # Click sounds
│   └── DistortionMixer.java     # Clean/distortion blending
│
├── canvas/                      # Canvas subsystem
│   ├── SketchCanvas.java        # Main canvas
│   ├── InteractionHandler.java  # Input handling
│   ├── SelectionManager.java    # Selection logic
│   └── ZoomPanController.java   # View transforms
│
├── elements/                    # Drawable elements
│   ├── DrawableElement.java     # Interface
│   ├── AbstractElement.java     # Base class
│   ├── PianoElement.java
│   ├── GuitarElement.java
│   ├── DrumElement.java
│   ├── SnareDrumElement.java
│   └── ElementFactory.java      # Factory
│
├── midi/                        # MIDI subsystem
│   ├── MidiNote.java            # Note data
│   ├── MidiSequence.java        # Note collection
│   ├── MidiSequencerPanel.java  # Piano roll UI
│   └── MidiPlaybackEngine.java  # Playback logic
│
├── recording/                   # Recording/playback
│   ├── RecordPanel.java         # UI only
│   ├── PlaybackController.java  # Logic
│   └── LatencyCompensator.java  # Timing adjustments
│
├── ui/                          # UI components
│   ├── ColorPalette.java
│   ├── ScalePreset.java
│   ├── ScaleSelector.java
│   ├── EQPanel.java
│   ├── SF2Manager.java
│   ├── LogoPanel.java
│   └── FilePanel.java
│
├── util/                        # Utilities
│   ├── NoteUtils.java           # Note calculations
│   ├── Colors.java              # Color constants
│   ├── FontManager.java
│   ├── ResourceLoader.java
│   └── UndoRedoManager.java
│
└── data/                        # Data management
    ├── ProjectData.java
    └── FileManager.java
```

---

## Priority Order

| Priority | Task | Risk | Effort | Impact |
|----------|------|------|--------|--------|
| 1 | Create `NoteUtils` centralized class | Low | Low | High |
| 2 | Remove `TrackManager` (dead code) | Low | Medium | Medium |
| 3 | Extract `EQProcessor` from `SoundManager` | Low | Low | Medium |
| 4 | Extract `SoundfontLoader` | Low | Medium | Medium |
| 5 | Event-based playback communication | Medium | High | High |
| 6 | Split `SketchCanvas` | High | High | Medium |
| 7 | Split `RecordPanel` | High | High | Medium |

---

## Quick Wins (Immediate)

### 1. Delete Dead Code
```bash
# Files that can be safely deleted:
# - TrackManager.java (if fully migrated to MidiSequence)
# - Track.java (if fully migrated to MidiSequence)
```

### 2. Centralize Color-to-Note Mapping
Create `NoteUtils.java` and replace all duplicate implementations.

### 3. Move Magic Numbers to Constants
```java
// Instead of:
if (saturation >= 0.55f)

// Use:
public static final float DISTORTION_SATURATION_THRESHOLD = 0.55f;
```

### 4. Add JavaDoc to Public Methods
Critical methods lack documentation.

---

## Metrics Summary

| Metric | Current | Target |
|--------|---------|--------|
| Max file size | 1800 lines | 500 lines |
| Cyclomatic complexity (max) | ~25 | ~10 |
| Duplicate code | High | Low |
| Test coverage | 0% | 70%+ |
| Coupling between classes | Tight | Loose |

---

## Conclusion

The codebase has evolved organically through feature additions, resulting in:
1. **God classes** that handle too many responsibilities
2. **Duplicate code** for common operations
3. **Legacy systems** that weren't removed after migration
4. **Tight coupling** making testing and modification difficult

The proposed refactoring follows the Single Responsibility Principle and would make the codebase more maintainable. Start with low-risk, high-impact changes (centralizing utilities, removing dead code) before tackling the larger structural changes.

