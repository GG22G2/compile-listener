package hsb.compile.springboot;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.autoimport.FileChangeListenerBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.util.messages.MessageBusConnection;
import hsb.compile.TaskTimeLine;
import hsb.compile.demo.MyRunConfigurationExtension;
import hsb.compile.service.PortPeer;
import hsb.compile.service.RunningSpringBootProject;
import hsb.compile.service.RunningSpringbootManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author hsb
 * @date 2024/2/11 13:48
 */
public class SpringBootDevtool {

    private static final Logger LOG = Logger.getInstance(SpringBootDevtool.class);

    Project project;
    MessageBusConnection connection;
    TaskTimeLine taskTimeLine = new TaskTimeLine();


    public SpringBootDevtool(Project project, MessageBusConnection connection) {
        this.project = project;
        this.connection = connection;
    }


    public void listener() {
        //todo 现在的监听粒度是以Project为单位，但是一个Project可能启动多个Springboot项目，所以taskTimeLine应该是和启动项目绑定的
        //监听文件保存事件，和文件编译事件的时间节点，查询是否有修改的时候，就查询最后一次编译事件后是否有文件保存或者是否有
        //判断当前是否有被修改文件存在，
        //监听文件被保存到磁盘，会监听到所有项目中文件的变动，所以需要区分项目
        connection.subscribe(VirtualFileManager.VFS_CHANGES, new FileChangeListenerBase() {
            @Override
            protected boolean isRelevant(String path) {
                return true;
            }

            @Override
            protected void updateFile(VirtualFile file, VFileEvent event) {
                // 这个事件不区分项目，所以这用这种方式判断是否是本项目的文件
                boolean isInSourceContent = ProjectRootManager.getInstance(project).getFileIndex().isInSourceContent(file);
                LOG.info("文件被创建/修改：" + file.getName() + ",isInSourceContent:" + isInSourceContent);
                if (isInSourceContent) {
                    taskTimeLine.fileTreeChange();
                }
            }

            @Override
            protected void deleteFile(VirtualFile file, VFileEvent event) {
                boolean isInSourceContent = ProjectRootManager.getInstance(project).getFileIndex().isInSourceContent(file);
                LOG.info("文件被删除：" + file.getName());
                if (isInSourceContent) {
                    taskTimeLine.fileTreeChange();
                }
            }

            @Override
            protected void apply() {

            }
        });


        CompilerManager.getInstance(project).addBeforeTask(new CompileTask() {
            @Override
            public boolean execute(@NotNull CompileContext context) {
                taskTimeLine.compiled();
                LOG.warn("编译之前，所有未保存文件被保存一次，记录编译时刻");
                return true;
            }
        });





        //这个是项目级别的通知
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
            @Override
            public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
                LOG.info("执行命令：" + executorId + ":" + env.getRunProfile().getName());
                if (!(executorId.equals("Run")||executorId.equals("Debug"))) {
                    return;
                }



                RunProfile runProfile = env.getRunProfile();
                Project project = env.getProject();

                if (runProfile instanceof ApplicationConfiguration appConfig) {
                    if (handler instanceof KillableProcessHandler p) {


                        Boolean userData = appConfig.getUserData(MyRunConfigurationExtension.SPRINGBOOT);
                        if (userData==null){
                            return;
                        }


                        Process process = p.getProcess();
                        int processID = OSProcessUtil.getProcessID(process);
                        System.out.println("启动进程pid:" + processID);

                        ApplicationManager.getApplication().invokeLater(() -> {
                            // 使用 PSI 框架查找 main 类
                            PsiClass mainClass = appConfig.getMainClass();
                            if (mainClass == null) {
                                return;
                            }

                            LOG.warn("识别到springboot项目启动，开启端口转发");

                            RunningSpringBootProject runningSpringBootProject = new RunningSpringBootProject(project, taskTimeLine, processID, appConfig.getName(), appConfig.getMainClassName(), null);
                            RunningSpringbootManager service = project.getService(RunningSpringbootManager.class);

                            service.addProject(runningSpringBootProject);
                            AtomicBoolean stop = runningSpringBootProject.stop;


                            //getProcessListenerPort(processID, stop);
                            try {
                                handler.addProcessListener(new ProcessListener() {
                                    @Override
                                    public void processTerminated(@NotNull ProcessEvent event) {
                                        LOG.warn("运行的springboot项目结束了，销毁netty监听");

                                        service.closeProject(processID);
                                    }
                                });
                            } catch (Exception e) {
                                LOG.error("netty启动失败", e);
                            }
                        });


                    }


                }
            }

        });


    }


    /**
     * 获取进程监听的端口列表
     */
//    private void getProcessListenerPort(int processID, AtomicBoolean stopSignal) {
//
//        //获取当前启动的springboot项目监听的端口列表
//        new Thread(() -> {
//            try {
//                Thread.sleep(1000*17);
//            } catch (InterruptedException e) {
//
//            }
//            int findCount = 0;
//            List<Integer> ports = new ArrayList<>();
//            while (!stopSignal.get() && findCount < 2) {
//                try {
//                    ports.clear();
//                    Thread.sleep(1000);
//                    // 使用系统命令获取监听的端口
//                    GeneralCommandLine cmd = new GeneralCommandLine("netstat", "-ano");
//
//                    // 执行命令获取输出结果
//                    ProcessOutput output = ExecUtil.execAndGetOutput(cmd);
//                    List<String> stdoutLines = output.getStdoutLines();
//                    String pid = String.valueOf(processID);
//                    for (String stdoutLine : stdoutLines) {
//                        String lineInfo = stdoutLine.trim().toLowerCase();
//
//                        if (lineInfo.startsWith("tcp")) {
//                            String[] split = lineInfo.split("\s+");
//                            if (split[4].equals(pid) && split[0].equals("tcp") && split[3].equals("listening")) {
//                                try {
//                                    String listenPort = split[1].split(":")[1];
//                                    System.out.println("发现一个应用的监听端口:" + listenPort);
//                                    ports.add(Integer.valueOf(listenPort));
//                                } catch (Exception e) {
//
//                                }
//
//                            }
//                        }
//                    }
//                    if (!ports.isEmpty()) {
//                        findCount++;
//                    }
//                } catch (Exception e) {
//
//                }
//            }
//
//
//            if (!stopSignal.get() && !ports.isEmpty()) {
//                RunningSpringbootManager service = project.getService(RunningSpringbootManager.class);
//
//                List<PortPeer> portPeers = new ArrayList<>(ports.size());
//                for (Integer port : ports) {
//                    portPeers.add(new PortPeer(port, 8080));
//                }
//                service.addProjectPortPeer(processID,portPeers);
//            }
//
//
//        }).start();
//
//
//    }

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
