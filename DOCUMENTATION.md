# SketchJam - Function Summary Documentation

## Overview

SketchJam is a Java-based visual music composition application that allows users to draw musical elements on a canvas and play them as sounds. It combines drawing, music theory, and loopstation functionality into a unique creative tool.

**Window Dimensions:** 1900×1050  
**Canvas Dimensions:** 1350×1050 (at position 200, 0)  
**Canvas Background:** #323232 (dark gray)  
**UI Background:** #383838

---

## Architecture

### Core Classes

| Class | Description |
|-------|-------------|
| `Main.java` | Application entry point. Initializes fonts and dark theme for dialogs. |
| `SketchJamApp.java` | Main JFrame. Sets up UI layout, manages all panels and components. |
| `SketchCanvas.java` | Central drawing canvas. Handles all drawing, selection, user interaction, zoom/pan. |

### Element Classes

| Class | Description |
|-------|-------------|
| `DrawableElement.java` | Interface defining all drawable element behaviors. |
| `AbstractElement.java` | Base abstract class implementing common element functionality. |
| `PianoElement.java` | Rectangle element representing piano notes. Fixed width (100px), height determines octave. |
| `GuitarElement.java` | Rectangle element representing guitar notes. Width determines octave, height determines duration. |
| `DrumElement.java` | Circle element representing drum sounds. Size determines drum type (tom sizes). |
| `SnareDrumElement.java` | Hollow circle element representing snare drum. Size determines rim/center shot. |

### Sound System

| Class | Description |
|-------|-------------|
| `SoundManager.java` | Singleton managing all audio. Loads SF2 soundfonts, handles MIDI synthesis, EQ DSP, distortion mixing. |
| `Track.java` | Represents a recorded sequence of note events with timestamps. |
| `TrackManager.java` | Manages recording and playback of tracks, including loop synchronization. |

### UI Panels

| Class | Description |
|-------|-------------|
| `ColorPalette.java` | 12×4 color matrix (notes × saturation levels) for selecting element colors. |
| `ScalePreset.java` | 7-note scale display showing notes in selected scale. |
| `ScaleSelector.java` | Root note (C-B) and scale type (Major/Minor) selection. |
| `SF2Manager.java` | Dropdown panels for assigning soundfonts to Piano, Guitar, Drums. |
| `EQPanel.java` | 12-level brightness slider controlling canvas background and audio EQ. |
| `RecordPanel.java` | Recording controls: BPM, Play/Record/Loop buttons, track list, beat selector. |
| `FilePanel.java` | File operations: New, Open, Save, Export buttons. |
| `LogoPanel.java` | Displays application logo at bottom-right. |

### Utility Classes

| Class | Description |
|-------|-------------|
| `FontManager.java` | Loads and provides custom Paperlogy fonts. |
| `UndoRedoManager.java` | Manages undo/redo history (20 states). |
| `FileManager.java` | Handles save/load (.sjam) and export (.wav) operations. |
| `ProjectData.java` | Serializable data class for saving/loading project state. |

---

## Element Types

### Piano (`PianoElement`)
- **Shape:** Rectangle
- **Fixed Width:** 100px
- **Height Snap:** 100, 200, 300, 400, 500 (Octaves 5, 4, 3, 2, 1)
- **Color:** Determines note (C through B based on hue)
- **Saturation:** Determines clean/distortion mix
- **Playback:** Note length controlled by mouse press duration (sustain pedal)

### Guitar (`GuitarElement`)
- **Shape:** Rectangle
- **Width Snap:** 5, 10, 15, 20, 25 (Octaves 5, 4, 3, 2, 1)
- **Height Snap:** Multiples of 25 (100-500, determines duration)
- **Color:** Determines note
- **Saturation:** Determines clean/distortion mix
- **Playback:** Height maps to ring time (damping for short, sustain for tall)

### Drum (`DrumElement`)
- **Shape:** Filled circle
- **Size Snap:** 100×100, 125×125, 150×150, 200×200 (High Tom, Mid Tom, Floor Tom, Bass Drum)
- **Color:** Always white (ignores color selection)
- **1:1 Aspect Ratio:** Maintained during resize

