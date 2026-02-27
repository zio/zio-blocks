package zio.blocks.http

opaque type Status = Int

object Status {

  inline def apply(code: Int): Status = code

  def fromInt(code: Int): Status = code

  extension (status: Status) {
    inline def code: Int                = status
    inline def isInformational: Boolean = status >= 100 && status < 200
    inline def isSuccess: Boolean       = status >= 200 && status < 300
    inline def isRedirection: Boolean   = status >= 300 && status < 400
    inline def isClientError: Boolean   = status >= 400 && status < 500
    inline def isServerError: Boolean   = status >= 500 && status < 600
    inline def isError: Boolean         = status >= 400
    def text: String                    = Status.reasonPhrase(status)
  }

  val Continue: Status           = 100
  val SwitchingProtocols: Status = 101
  val Processing: Status         = 102
  val EarlyHints: Status         = 103

  val Ok: Status                          = 200
  val Created: Status                     = 201
  val Accepted: Status                    = 202
  val NonAuthoritativeInformation: Status = 203
  val NoContent: Status                   = 204
  val ResetContent: Status                = 205
  val PartialContent: Status              = 206
  val MultiStatus: Status                 = 207

  val MultipleChoices: Status   = 300
  val MovedPermanently: Status  = 301
  val Found: Status             = 302
  val SeeOther: Status          = 303
  val NotModified: Status       = 304
  val TemporaryRedirect: Status = 307
  val PermanentRedirect: Status = 308

  val BadRequest: Status                  = 400
  val Unauthorized: Status                = 401
  val PaymentRequired: Status             = 402
  val Forbidden: Status                   = 403
  val NotFound: Status                    = 404
  val MethodNotAllowed: Status            = 405
  val NotAcceptable: Status               = 406
  val ProxyAuthenticationRequired: Status = 407
  val RequestTimeout: Status              = 408
  val Conflict: Status                    = 409
  val Gone: Status                        = 410
  val LengthRequired: Status              = 411
  val PreconditionFailed: Status          = 412
  val PayloadTooLarge: Status             = 413
  val UriTooLong: Status                  = 414
  val UnsupportedMediaType: Status        = 415
  val RangeNotSatisfiable: Status         = 416
  val ExpectationFailed: Status           = 417
  val ImATeapot: Status                   = 418
  val MisdirectedRequest: Status          = 421
  val UnprocessableEntity: Status         = 422
  val TooEarly: Status                    = 425
  val UpgradeRequired: Status             = 426
  val PreconditionRequired: Status        = 428
  val TooManyRequests: Status             = 429
  val RequestHeaderFieldsTooLarge: Status = 431
  val UnavailableForLegalReasons: Status  = 451

  val InternalServerError: Status           = 500
  val NotImplemented: Status                = 501
  val BadGateway: Status                    = 502
  val ServiceUnavailable: Status            = 503
  val GatewayTimeout: Status                = 504
  val HttpVersionNotSupported: Status       = 505
  val InsufficientStorage: Status           = 507
  val NetworkAuthenticationRequired: Status = 511

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
