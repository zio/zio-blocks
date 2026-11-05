/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

/**
 * An HTTP status code, such as `200` or `404`, with a constant for each common
 * code (`Status.Ok`, `Status.NotFound`, ...) and tests for its class
 * (`isSuccess`, `isClientError`, ...). Any `Int` is accepted, including codes
 * without a constant and numbers outside 100 to 599; nothing is checked.
 *
 * On Scala 3, `Status` is an opaque type over `Int`, so at run time it is a
 * plain `Int` and `toString` prints the number, `404`. On Scala 2 it is a value
 * class and `toString` prints `Status(404)`. Use `code` or `text` when you need
 * the same string on both.
 *
 * {{{
 * val status = Status.NotFound
 *
 * status.code           // 404
 * status.text           // Not Found
 * status.isClientError  // true
 * status.isError        // true
 * Status(299).isSuccess // true
 * Status(299).text      // Unknown
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#status Status]]
 */
opaque type Status = Int

/**
 * Constants for the common status codes, named after their reason phrases, and
 * constructors for any other code.
 *
 * @see
 *   [[https://zioblocks.com/reference/http-model/model/#predefined-status-codes Predefined Status Codes]]
 */
object Status {

  /**
   * Creates a status from `code` without checking it.
   *
   * {{{
   * val validationFailed = Status(422)
   *
   * validationFailed == Status.UnprocessableEntity // true
   * Status(799).text                               // Unknown
   * }}}
   */
  inline def apply(code: Int): Status = code

  /** Creates a status from `code` without checking it; the same as `apply`. */
  def fromInt(code: Int): Status = code

  extension (status: Status) {

    /** The numeric code. */
    inline def code: Int = status

    /** Whether the code is from 100 to 199. */
    inline def isInformational: Boolean = status >= 100 && status < 200

    /** Whether the code is from 200 to 299. */
    inline def isSuccess: Boolean = status >= 200 && status < 300

    /** Whether the code is from 300 to 399. */
    inline def isRedirection: Boolean = status >= 300 && status < 400

    /** Whether the code is from 400 to 499. */
    inline def isClientError: Boolean = status >= 400 && status < 500

    /** Whether the code is from 500 to 599. */
    inline def isServerError: Boolean = status >= 500 && status < 600

    /**
     * Whether the code is 400 or above: a client or server error, or any larger
     * number.
     */
    inline def isError: Boolean = status >= 400

    /**
     * The reason phrase of the code, such as `Not Found` for 404, or `Unknown`
     * for a code without a constant in [[Status]].
     */
    def text: String = Status.reasonPhrase(status)
  }

  /** `100 Continue`. */
  val Continue: Status = 100

  /** `101 Switching Protocols`. */
  val SwitchingProtocols: Status = 101

  /** `102 Processing`. */
  val Processing: Status = 102

  /** `103 Early Hints`. */
  val EarlyHints: Status = 103

  /** `200 OK`. */
  val Ok: Status = 200

  /** `201 Created`. */
  val Created: Status = 201

  /** `202 Accepted`. */
  val Accepted: Status = 202

  /** `203 Non-Authoritative Information`. */
  val NonAuthoritativeInformation: Status = 203

  /** `204 No Content`. */
  val NoContent: Status = 204

  /** `205 Reset Content`. */
  val ResetContent: Status = 205

  /** `206 Partial Content`. */
  val PartialContent: Status = 206

  /** `207 Multi-Status`. */
  val MultiStatus: Status = 207

  /** `300 Multiple Choices`. */
  val MultipleChoices: Status = 300

  /** `301 Moved Permanently`. */
  val MovedPermanently: Status = 301

  /** `302 Found`. */
  val Found: Status = 302

  /** `303 See Other`. */
  val SeeOther: Status = 303

  /** `304 Not Modified`. */
  val NotModified: Status = 304

  /** `307 Temporary Redirect`. */
  val TemporaryRedirect: Status = 307

  /** `308 Permanent Redirect`. */
  val PermanentRedirect: Status = 308

  /** `400 Bad Request`. */
  val BadRequest: Status = 400

  /** `401 Unauthorized`. */
  val Unauthorized: Status = 401

  /** `402 Payment Required`. */
  val PaymentRequired: Status = 402

  /** `403 Forbidden`. */
  val Forbidden: Status = 403

  /** `404 Not Found`. */
  val NotFound: Status = 404

  /** `405 Method Not Allowed`. */
  val MethodNotAllowed: Status = 405

  /** `406 Not Acceptable`. */
  val NotAcceptable: Status = 406

  /** `407 Proxy Authentication Required`. */
  val ProxyAuthenticationRequired: Status = 407

  /** `408 Request Timeout`. */
  val RequestTimeout: Status = 408

  /** `409 Conflict`. */
  val Conflict: Status = 409

  /** `410 Gone`. */
  val Gone: Status = 410

  /** `411 Length Required`. */
  val LengthRequired: Status = 411

  /** `412 Precondition Failed`. */
  val PreconditionFailed: Status = 412

  /** `413 Payload Too Large`. The same value as `RequestEntityTooLarge`. */
  val PayloadTooLarge: Status = 413

  /**
   * `413 Payload Too Large`. The older name of `PayloadTooLarge`, the same
   * value; `text` gives `Payload Too Large`.
   */
  val RequestEntityTooLarge: Status = 413

  /** `414 URI Too Long`. */
  val UriTooLong: Status = 414

  /** `415 Unsupported Media Type`. */
  val UnsupportedMediaType: Status = 415

  /** `416 Range Not Satisfiable`. */
  val RangeNotSatisfiable: Status = 416

  /** `417 Expectation Failed`. */
  val ExpectationFailed: Status = 417

  /** `418 I'm a Teapot`. */
  val ImATeapot: Status = 418

  /** `421 Misdirected Request`. */
  val MisdirectedRequest: Status = 421

  /** `422 Unprocessable Entity`. */
  val UnprocessableEntity: Status = 422

  /** `425 Too Early`. */
  val TooEarly: Status = 425

  /** `426 Upgrade Required`. */
  val UpgradeRequired: Status = 426

  /** `428 Precondition Required`. */
  val PreconditionRequired: Status = 428

  /** `429 Too Many Requests`. */
  val TooManyRequests: Status = 429

  /** `431 Request Header Fields Too Large`. */
  val RequestHeaderFieldsTooLarge: Status = 431

  /** `451 Unavailable For Legal Reasons`. */
  val UnavailableForLegalReasons: Status = 451

  /** `500 Internal Server Error`. */
  val InternalServerError: Status = 500

  /** `501 Not Implemented`. */
  val NotImplemented: Status = 501

  /** `502 Bad Gateway`. */
  val BadGateway: Status = 502

  /** `503 Service Unavailable`. */
  val ServiceUnavailable: Status = 503

  /** `504 Gateway Timeout`. */
  val GatewayTimeout: Status = 504

  /** `505 HTTP Version Not Supported`. */
  val HttpVersionNotSupported: Status = 505

  /** `507 Insufficient Storage`. */
  val InsufficientStorage: Status = 507

  /** `511 Network Authentication Required`. */
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
