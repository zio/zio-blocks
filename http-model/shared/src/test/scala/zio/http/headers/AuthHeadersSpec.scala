package zio.http.headers

import zio.test._

object AuthHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("AuthHeaders")(
    suite("Authorization")(
      test("parse Basic") {
        val encoded = java.util.Base64.getEncoder.encodeToString("user:pass".getBytes("UTF-8"))
        val result  = Authorization.parse(s"Basic $encoded")
        assertTrue(
          result == Right(Authorization.Basic("user", "pass")),
          result.map(_.headerName) == Right("authorization")
        )
      },
      test("parse Basic with colon in password") {
        val encoded = java.util.Base64.getEncoder.encodeToString("user:p:a:ss".getBytes("UTF-8"))
        val result  = Authorization.parse(s"Basic $encoded")
        assertTrue(result == Right(Authorization.Basic("user", "p:a:ss")))
      },
      test("parse Bearer") {
        val result = Authorization.parse("Bearer mytoken123")
        assertTrue(result == Right(Authorization.Bearer("mytoken123")))
      },
      test("parse Digest") {
        val result = Authorization.parse("""Digest username="alice", realm="example"""")
        assertTrue(
          result.isRight,
          result.map(_.asInstanceOf[Authorization.Digest].params("username")) == Right("alice"),
          result.map(_.asInstanceOf[Authorization.Digest].params("realm")) == Right("example")
        )
      },
      test("parse unknown scheme as Unparsed") {
        val result = Authorization.parse("CustomScheme params123")
        assertTrue(result == Right(Authorization.Unparsed("CustomScheme", "params123")))
      },
      test("parse empty returns Left") {
        assertTrue(Authorization.parse("").isLeft)
      },
      test("parse no space returns Left") {
        assertTrue(Authorization.parse("Bearer").isLeft)
      },
      test("parse invalid base64 returns Left") {
        assertTrue(Authorization.parse("Basic !!invalid!!").isLeft)
      },
      test("parse basic without colon returns Left") {
        val encoded = java.util.Base64.getEncoder.encodeToString("nocolon".getBytes("UTF-8"))
        assertTrue(Authorization.parse(s"Basic $encoded").isLeft)
      },
      test("render Basic") {
        val h        = Authorization.Basic("user", "pass")
        val rendered = Authorization.render(h)
        val encoded  = java.util.Base64.getEncoder.encodeToString("user:pass".getBytes("UTF-8"))
        assertTrue(rendered == s"Basic $encoded")
      },
      test("render Bearer") {
        assertTrue(Authorization.render(Authorization.Bearer("tok")) == "Bearer tok")
      },
      test("render Digest") {
        val h = Authorization.Digest(Map("username" -> "alice"))
        assertTrue(Authorization.render(h).startsWith("Digest "))
      },
      test("render Unparsed") {
        assertTrue(Authorization.render(Authorization.Unparsed("Custom", "data")) == "Custom data")
      },
      test("round-trip Basic") {
        val original = Authorization.Basic("admin", "secret")
        val rendered = Authorization.render(original)
        assertTrue(Authorization.parse(rendered) == Right(original))
      },
      test("round-trip Bearer") {
        val original = Authorization.Bearer("abc123")
        val rendered = Authorization.render(original)
        assertTrue(Authorization.parse(rendered) == Right(original))
      }
    ),
    suite("ProxyAuthorization")(
      test("parse Basic") {
        val encoded = java.util.Base64.getEncoder.encodeToString("user:pass".getBytes("UTF-8"))
        val result  = ProxyAuthorization.parse(s"Basic $encoded")
        assertTrue(
          result == Right(ProxyAuthorization.Basic("user", "pass")),
          result.map(_.headerName) == Right("proxy-authorization")
        )
      },
      test("parse Bearer") {
        val result = ProxyAuthorization.parse("Bearer proxytoken")
        assertTrue(result == Right(ProxyAuthorization.Bearer("proxytoken")))
      },
      test("parse empty returns Left") {
        assertTrue(ProxyAuthorization.parse("").isLeft)
      },
      test("render Basic") {
        val h        = ProxyAuthorization.Basic("user", "pass")
        val rendered = ProxyAuthorization.render(h)
        val encoded  = java.util.Base64.getEncoder.encodeToString("user:pass".getBytes("UTF-8"))
        assertTrue(rendered == s"Basic $encoded")
      },
      test("round-trip Bearer") {
        val original = ProxyAuthorization.Bearer("xyz")
        val rendered = ProxyAuthorization.render(original)
        assertTrue(ProxyAuthorization.parse(rendered) == Right(original))
      },
      test("parse Digest") {
        val result = ProxyAuthorization.parse("""Digest username="bob", realm="proxy"""")
        assertTrue(
          result.isRight,
          result.map(_.asInstanceOf[ProxyAuthorization.Digest].params("username")) == Right("bob")
        )
      },
      test("parse unknown scheme as Unparsed") {
        val result = ProxyAuthorization.parse("Custom data123")
        assertTrue(result == Right(ProxyAuthorization.Unparsed("Custom", "data123")))
      },
      test("parse no space returns Left") {
        assertTrue(ProxyAuthorization.parse("Bearer").isLeft)
      },
      test("parse invalid base64 returns Left") {
        assertTrue(ProxyAuthorization.parse("Basic !!invalid!!").isLeft)
      },
      test("parse basic without colon returns Left") {
        val encoded = java.util.Base64.getEncoder.encodeToString("nocolon".getBytes("UTF-8"))
        assertTrue(ProxyAuthorization.parse(s"Basic $encoded").isLeft)
      },
      test("render Bearer") {
        assertTrue(ProxyAuthorization.render(ProxyAuthorization.Bearer("tok")) == "Bearer tok")
      },
      test("render Digest") {
        val h = ProxyAuthorization.Digest(Map("username" -> "alice"))
        assertTrue(ProxyAuthorization.render(h).startsWith("Digest "))
      },
      test("render Unparsed") {
        assertTrue(ProxyAuthorization.render(ProxyAuthorization.Unparsed("Scheme", "val")) == "Scheme val")
      }
    ),
    suite("WWWAuthenticate")(
      test("parse scheme only") {
        val result = WWWAuthenticate.parse("Bearer")
        assertTrue(result == Right(WWWAuthenticate("Bearer", Map.empty)))
      },
      test("parse scheme with params") {
        val result = WWWAuthenticate.parse("""Bearer realm="example"""")
        assertTrue(
          result == Right(WWWAuthenticate("Bearer", Map("realm" -> "example"))),
          result.map(_.headerName) == Right("www-authenticate")
        )
      },
      test("parse empty returns Left") {
        assertTrue(WWWAuthenticate.parse("").isLeft)
      },
      test("render scheme only") {
        assertTrue(WWWAuthenticate.render(WWWAuthenticate("Basic", Map.empty)) == "Basic")
      },
      test("render scheme with params") {
        val h = WWWAuthenticate("Bearer", Map("realm" -> "example"))
        assertTrue(WWWAuthenticate.render(h).startsWith("Bearer "))
      },
      test("round-trip scheme only") {
        val original = WWWAuthenticate("Negotiate", Map.empty)
        val rendered = WWWAuthenticate.render(original)
        assertTrue(WWWAuthenticate.parse(rendered) == Right(original))
      },
      test("round-trip scheme with params") {
        val original = WWWAuthenticate("Bearer", Map("realm" -> "test"))
        val rendered = WWWAuthenticate.render(original)
        assertTrue(WWWAuthenticate.parse(rendered) == Right(original))
      }
    ),
    suite("ProxyAuthenticate")(
      test("parse scheme only") {
        val result = ProxyAuthenticate.parse("Basic")
        assertTrue(
          result == Right(ProxyAuthenticate("Basic", Map.empty)),
          result.map(_.headerName) == Right("proxy-authenticate")
        )
      },
      test("parse scheme with params") {
        val result = ProxyAuthenticate.parse("""Basic realm="proxy"""")
        assertTrue(result == Right(ProxyAuthenticate("Basic", Map("realm" -> "proxy"))))
      },
      test("parse empty returns Left") {
        assertTrue(ProxyAuthenticate.parse("").isLeft)
      },
      test("render scheme only") {
        assertTrue(ProxyAuthenticate.render(ProxyAuthenticate("Basic", Map.empty)) == "Basic")
      },
      test("render scheme with params") {
        val h = ProxyAuthenticate("Bearer", Map("realm" -> "proxy"))
        assertTrue(ProxyAuthenticate.render(h).startsWith("Bearer "))
      },
      test("round-trip with params") {
        val original = ProxyAuthenticate("Bearer", Map("realm" -> "proxy"))
        val rendered = ProxyAuthenticate.render(original)
        assertTrue(ProxyAuthenticate.parse(rendered) == Right(original))
      },
      test("round-trip") {
        val original = ProxyAuthenticate("Basic", Map.empty)
        val rendered = ProxyAuthenticate.render(original)
        assertTrue(ProxyAuthenticate.parse(rendered) == Right(original))
      }
    )
  )
}
