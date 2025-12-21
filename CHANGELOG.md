# SketchJam Changelog

## Recent Updates

### Quasi-Mode Scaling (Canvas Elements)

Added keyboard quasi-modes for easier element scaling in OBJECT mode:

- **'S' + Drag**: Height scaling mode
  - **Piano**: Adjust height to change octave (100-500, snaps to 100)
  - **Guitar**: Adjust height to change duration/ring time (100-500, snaps to 25)
  - **Drum/Snare**: Adjust size uniformly
  - Cursor changes to vertical resize (↕) when active

- **'A' + Drag**: Width scaling mode (Guitar only)
  - Adjust guitar string width to change octave (5=high, 25=low)
  - Width snaps to 5, 10, 15, 20, or 25
  - Cursor changes to horizontal resize (↔) when active

### MIDI Sequencer Enhancements

#### Duration Adjustment
- **'S' + Drag**: Adjust duration of selected MIDI notes
  - Select notes, hold 'S', drag horizontally to change duration
  - All selected notes adjust together
  - Snaps to grid (0.25 beats)

#### Visual Improvements
- **Selected note highlighting**: Semi-transparent white overlay (120 alpha) with 2px white border
  - Makes selected notes clearly visible while preserving track color

### Audio Timing Fixes

#### Latency Compensation
- Piano and Guitar notes now trigger 0.25 beats earlier to compensate for audio latency
- Drums remain unaffected (no latency issues)
- Timer interval reduced from 10ms to 5ms for tighter timing

#### Fixed Piano Duration
- **Recording**: Piano notes are recorded with fixed 1 beat duration (ignoring press length)
- **Playback**: Piano notes play with fixed 500ms duration
- Provides consistent, predictable piano sound

### Recording Improvements

#### Snap Behavior
- Changed from `Math.round()` to `Math.floor()` for recording snap
- Notes now snap to the earlier beat point for more natural feel
- Compensates for typical human tendency to play slightly behind the beat

---

## Keyboard Shortcuts Summary

### Canvas (OBJECT Mode)
| Key | Action |
|-----|--------|
| S + Drag | Scale height of selected element |
| A + Drag | Scale width of guitar element |
| C + Click | Select all elements with the same color |
| Space + Drag | Pan canvas |
| T, Y, U, I | Play elements (act as mouse clicks) |
| Alt + Drag | Duplicate element(s) |
| Delete | Delete selected element(s) |

### MIDI Sequencer
| Key | Action |
|-----|--------|
| S + Drag | Adjust duration of selected notes |
| ← / → | Move selected notes left/right |
| ↑ / ↓ | Move selected notes up/down (change row) |
| Delete / Backspace | Delete selected notes |
| Ctrl + D | Duplicate selected notes |
| Ctrl + A | Select all notes |
| Q | Quantize selected notes |
| Alt + Drag | Duplicate notes while dragging |
| Alt + Scroll | Vertical scroll through rows |

