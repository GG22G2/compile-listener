package hsb.compile.demo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author 胡帅博
 * @date 2024/2/9 0:03
 */
public class CompilerDemo {

    public static void make(Project project){
        PsiDocumentManager instance = PsiDocumentManager.getInstance(project);

      //  boolean b = instance.hasUncommitedDocuments();


       // PsiDocumentManager.getInstance(project).commitAllDocuments();




     //   FileDocumentManager.getInstance().saveAllDocuments();



        CompilerManager compilerManager = CompilerManager.getInstance(project);
        new Thread(()->{
            try {
                for(int i = 0; i < 10; i++) {
                    Thread.sleep(1000 * 5);
                    System.out.println("调用编译");

                    ApplicationManager.getApplication().invokeLater(()->{
                        //重新build项目，也就是完整的从头编译一次
//                    compilerManager.rebuild(new CompileStatusNotification() {
//                        @Override
//                        public void finished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
//                            System.out.println("触发重新构建，监听到结束");
//                        }
//                    });

                        //编译项目，make是增量编译，或许改动的编译速度比较快
                        compilerManager.make(new CompileStatusNotification() {
                            @Override
                            public void finished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
                                System.out.println("make完成");
                            }
                        });

                        //按照模块编译，但感觉速度上和调用public abstract void make(@Nullable CompileStatusNotification callback);没啥区别
//                        Module[] modules = ModuleManager.getInstance(project).getModules();
//                        for (Module module : modules) {
//                            if ("ruoyi-admin".equals(module.getName())){
//                                CompileScope moduleGroupCompileScope = compilerManager.createModuleGroupCompileScope(project, modules, false);
//                                compilerManager.make(moduleGroupCompileScope,new CompileStatusNotification() {
//                                    @Override
//                                    public void finished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
//                                        System.out.println("make完成");
//                                    }
//                                });
//                            }
//                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();
    }

}
