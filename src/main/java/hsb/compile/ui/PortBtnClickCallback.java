package hsb.compile.ui;

import hsb.compile.service.PortPeer;

import javax.swing.*;

/**
 * @author 胡帅博
 * @date 2024/2/13 12:02
 */
public interface PortBtnClickCallback {


    public void onClick(JButton button,int pid, PortPeer portPeer);

}
