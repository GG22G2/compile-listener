package hsb.compile.service;

/**
 * @author hsb
 * @date 2024/2/13 11:29
 */
public interface Listener {

    public void addProject(RunningSpringBootProject item );
    public void closeProject(int pid);
}