### Snare Drum (`SnareDrumElement`)
- **Shape:** Hollow circle (inner circle at 50% size)
- **Size Snap:** 100×100, 150×150 (Rim Shot, Center Shot)
- **Color:** Always white
- **1:1 Aspect Ratio:** Maintained during resize

---

## Keyboard Shortcuts

### Drawing Modes (Quasi-modes - hold key to draw)
| Key | Action |
|-----|--------|
| `D` | Draw Drum (hold and drag) |
| `Ctrl+D` | Draw Snare Drum (hold and drag) |
| `F` | Draw Piano (hold and drag) |
| `G` | Draw Guitar (hold and drag) |

### Interaction Modes
| Key | Action |
|-----|--------|
| `O` | Switch to Object Mode (editing) |
| `P` | Switch to Preview Mode (playing only) |

### Element Manipulation (Object Mode)
| Key | Action |
|-----|--------|
| `R` | Rotate selected element 90° |
| `Delete` | Delete selected element(s) |
| `[` | Decrease opacity by 20% (min 20%) |
| `]` | Increase opacity by 20% (max 100%) |
| `Alt+Drag` | Duplicate selected element(s) |

### Canvas Navigation
| Key | Action |
|-----|--------|
| `Ctrl++` | Zoom in (25% steps, max 400%) |
| `Ctrl+-` | Zoom out (25% steps, min 25%) |
| `1` | Reset to 100% zoom, original position |
| `Space+Drag` | Pan canvas |

### File Operations
| Key | Action |
|-----|--------|
| `Ctrl+N` | New project |
| `Ctrl+O` | Open project (.sjam) |
| `Ctrl+S` | Save project |

### Undo/Redo
| Key | Action |
|-----|--------|
| `Ctrl+Z` | Undo (up to 20 states) |
| `Ctrl+Y` | Redo |

### Playback
| Key | Action |
|-----|--------|
| `T` | Trigger element under cursor (in Preview/Record modes) |

---

## Mouse Interactions

### Object Mode
| Action | Result |
|--------|--------|
| Click element | Select element |
| Click empty space | Deselect |
| Drag empty space | Marquee selection (multiple elements) |
| Drag element | Move element (snaps to 5px grid) |
| Drag handle | Resize element |
| Alt+Drag element | Duplicate and move |

### Preview Mode
| Action | Result |
|--------|--------|
| Click element | Play element sound |
| Drag over elements | Play all elements in path (guitar only) |

### Record Mode
| Action | Result |
|--------|--------|
| Click/Drag elements | Play and record to current track |

---

## Sound System

