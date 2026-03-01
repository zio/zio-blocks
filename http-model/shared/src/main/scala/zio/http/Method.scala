package zio.http

/**
 * HTTP request method as defined by RFC 9110.
 *
 * Each method has a unique `ordinal` for efficient array-based dispatch.
 */
sealed abstract class Method(val name: String, val ordinal: Int) {
  override def toString: String = name
}

object Method {
  case object GET     extends Method("GET", 0)
  case object POST    extends Method("POST", 1)
  case object PUT     extends Method("PUT", 2)
  case object DELETE  extends Method("DELETE", 3)
  case object PATCH   extends Method("PATCH", 4)
  case object HEAD    extends Method("HEAD", 5)
  case object OPTIONS extends Method("OPTIONS", 6)
  case object TRACE   extends Method("TRACE", 7)
  case object CONNECT extends Method("CONNECT", 8)

  val values: Array[Method] = Array(GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE, CONNECT)

  private val byName: Map[String, Method] = values.iterator.map(m => m.name -> m).toMap

  def fromString(s: String): Option[Method] = byName.get(s)

  def render(method: Method): String = method.name
}
