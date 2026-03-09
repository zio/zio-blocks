package zio.http

final case class Boundary(value: String) {
  override def toString: String = value
}

object Boundary {
  def generate: Boundary = {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val sb    = new StringBuilder(24)
    var i     = 0
    while (i < 24) {
      sb.append(chars.charAt(scala.util.Random.nextInt(chars.length)))
      i += 1
    }
    Boundary(sb.toString)
  }
}
