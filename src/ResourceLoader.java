import java.io.File;
import java.net.URISyntaxException;

/**
 * Utility class to load resources from the correct location
 * whether running from IDE or packaged application.
 */
public class ResourceLoader {
    
    private static File baseDir = null;
    
    /**
     * Get the base directory where resources are located.
     * For packaged apps, this is the 'app' folder.
     * For development, this is the project root.
     */
    public static File getBaseDir() {
        if (baseDir != null) {
            return baseDir;
        }
        
        try {
            // Get the location of this class
            File jarLocation = new File(ResourceLoader.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            
            // If running from JAR, jarLocation is the JAR file
            // If running from classes, jarLocation is the 'out' folder
            
            if (jarLocation.isFile() && jarLocation.getName().endsWith(".jar")) {
                // Running from JAR (packaged app)
                // Resources are in the same folder as the JAR
                baseDir = jarLocation.getParentFile();
            } else {
                // Running from IDE (classes folder)
                // Go up to project root
                baseDir = jarLocation.getParentFile();
            }
        } catch (URISyntaxException e) {
            // Fallback to current directory
            baseDir = new File(".");
        }
        
        return baseDir;
    }
    
    /**
     * Get a resource file by relative path.
     */
    public static File getResource(String relativePath) {
        return new File(getBaseDir(), relativePath);
    }
    
    /**
     * Get the symbols directory.
     */
    public static File getSymbolsDir() {
        return new File(getBaseDir(), "symbols");
    }
    
    /**
     * Get the soundfonts directory.
     */
    public static File getSoundfontsDir() {
        return new File(getBaseDir(), "soundfonts");
    }
    
    /**
     * Get the distortion directory.
     */
    public static File getDistortionDir() {
        return new File(getBaseDir(), "distortion");
    }
    
    /**
     * Get the fonts directory.
     */
    public static File getFontsDir() {
        return new File(getBaseDir(), "fonts");
    }
}





