package client;


import client.handler.TransClientHandler;
import client.handler.TransClientHeartBeatHandler;
import com.google.protobuf.ByteString;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import pojo.TransMessageData;

import java.io.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class TransClient {
    public void bind(int port) {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline()
                                    //解码时，解决粘包/半包的问题
                                    .addLast(new ProtobufVarint32FrameDecoder())
                                    //对接收到的信息，使用protobuf进行解码
                                    .addLast(new ProtobufDecoder(TransMessageData.MessageData.getDefaultInstance()))
                                    //编码时，解决粘包/半包的问题
                                    .addLast(new ProtobufVarint32LengthFieldPrepender())
                                    //对于要发送的信息，使用protobuf进行编码
                                    .addLast(new ProtobufEncoder())
                                    // 消息处理
                                    .addLast(new TransClientHandler())
                                    // 心跳响应
                                    .addLast(new TransClientHeartBeatHandler());
                        }
                    });
            ChannelFuture f = b.connect("127.0.0.1", port).sync();
            Channel channel = f.channel();
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String in = br.readLine();
            Random r = new Random(1);
            while(true){
                if(in.equals("register")){
                    int magic = 0x249988DF;
                    String name = "register";
                    ByteString byteString = ByteString.copyFrom(name.getBytes());
                    TransMessageData.MessageData.Content content = TransMessageData.MessageData.Content.newBuilder().setData(byteString).setContentLength(byteString.size()).build();
                    TransMessageData.MessageData requsetData = TransMessageData.MessageData.newBuilder()
                            .setType(TransMessageData.MessageData.DataType.REQ_REG).setMagic(magic).setSerialNum(0x050b).setSessionId(12345).setContent(content).build();
                    channel.writeAndFlush(requsetData);
                }else if(in.equals("trans")){
                    File file = new File("/home/lqc/图表1.vsd");
                    long fileSize = file.length();
                    FileInputStream inputStream = new FileInputStream(file);
                    byte[] bytes = new byte[1024 * 128];
                    int len = -1;
                    int num = 0;
                    while((len = inputStream.read(bytes)) != -1){
                        int magic = 0x249988DF;
                        fileSize -= len;
                        ByteString byteString = ByteString.copyFrom(bytes,0,len);
//                        num++;
//                        System.out.println(num + "---> fileSize : " + fileSize +" , len: " +len);
                        TransMessageData.MessageData.Content content = TransMessageData.MessageData.Content.newBuilder().setData(byteString).setContentLength(byteString.size()).build();
                        TransMessageData.MessageData requsetData = TransMessageData.MessageData.newBuilder()
                                .setType(TransMessageData.MessageData.DataType.REQ_TRANS).setMagic(magic).setSerialNum(0x050b).setSessionId(12345).setContent(content).build();
                        channel.writeAndFlush(requsetData);
                        int random = r.nextInt(128);
                        bytes = new byte[1024 * random];
                    }
                    inputStream.close();
                    int magic = 0x249988DF;
                    TransMessageData.MessageData requsetData = TransMessageData.MessageData.newBuilder()
                            .setType(TransMessageData.MessageData.DataType.RSP_TRANS).setMagic(magic).setSerialNum(0x050b).setSessionId(12345).build(); //结束
                    channel.writeAndFlush(requsetData);
                }
                in = br.readLine();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 短线重连 定时5秒
            group.execute(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(5);
                    bind(port);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
//            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws IOException {
        TransClient transClient = new TransClient();
        transClient.bind(8090);

    }
}
