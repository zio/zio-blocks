package zio.http

final case class Status private[http] (code: Int) extends AnyVal {
  def isInformational: Boolean = code >= 100 && code < 200
  def isSuccess: Boolean       = code >= 200 && code < 300
  def isRedirection: Boolean   = code >= 300 && code < 400
  def isClientError: Boolean   = code >= 400 && code < 500
  def isServerError: Boolean   = code >= 500 && code < 600
  def isError: Boolean         = code >= 400
  def text: String             = Status.reasonPhrase(code)
}

object Status {

  def apply(code: Int): Status = new Status(code)

  def fromInt(code: Int): Status = new Status(code)

  val Continue: Status           = Status(100)
  val SwitchingProtocols: Status = Status(101)
  val Processing: Status         = Status(102)
  val EarlyHints: Status         = Status(103)

  val Ok: Status                          = Status(200)
  val Created: Status                     = Status(201)
  val Accepted: Status                    = Status(202)
  val NonAuthoritativeInformation: Status = Status(203)
  val NoContent: Status                   = Status(204)
  val ResetContent: Status                = Status(205)
  val PartialContent: Status              = Status(206)
  val MultiStatus: Status                 = Status(207)

  val MultipleChoices: Status   = Status(300)
  val MovedPermanently: Status  = Status(301)
  val Found: Status             = Status(302)
  val SeeOther: Status          = Status(303)
  val NotModified: Status       = Status(304)
  val TemporaryRedirect: Status = Status(307)
  val PermanentRedirect: Status = Status(308)

  val BadRequest: Status                  = Status(400)
  val Unauthorized: Status                = Status(401)
  val PaymentRequired: Status             = Status(402)
  val Forbidden: Status                   = Status(403)
  val NotFound: Status                    = Status(404)
  val MethodNotAllowed: Status            = Status(405)
  val NotAcceptable: Status               = Status(406)
  val ProxyAuthenticationRequired: Status = Status(407)
  val RequestTimeout: Status              = Status(408)
  val Conflict: Status                    = Status(409)
  val Gone: Status                        = Status(410)
  val LengthRequired: Status              = Status(411)
  val PreconditionFailed: Status          = Status(412)
  val PayloadTooLarge: Status             = Status(413)
  val UriTooLong: Status                  = Status(414)
  val UnsupportedMediaType: Status        = Status(415)
  val RangeNotSatisfiable: Status         = Status(416)
  val ExpectationFailed: Status           = Status(417)
  val ImATeapot: Status                   = Status(418)
  val MisdirectedRequest: Status          = Status(421)
  val UnprocessableEntity: Status         = Status(422)
  val TooEarly: Status                    = Status(425)
  val UpgradeRequired: Status             = Status(426)
  val PreconditionRequired: Status        = Status(428)
  val TooManyRequests: Status             = Status(429)
  val RequestHeaderFieldsTooLarge: Status = Status(431)
  val UnavailableForLegalReasons: Status  = Status(451)

  val InternalServerError: Status           = Status(500)
  val NotImplemented: Status                = Status(501)
  val BadGateway: Status                    = Status(502)
  val ServiceUnavailable: Status            = Status(503)
  val GatewayTimeout: Status                = Status(504)
  val HttpVersionNotSupported: Status       = Status(505)
  val InsufficientStorage: Status           = Status(507)
  val NetworkAuthenticationRequired: Status = Status(511)

  private def reasonPhrase(code: Int): String = code match {
    case 100 => "Continue"
    case 101 => "Switching Protocols"
    case 102 => "Processing"
    case 103 => "Early Hints"
    case 200 => "OK"
    case 201 => "Created"
    case 202 => "Accepted"
    case 203 => "Non-Authoritative Information"
    case 204 => "No Content"
    case 205 => "Reset Content"
    case 206 => "Partial Content"
    case 207 => "Multi-Status"
    case 300 => "Multiple Choices"
    case 301 => "Moved Permanently"
    case 302 => "Found"
    case 303 => "See Other"
    case 304 => "Not Modified"
    case 307 => "Temporary Redirect"
    case 308 => "Permanent Redirect"
    case 400 => "Bad Request"
    case 401 => "Unauthorized"
    case 402 => "Payment Required"
    case 403 => "Forbidden"
    case 404 => "Not Found"
    case 405 => "Method Not Allowed"
    case 406 => "Not Acceptable"
    case 407 => "Proxy Authentication Required"
    case 408 => "Request Timeout"
    case 409 => "Conflict"
    case 410 => "Gone"
    case 411 => "Length Required"
    case 412 => "Precondition Failed"
    case 413 => "Payload Too Large"
    case 414 => "URI Too Long"
    case 415 => "Unsupported Media Type"
    case 416 => "Range Not Satisfiable"
    case 417 => "Expectation Failed"
    case 418 => "I'm a Teapot"
    case 421 => "Misdirected Request"
    case 422 => "Unprocessable Entity"
    case 425 => "Too Early"
    case 426 => "Upgrade Required"
    case 428 => "Precondition Required"
    case 429 => "Too Many Requests"
    case 431 => "Request Header Fields Too Large"
    case 451 => "Unavailable For Legal Reasons"
    case 500 => "Internal Server Error"
    case 501 => "Not Implemented"
    case 502 => "Bad Gateway"
    case 503 => "Service Unavailable"
    case 504 => "Gateway Timeout"
    case 505 => "HTTP Version Not Supported"
    case 507 => "Insufficient Storage"
    case 511 => "Network Authentication Required"
    case _   => "Unknown"
  }
}
