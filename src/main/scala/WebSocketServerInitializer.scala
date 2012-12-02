import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpChunkAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame

class WebSocketServerInitializer(tap: Tap[String]) extends ChannelInitializer[SocketChannel] {
    override def initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline
        pipeline.addLast("decoder", new HttpRequestDecoder)
        pipeline.addLast("aggregator", new HttpChunkAggregator(65536))
        pipeline.addLast("encoder", new HttpResponseEncoder)
        pipeline.addLast("handler", new WebSocketServerHandler(tap))
    }
}

class WebSocketServerHandler(tap: Tap[String]) extends ChannelInboundMessageHandlerAdapter[Object] {
  override def messageReceived(ctx: ChannelHandlerContext, msg: Object) {
    if (msg.isInstanceOf[HttpRequest]) {
      println("New websocket request");
      tap.connect(new WSSink(ctx))
      val req = msg.asInstanceOf[HttpRequest];
      val wsFactory = new WebSocketServerHandshakerFactory("ws://localhost:9000/", null, false);
      val handshaker = wsFactory.newHandshaker(req);
      if (handshaker == null) {
        println("smth went wrong")
     } else {
      handshaker.handshake(ctx.channel(), req);
     }
   } else {
     // we currently don't handle incoming messages
     println("pong!")
   }
  }
}
