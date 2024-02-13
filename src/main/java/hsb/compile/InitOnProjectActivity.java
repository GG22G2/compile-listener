package hsb.compile;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerTopics;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.util.messages.MessageBusConnection;
import hsb.compile.service.SocketService;
import hsb.compile.springboot.SpringBootDevtool;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * @author hsb
 * @date 2024/2/8 20:28
 * <p>
 * 这个
 */
public class InitOnProjectActivity implements ProjectActivity, Disposable {
    /**
     * 重新加载代码优化流程优化， 自动检测springboot的web端口，并开启一个新的代理端口
     * <p>
     * 代理端口负则接收请求，并将请求转发给springboot的web端口
     * <p>
     * 每次接收到请求的时候都判断一下是否项目重新编译了，如果项目重新编译了则先阻塞请求，并重启项目，并且重启期间的所有请求都按顺序排队等待。
     * <p>
     * 项目重启完成后在这些请求重新转发给springboot
     * <p>
     * 这样可以实现请求过来的时候热加载
     */
    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {



        MessageBusConnection connection = project.getMessageBus().connect();
        //这个是项目相关的，每个项目注册一次
        connection.subscribe(CompilerTopics.COMPILATION_STATUS, new MyCompilationStatusListener(project));

        //初始化一下
        SocketService service = ApplicationManager.getApplication().getService(SocketService.class);


        SpringBootDevtool devtool = new SpringBootDevtool(project, connection);
        devtool.listener();


//        connection.subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
//        });
        //监听项目关闭，做一些事情，比如释放资源
//        ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerListener() {
//            @Override
//            public void projectClosing(@NotNull Project project) {
//                System.out.println("项目正在关闭中");
//            }
//        });

        return null;
    }

    @Override
    public void dispose() {
        System.out.println("项目正在关闭中");
    }
}
