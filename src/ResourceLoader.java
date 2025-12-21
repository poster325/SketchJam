import java.io.File;
import java.net.URISyntaxException;

/**
 * Utility class to load resources from the correct location
 * whether running from IDE, JAR, or jpackage executable.
 */
public class ResourceLoader {
    
    private static File baseDir = null;
    
    /**
     * Get the base directory where resources are located.
     * 
     * Directory structures supported:
     * 1. IDE/Development: project_root/out/ -> resources in project_root/
     * 2. JAR (run.bat): project_root/SketchJam.jar -> resources in project_root/
     * 3. jpackage: dist/SketchJam/app/SketchJam.jar -> resources in dist/SketchJam/app/
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
                // Running from JAR (packaged app or jpackage)
                // Resources are in the same folder as the JAR
                baseDir = jarLocation.getParentFile();
                
                // Check if we're in a jpackage 'app' folder
                // In that case, resources should be in the 'app' folder alongside the JAR
                System.out.println("JAR location: " + jarLocation.getAbsolutePath());
                System.out.println("Base dir: " + baseDir.getAbsolutePath());
            } else {
                // Running from IDE (classes folder)
                // Go up to project root
                baseDir = jarLocation.getParentFile();
            }
            
            // Verify resources exist, if not try parent
            File symbolsCheck = new File(baseDir, "symbols");
            if (!symbolsCheck.exists()) {
                File parentCheck = new File(baseDir.getParentFile(), "symbols");
                if (parentCheck.exists()) {
                    baseDir = baseDir.getParentFile();
                    System.out.println("Adjusted base dir to parent: " + baseDir.getAbsolutePath());
                }
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






