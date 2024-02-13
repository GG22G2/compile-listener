package hsb.compile;

import com.intellij.openapi.project.Project;
import hsb.compile.springboot.SpringBootPortForwardingProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author 胡帅博
 * @date 2024/2/12 18:40
 */
public class SpringbootProjectPotProxy {

    List<SpringBootPortForwardingProxy> proxies = new ArrayList<>(2);



    public SpringbootProjectPotProxy(int localPort, String remoteHost, int remotePort, TaskTimeLine taskTimeLine, Project project, AtomicBoolean stop) {
//        this.localPort = localPort;
//        this.remoteHost = remoteHost;
//        this.remotePort = remotePort;
//        this.taskTimeLine = taskTimeLine;
//        this.project = project;
//        this.stop = stop;
    }


}
