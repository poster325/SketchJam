import java.util.ArrayList;
import java.util.List;

/**
 * Manages undo/redo operations with a fixed history size
 * Only tracks manipulation actions (move, resize), not selections
 */
public class UndoRedoManager {
    
    private final int maxHistory;
    private List<CanvasState> undoStack = new ArrayList<>();
    private List<CanvasState> redoStack = new ArrayList<>();
    private SketchCanvas canvas;
    
    public UndoRedoManager(int maxHistory) {
        this.maxHistory = maxHistory;
    }
    
    public void setCanvas(SketchCanvas canvas) {
        this.canvas = canvas;
    }
    
    /**
     * Save the current state before a manipulation action
     */
    public void saveState(List<DrawableElement> elements) {
        // Copy all elements to preserve state
        List<DrawableElement> clonedElements = new ArrayList<>();
        for (DrawableElement element : elements) {
            clonedElements.add(element.copy());
        }
        
        CanvasState state = new CanvasState(clonedElements);
        undoStack.add(state);
        
        // Limit history size
        if (undoStack.size() > maxHistory) {
            undoStack.remove(0);
        }
        
        // Clear redo stack when new action is performed
        redoStack.clear();
        
        // Mark project as having unsaved changes
        FileManager.getInstance().markUnsaved();
    }
    
    /**
     * Undo the last manipulation action
     */
    public void undo() {
        if (canvas == null || undoStack.isEmpty()) {
            return;
        }
        
        // Save current state to redo stack
        List<DrawableElement> currentElements = canvas.getElements();
        List<DrawableElement> clonedCurrent = new ArrayList<>();
        for (DrawableElement element : currentElements) {
            clonedCurrent.add(element.copy());
        }
        redoStack.add(new CanvasState(clonedCurrent));
        
        // Restore previous state
        CanvasState previousState = undoStack.remove(undoStack.size() - 1);
        canvas.setElements(previousState.getElements());
    }
    
    /**
     * Redo the last undone action
     */
    public void redo() {
        if (canvas == null || redoStack.isEmpty()) {
            return;
        }
        
        // Save current state to undo stack
        List<DrawableElement> currentElements = canvas.getElements();
        List<DrawableElement> clonedCurrent = new ArrayList<>();
        for (DrawableElement element : currentElements) {
            clonedCurrent.add(element.copy());
        }
        undoStack.add(new CanvasState(clonedCurrent));
        
        // Restore redo state
        CanvasState redoState = redoStack.remove(redoStack.size() - 1);
        canvas.setElements(redoState.getElements());
    }
    
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }
    
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
    
    public int getUndoCount() {
        return undoStack.size();
    }
    
    public int getRedoCount() {
        return redoStack.size();
    }
    
    /**
     * Represents a snapshot of the canvas state
     */
    private static class CanvasState {
        private final List<DrawableElement> elements;
        
        public CanvasState(List<DrawableElement> elements) {
            this.elements = elements;
        }
        
        public List<DrawableElement> getElements() {
            return new ArrayList<>(elements);
        }
    }
}

