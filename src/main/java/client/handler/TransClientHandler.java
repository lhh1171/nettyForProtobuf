package client.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import pojo.TransMessageData;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TransClientHandler extends ChannelInboundHandlerAdapter {
    static int num = 0;
    static int ok = 0;
    static FileOutputStream out = null;
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        TransMessageData.MessageData reqData = TransMessageData
                .MessageData
                .newBuilder()
                .setType(TransMessageData.MessageData.DataType.REQ_LOGIN)
                .build();
        ctx.channel().writeAndFlush(reqData);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        TransMessageData.MessageData respData = (TransMessageData.MessageData) msg;
        if (respData.getType() == TransMessageData.MessageData.DataType.RSP_LOGIN) {
            // 响应登录请求处理逻辑
            byte[] byteArray =respData.getContent().getData().toByteArray();
            String string = new String(byteArray);
            boolean equals = string.equals("SUCCESS");
            if (equals) {
                System.out.println("Receive-Server:LoginSuccess,time:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                System.out.println(respData.toString());
                // 传递下一个handler
                ctx.fireChannelRead(msg);
            } else {
                // 登录失败
                if (ctx.channel().isActive()) {
                    ctx.close();
                }
            }
        } else if(respData.getType() == TransMessageData.MessageData.DataType.REQ_TRANS){
            try {
//                num++;
//                int length = (int) respData.getContent().getContentLength();
//                System.out.println(num + " --->" + length);
                if (ok == 0){
                    ok = 1;
                    out = new FileOutputStream("/home/lqc/yes",true);
                }
                byte[] byteArray = respData.getContent().getData().toByteArray();
                out.write(byteArray,0,byteArray.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if(respData.getType() == TransMessageData.MessageData.DataType.RSP_TRANS){
            if(out != null){
                out.close();
            }
            System.out.println("关闭");
        }
        else if(respData.getType() == TransMessageData.MessageData.DataType.RSP_ERROR){
            System.out.println(respData.toBuilder());
        } else {
            // 响应心跳处理逻辑
            ctx.fireChannelRead(msg);
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        if (ctx.channel().isActive()) {
            ctx.close();
        }
    }
}

