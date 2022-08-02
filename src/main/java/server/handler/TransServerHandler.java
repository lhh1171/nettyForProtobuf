package server.handler;


import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import pojo.TransMessageData;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransServerHandler extends ChannelInboundHandlerAdapter {
    private String[] whiteIPv4List = {"127.0.0.1"};
    public static ConcurrentHashMap nodeCheck = new ConcurrentHashMap();
    private static final int MAX_CONN = 3;//指定最大连接数
    private int connectNum = 0; //当前连接数
    static int num = 0;
    //key : serial_num:sessionid
    private static Map<String , List<ChannelHandlerContext>> contextLinks = new HashMap<String, List<ChannelHandlerContext>>();
    private static List<ChannelHandlerContext> contexts = new ArrayList<ChannelHandlerContext>();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        TransMessageData.MessageData messageData = (TransMessageData.MessageData) msg;
        int sessionid = messageData.getSessionId();
        long serialNum = messageData.getSerialNum();
        String str = serialNum + ":" + sessionid;
        if (messageData.getType() == TransMessageData.MessageData.DataType.UNRECOGNIZED) {
            // 无法识别的消息类型
            ctx.close();
        }
        if (messageData.getType() == TransMessageData.MessageData.DataType.REQ_LOGIN) {
            // 检查重复登录
            String nodeIndex = ctx.channel().remoteAddress().toString();
            if (nodeCheck.contains(nodeIndex)) {
                // 重复登录
                ctx.writeAndFlush(builderResp(false));
                return;
            } else if(MAX_CONN <= connectNum){
                //最大连接数
                ctx.writeAndFlush(builderMaxResp());
                ctx.close();
                return;
            } else {
                InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
                String ip = socketAddress.getAddress().getHostAddress();
                boolean isOk = false;
                // 检查白名单
                for (String s : whiteIPv4List) {
                    if (s.equals(ip)) {
                        isOk = true;
                        break;
                    }
                }
                // 成功响应
                TransMessageData.MessageData responseData = isOk ? builderResp(true) : builderResp(false);
                if (isOk) {
                    nodeCheck.put(nodeIndex, true);
                    contexts.add(ctx);
                    connectNum++;
                }
                ctx.writeAndFlush(responseData);
            }
        } else if(messageData.getType() == TransMessageData.MessageData.DataType.REQ_REG){ //注册
            System.out.println("进入注册");
            System.out.println(messageData);
            List<ChannelHandlerContext> list = contextLinks.get(str);
            if(list == null){
                list = new ArrayList<ChannelHandlerContext>();
            }
            list.add(ctx);
            contextLinks.put(str,list);
        } else if(messageData.getType() == TransMessageData.MessageData.DataType.REQ_TRANS){ //传输

            num++;
//            System.out.println(num + "---->"+ messageData.getContent().getContentLength());
            List<ChannelHandlerContext> list = contextLinks.get(str);
            TransMessageData.MessageData responseData = transResp(messageData,list,ctx);
            if(responseData != null){ //返回异常问题
                ctx.writeAndFlush(messageData);
            }
        } else if(messageData.getType() == TransMessageData.MessageData.DataType.RSP_TRANS){
            List<ChannelHandlerContext> list = contextLinks.get(str);
            for (int i = 0; i < list.size(); i++) {
                if(ctx != list.get(i)){
                    list.get(i).writeAndFlush(TransMessageData.MessageData.newBuilder().setType(TransMessageData.MessageData.DataType.RSP_TRANS).build());
                }
            }
        } else{
            //心跳消息处理
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        nodeCheck.remove(ctx.channel().remoteAddress().toString());
        if (ctx.channel().isActive()) {
            connectNum--;
            contexts.remove(ctx);
            ctx.close();
        }
    }

    public TransMessageData.MessageData builderResp(boolean isOk) {
        String r = isOk ? "SUCCESS" : "FAILED";
        ByteString bytes = ByteString.copyFrom(r.getBytes());
        TransMessageData.MessageData.Content responseContent = TransMessageData.MessageData.Content.newBuilder().setData(bytes).setContentLength(bytes.size()).build();
        TransMessageData.MessageData responseData = TransMessageData.MessageData.newBuilder().setType(TransMessageData.MessageData.DataType.RSP_LOGIN).setContent(responseContent).build();
        return responseData;
    }

    public TransMessageData.MessageData builderMaxResp() {
        String r = "到达最大连接数,连接失败，请稍后连接";
        ByteString bytes = ByteString.copyFrom(r.getBytes());
        TransMessageData.MessageData.Content responseContent = TransMessageData.MessageData.Content.newBuilder().setData(bytes).setContentLength(bytes.size()).build();
        TransMessageData.MessageData responseData = TransMessageData.MessageData.newBuilder().setType(TransMessageData.MessageData.DataType.RSP_LOGIN).setContent(responseContent).build();
        return responseData;
    }

    public TransMessageData.MessageData transResp(TransMessageData.MessageData messageData, List<ChannelHandlerContext> list, ChannelHandlerContext ctx){
        String r = null;
        TransMessageData.MessageData.Content responseContent = null;
        TransMessageData.MessageData responseData = null;
        if(list == null){
            r = "您还没有进行注册";
        }else if(list.size() == 1){
           r = "对方还没有连接";
        }else{
            for (int i = 0; i < list.size(); i++) {
                if(ctx != list.get(i)){
                    list.get(i).writeAndFlush(messageData);
                }
            }
            return null;
        }
        ByteString bytes = ByteString.copyFrom(r.getBytes());
        responseContent = TransMessageData.MessageData.Content.newBuilder().setData(bytes).setContentLength(bytes.size()).build();
        responseData = TransMessageData.MessageData.newBuilder().setType(TransMessageData.MessageData.DataType.RSP_ERROR).setContent(responseContent).build();
        return responseData;
    }
}
