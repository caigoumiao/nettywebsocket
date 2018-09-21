import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;

import java.nio.charset.Charset;
import java.util.Date;

import static io.netty.handler.codec.http.HttpHeaderUtil.isKeepAlive;
import static io.netty.handler.codec.http.HttpHeaderUtil.setContentLength;

public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object>
{
    private WebSocketServerHandshaker handshaker;

    protected void messageReceived(ChannelHandlerContext ctx , Object o) throws Exception
    {
        // 传统的HTTP接入
        if(o instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest)o);
        }
        // WebSocket 接入
        else if(o instanceof WebSocketFrame){
            handleWebSocketFrame(ctx, (WebSocketFrame)o);
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx , WebSocketFrame frame)
    {
        // 判断是否是关闭链路的指令
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return ;
        }

        // 判断是否是 Ping 消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(
                    new PongWebSocketFrame(frame.content().retain())
            );
        }

        // 本例程仅支持文本消息，不支持二进制消息
        if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(
                    String.format("%s frame types not supported", frame.getClass().getName())
            );
        }
        // 返回应答消息
        String req = ((TextWebSocketFrame)frame).text();
        ctx.channel().write(
                new TextWebSocketFrame(
                        req + ", 欢迎使用netty WebSocket 服务, 现在时刻："
                                + new Date().toString()
                )
        );
    }

    private void handleHttpRequest(ChannelHandlerContext ctx , FullHttpRequest req)
    {
        // 如果HTTP解码失败，返回HTTP异常
        if(!req.decoderResult().isSuccess() ||
                (!"websocket".equals(req.headers().get("Upgrade")))){
            sendHttpResponse(ctx, req,
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
        }

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                "ws://localhost:8080/websocket",
                null,
                false
        );
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null){
            WebSocketServerHandshakerFactory
                    .sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }

    private void sendHttpResponse(ChannelHandlerContext ctx , FullHttpRequest req , FullHttpResponse resp)
    {
        // 返回应答给客户端
        if(resp.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(
                    resp.status().toString(),
                    Charset.forName("utf8")
            );
            resp.content().writeBytes(buf);
            buf.release();
            setContentLength(resp, resp.content().readableBytes());
        }

        ChannelFuture f= ctx.channel().writeAndFlush(resp);
        if(!isKeepAlive(req) || resp.status().code() !=200){
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception
    {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx , Throwable cause) throws Exception
    {
        cause.printStackTrace();
        ctx.close();
    }
}
