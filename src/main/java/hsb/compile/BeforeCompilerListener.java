package hsb.compile;

import com.intellij.openapi.compiler.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author 胡帅博
 * @date 2024/2/8 12:39
 */
public class BeforeCompilerListener implements CompileTask {


    @Override
    public boolean execute(@NotNull CompileContext context) {
        System.out.println("编译之前");
        return true;
    }
}
