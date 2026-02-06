package zio.blocks.scope

import scala.annotation.nowarn
import zio.test._

object LeakSpec extends ZIOSpecDefault {

  given Scope.Any = Scope.global

  class Resource(val value: Int) {
    def getData(): String = s"data-$value"
  }

  class Request {
    val body: Resource = new Resource(42)
  }

  def spec = suite("LeakSpec")(
    suite("leak function")(
      test("leak unwraps scoped value to raw value") {
        val closeable = injected(new Resource(123))
        closeable.use {
          val scoped = $[Resource]
          // leak should return the raw Resource
          val raw: Resource = leak(scoped): @nowarn("msg=is being leaked")
          assertTrue(raw.value == 123)
        }
      },
      test("leak works with chained access") {
        val closeable = injected(new Request)
        closeable.use {
          val request = $[Request]
          // Simulate $[Request].body.getData() pattern
          val data: String = (leak(request.map(_.body)): @nowarn("msg=is being leaked")) match {
            case body => body.getData()
          }
          assertTrue(data == "data-42")
        }
      },
      test("leaked value can be passed to external API") {
        def legacyApi(resource: Resource): Int = resource.value * 2

        val closeable = injected(new Resource(21))
        closeable.use {
          val scoped = $[Resource]
          val result = legacyApi(leak(scoped): @nowarn("msg=is being leaked"))
          assertTrue(result == 42)
        }
      }
    )
  )
}
