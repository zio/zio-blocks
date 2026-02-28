package zio.http

final case class Boundary(value: String) {
  override def toString: String = value
}

object Boundary {
  private val rng: java.util.Random = new java.util.Random()
  def generate: Boundary            = {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val sb    = new StringBuilder(24)
    val rng   = Boundary.rng
    var i     = 0
    while (i < 24) {
      sb.append(chars.charAt(rng.nextInt(chars.length)))
      i += 1
    }
    Boundary(sb.toString)
  }
}
