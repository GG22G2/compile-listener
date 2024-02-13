package hsb.compile.ui;

import hsb.compile.service.PortPeer;

import javax.swing.*;

/**
 * @author hsb
 * @date 2024/2/13 12:02
 */
public interface PortBtnClickCallback {


    public void onClick(JButton button,int pid, PortPeer portPeer);

}
