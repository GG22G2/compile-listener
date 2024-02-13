package hsb.compile.service;

import com.intellij.openapi.project.Project;
import hsb.compile.TaskTimeLine;
import hsb.compile.springboot.SpringBootPortForwardingProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author 胡帅博
 * @date 2024/2/13 11:22
 */
public class RunningSpringBootProject {

    Project project;
    TaskTimeLine taskTimeLine;
    public int pid;  //进程pid
    public String mainClass; //启动类
    public String name; //运行配置的名称
    public List<PortPeer> portPeers; //检测到的端口以及默认分配的端口

    public AtomicBoolean stop = new AtomicBoolean(false);
    private List<SpringBootPortForwardingProxy> nettyProxy = new ArrayList<>();


    public void createNettyProxy(PortPeer portPeer) throws InterruptedException {
        SpringBootPortForwardingProxy portForward = new SpringBootPortForwardingProxy(portPeer.proxyPort, "localhost", portPeer.realPort, taskTimeLine, project, stop);
        nettyProxy.add(portForward);
        portForward.run();
    }


    public RunningSpringBootProject(Project project, TaskTimeLine taskTimeLine, int pid, String name, String mainClass, List<PortPeer> portPeers) {
        this.project = project;
        this.taskTimeLine = taskTimeLine;
        this.pid = pid;
        this.name = name;
        this.mainClass = mainClass;
        this.portPeers = portPeers;
    }


    public void close() {
        stop.set(true);
        if (!nettyProxy.isEmpty()) {
            for (SpringBootPortForwardingProxy springBootPortForwardingProxy : nettyProxy) {
                springBootPortForwardingProxy.close();
            }
        }
        nettyProxy.clear();
    }

}
