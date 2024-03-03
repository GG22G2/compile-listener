package hsb.compile;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;

import com.intellij.openapi.project.Project;
import hsb.compile.service.RunningSpringbootManager;
import hsb.compile.service.SocketService;

/**
 * @author hsb
 * @date 2024/2/8 20:30
 */
public class MyCompilationStatusListener implements CompilationStatusListener {

    Project project;

    boolean change =false;

    public MyCompilationStatusListener(Project project) {
        this.project = project;
    }

    /**
     *
     *
     * */
    @Override
    public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        if (errors>0){
            System.out.println("编译失败");
            return;
        }
        // 编译完成后的代码
        //todo 判断是不是有编译错误
        printMsg(change);
        change=false;
    }

    @Override
    public void automakeCompilationFinished(int errors, int warnings, CompileContext compileContext) {
        // 自动编译完成后的代码
        printMsg(change);
        change=false;
    }

    @Override
    public void fileGenerated(String outputRoot, String relativePath) {
        change=true;
        // 文件生成后的代码
        System.out.println("fileGenerated");

    }

    private void printMsg(boolean change){
        if (change){
            System.out.println("编译完成");
        }else {
            System.out.println("编译完成，但可能没有文件改变");
        }
        SocketService service = ApplicationManager.getApplication().getService(SocketService.class);
        //todo 这里应该改成进程pid

        RunningSpringbootManager springbootManager = project.getService(RunningSpringbootManager.class);
        int[] pids= springbootManager.getAllPid();
        for (int pid : pids) {
            service.send(pid);
        }

    }


    public void dispose() {
        // 断开消息总线连接
      //  connection.disconnect();
    }
}