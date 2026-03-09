package zio.http

private[http] trait CharsetPlatformSpecific { self: Charset =>
  def toJava: java.nio.charset.Charset = self match {
    case Charset.UTF8       => java.nio.charset.StandardCharsets.UTF_8
    case Charset.ASCII      => java.nio.charset.StandardCharsets.US_ASCII
    case Charset.ISO_8859_1 => java.nio.charset.StandardCharsets.ISO_8859_1
    case Charset.UTF16      => java.nio.charset.StandardCharsets.UTF_16
    case Charset.UTF16BE    => java.nio.charset.StandardCharsets.UTF_16BE
    case Charset.UTF16LE    => java.nio.charset.StandardCharsets.UTF_16LE
  }
}
