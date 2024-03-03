package hsb.compile.demo;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.util.PathsList;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author 胡帅博
 * @date 2024/3/3 11:16
 *
 * 运行配置，用于检测是不是springboot项目，对于springboot项目，添加一个jar到 classpath
 *
 */
public class MyRunConfigurationExtension extends RunConfigurationExtension {

    public static final Key<Boolean> SPRINGBOOT = new Key<>("SPRINGBOOT_PROJECT");

    @Override
    public <T extends RunConfigurationBase<?>> void updateJavaParameters(@NotNull T configuration, @NotNull JavaParameters params, @Nullable RunnerSettings runnerSettings) throws ExecutionException {
        if (configuration instanceof ApplicationConfiguration appConfig){
            // 使用 PSI 框架查找 main 类
            PsiClass mainClass = appConfig.getMainClass();
            if (mainClass == null) {
                return;
            }

            // 检查 main 类是否有 @SpringBootApplication 注解
            if (hasSpringBootApplicationAnnotation(mainClass)) {
                PathsList classPath = params.getClassPath();
                classPath.add("G:\\kaifa_environment\\code\\java\\starter_demo\\target\\port-1.0.jar");
                configuration.putUserData(SPRINGBOOT,true);
            }
        }
    }

    @Override
    public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
        return configuration instanceof JavaRunConfigurationBase;
    }

    private boolean hasSpringBootApplicationAnnotation(PsiClass psiClass) {
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList != null) {
            PsiAnnotation[] annotations = modifierList.getAnnotations();
            for (PsiAnnotation annotation : annotations) {
                String annotationShortName = PsiAnnotationImpl.getAnnotationShortName(annotation.getText());
                // String qualifiedName = annotation.getQualifiedName(); //这个第一次比较慢，
                if ("SpringBootApplication".equals(annotationShortName)) {
                    return true;
                }
            }
        }
        return false;
    }
}

