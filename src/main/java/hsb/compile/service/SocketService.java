package hsb.compile.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author 胡帅博
 * @date 2024/2/9 1:12
 */

@Service
public final class SocketService implements Disposable {

    ServerSocket serverSocket;

    int port = 60012;
    Thread acceptThread;

    Map<String, Socket> registerProject = new ConcurrentHashMap<>();

    public SocketService() {
        try {
            serverSocket = new ServerSocket(port);

            // 可以在一个新的线程中运行您的 Socket 服务，以避免阻塞 UI 线程
            acceptThread = new Thread(() -> {
                System.out.println("开始监听");
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket socket = serverSocket.accept();
                        String projectName = readProjectName(socket);
                        if (projectName == null) {
                            socket.close();
                        } else {
                            Socket oldSocket = registerProject.get(projectName);
                            if (oldSocket != null) {
                                oldSocket.close();
                            }
                            registerProject.put(projectName, socket);
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

    private String readProjectName(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            return reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void send(String projectName) {
        //发送后就认为客户端会重新编译，链接也肯定会断开，所以服务器端主动断开
        try(Socket socket = registerProject.remove(projectName)) {
            if (socket == null) {
                return;
            }
            if (socket.isClosed()) {
                registerProject.remove(projectName);
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
