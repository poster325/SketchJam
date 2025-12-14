import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * SF2 Manager panel for assigning soundfonts to different instruments
 * Drums are filtered separately from melodic instruments
 */
public class SF2Manager extends JPanel {
    
    private static final int ROW_HEIGHT = 25;
    private static final int LABEL_WIDTH = 60;
    private static final int SELECTOR_WIDTH = 290;
    
    // Separate lists for melodic and drum soundfonts
    private List<String> melodicSoundfonts = new ArrayList<>();
    private List<String> drumSoundfonts = new ArrayList<>();
    
    // Current selections (index in respective lists)
    private int pianoSelection = 0;
    private int guitarSelection = 0;
    private int drumsSelection = 0;
    
    // Dropdown state
    private int expandedRow = -1; // Which row's dropdown is expanded (-1 = none)
    private int hoveredOption = -1; // Which option in dropdown is hovered
    
    // Popup for dropdown
    private JPopupMenu dropdownPopup;
    
    public SF2Manager() {
        // Height: Label (25) + Piano row (25) + Guitar row (25) + Drums row (25) = 100
        setPreferredSize(new Dimension(350, ROW_HEIGHT * 4));
        setBackground(new Color(0x38, 0x38, 0x38));
        
        loadAvailableSoundfonts();
        autoAssignSoundfonts();
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = e.getY() / ROW_HEIGHT;
                int x = e.getX();
                
                // Skip label row
                if (row == 0) return;
                
                // Check if clicking on dropdown area
                if (x >= LABEL_WIDTH && x < LABEL_WIDTH + SELECTOR_WIDTH) {
                    showDropdown(row - 1, e); // row-1: 0=piano, 1=guitar, 2=drums
                }
            }
        });
    }
    
    private void loadAvailableSoundfonts() {
        // Save current selections by name
        String currentPiano = pianoSelection < melodicSoundfonts.size() ? melodicSoundfonts.get(pianoSelection) : "(Default)";
        String currentGuitar = guitarSelection < melodicSoundfonts.size() ? melodicSoundfonts.get(guitarSelection) : "(Default)";
        String currentDrums = drumsSelection < drumSoundfonts.size() ? drumSoundfonts.get(drumsSelection) : "(Default)";
        
        melodicSoundfonts.clear();
        drumSoundfonts.clear();
        
        melodicSoundfonts.add("(Default)");
        drumSoundfonts.add("(Default)");
        
        File soundfontsDir = new File("soundfonts");
        if (soundfontsDir.exists() && soundfontsDir.isDirectory()) {
            File[] sf2Files = soundfontsDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".sf2") || name.toLowerCase().endsWith(".sf3"));
            
            if (sf2Files != null) {
                for (File sf2 : sf2Files) {
                    String name = sf2.getName();
                    String nameLower = name.toLowerCase();
                    
                    // Separate drum soundfonts from melodic ones
                    if (nameLower.contains("drum") || nameLower.contains("percussion") || nameLower.contains("kit")) {
                        drumSoundfonts.add(name);
                    } else {
                        melodicSoundfonts.add(name);
                    }
                }
            }
        }
        
        // Restore selections by name (in case indices changed)
        pianoSelection = Math.max(0, melodicSoundfonts.indexOf(currentPiano));
        guitarSelection = Math.max(0, melodicSoundfonts.indexOf(currentGuitar));
        drumsSelection = Math.max(0, drumSoundfonts.indexOf(currentDrums));
    }
    
    /**
     * Refresh the list of available soundfonts (called when dropdown opens)
     */
    public void refreshSoundfonts() {
        loadAvailableSoundfonts();
        repaint();
    }
    
    private void autoAssignSoundfonts() {
        // Auto-assign melodic instruments based on file names
        for (int i = 1; i < melodicSoundfonts.size(); i++) {
            String name = melodicSoundfonts.get(i).toLowerCase();
            
            if (name.contains("piano") && pianoSelection == 0) {
                pianoSelection = i;
            }
            if ((name.contains("guitar") && name.contains("clean")) && guitarSelection == 0) {
                guitarSelection = i;
            } else if (name.contains("guitar") && guitarSelection == 0) {
                guitarSelection = i;
            }
        }
        
        // Auto-assign drums
        for (int i = 1; i < drumSoundfonts.size(); i++) {
            String name = drumSoundfonts.get(i).toLowerCase();
            if (name.contains("drum")) {
                drumsSelection = i;
                break;
            }
        }
    }
    
    private void showDropdown(int instrument, MouseEvent e) {
        // Refresh the list of available soundfonts before showing dropdown
        refreshSoundfonts();
        
        dropdownPopup = new JPopupMenu();
        dropdownPopup.setBackground(new Color(0x40, 0x40, 0x40));
        dropdownPopup.setBorder(BorderFactory.createLineBorder(new Color(0x60, 0x60, 0x60)));
        
        List<String> options;
        int currentSelection;
        
        if (instrument == 2) { // Drums
            options = drumSoundfonts;
            currentSelection = drumsSelection;
        } else { // Piano or Guitar
            options = melodicSoundfonts;
            currentSelection = (instrument == 0) ? pianoSelection : guitarSelection;
        }
        
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            final int inst = instrument;
            
            JMenuItem item = new JMenuItem(options.get(i));
            item.setBackground(i == currentSelection ? new Color(0x60, 0x60, 0x60) : new Color(0x40, 0x40, 0x40));
            item.setForeground(Color.WHITE);
            item.setFont(FontManager.getRegular(10));
            item.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            
            item.addActionListener(ev -> {
                selectOption(inst, index);
            });
            
            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    item.setBackground(new Color(0x55, 0x55, 0x55));
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    item.setBackground(index == currentSelection ? new Color(0x60, 0x60, 0x60) : new Color(0x40, 0x40, 0x40));
                }
            });
            
            dropdownPopup.add(item);
        }
        
        // Show popup below the row
        int popupX = LABEL_WIDTH;
        int popupY = (instrument + 2) * ROW_HEIGHT;
        dropdownPopup.show(this, popupX, popupY);
    }
    
    private void selectOption(int instrument, int optionIndex) {
        switch (instrument) {
            case 0: // Piano
                pianoSelection = optionIndex;
                String pianoFont = optionIndex == 0 ? null : melodicSoundfonts.get(optionIndex);
                SoundManager.getInstance().setPianoSoundfont(pianoFont);
                break;
            case 1: // Guitar
                guitarSelection = optionIndex;
                String guitarFont = optionIndex == 0 ? null : melodicSoundfonts.get(optionIndex);
                SoundManager.getInstance().setGuitarSoundfont(guitarFont);
                break;
            case 2: // Drums
                drumsSelection = optionIndex;
                String drumsFont = optionIndex == 0 ? null : drumSoundfonts.get(optionIndex);
                SoundManager.getInstance().setDrumsSoundfont(drumsFont);
                break;
        }
        repaint();
    }
    
    private String getSelectionText(int instrument) {
        String name;
        switch (instrument) {
            case 0: 
                name = melodicSoundfonts.get(pianoSelection);
                break;
            case 1: 
                name = melodicSoundfonts.get(guitarSelection);
                break;
            case 2: 
                name = drumSoundfonts.get(drumsSelection);
                break;
            default: 
                return "";
        }
        
        // Truncate if too long
        if (name.length() > 35) {
            name = name.substring(0, 32) + "...";
        }
        return name;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        FontMetrics fm;
        
        // Row 0: Label "SF2 MANAGER"
        g2d.setFont(FontManager.getBold(11));
        fm = g2d.getFontMetrics();
        g2d.setColor(Color.WHITE);
        String label = "SF2 MANAGER";
        int labelWidth = fm.stringWidth(label);
        g2d.drawString(label, (350 - labelWidth) / 2, (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2);
        
        // Instrument rows
        String[] instruments = {"Piano", "Guitar", "Drums"};
        g2d.setFont(FontManager.getRegular(10));
        fm = g2d.getFontMetrics();
        
        for (int i = 0; i < 3; i++) {
            int y = (i + 1) * ROW_HEIGHT;
            
            // Instrument label
            g2d.setColor(new Color(0xAA, 0xAA, 0xAA));
            int textY = y + (ROW_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            g2d.drawString(instruments[i] + ":", 5, textY);
            
            // Dropdown area
            int dropX = LABEL_WIDTH;
            int dropWidth = SELECTOR_WIDTH;
            
            // Dropdown background
            g2d.setColor(new Color(0x50, 0x50, 0x50));
            g2d.fillRect(dropX, y + 2, dropWidth, ROW_HEIGHT - 4);
            
            // Dropdown border
            g2d.setColor(new Color(0x30, 0x30, 0x30));
            g2d.drawRect(dropX, y + 2, dropWidth, ROW_HEIGHT - 4);
            
            // Selected text
            g2d.setColor(Color.WHITE);
            String selectedText = getSelectionText(i);
            g2d.drawString(selectedText, dropX + 5, textY);
            
            // Dropdown arrow
            g2d.setColor(new Color(0xAA, 0xAA, 0xAA));
            int arrowX = dropX + dropWidth - 15;
            g2d.drawString("▼", arrowX, textY);
        }
    }
}
