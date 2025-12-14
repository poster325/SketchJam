import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a recorded track with timestamped note events
 */
public class Track {
    
    /**
     * A single note event in the track
     */
    public static class NoteEvent {
        public final long timestampMs;
        public final String instrumentType;

        // ✅ 최종 재생 단위로 저장
        public final int midiNote;   // Piano/Guitar용 (예: 60 = C4)
        public final int drumKey;    // Drum/Snare용 (0=small,1=mid,2=big 등)

        public final float velocity;
        public final int durationMs;

        public final int colorRGB;     // saturation 계산용
        public final int heightPx;     // 기타 링타임(또는 기타 톤 파라미터)용

        public NoteEvent(long timestampMs, String instrumentType,
            int midiNote, int drumKey,
            float velocity, int durationMs,
            int colorRGB, int heightPx) {
            this.timestampMs = timestampMs;
            this.instrumentType = instrumentType;
            this.midiNote = midiNote;
            this.drumKey = drumKey;
            this.velocity = velocity;
            this.durationMs = durationMs;
            this.colorRGB = colorRGB;
            this.heightPx = heightPx;
        }
    }
    
    private String name;
    private Color color;
    private List<NoteEvent> events;
    private int bpm;
    private long durationMs;  // Total duration of the track
    
    public Track(String name, Color color, int bpm) {
        this.name = name;
        this.color = color;
        this.bpm = bpm;
        this.events = new ArrayList<>();
        this.durationMs = 0;
    }
    
    public void addEvent(NoteEvent event) {
        events.add(event);
        // Update duration to include this event
        if (event.timestampMs + event.durationMs > durationMs) {
            durationMs = event.timestampMs + event.durationMs;
        }
    }
    
    public List<NoteEvent> getEvents() {
        return events;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
    
    public int getBpm() {
        return bpm;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
    
    public int getEventCount() {
        return events.size();
    }
    
    public boolean isEmpty() {
        return events.isEmpty();
    }
    
    /**
     * Calculate the duration of 16 beats at current BPM
     */
    public static long get16BeatDurationMs(int bpm) {
        // 1 beat = 60000 / bpm milliseconds
        // 16 beats = 16 * 60000 / bpm
        return (16 * 60000L) / bpm;
    }


    public void retimeToNewLoop(long oldLoopMs, long newLoopMs) {
        if (oldLoopMs <= 0 || newLoopMs <= 0 || events.isEmpty()) return;

        List<NoteEvent> newEvents = new ArrayList<>(events.size());
        for (NoteEvent e : events) {
            long newTs = (e.timestampMs * newLoopMs) / oldLoopMs;
            // duration도 같이 스케일할지 선택 가능 (기타 sustain 등)
            int newDur = (int) Math.max(1, (e.durationMs * newLoopMs) / oldLoopMs);

            newEvents.add(new NoteEvent(
                newTs,
                e.instrumentType,
                e.midiNote,
                e.drumKey,
                e.velocity,
                newDur,
                e.colorRGB,
                e.heightPx
            ));
        }

        events.clear();
        events.addAll(newEvents);

        // durationMs 갱신
        long max = 0;
        for (NoteEvent e : events) {
            long end = e.timestampMs + e.durationMs;
            if (end > max) max = end;
        }
        durationMs = max;
    }

}

