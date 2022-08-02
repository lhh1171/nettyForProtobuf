package server;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import pojo.TransMessageData;
import server.handler.TransServerHandler;
import server.handler.TransServerHeartBeatHandler;

public class TransServer {
    public void bind(int port) {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap
                    .group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline()
                                    //消息头定长
                                    .addLast(new ProtobufVarint32FrameDecoder())
                                    //解码指定的消息类型
                                    .addLast(new ProtobufDecoder(TransMessageData.MessageData.getDefaultInstance()))
                                    //消息头设置长度
                                    .addLast(new ProtobufVarint32LengthFieldPrepender())
                                    //解码
                                    .addLast(new ProtobufEncoder())
                                    //心跳检测，超过设置的时间将会抛出异常ReadTimeoutException
                                    .addLast(new ReadTimeoutHandler(8))
                                    //消息处理
                                    .addLast(new TransServerHandler())
                                    //心跳响应
                                    .addLast(new TransServerHeartBeatHandler());
                        }
                    });
            // 绑定端口同步等待启动成功
            ChannelFuture sync = bootstrap.bind(port).sync();

            // 等待服务监听端口关闭
            sync.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        TransServer customServer = new TransServer();
        customServer.bind(8090);
    }
}