### Note Mapping
Colors map to musical notes based on hue:
| Color | Note |
|-------|------|
| Red (#FF0000) | C |
| Orange (#FF8000) | C# |
| Yellow (#FFFF00) | D |
| Yellow-Green (#80FF00) | D# |
| Green (#00FF00) | E |
| Green-Cyan (#00FF80) | F |
| Cyan (#00FFFF) | F# |
| Cyan-Blue (#0080FF) | G |
| Blue (#0000FF) | G# |
| Purple (#8000FF) | A |
| Magenta (#FF00FF) | A# |
| Pink-Red (#FF0080) | B |

### Octave Mapping
- **Piano:** Height 100px = Octave 5 (highest), Height 500px = Octave 1 (lowest). Shifted 1 octave up.
- **Guitar:** Width 5px = Octave 5 (highest), Width 25px = Octave 1 (lowest). Notes play at natural pitch.

### Saturation → Distortion Mixing
| Saturation | Clean Volume | Distortion Volume | Total |
|------------|--------------|-------------------|-------|
| 100% | 0% | 100% | 100% |
| 80% | 40% | 80% | 120% |
| 60% | 80% | 40% | 120% |
| 40% | 100% | 0% | 100% |

### Opacity → Volume Mapping
Element opacity (20%-100%) directly maps to playback volume.

### EQ System
12 brightness levels control:
- **Canvas background:** White (#FFFFFF) to Black (#000000)
- **Grid/Drum colors:** Invert at midpoint
- **Audio EQ:**
  - Light (white): Mids boosted, bass cut
  - Dark (black): Bass boosted, mids cut

---

## Loopstation Functionality

### Recording
1. Set BPM (greyed out during playback)
2. Select loop beats (4, 8, 16, or 32)
3. Click Record → 4-beat count-in plays
4. Play elements to record
5. Click Record again to stop

### Playback
- **Play button:** Plays all recorded tracks once
- **Loop + Play:** Repeats all tracks every `loopBeats` beats

### Loop Recording
- **Loop + Record:** Auto-creates new tracks every `loopBeats` beats
- Previous tracks play simultaneously during recording

### Track Management
- Up to 7 tracks supported
- Click track name to select
- Click X to delete track

---

## File Format

### .sjam Project File
Serialized Java object containing:
- All canvas elements (position, size, color, opacity, rotation)
- All recorded tracks (events with timestamps)
- BPM and loop beat settings
- Selected scale (root note + Major/Minor)
- EQ brightness level
- SF2 assignments (Piano, Guitar, Drums)
- Selected color

### .wav Export
Renders all recorded tracks to stereo WAV audio file (44.1kHz, 16-bit).

---

## Folder Structure

```
SketchJam/
├── src/                    # Java source files
│   ├── Main.java
│   ├── SketchJamApp.java
│   ├── SketchCanvas.java
│   ├── DrawableElement.java
│   ├── AbstractElement.java
│   ├── PianoElement.java
│   ├── GuitarElement.java
│   ├── DrumElement.java
│   ├── SnareDrumElement.java
│   ├── SoundManager.java
│   ├── Track.java
│   ├── TrackManager.java
│   ├── ColorPalette.java
│   ├── ScalePreset.java
│   ├── ScaleSelector.java
│   ├── SF2Manager.java
│   ├── EQPanel.java
│   ├── RecordPanel.java
│   ├── FilePanel.java
│   ├── LogoPanel.java
│   ├── FontManager.java
│   ├── UndoRedoManager.java
│   ├── FileManager.java
│   └── ProjectData.java
├── out/                    # Compiled .class files
├── soundfonts/             # SF2 soundfont files
│   ├── Galaxy_Electric_Pianos.sf2
│   ├── Clean Guitar Bank.sf2
│   ├── Funk Guitar Bank.sf2
│   ├── Jazz Guitar Bank(FREE).sf2
│   └── DrumsV2.sf2
├── distortion/             # Distortion SF2 files
│   ├── 8bitsf.SF2
│   └── Distortion_Guitar.sf2
├── fonts/                  # Custom fonts
│   ├── Paperlogy-4Regular.ttf
│   └── Paperlogy-7Bold.ttf
├── symbols/                # UI icons
│   ├── play.png
│   ├── record.png
│   ├── loop.png
│   ├── new.png
│   ├── open.png
│   ├── save.png
│   ├── export.png
│   └── bottomlogo.png
└── .gitignore
```

---

## Build & Run

### Compile
```bash
javac -d out src/*.java
```

### Run
```bash
java --add-opens java.desktop/com.sun.media.sound=ALL-UNNAMED -cp out Main
```

The `--add-opens` flag is required for EQ DSP audio streaming.

### Windows Batch File
```batch
@echo off
cd /d "%~dp0"
javac -d out src/*.java
java --add-opens java.desktop/com.sun.media.sound=ALL-UNNAMED -cp out Main
```

---

## Technical Notes

### Grid System
- **Grid size:** 25px
- **Snap size:** 5px for all move/resize operations
- **Grid opacity:** 10% white

### Selection Handles
- **Size:** 6×6 pixels
- **Position:** Centered on edge midpoints
- **Piano:** Top and bottom handles only (fixed width)
- **Drums:** Corner handles (diagonal scaling)
- **Others:** All four side handles

### Glow Effect
Elements glow when played in Preview mode with a fade-out animation.

### Double Buffering
Canvas uses double buffering to prevent flickering during drawing and animation.

### Separate Synthesizers
Clean and distortion sounds use separate MIDI synthesizers to avoid program conflicts.

---

## Dependencies

- **Java 11+** (tested with Eclipse Temurin)
- **javax.sound.midi** (MIDI synthesis)
- **javax.sound.sampled** (Audio streaming, metronome)
- **Java Swing** (GUI)
- **SF2 SoundFonts** (External audio samples)

---

*SketchJam v1.0 - Visual Music Composition Tool*

