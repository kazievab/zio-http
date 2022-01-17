package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import zhttp.http.headers.HeaderExtension
import zhttp.service.server.ContentDecoder
import zio.{Chunk, Task, UIO, ZIO}

import java.net.InetAddress

trait Request extends HeaderExtension[Request] { self =>

  /**
   * Updates the headers using the provided function
   */
  final override def updateHeaders(update: Headers => Headers): Request = self.copy(headers = update(self.headers))

  def copy(method: Method = self.method, url: URL = self.url, headers: Headers = self.headers): Request = {
    val m = method
    val u = url
    val h = headers
    new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def headers: Headers                   = h
      override def remoteAddress: Option[InetAddress] = self.remoteAddress
      override private[zhttp] def getBodyAsByteBuf    = self.getBodyAsByteBuf
      override def decodeContent[R, B](
        decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
      ): ZIO[R, Throwable, B] =
        self.decodeContent(decoder)
    }
  }

  def decodeContent[R, B](decoder: ContentDecoder[R, Throwable, Chunk[Byte], B]): ZIO[R, Throwable, B]

  /**
   * Decodes the content of request as a Chunk of Bytes
   */
  def body: Task[Chunk[Byte]] =
    bodyAsByteBuf.flatMap(buf => Task(Chunk.fromArray(ByteBufUtil.getBytes(buf))))

  /**
   * Decodes the content of request as string
   */
  def bodyAsString: Task[String] =
    bodyAsByteBuf.flatMap(buf => Task(buf.toString(charset)))

  /**
   * Gets all the headers in the Request
   */
  def headers: Headers

  /**
   * Checks is the request is a pre-flight request or not
   */
  def isPreflight: Boolean = method == Method.OPTIONS

  /**
   * Gets the request's method
   */
  def method: Method

  /**
   * Gets the request's path
   */
  def path: Path = url.path

  /**
   * Gets the remote address if available
   */
  def remoteAddress: Option[InetAddress]

  /**
   * Overwrites the method in the request
   */
  def setMethod(method: Method): Request = self.copy(method = method)

  /**
   * Overwrites the path in the request
   */
  def setPath(path: Path): Request = self.copy(url = self.url.copy(path = path))

  /**
   * Overwrites the url in the request
   */
  def setUrl(url: URL): Request = self.copy(url = url)

  /**
   * Gets the complete url
   */
  def url: URL

  private[zhttp] def bodyAsByteBuf: Task[ByteBuf]
}

object Request {

  /**
   * Constructor for Request
   */
  def apply(
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: Headers = Headers.empty,
    remoteAddress: Option[InetAddress] = None,
    data: HttpData = HttpData.Empty,
  ): Request = {
    val m  = method
    val u  = url
    val h  = headers
    val ra = remoteAddress
    new Request {
      override def method: Method                              = m
      override def url: URL                                    = u
      override def headers: Headers                            = h
      override def remoteAddress: Option[InetAddress]          = ra
      override private[zhttp] def bodyAsByteBuf: Task[ByteBuf] = data.toByteBuf
      override def decodeContent[R, B](
        decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
      ): ZIO[R, Throwable, B] =
        decoder.decode(data, method, url, headers)
    }
  }

  /**
   * Effectfully create a new Request object
   */
  def make[E <: Throwable](
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: Headers = Headers.empty,
    remoteAddress: Option[InetAddress],
    content: HttpData = HttpData.empty,
  ): UIO[Request] =
    UIO(Request(method, url, headers, remoteAddress, content))

  /**
   * Lift request to TypedRequest with option to extract params
   */
  final class ParameterizedRequest[A](req: Request, val params: A) extends Request {
    override def getHeaders: Headers                            = req.getHeaders
    override def method: Method                                 = req.method
    override def remoteAddress: Option[InetAddress]             = req.remoteAddress
    override def url: URL                                       = req.url
    override private[zhttp] def getBodyAsByteBuf: Task[ByteBuf] = req.getBodyAsByteBuf
    override def decodeContent[R, B](
      decoder: ContentDecoder[R, Throwable, Chunk[Byte], B],
    ): ZIO[R, Throwable, B] =
      req.decodeContent(decoder)

  }

  object ParameterizedRequest {
    def apply[A](req: Request, params: A): ParameterizedRequest[A] = new ParameterizedRequest(req, params)
  }
}
