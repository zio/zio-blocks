package zio.http.headers

import zio.http._

// --- 1. CookieHeader ---

final case class CookieHeader(value: String) extends Header {
  def headerName: String    = CookieHeader.name
  def renderedValue: String = value
}

object CookieHeader extends Header.Typed[CookieHeader] {
  val name: String                                       = "cookie"
  def parse(value: String): Either[String, CookieHeader] = Right(CookieHeader(value))
  def render(h: CookieHeader): String                    = h.renderedValue
}

// --- 2. SetCookieHeader ---

final case class SetCookieHeader(value: String) extends Header {
  def headerName: String    = SetCookieHeader.name
  def renderedValue: String = value
}

object SetCookieHeader extends Header.Typed[SetCookieHeader] {
  val name: String                                          = "set-cookie"
  def parse(value: String): Either[String, SetCookieHeader] = Right(SetCookieHeader(value))
  def render(h: SetCookieHeader): String                    = h.renderedValue
}
