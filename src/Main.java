import javax.swing.*;

/**
 * Main entry point for SketchJam application.
 */
public class Main {
    
    public static void main(String[] args) {
        // Set system look and feel for native appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default look and feel if system L&F is not available
        }
        
        // Launch application on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            SketchJamApp app = new SketchJamApp();
            app.setVisible(true);
        });
    }
}

