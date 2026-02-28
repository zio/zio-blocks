package zio.http

import zio.test._

object StatusSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Status")(
    suite("construction")(
      test("Status(200).code == 200") {
        assertTrue(Status(200).code == 200)
      },
      test("Status.Ok.code == 200") {
        assertTrue(Status.Ok.code == 200)
      },
      test("Status.NotFound.code == 404") {
        assertTrue(Status.NotFound.code == 404)
      },
      test("Status.fromInt(200) == Status.Ok") {
        assertTrue(Status.fromInt(200) == Status.Ok)
      }
    ),
    suite("classification")(
      test("isInformational for 1xx") {
        assertTrue(Status(100).isInformational)
      },
      test("isSuccess for 2xx") {
        assertTrue(Status(200).isSuccess)
      },
      test("isRedirection for 3xx") {
        assertTrue(Status(301).isRedirection)
      },
      test("isClientError for 4xx") {
        assertTrue(Status(404).isClientError)
      },
      test("isServerError for 5xx") {
        assertTrue(Status(500).isServerError)
      },
      test("isError for 4xx") {
        assertTrue(Status(400).isError)
      },
      test("isError for 5xx") {
        assertTrue(Status(500).isError)
      },
      test("isError is false for 2xx") {
        assertTrue(!Status(200).isError)
      }
    ),
    suite("text")(
      test("Status.Ok.text == \"OK\"") {
        assertTrue(Status.Ok.text == "OK")
      },
      test("Status.NotFound.text == \"Not Found\"") {
        assertTrue(Status.NotFound.text == "Not Found")
      },
      test("unknown code returns \"Unknown\"") {
        assertTrue(Status(999).text == "Unknown")
      }
    ),
    suite("well-known codes")(
      test("informational codes") {
        assertTrue(
          Status.Continue.code == 100,
          Status.SwitchingProtocols.code == 101,
          Status.Processing.code == 102,
          Status.EarlyHints.code == 103
        )
      },
      test("success codes") {
        assertTrue(
          Status.Ok.code == 200,
          Status.Created.code == 201,
          Status.Accepted.code == 202,
          Status.NonAuthoritativeInformation.code == 203,
          Status.NoContent.code == 204,
          Status.ResetContent.code == 205,
          Status.PartialContent.code == 206,
          Status.MultiStatus.code == 207
        )
      },
      test("redirection codes") {
        assertTrue(
          Status.MultipleChoices.code == 300,
          Status.MovedPermanently.code == 301,
          Status.Found.code == 302,
          Status.SeeOther.code == 303,
          Status.NotModified.code == 304,
          Status.TemporaryRedirect.code == 307,
          Status.PermanentRedirect.code == 308
        )
      },
      test("client error codes") {
        assertTrue(
          Status.BadRequest.code == 400,
          Status.Unauthorized.code == 401,
          Status.PaymentRequired.code == 402,
          Status.Forbidden.code == 403,
          Status.NotFound.code == 404,
          Status.MethodNotAllowed.code == 405,
          Status.NotAcceptable.code == 406,
          Status.ProxyAuthenticationRequired.code == 407,
          Status.RequestTimeout.code == 408,
          Status.Conflict.code == 409,
          Status.Gone.code == 410,
          Status.LengthRequired.code == 411,
          Status.PreconditionFailed.code == 412,
          Status.PayloadTooLarge.code == 413,
          Status.UriTooLong.code == 414,
          Status.UnsupportedMediaType.code == 415,
          Status.RangeNotSatisfiable.code == 416,
          Status.ExpectationFailed.code == 417,
          Status.ImATeapot.code == 418,
          Status.MisdirectedRequest.code == 421,
          Status.UnprocessableEntity.code == 422,
          Status.TooEarly.code == 425,
          Status.UpgradeRequired.code == 426,
          Status.PreconditionRequired.code == 428,
          Status.TooManyRequests.code == 429,
          Status.RequestHeaderFieldsTooLarge.code == 431,
          Status.UnavailableForLegalReasons.code == 451
        )
      },
      test("server error codes") {
        assertTrue(
          Status.InternalServerError.code == 500,
          Status.NotImplemented.code == 501,
          Status.BadGateway.code == 502,
          Status.ServiceUnavailable.code == 503,
          Status.GatewayTimeout.code == 504,
          Status.HttpVersionNotSupported.code == 505,
          Status.InsufficientStorage.code == 507,
          Status.NetworkAuthenticationRequired.code == 511
        )
      }
    )
  )
}
