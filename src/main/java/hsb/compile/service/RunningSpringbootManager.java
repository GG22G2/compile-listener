package hsb.compile.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author hsb
 * @date 2024/2/13 11:18
 */
@Service(Service.Level.PROJECT)
public final class RunningSpringbootManager implements Disposable {


    Map<Integer, RunningSpringBootProject> springBootProject = new HashMap<>(4);


    private final List<Listener> listeners = new ArrayList<>();


    public void addProject(RunningSpringBootProject item) {
        springBootProject.put(item.pid, item);
    }

    public void addProjectPortPeer(int pid, List<PortPeer> portPeers) {
        RunningSpringBootProject runningSpringBootProject = springBootProject.get(pid);
        if (runningSpringBootProject==null){
            return;
        }
        runningSpringBootProject.portPeers = portPeers;
        for (Listener listener : listeners) {
            listener.addProject(runningSpringBootProject);
        }
    }

    public void closeProject(int pid) {
        RunningSpringBootProject runningSpringBootProject = springBootProject.remove(pid);
        runningSpringBootProject.close();
        for (Listener listener : listeners) {
            listener.closeProject(pid);
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }


    @Override
    public void dispose() {

    }


    public void startNetty(int pid, PortPeer portPeer) throws InterruptedException {
        RunningSpringBootProject runningSpringBootProject = springBootProject.get(pid);
        if (runningSpringBootProject!=null){
            runningSpringBootProject.createNettyProxy(portPeer);
        }
    }
}
