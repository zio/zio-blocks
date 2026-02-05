package zio.blocks.scope.experimental

import zio.test._

// === Mock types for testing ===

class InputStream {
  def read(): Int                 = 42
  def readAllBytes(): Array[Byte] = Array(1, 2, 3)
}

class Request(val body: InputStream)

class Response(val data: String)
object Response {
  def ok(s: String): Response = new Response(s)
  given SafeData[Response]    = new SafeData[Response] {}
}

object ScopedTaggingSpec extends ZIOSpecDefault {

  def spec = suite("ScopedTagging")(
    test("SafeData types escape untagged") {
      given scope: Scope = new Scope { type Tag = this.type }

      val req: Request @@ scope.Tag = @@.tag(new Request(new InputStream))

      // body is InputStream, no SafeData → stays tagged
      val body = req $ (_.body)
      // body: InputStream @@ scope.Tag

      // readAllBytes returns Array[Byte], SafeData[Array[Byte]] exists → untagged
      val bytes: Array[Byte] = body $ (_.readAllBytes())

      // read returns Int, SafeData[Int] exists → untagged
      val n: Int = body $ (_.read())

      // Can use untagged values directly
      val response = Response.ok(new String(bytes))

      assertTrue(n == 42, bytes.length == 3, response.data == "\u0001\u0002\u0003")
    },
    test("Resourceful types stay tagged") {
      given scope: Scope = new Scope { type Tag = this.type }

      val req: Request @@ scope.Tag = @@.tag(new Request(new InputStream))

      // body is InputStream, no SafeData → stays tagged
      val body = req $ (_.body)

      // This should be InputStream @@ scope.Tag, not InputStream
      // We verify by checking the type compiles correctly
      val bodyTagged: InputStream @@ scope.Tag = body

      assertTrue(true)
    }
  )
}
