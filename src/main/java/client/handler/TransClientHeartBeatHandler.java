package client.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import pojo.TransMessageData;

import java.util.concurrent.TimeUnit;

public class TransClientHeartBeatHandler extends ChannelInboundHandlerAdapter {

    private static ScheduledFuture heartbeatFuture;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        TransMessageData.MessageData messageData = (TransMessageData.MessageData) msg;
        if (messageData.getType() == TransMessageData.MessageData.DataType.RSP_LOGIN) {
            // 登录成功后保持心跳 间隔为5秒
            heartbeatFuture = ctx.executor().scheduleAtFixedRate(() -> {
                TransMessageData.MessageData req = TransMessageData.MessageData.newBuilder()
                        .setType(TransMessageData.MessageData.DataType.PING).build();
//                System.out.println("Send-Server:PING,time:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                ctx.writeAndFlush(req);
            }, 0, 5, TimeUnit.SECONDS);
        } else if (messageData.getType() == TransMessageData.MessageData.DataType.PONG) {
//            System.out.println("Receive-Server:PONG,time:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
//            System.out.println();
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 发生异常就取消心跳保持
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
            heartbeatFuture = null;
        }
        ctx.fireExceptionCaught(cause);
    }
}

