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

  object Custom
}
