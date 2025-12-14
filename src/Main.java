import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        // Initialize custom fonts
        FontManager.init();
        
        // Set up dark theme for dialogs
        setupDarkTheme();
        
        javax.swing.SwingUtilities.invokeLater(() -> {
            new SketchJamApp();
        });
    }
    
    private static void setupDarkTheme() {
        Color bgColor = new Color(0x38, 0x38, 0x38);
        Color fgColor = Color.WHITE;
        Color buttonBg = new Color(0x50, 0x50, 0x50);
        Color selectionBg = new Color(0x60, 0x60, 0x60);
        
        UIManager.put("OptionPane.background", bgColor);
        UIManager.put("OptionPane.messageForeground", fgColor);
        UIManager.put("Panel.background", bgColor);
        UIManager.put("Panel.foreground", fgColor);
        
        UIManager.put("Button.background", buttonBg);
        UIManager.put("Button.foreground", fgColor);
        UIManager.put("Button.select", selectionBg);
        UIManager.put("Button.focus", selectionBg);
        
        UIManager.put("Label.foreground", fgColor);
        UIManager.put("Label.background", bgColor);
        
        UIManager.put("TextField.background", new Color(0x48, 0x48, 0x48));
        UIManager.put("TextField.foreground", fgColor);
        UIManager.put("TextField.caretForeground", fgColor);
        
        UIManager.put("ComboBox.background", buttonBg);
        UIManager.put("ComboBox.foreground", fgColor);
        UIManager.put("ComboBox.selectionBackground", selectionBg);
        UIManager.put("ComboBox.selectionForeground", fgColor);
        
        UIManager.put("List.background", bgColor);
        UIManager.put("List.foreground", fgColor);
        UIManager.put("List.selectionBackground", selectionBg);
        UIManager.put("List.selectionForeground", fgColor);
        
        UIManager.put("ScrollPane.background", bgColor);
        UIManager.put("Viewport.background", bgColor);
        
        UIManager.put("FileChooser.background", bgColor);
        UIManager.put("FileChooser.foreground", fgColor);
        UIManager.put("FileChooser.listViewBackground", bgColor);
        
        // File chooser specific
        UIManager.put("FileView.directoryIcon", null);
        UIManager.put("FileView.fileIcon", null);
        
        // Try to use the custom font for dialogs
        try {
            Font dialogFont = FontManager.getRegular(12);
            if (dialogFont != null) {
                UIManager.put("OptionPane.messageFont", dialogFont);
                UIManager.put("OptionPane.buttonFont", dialogFont);
                UIManager.put("Button.font", dialogFont);
                UIManager.put("Label.font", dialogFont);
                UIManager.put("TextField.font", dialogFont);
                UIManager.put("ComboBox.font", dialogFont);
                UIManager.put("List.font", dialogFont);
            }
        } catch (Exception e) {
            // Ignore font errors
        }
    }
}

