package hsb.compile.window;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import hsb.compile.service.Listener;
import hsb.compile.service.PortPeer;
import hsb.compile.service.RunningSpringBootProject;
import hsb.compile.service.RunningSpringbootManager;
import hsb.compile.ui.PortBtnClickCallback;
import hsb.compile.ui.PortListPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author 胡帅博
 * @date 2024/2/12 21:13
 */
public class SpringbootPortShow implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        //todo 把识别到的端口号都展示出来，让用户手动开启监听
        PortListPanel myJPanel = new PortListPanel();

        RunningSpringbootManager service = project.getService(RunningSpringbootManager.class);

        service.addListener(new Listener() {
            @Override
            public void addProject(RunningSpringBootProject item) {
                myJPanel.addProject(item);
            }

            @Override
            public void closeProject(int pid) {
                myJPanel.closeProject(pid);
            }
        });
        myJPanel.setClickListener(new PortBtnClickCallback() {
            @Override
            public void onClick(JButton btn, int pid, PortPeer portPeer) {

                try {
                    service.startNetty(pid,portPeer);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }finally {
                    //开启一组监听
                    btn.setText("监听中");
                    btn.setEnabled(false);
                }



            }
        });

        Content content = toolWindow.getContentManager().getFactory().createContent(myJPanel, "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
