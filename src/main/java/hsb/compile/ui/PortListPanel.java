package hsb.compile.ui;

import com.intellij.ui.PortField;
import hsb.compile.service.PortPeer;
import hsb.compile.service.RunningSpringBootProject;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author 胡帅博
 * @date 2024/2/12 21:18
 */
public class PortListPanel extends JPanel {


    Map<Integer, List<JComponent>> group = new HashMap<>();


    PortBtnClickCallback callback;


    public void setClickListener(PortBtnClickCallback callback) {
        this.callback = callback;
    }

    public PortListPanel() {
        BoxLayout boxLayout = new BoxLayout(this, BoxLayout.Y_AXIS);
        setLayout(boxLayout);
    }


    public void addProject(RunningSpringBootProject project) {
        List<JComponent> components = new ArrayList<>(2);
        int pid = project.pid;
        String mainClass = project.mainClass;

        components.add(addProjectName(project.name,mainClass));

        List<PortPeer> portPeers = project.portPeers;

        for (PortPeer portPeer : portPeers) {
            components.add(addProjectPortMapping(pid, portPeer));
        }
        group.put(pid, components);

        updateUI();
    }

    public void closeProject(int pid) {
        List<JComponent> components = group.remove(pid);
        if (components != null && !components.isEmpty()) {
            for (JComponent component : components) {
                remove(component);
            }
        }
    }

    private JComponent addProjectName(String name, String mainClass) {
        JLabel jLabel = new JLabel(name + "(" + mainClass + ")");
        add(jLabel);
        return jLabel;
    }

    private JComponent addProjectPortMapping(int pid, PortPeer portPeer) {
        JPanel portInfo = new JPanel(new FlowLayout(FlowLayout.LEFT));

        PortField portField = new PortField(portPeer.proxyPort);
        portField.enableInputMethods(false);
        portInfo.add(portField);
        portInfo.add(new JLabel("->"));
        portInfo.add(new JLabel("" + portPeer.realPort));
        JButton startListenerBtn = new JButton("开始");

        startListenerBtn.addActionListener(e -> {

            JButton source = (JButton) e.getSource();
            //开启监听
            if (callback != null) {
                portPeer.proxyPort = portField.getNumber();
                callback.onClick(source, pid, portPeer);
            }
        });
        portInfo.add(startListenerBtn);

        portInfo.setMaximumSize(new Dimension(Integer.MAX_VALUE, portInfo.getPreferredSize().height));
        portInfo.setEnabled(false);

        add(portInfo);
        return portInfo;
    }

}
