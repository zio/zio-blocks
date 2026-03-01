package zio.http

import zio.test._

object MethodSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Method")(
    suite("case objects")(
      test("GET has correct name and ordinal") {
        assertTrue(Method.GET.name == "GET", Method.GET.ordinal == 0)
      },
      test("POST has correct name and ordinal") {
        assertTrue(Method.POST.name == "POST", Method.POST.ordinal == 1)
      },
      test("PUT has correct name and ordinal") {
        assertTrue(Method.PUT.name == "PUT", Method.PUT.ordinal == 2)
      },
      test("DELETE has correct name and ordinal") {
        assertTrue(Method.DELETE.name == "DELETE", Method.DELETE.ordinal == 3)
      },
      test("PATCH has correct name and ordinal") {
        assertTrue(Method.PATCH.name == "PATCH", Method.PATCH.ordinal == 4)
      },
      test("HEAD has correct name and ordinal") {
        assertTrue(Method.HEAD.name == "HEAD", Method.HEAD.ordinal == 5)
      },
      test("OPTIONS has correct name and ordinal") {
        assertTrue(Method.OPTIONS.name == "OPTIONS", Method.OPTIONS.ordinal == 6)
      },
      test("TRACE has correct name and ordinal") {
        assertTrue(Method.TRACE.name == "TRACE", Method.TRACE.ordinal == 7)
      },
      test("CONNECT has correct name and ordinal") {
        assertTrue(Method.CONNECT.name == "CONNECT", Method.CONNECT.ordinal == 8)
      }
    ),
    suite("ordinals")(
      test("ordinal values are unique and dense (0-8)") {
        val ordinals = Method.values.map(_.ordinal).toSet
        assertTrue(ordinals == Set(0, 1, 2, 3, 4, 5, 6, 7, 8))
      }
    ),
    suite("values")(
      test("contains all 9 methods") {
        assertTrue(Method.values.length == 9)
      },
      test("values are indexed by ordinal") {
        assertTrue(
          Method.values.zipWithIndex.forall { case (m, i) => m.ordinal == i }
        )
      }
    ),
    suite("fromString")(
      test("returns Some for valid method name") {
        assertTrue(Method.fromString("GET") == Some(Method.GET))
      },
      test("returns None for unknown method") {
        assertTrue(Method.fromString("UNKNOWN") == None)
      },
      test("is case-sensitive") {
        assertTrue(Method.fromString("get") == None)
      },
      test("resolves all methods") {
        assertTrue(
          Method.fromString("GET") == Some(Method.GET),
          Method.fromString("POST") == Some(Method.POST),
          Method.fromString("PUT") == Some(Method.PUT),
          Method.fromString("DELETE") == Some(Method.DELETE),
          Method.fromString("PATCH") == Some(Method.PATCH),
          Method.fromString("HEAD") == Some(Method.HEAD),
          Method.fromString("OPTIONS") == Some(Method.OPTIONS),
          Method.fromString("TRACE") == Some(Method.TRACE),
          Method.fromString("CONNECT") == Some(Method.CONNECT)
        )
      }
    ),
    suite("render")(
      test("returns the method name") {
        assertTrue(
          Method.render(Method.GET) == "GET",
          Method.render(Method.POST) == "POST",
          Method.render(Method.DELETE) == "DELETE"
        )
      }
    ),
    suite("toString")(
      test("returns the method name") {
        assertTrue(
          Method.GET.toString == "GET",
          Method.POST.toString == "POST",
          Method.DELETE.toString == "DELETE"
        )
      }
    )
  )
}
