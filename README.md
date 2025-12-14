# SketchJam User Guide

## Welcome to SketchJam! 🎨🎵

SketchJam is a visual music creation tool where you **draw shapes to make music**. Each shape becomes a musical note - rectangles are piano and guitar, circles are drums. Just draw, click, and play!

---

## Getting Started

### Launch the App
Double-click `build_and_run.bat` or run:
```
java --add-opens java.desktop/com.sun.media.sound=ALL-UNNAMED -cp out Main
```

### The Interface

```
┌─────────────────────────────────────────────────────────────────┐
│ [NEW][OPEN][SAVE][EXPORT]                     [COLOR PALETTE]   │
│                                               [SCALE PRESET]    │
│  [BPM: 120]                                   [SCALE SELECTOR]  │
│  [▶][●][🔁]        ┌─────────────────────┐   [SF2 MANAGER]     │
│                    │                     │   [EQ SETTINGS]     │
│  [Track 1]         │                     │                     │
│  [Track 2]         │      CANVAS         │                     │
│  [Track 3]         │                     │                     │
│  [Track 4]         │   (Draw here!)      │                     │
│  [Track 5]         │                     │                     │
│  [Track 6]         │                     │                     │
│  [Track 7]         └─────────────────────┘   [LOGO]            │
│  [4][8][16][32]                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## How to Draw Instruments

### 🎹 Piano
1. **Hold F** and drag on canvas
2. Release to finish
3. **Height = Octave**: Taller = lower notes, shorter = higher notes
4. Width is fixed at 100px

### 🎸 Guitar  
1. **Hold G** and drag on canvas
2. Release to finish
3. **Width = Octave**: Thicker = lower notes, thinner = higher notes
4. **Height = Duration**: Taller notes ring longer

### 🥁 Drum (Toms & Bass)
1. **Hold D** and drag on canvas
2. Release to finish
3. **Size = Drum Type**:
   - Small (100px) = High Tom
   - Medium (125px) = Mid Tom
   - Large (150px) = Floor Tom
   - Extra Large (200px) = Bass Drum

### 🪘 Snare Drum
1. **Hold Ctrl+D** and drag on canvas
2. Release to finish
3. **Size = Hit Type**:
   - Small (100px) = Rim Shot
   - Large (150px) = Center Hit

---

## Choosing Colors = Choosing Notes

The **Color Palette** (top-right) has 12 columns × 4 rows:

### Columns = Musical Notes
```
C  C# D  D# E  F  F# G  G# A  A# B
🔴 🟠 🟡 🟢 🟢 🩵 🩵 🔵 🔵 🟣 🟣 🩷
```

### Rows = Saturation (Distortion)
- **Top row (100%)**: Full distortion
- **Second row (80%)**: Heavy distortion + some clean
- **Third row (60%)**: Light distortion + more clean  
- **Bottom row (40%)**: Clean sound only

**Click a color** to select it. You'll hear the note play as feedback!

---

## Playing Your Music

### Two Modes

| Mode | How to Enter | What You Can Do |
|------|--------------|-----------------|
| **Object Mode** | Press `O` | Draw, select, move, resize, delete |
| **Preview Mode** | Press `P` | Click elements to play sounds |

### Playing Sounds

1. Press `P` to enter Preview Mode
2. **Click** any element to play it
3. **Drag** across guitar elements to strum them
4. **Hold** on piano elements - longer hold = longer note

### Keyboard Trigger
In Preview Mode, hover over an element and press `T` to play it (great for fast playing!)

---

## Editing Your Shapes

### Select
- **Click** an element to select it (shows handles)
- **Drag on empty space** to select multiple elements

### Move
- **Drag** a selected element to move it
- Elements snap to a 5px grid

### Resize
- **Drag the handles** (small squares on edges)
- Piano: Only top/bottom handles (width is fixed)
- Drums: Corner handles (keeps circle shape)

### Rotate
- Select an element and press `R`
- Rotates 90° clockwise

### Delete
- Select element(s) and press `Delete`

### Duplicate
- **Hold Alt** and drag an element to copy it

### Change Volume
- Select element(s)
- Press `]` to increase volume (opacity)
- Press `[` to decrease volume (opacity)
- Range: 20% to 100%

---

## Using Scales

### Scale Preset (7 colored boxes)
Shows the 7 notes in your selected scale. Click any to play!

### Scale Selector
1. Click a **root note** (C through B)
2. Click **MAJOR** or **MINOR**
3. The Scale Preset updates with the correct notes

This helps you stay in key when composing!

---

## Recording & Looping

### Set Your Tempo
1. Use **+/-** buttons next to BPM
2. Default is 120 BPM
3. BPM is locked while playing/recording

### Basic Recording
1. Click the **Record button** (●)
2. Wait for 4-beat count-in (beep beep beep beep)
3. Play your elements!
4. Click Record again to stop
5. Your performance is saved as a track

### Playback
- Click **Play button** (▶) to hear all tracks
- Click again to stop

### Loop Mode
1. Click **Loop button** (🔁) to enable
2. Select loop length: **4, 8, 16, or 32 beats**
3. Now Play/Record will loop automatically!

### Loop Recording (Building Layers)
1. Enable Loop mode
2. Click Record
3. Play something - it becomes Track 1
4. Keep playing - Track 1 loops while you add Track 2
5. Continue layering up to 7 tracks!

### Managing Tracks
- Click a track name to select it
- Click **X** to delete a track

---

## Changing Sounds

### SF2 Manager
Assign different soundfonts to each instrument:

1. Click the dropdown button next to **Piano**, **Guitar**, or **Drums**
2. Select a soundfont from the list
3. Sound changes immediately!

**Note**: Drum soundfonts are listed separately from melodic instruments.

### Adding New Sounds
1. Download .sf2 soundfont files
2. Put them in the `soundfonts/` folder
3. For distortion sounds, put them in the `distortion/` folder
4. Restart the app or click the dropdown to refresh

---

## EQ & Brightness

The **EQ Settings** panel has 12 brightness levels:

| Left (White) | Right (Black) |
|--------------|---------------|
| Bright sound | Dark sound |
| Mids boosted | Bass boosted |
| Light canvas | Dark canvas |

Click a level to:
- Change canvas background color
- Adjust audio EQ
- Grid and drum colors auto-adjust for visibility

---

## Navigating the Canvas

### Zoom
- `Ctrl + Plus` = Zoom in (max 400%)
- `Ctrl + Minus` = Zoom out (min 25%)

### Pan
- Hold `Spacebar` and drag to pan around

### Reset View
- Press `1` to reset to 100% zoom, original position

---

## Saving Your Work

### Save Project
- Press `Ctrl+S` or click **SAVE**
- Saves as `.sjam` file (keeps everything!)

### Open Project
- Press `Ctrl+O` or click **OPEN**
- Load a previously saved `.sjam` file

### New Project
- Press `Ctrl+N` or click **NEW**
- Clears everything (you'll be asked to save first)

### Export Audio
- Click **EXPORT**
- Saves your recorded tracks as a `.wav` audio file

---

## Undo & Redo

- `Ctrl+Z` = Undo (up to 20 steps)
- `Ctrl+Y` = Redo

Works for: drawing, moving, resizing, deleting, color changes

---

## Quick Reference Card

### Drawing (Hold key + drag)
| Key | Instrument |
|-----|------------|
| `F` | Piano |
| `G` | Guitar |
| `D` | Drum |
| `Ctrl+D` | Snare |

### Modes
| Key | Mode |
|-----|------|
| `O` | Object (Edit) |
| `P` | Preview (Play) |

### Editing
| Key | Action |
|-----|--------|
| `R` | Rotate 90° |
| `Delete` | Delete |
| `[` | Volume down |
| `]` | Volume up |
| `Alt+Drag` | Duplicate |

### Navigation
| Key | Action |
|-----|--------|
| `Ctrl++` | Zoom in |
| `Ctrl+-` | Zoom out |
| `Space+Drag` | Pan |
| `1` | Reset view |

### Files
| Key | Action |
|-----|--------|
| `Ctrl+N` | New |
| `Ctrl+O` | Open |
| `Ctrl+S` | Save |
| `Ctrl+Z` | Undo |
| `Ctrl+Y` | Redo |

---

## Tips & Tricks

1. **Start with a scale** - Select your root note and Major/Minor before drawing to stay in key

2. **Use the Scale Preset** - Click the 7 preset colors for quick in-key note selection

3. **Layer with loops** - Record a simple beat first, then add melody layers

4. **Experiment with saturation** - Top row colors = heavy distortion, bottom row = clean

5. **Use opacity for dynamics** - Quieter elements (lower opacity) add texture without overpowering

6. **Guitar strumming** - In Preview mode, quickly drag across multiple guitar elements for a strum effect

7. **Quick keyboard playing** - In Preview mode, use `T` key while hovering over elements for faster triggering

8. **Visual organization** - Use the grid to align elements neatly

9. **Save often** - Use `Ctrl+S` regularly to save your work!

---

## Troubleshooting

### No sound?
- Check that soundfonts exist in `soundfonts/` folder
- Make sure your system audio is working
- Try a different SF2 file in SF2 Manager

### Lag or stuttering?
- Close other audio applications
- Reduce number of simultaneous elements

### Elements won't draw?
- Make sure you're in Object Mode (press `O`)
- Hold the key (F, G, D) while dragging

### Can't edit in Preview mode?
- Press `O` to switch to Object Mode
- You can only play sounds in Preview Mode

---

Enjoy making music with SketchJam! 🎶

