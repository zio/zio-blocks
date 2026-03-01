package zio.http.headers

import zio.test._
import zio.blocks.chunk.Chunk

object SecurityHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("SecurityHeaders")(
    suite("XFrameOptions")(
      test("parse deny") {
        assertTrue(XFrameOptions.parse("DENY") == Right(XFrameOptions.Deny))
      },
      test("parse sameorigin") {
        assertTrue(XFrameOptions.parse("SAMEORIGIN") == Right(XFrameOptions.SameOrigin))
      },
      test("parse case-insensitive") {
        assertTrue(XFrameOptions.parse("deny") == Right(XFrameOptions.Deny))
      },
      test("parse invalid returns Left") {
        assertTrue(XFrameOptions.parse("ALLOW-FROM").isLeft)
      },
      test("render deny") {
        assertTrue(XFrameOptions.render(XFrameOptions.Deny) == "DENY")
      },
      test("render sameorigin") {
        assertTrue(XFrameOptions.render(XFrameOptions.SameOrigin) == "SAMEORIGIN")
      },
      test("header name") {
        assertTrue(XFrameOptions.Deny.headerName == "x-frame-options")
      }
    ),
    suite("XRequestedWith")(
      test("parse and render") {
        val result = XRequestedWith.parse("XMLHttpRequest")
        assertTrue(
          result == Right(XRequestedWith("XMLHttpRequest")),
          result.map(_.headerName) == Right("x-requested-with")
        )
      }
    ),
    suite("DNT")(
      test("parse 0") {
        assertTrue(DNT.parse("0") == Right(DNT.TrackingAllowed))
      },
      test("parse 1") {
        assertTrue(DNT.parse("1") == Right(DNT.TrackingNotAllowed))
      },
      test("parse null") {
        assertTrue(DNT.parse("null") == Right(DNT.Unset))
      },
      test("parse invalid returns Left") {
        assertTrue(DNT.parse("2").isLeft)
      },
      test("render 0") {
        assertTrue(DNT.render(DNT.TrackingAllowed) == "0")
      },
      test("render 1") {
        assertTrue(DNT.render(DNT.TrackingNotAllowed) == "1")
      },
      test("render null") {
        assertTrue(DNT.render(DNT.Unset) == "null")
      },
      test("header name") {
        assertTrue(DNT.TrackingAllowed.headerName == "dnt")
      }
    ),
    suite("UpgradeInsecureRequests")(
      test("parse 1") {
        assertTrue(UpgradeInsecureRequests.parse("1") == Right(UpgradeInsecureRequests(true)))
      },
      test("parse 0") {
        assertTrue(UpgradeInsecureRequests.parse("0") == Right(UpgradeInsecureRequests(false)))
      },
      test("parse invalid returns Left") {
        assertTrue(UpgradeInsecureRequests.parse("yes").isLeft)
      },
      test("render true") {
        assertTrue(UpgradeInsecureRequests.render(UpgradeInsecureRequests(true)) == "1")
      },
      test("render false") {
        assertTrue(UpgradeInsecureRequests.render(UpgradeInsecureRequests(false)) == "0")
      },
      test("header name") {
        assertTrue(UpgradeInsecureRequests(true).headerName == "upgrade-insecure-requests")
      }
    ),
    suite("ClearSiteData")(
      test("parse quoted directives") {
        val result = ClearSiteData.parse(""""cache", "cookies"""")
        assertTrue(result == Right(ClearSiteData(Chunk("cache", "cookies"))))
      },
      test("parse single directive") {
        val result = ClearSiteData.parse(""""*"""")
        assertTrue(result == Right(ClearSiteData(Chunk("*"))))
      },
      test("parse empty returns Left") {
        assertTrue(ClearSiteData.parse("").isLeft)
      },
      test("render") {
        val h = ClearSiteData(Chunk("cache", "cookies"))
        assertTrue(ClearSiteData.render(h) == """"cache", "cookies"""")
      },
      test("header name") {
        assertTrue(ClearSiteData(Chunk("cache")).headerName == "clear-site-data")
      }
    )
  )
}
