package hsb.compile.springboot;

/**
 * @author hsb
 * @date 2024/2/10 0:55
 */

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import hsb.compile.TaskTimeLine;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


public class SpringBootPortForwardingProxy {
    private static final Logger LOG = Logger.getInstance(SpringBootPortForwardingProxy.class);
    Project project;
    TaskTimeLine taskTimeLine;

    private final int localPort;
    private final String remoteHost;
    private final int remotePort;
    Thread resumeThread = null;
    AtomicBoolean stop;
    EventLoopGroup bossGroup = new NioEventLoopGroup();
    EventLoopGroup workerGroup = new NioEventLoopGroup(1);
    boolean close = false;


    public SpringBootPortForwardingProxy(int localPort, String remoteHost, int remotePort, TaskTimeLine taskTimeLine, Project project, AtomicBoolean stop) {
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.taskTimeLine = taskTimeLine;
        this.project = project;
        this.stop = stop;
    }

    public synchronized void close() {
        close = true;
        if (resumeThread != null) {
            resumeThread.interrupt();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }


    public synchronized void run() throws InterruptedException {
        if (close) {
            return;
        }
        System.out.println("开始运行netty服务");
        ServerBootstrap b = new ServerBootstrap();
        ForwardingHandler forwardingHandler = new ForwardingHandler(remoteHost, remotePort);
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.AUTO_READ, false) //设置为不自动读取，等到和服务器端成功建立连接后在设置为自动读
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new ByteArrayDecoder());
                        p.addLast(new ByteArrayEncoder());
                        p.addLast(forwardingHandler);
                    }
                });

        ChannelFuture server = b.bind(localPort).sync();
        server.channel().closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        });


    }

    @ChannelHandler.Sharable
    class ForwardingHandler extends ChannelInboundHandlerAdapter {
        private final String remoteHost;
        private final int remotePort;

        Map<Channel, Channel> peerConnect = new ConcurrentHashMap<>(8);

        List<Channel> waitStartRead = new ArrayList<>();


        final Object connectInitLock = new Object();


        public ForwardingHandler(String remoteHost, int remotePort) {
            this.remoteHost = remoteHost;
            this.remotePort = remotePort;
            System.out.println("我被创建了");
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws InterruptedException {

            LOG.info("client: channelActive");
            final Channel clientChannel = ctx.channel();

            synchronized (connectInitLock) {
                System.out.println("client: channelActive");
                //先判断是否需要重新编译，需要重新编译的话等待，重新连接练上来，获取到端口号
                int state = taskTimeLine.hasFileModify();
                if (state == TaskTimeLine.NEED_COMPILE) {
                    taskTimeLine.changeState(TaskTimeLine.COMPILING);

                    ApplicationManager.getApplication().invokeLater(() -> {
                        //编译项目，make是增量编译
                        CompilerManager compilerManager = CompilerManager.getInstance(project);
                        compilerManager.make((aborted, errors, warnings, compileContext) -> {
                            LOG.info("重新编译完成");
                            taskTimeLine.changeState(TaskTimeLine.UNCERTAIN);
                            //编译完成后，再等待一秒，开始建立连接

                            resumeConnectAfterCompile();
                        });
                    });

                    LOG.warn("接收到新请求，需要重新编译");
                    waitStartRead.add(clientChannel);

                } else if (state == TaskTimeLine.NOT_CHANGE) {
                    LOG.warn("接收到新请求，和远端建立连接");
                    Bootstrap  config = createBootstrap(clientChannel);
                    connectServer(config, clientChannel);
                } else if (state == TaskTimeLine.COMPILING) {
                    LOG.warn("接收到新请求，正在编译，放入等待队列");
                    waitStartRead.add(clientChannel);//todo 每次的waitStartRead对象都不一样？？？？ 什么鬼
                    LOG.warn("size:"+waitStartRead.size());
                }
            }
        }

        public void resumeConnectAfterCompile() {
            resumeThread = new Thread(() -> {
                synchronized (connectInitLock) {
                    try {
                        Thread.sleep(1700);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    //先让第一个连接上，然后一次连接后续的
                    if (!waitStartRead.isEmpty()) {
                        final Channel channel = waitStartRead.get(0);
                        Bootstrap config = createBootstrap(channel);
                        LOG.info("重编译后，开始恢复连接");

                        ChannelFuture result = null;
                        while (!stop.get()) {
                            try {
                                Thread.sleep(200);
                                result = config.connect(remoteHost, remotePort).sync();
                            } catch (Exception e) {
                                LOG.error("重编译后，连接失败");
                            } finally {
                                if (result != null) {
                                    LOG.warn("重编译后，连接成功");
                                    break;
                                }
                            }
                        }

                        if (!stop.get()) {
                            peerConnect.put(channel, result.channel());
                            channel.config().setAutoRead(true);
                            for (int i = 1; i < waitStartRead.size(); i++) {
                                Channel temp = waitStartRead.get(i);
                                config = createBootstrap(temp);
                                connectServer(config, temp);
                            }
                        }else{
                            //即使连接失败也开启自动读
                            for (int i = 1; i < waitStartRead.size(); i++) {
                                Channel temp = waitStartRead.get(i);
                                temp.config().setAutoRead(true);
                            }
                        }

                    }
                    waitStartRead.clear();
                }
            });
            resumeThread.start();
        }

        public Bootstrap createBootstrap(Channel clientChannel) {
            Bootstrap config = new Bootstrap();

            config.group(clientChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new ByteArrayDecoder(), new ByteArrayEncoder(), new OutboundHandler(clientChannel));
                        }
                    });

            return config;
        }

        public void connectServer(Bootstrap config, Channel clientChannel) {
            ChannelFuture f = config.connect(remoteHost, remotePort);
            f.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        Channel remoteServerChannel = f.channel();
                        peerConnect.put(clientChannel, remoteServerChannel);
                        clientChannel.config().setAutoRead(true);
                        // clientChannel.read();
                    } else {
                        clientChannel.close();
                    }
                }
            });
        }


        @Override
        public void channelRead(final ChannelHandlerContext ctx, Object msg) {
            Channel remoteServerChannel = peerConnect.get(ctx.channel());

            if (remoteServerChannel.isActive()) {
                remoteServerChannel.writeAndFlush(msg);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            LOG.info("client: channelInactive");
            Channel remoteServerChannel = peerConnect.get(ctx.channel());
            if (remoteServerChannel != null) {
                closeOnFlush(remoteServerChannel);
            }

        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            closeOnFlush(ctx.channel());
        }

        static void closeOnFlush(Channel ch) {
            if (ch.isActive()) {
                ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    static class OutboundHandler extends ChannelInboundHandlerAdapter {
        private final Channel clientChannel;

        public OutboundHandler(Channel clientChannel) {
            this.clientChannel = clientChannel;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            clientChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    if (future.isSuccess()) {
                        ctx.channel().read();
                    } else {
                        future.channel().close();
                    }
                }
            });
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            LOG.info("server: channelActive");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            LOG.info("server: channelInactive");
            ForwardingHandler.closeOnFlush(clientChannel);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ForwardingHandler.closeOnFlush(ctx.channel());
        }
    }

}