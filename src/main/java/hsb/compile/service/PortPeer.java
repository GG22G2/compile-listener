package hsb.compile.service;

/**
 * @author 胡帅博
 * @date 2024/2/13 11:21
 */
public class PortPeer {
    //springboot项目的实际端口
    public int realPort;
    //代理访问端口
    public int proxyPort;

    public PortPeer(int realPort, int proxyPort) {
        this.realPort = realPort;
        this.proxyPort = proxyPort;
    }
}
