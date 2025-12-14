public class Main {
    public static void main(String[] args) {
        // Initialize custom fonts
        FontManager.init();
        
        javax.swing.SwingUtilities.invokeLater(() -> {
            new SketchJamApp();
        });
    }
}

