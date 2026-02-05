package zio.blocks.scope

import zio.test._

class MockInputStream {
  def read(): Int                 = 42
  def readAllBytes(): Array[Byte] = Array(1, 2, 3)
}

class MockRequest(val body: MockInputStream)

class MockResponse(val data: String)
object MockResponse {
  def ok(s: String): MockResponse = new MockResponse(s)
  given Unscoped[MockResponse]    = new Unscoped[MockResponse] {}
}

object TaggedEscapeSpec extends ZIOSpecDefault {

  given Scope.Any = Scope.global

  def spec = suite("Tagged Escape Prevention")(
    suite("Closeable.value returns tagged value")(
      test("value method returns tagged head") {
        val closeable                                      = injectedValue(new MockInputStream)
        val taggedStream: MockInputStream @@ closeable.Tag = closeable.value
        closeable.closeOrThrow()
        assertTrue(true)
      }
    ),
    suite("$ operator with Unscoped types")(
      test("Unscoped types escape untagged via $ operator") {
        val closeable                                      = injectedValue(new MockInputStream)
        val taggedStream: MockInputStream @@ closeable.Tag = closeable.value

        val n: Int             = taggedStream.$(_.read())(using closeable)(using summon)
        val bytes: Array[Byte] = taggedStream.$(_.readAllBytes())(using closeable)(using summon)

        closeable.closeOrThrow()
        assertTrue(n == 42, bytes.length == 3)
      },
      test("String escapes untagged") {
        val closeable                                 = injectedValue(MockResponse.ok("hello"))
        val taggedResp: MockResponse @@ closeable.Tag = closeable.value

        val data: String = taggedResp.$(_.data)(using closeable)(using summon)

        closeable.closeOrThrow()
        assertTrue(data == "hello")
      }
    ),
    suite("$ operator with resourceful types")(
      test("Resourceful types stay tagged") {
        val closeable                               = injectedValue(new MockRequest(new MockInputStream))
        val taggedReq: MockRequest @@ closeable.Tag = closeable.value

        val body: MockInputStream @@ closeable.Tag = taggedReq.$(_.body)(using closeable)(using summon)

        closeable.closeOrThrow()
        assertTrue(true)
      }
    ),
    suite("For-comprehension support")(
      test("map preserves tag") {
        val closeable                             = injectedValue(new MockRequest(new MockInputStream))
        val request: MockRequest @@ closeable.Tag = closeable.value

        val body: MockInputStream @@ closeable.Tag = request.map(_.body)

        closeable.closeOrThrow()
        assertTrue(true)
      },
      test("flatMap combines tags via union") {
        val closeable1 = injectedValue(new MockInputStream)
        val closeable2 = injectedValue(new MockInputStream)

        val stream1: MockInputStream @@ closeable1.Tag = closeable1.value
        val stream2: MockInputStream @@ closeable2.Tag = closeable2.value

        val combined: (MockInputStream, MockInputStream) @@ (closeable1.Tag | closeable2.Tag) =
          stream1.flatMap(s1 => stream2.map(s2 => (s1, s2)))

        closeable1.closeOrThrow()
        closeable2.closeOrThrow()
        assertTrue(true)
      },
      test("for-comprehension syntax works") {
        val closeable1 = injectedValue(new MockInputStream)
        val closeable2 = injectedValue(new MockRequest(new MockInputStream))

        val stream: MockInputStream @@ closeable1.Tag = closeable1.value
        val request: MockRequest @@ closeable2.Tag    = closeable2.value

        val combined: (MockInputStream, MockRequest) @@ (closeable1.Tag | closeable2.Tag) = for {
          s <- stream
          r <- request
        } yield (s, r)

        closeable1.closeOrThrow()
        closeable2.closeOrThrow()
        assertTrue(true)
      }
    )
  )
}
