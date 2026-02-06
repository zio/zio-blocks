package zio.blocks.scope

import zio.test._

class MockInputStream {
  def read(): Int                 = 42
  def readAllBytes(): Array[Byte] = Array(1, 2, 3)
}

class MockRequest(val body: MockInputStream)

class MockResponse(val data: String)
object MockResponse {
  def ok(s: String): MockResponse               = new MockResponse(s)
  implicit val unscoped: Unscoped[MockResponse] = new Unscoped[MockResponse] {}
}

object ScopedEscapeSpec extends ZIOSpecDefault {

  implicit val globalScope: Scope.Any = Scope.global

  def spec = suite("Scoped Escape Prevention (Scala 2)")(
    suite("$[T] returns scoped value")(
      test("$[T] returns scoped service from scope") {
        val closeable = injected(new MockInputStream)
        closeable.run { implicit scope =>
          val scopedStream = $[MockInputStream]
          // Verify it's scoped by using $ operator
          val n: Int = scopedStream.$(_.read())
          assertTrue(n == 42)
        }
      }
    ),
    suite("$ operator with Unscoped types")(
      test("Unscoped types escape unscoped via $ operator") {
        val closeable = injected(new MockInputStream)
        closeable.run { implicit scope =>
          val scopedStream = $[MockInputStream]

          val n: Int             = scopedStream.$(_.read())
          val bytes: Array[Byte] = scopedStream.$(_.readAllBytes())

          assertTrue(n == 42, bytes.length == 3)
        }
      },
      test("String escapes unscoped") {
        val closeable = injected(MockResponse.ok("hello"))
        closeable.run { implicit scope =>
          val scopedResp = $[MockResponse]

          val data: String = scopedResp.$(_.data)

          assertTrue(data == "hello")
        }
      }
    ),
    suite("$ operator with resourceful types")(
      test("Resourceful types stay scoped") {
        val closeable = injected(new MockRequest(new MockInputStream))
        closeable.run { implicit scope =>
          val scopedReq = $[MockRequest]

          // body is not Unscoped, so it stays scoped
          val body = scopedReq.$(_.body)

          // Can use body with $ operator to get data
          val n: Int = body.$(_.read())
          assertTrue(n == 42)
        }
      }
    ),
    suite("For-comprehension support")(
      test("map preserves tag") {
        val closeable = injected(new MockRequest(new MockInputStream))
        closeable.run { implicit scope =>
          val request = $[MockRequest]

          val body = request.map(_.body)

          // Verify body is scoped by using $ operator
          val n: Int = body.$(_.read())
          assertTrue(n == 42)
        }
      },
      test("flatMap combines tags") {
        // Tests type algebra of scoped values in isolation.
        // Uses artificial String tag (not from a real scope) to verify
        // that map/flatMap preserve and combine tags correctly.
        val stream1: MockInputStream @@ String = @@.scoped(new MockInputStream)
        val stream2: MockInputStream @@ String = @@.scoped(new MockInputStream)

        val combined = stream1.flatMap(s1 => stream2.map(s2 => (s1, s2)))
        // Compilation proves type algebra works; verify runtime behavior
        val result: Int @@ String = combined.map { case (a, b) => a.read() + b.read() }
        assertTrue(@@.unscoped(result) == 84)
      },
      test("for-comprehension syntax works") {
        // Tests type algebra of scoped values in isolation.
        // Uses artificial String tag (not from a real scope) to verify
        // that for-comprehension desugaring works correctly.
        val stream: MockInputStream @@ String = @@.scoped(new MockInputStream)
        val request: MockRequest @@ String    = @@.scoped(new MockRequest(new MockInputStream))

        val combined = for {
          s <- stream
          r <- request
        } yield (s, r)

        // Compilation proves type algebra works; verify runtime behavior
        val result: Int @@ String = combined.map { case (a, _) => a.read() }
        assertTrue(@@.unscoped(result) == 42)
      }
    )
  )
}
