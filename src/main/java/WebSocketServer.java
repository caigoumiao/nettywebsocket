import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

public class WebSocketServer
{
    public void run(int port) throws InterruptedException
    {
        EventLoopGroup boss = new NioEventLoopGroup();
        EventLoopGroup worker = new NioEventLoopGroup();

        try
        {
            ServerBootstrap b=new ServerBootstrap();
            b.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>()
                    {
                        protected void initChannel(SocketChannel ch) throws Exception
                        {
                            // 将请求和应答消息编码或者解码为HTTP消息
                            ch.pipeline().addLast("http-codec", new HttpServerCodec());
                            // 将HTTP消息的多个部分组合成一条完整的HTTP消息
                            ch.pipeline().addLast("aggregator", new HttpObjectAggregator(65536));
                            // 处理大数据流
                            ch.pipeline().addLast("http-chunked", new ChunkedWriteHandler());
                            ch.pipeline().addLast(new WebSocketServerHandler());
                        }
                    });

            Channel ch = b.bind(port).sync().channel();
            System.out.println("Web Socket server started at port "+port);
            System.out.println("Open your browser and navigate to http://localhost:"+port+"/");

            ch.closeFuture().sync();
        } finally
        {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws InterruptedException
    {
        int port = 8080;
        new WebSocketServer().run(port);
    }
}
