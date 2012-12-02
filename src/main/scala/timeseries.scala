import play.api.libs.iteratee._
import play.api.libs.iteratee.Enumerator.Pushee
import play.api.libs.iteratee.Concurrent.Channel
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.socket.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.ChannelInboundMessageHandlerAdapter
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import java.nio.channels.Channels
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;

object Server {

  def main(args: Array[String]): Unit = {

    val statdMetricEnumeratee: Enumeratee[String,String] = Enumeratee.map[String]{ s => s.split('|').head}

    val tap = new StatsdTap(statdMetricEnumeratee, 3147)

    val bws = new ServerBootstrap;
    bws.group(new NioEventLoopGroup)
      .channel(classOf[NioServerSocketChannel])
      .localAddress(9000)
      .childHandler(new WebSocketServerInitializer(tap))

    val ch = bws.bind().sync.channel;
    tap.run()

    //val statdBucketEnumeratee: Enumeratee[String,String] = Enumeratee.map[String]{ s => s.split(':').last}

  }
}

// A simple tap implementation for statsd 
class StatsdTap(connector: Enumeratee[String,String], port: Int) extends ChannelInboundMessageHandlerAdapter[DatagramPacket] with Tap[String]{

  val head = Enumeratee.map[String]{ s => s.split('|').head}

    val b = new Bootstrap;
    b.group(new NioEventLoopGroup)
      .channel(classOf[NioDatagramChannel])
      .localAddress(port)
      .handler(this);

  def run() {
    b.bind().sync.channel.closeFuture.sync;
  }

  override def messageReceived(ctx: ChannelHandlerContext,msg: DatagramPacket) {
    push(msg.data.toString(CharsetUtil.UTF_8))
  }
}

trait Tap[T] {
  private val broadcast = Concurrent.broadcast[T] 
  protected val channel = broadcast._2
  val enumerator = broadcast._1
  implicit val head : Enumeratee[T,T]

  def push(msg: T){
    channel.push(msg)
  }

  // add an iteratee to a tap
  def apply(iteratee: Iteratee[T,_]) {
    enumerator.apply(iteratee)
  }

  // directly connect Tap to Sink
  def connect(sink: Sink[T]) {
    apply(head.transform(sink.tail))
  }

  def connect(sink: Sink[T], connector: Enumeratee[T,T]) {
    val chain = head ><> connector
    apply(chain.transform(sink.tail))
  }
}

trait Sink[T] {
  // a sink should always return a Unit as it leaves the system
  val tail : Iteratee[T, Unit]
}

class WSSink(ctx: ChannelHandlerContext) extends Sink[String] {
  val tail : Iteratee[String, Unit] = Iteratee.foreach[String]( s => 
      ctx.write(new TextWebSocketFrame(s))
  )
}

/* currently not used */
class ServerPipelineFactory(iteratee: Iteratee[String,Unit]) extends ChannelInitializer[NioDatagramChannel] {

  val tuple = Concurrent.broadcast[String]

  override def initChannel(ch: NioDatagramChannel) {
    val pipeline = ch.pipeline;

    pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192, (Delimiters.lineDelimiter):_*))

    val enumerator = tuple._1
    enumerator.apply(iteratee)
    pipeline.addLast("decoder", new StringDecoder)
    pipeline.addLast("encoder", new StringEncoder)
    pipeline.addLast("handler", new ServerHandler(tuple._2))
  }
}

class ServerHandler(enumerator: Channel[String]) extends ChannelInboundMessageHandlerAdapter[String] {
  override def messageReceived(ctx: ChannelHandlerContext,msg: String) {
    enumerator.push(msg)
  }
}
