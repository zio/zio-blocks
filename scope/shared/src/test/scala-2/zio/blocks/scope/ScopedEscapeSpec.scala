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
        closeable.use { implicit scope =>
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
        closeable.use { implicit scope =>
          val scopedStream = $[MockInputStream]

          val n: Int             = scopedStream.$(_.read())
          val bytes: Array[Byte] = scopedStream.$(_.readAllBytes())

          assertTrue(n == 42, bytes.length == 3)
        }
      },
      test("String escapes unscoped") {
        val closeable = injected(MockResponse.ok("hello"))
        closeable.use { implicit scope =>
          val scopedResp = $[MockResponse]

          val data: String = scopedResp.$(_.data)

          assertTrue(data == "hello")
        }
      }
    ),
    suite("$ operator with resourceful types")(
      test("Resourceful types stay scoped") {
        val closeable = injected(new MockRequest(new MockInputStream))
        closeable.use { implicit scope =>
          val scopedReq = $[MockRequest]

          // body is not Unscoped, so it stays scoped
          val body = scopedReq.$(_.body)

          // Can use body with $ operator to get data
          val n: Int = body.$(_.read())
          assertTrue(n == 42)
        }
      }
    ),
    suite("For-comprehension and tag algebra")(
      test("map preserves tag") {
        val closeable = injected(new MockRequest(new MockInputStream))
        closeable.use { implicit scope =>
          val request = $[MockRequest]

          val body = request.map(_.body)

          // Verify body is scoped by using $ operator
          val n: Int = body.$(_.read())
          assertTrue(n == 42)
        }
      },
      test("map preserves tag type via .get") {
        // Verify that map returns a scoped value
        val closeable = injected(new MockInputStream)
        closeable.use { implicit scope =>
          val scoped = $[MockInputStream]
          val mapped = scoped.map(_.read())
          // mapped is Int @@ Tag - verify we can use .get to extract
          val result: Int = mapped.get
          assertTrue(result == 42)
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
      test("flatMap combines tags with mixed types") {
        // Tests type algebra of scoped values in isolation.
        // Uses artificial String tag (not from a real scope) to verify
        // that map/flatMap preserve and combine tags correctly.
        val t1: Int @@ String    = @@.scoped(1)
        val t2: String @@ String = @@.scoped("hello")

        val combined = for {
          x <- t1
          y <- t2
        } yield (x, y)

        // Compilation proves type algebra works; verify runtime behavior
        val result: Int @@ String = combined.map { case (a, b) => a + b.length }
        assertTrue(@@.unscoped(result) == 6)
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
    ),
    suite("Nested scope tag hierarchy")(
      test("child scope can use parent-scoped values") {
        val parent = injected(new MockInputStream)
        parent.use { implicit parentScope =>
          val parentScoped = $[MockInputStream]

          val child = injected(new MockRequest(new MockInputStream))
          child.use { implicit childScope =>
            // This should compile: child scope's Tag is a supertype of parent's Tag
            val n: Int = parentScoped.$(_.read())
            assertTrue(n == 42)
          }
        }
      },
      test("deeply nested scopes maintain tag hierarchy") {
        val grandparent = injected(new MockInputStream)
        grandparent.use { implicit gpScope =>
          val gpScoped = $[MockInputStream]

          val parent = injected(new MockRequest(new MockInputStream))
          parent.use { implicit pScope =>
            val pScoped = $[MockRequest]

            val child = injected(MockResponse.ok("hello"))
            child.use { implicit cScope =>
              // Should be able to use grandparent-scoped value from deeply nested scope
              val n1: Int = gpScoped.$(_.read())
              // Should be able to use parent-scoped value too
              val body    = pScoped.$(_.body)
              val n2: Int = body.$(_.read())
              assertTrue(n1 == 42, n2 == 42)
            }
          }
        }
      }
    ),
    suite("Unscoped types escape")(
      test("Int escapes unscoped via $ operator") {
        val closeable = injected(new MockInputStream)
        closeable.use { implicit scope =>
          val scoped = $[MockInputStream]
          val n: Int = scoped.$(_.read())
          assertTrue(n == 42)
        }
      },
      test("resourceful types stay scoped via $ operator") {
        class Inner { def value: Int = 99          }
        class Outer { def inner: Inner = new Inner }

        val closeable = injected(new Outer)
        closeable.use { implicit scope =>
          val scoped = $[Outer]

          // inner is not Unscoped, so it stays scoped (can't assign to raw Inner)
          val inner = scoped.$(_.inner)

          // Can extract value from inner with same scope
          val n: Int = inner.$(_.value)

          assertTrue(n == 99)
        }
      }
    )
  )
}
