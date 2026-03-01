package zio.http

trait Header {
  def headerName: String
  def renderedValue: String
}

object Header {

  trait Typed[H <: Header] {
    def name: String
    def parse(value: String): Either[String, H]
    def render(h: H): String
  }

  final case class Custom(override val headerName: String, rawValue: String) extends Header {
    def renderedValue: String = rawValue
  }

  object Custom extends Header.Typed[Custom] {
    val name: String                                 = "x-custom"
    def parse(value: String): Either[String, Custom] = Right(Custom(name, value))
    def render(h: Custom): String                    = h.renderedValue
  }
}
