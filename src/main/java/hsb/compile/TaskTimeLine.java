package hsb.compile;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;

/**
 * @author hsb
 * @date 2024/2/9 23:03
 */
public class TaskTimeLine {

    private volatile long lastCompilerStartTime = 0;
    private volatile long lastFileSaveTime = 0;

    private volatile int state = NOT_CHANGE;

    public boolean hasFileSaveAfterCompiler() {
        if (lastFileSaveTime > lastCompilerStartTime) {
            return true;
        }
        return false;
    }


    public void compiled() {
        lastCompilerStartTime = System.nanoTime();
    }

    public void fileTreeChange() {
        lastFileSaveTime = System.nanoTime();
    }


    public final static int UNCERTAIN = 0;
    public final static int NOT_CHANGE = 1;
    public final static int NEED_COMPILE = 2;

    public final static int COMPILING = 3;


    public  void changeState(int newState) {
        this.state = newState;
    }

    public int hasFileModify() {
        if (state == COMPILING) {
            return COMPILING;
        }
        Document[] unsavedDocuments = FileDocumentManager.getInstance().getUnsavedDocuments();
        boolean unSaveFile = unsavedDocuments.length > 0;


        boolean needCompile = hasFileSaveAfterCompiler();

        if (needCompile || unSaveFile) {
            return NEED_COMPILE;
        }

        return NOT_CHANGE;
    }

}
