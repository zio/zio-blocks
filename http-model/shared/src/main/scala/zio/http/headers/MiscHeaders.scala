package zio.http.headers

import zio.blocks.chunk.Chunk

import zio.http._

// --- 1. UserAgent ---

final case class UserAgent(product: String) extends Header {
  def headerName: String    = UserAgent.name
  def renderedValue: String = product
}

object UserAgent extends Header.Typed[UserAgent] {
  val name: String                                    = "user-agent"
  def parse(value: String): Either[String, UserAgent] = Right(UserAgent(value.trim))
  def render(h: UserAgent): String                    = h.renderedValue
}

// --- 2. Server ---

final case class Server(product: String) extends Header {
  def headerName: String    = Server.name
  def renderedValue: String = product
}

object Server extends Header.Typed[Server] {
  val name: String                                 = "server"
  def parse(value: String): Either[String, Server] = Right(Server(value.trim))
  def render(h: Server): String                    = h.renderedValue
}

// --- 3. Date ---

final case class Date(value: String) extends Header {
  def headerName: String    = Date.name
  def renderedValue: String = value
}

object Date extends Header.Typed[Date] {
  val name: String                               = "date"
  def parse(value: String): Either[String, Date] = Right(Date(value.trim))
  def render(h: Date): String                    = h.renderedValue
}

// --- 4. Link ---

final case class Link(value: String) extends Header {
  def headerName: String    = Link.name
  def renderedValue: String = value
}

object Link extends Header.Typed[Link] {
  val name: String                               = "link"
  def parse(value: String): Either[String, Link] = Right(Link(value.trim))
  def render(h: Link): String                    = h.renderedValue
}

// --- 5. RetryAfter ---

final case class RetryAfter(value: String) extends Header {
  def headerName: String    = RetryAfter.name
  def renderedValue: String = value
}

object RetryAfter extends Header.Typed[RetryAfter] {
  val name: String                                     = "retry-after"
  def parse(value: String): Either[String, RetryAfter] = Right(RetryAfter(value.trim))
  def render(h: RetryAfter): String                    = h.renderedValue
}

// --- 6. Allow ---

final case class Allow(methods: Chunk[Method]) extends Header {
  def headerName: String    = Allow.name
  def renderedValue: String = methods.map(_.name).mkString(", ")
}

object Allow extends Header.Typed[Allow] {
  val name: String = "allow"

  def parse(value: String): Either[String, Allow] = {
    val parts = value.split(",").map(_.trim).filter(_.nonEmpty)
    if (parts.isEmpty) Left("Empty allow header")
    else {
      val methods = new scala.collection.mutable.ArrayBuffer[Method](parts.length)
      var i       = 0
      while (i < parts.length) {
        Method.fromString(parts(i)) match {
          case Some(m) => methods += m
          case None    => return Left(s"Invalid method: ${parts(i)}")
        }
        i += 1
      }
      Right(Allow(Chunk.fromArray(methods.toArray)))
    }
  }

  def render(h: Allow): String = h.renderedValue
}

// --- 7. Expect ---

final case class Expect(value: String) extends Header {
  def headerName: String    = Expect.name
  def renderedValue: String = value
}

object Expect extends Header.Typed[Expect] {
  val name: String                                 = "expect"
  def parse(value: String): Either[String, Expect] = Right(Expect(value.trim))
  def render(h: Expect): String                    = h.renderedValue
}

// --- 8. Range ---

final case class Range(unit: String, ranges: String) extends Header {
  def headerName: String    = Range.name
  def renderedValue: String = unit + "=" + ranges
}

object Range extends Header.Typed[Range] {
  val name: String = "range"

  def parse(value: String): Either[String, Range] = {
    val trimmed = value.trim
    val eqIdx   = trimmed.indexOf('=')
    if (eqIdx < 0) Left(s"Invalid range: $trimmed")
    else {
      val unit      = trimmed.substring(0, eqIdx).trim
      val rangeSpec = trimmed.substring(eqIdx + 1).trim
      if (unit.isEmpty || rangeSpec.isEmpty) Left(s"Invalid range: $trimmed")
      else Right(Range(unit, rangeSpec))
    }
  }

  def render(h: Range): String = h.renderedValue
}
