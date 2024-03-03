package hsb.compile.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author hsb
 * @date 2024/2/9 1:12
 */

@Service
public final class SocketService implements Disposable {

    ServerSocket serverSocket;

    int port = 60012;
    Thread acceptThread;

    Map<Integer, Socket> registerProject = new ConcurrentHashMap<>();

    public SocketService() {
        try {
            serverSocket = new ServerSocket(port);

            // 可以在一个新的线程中运行您的 Socket 服务，以避免阻塞 UI 线程
            acceptThread = new Thread(() -> {
                System.out.println("开始监听");
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket socket = serverSocket.accept();


                        DataInputStream in= new DataInputStream(socket.getInputStream());
                        int type = in.readInt();

                        if (type==1){
                            //更新后的通知socket
                            int pid = in.readInt();

                            Socket oldSocket = registerProject.get(pid);
                            if (oldSocket != null) {
                                oldSocket.close();
                            }
                            registerProject.put(pid, socket);

                        }else if (type==2){
                            //用来发送进程的web端口号
                            int pid = in.readInt();
                            int port = in.readInt();

                            //通知进程对应的端口

                            //ApplicationManager.getApplication().p


                            @NotNull Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
                            for (Project openProject : openProjects) {
                                RunningSpringbootManager service = openProject.getService(RunningSpringbootManager.class);
                                //service.
                                List<PortPeer> portPeers = new ArrayList<>(1);
                                portPeers.add(new PortPeer(port, 8080));
                                service.addProjectPortPeer(pid,portPeers);
                            }
                            socket.close();
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            });
            acceptThread.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public void send(int pid) {
        try{
            Socket socket = registerProject.get(pid);
            if (socket == null) {
                return;
            }
            if (socket.isClosed()) {
                registerProject.remove(pid);
                return;
            }
            //随便写点
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(new byte[1]);
            outputStream.flush();
        } catch (Exception ignored) {

        }
    }


    @Override
    public void dispose() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        acceptThread.interrupt();


        registerProject.values().forEach(socket -> {
            try {
                socket.close();
            } catch (IOException e) {

            }
        });


    }
}
