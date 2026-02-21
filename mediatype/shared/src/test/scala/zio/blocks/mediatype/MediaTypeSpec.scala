package zio.blocks.mediatype

import zio.test._

object MediaTypeSpec extends MediaTypeBaseSpec {
  def spec = suite("MediaType")(
    suite("construction")(
      test("creates MediaType with all fields") {
        val mt = MediaType(
          mainType = "application",
          subType = "json",
          compressible = true,
          binary = false,
          fileExtensions = List("json"),
          extensions = Map("source" -> "iana"),
          parameters = Map("charset" -> "utf-8")
        )
        assertTrue(
          mt.mainType == "application",
          mt.subType == "json",
          mt.compressible == true,
          mt.binary == false,
          mt.fileExtensions == List("json"),
          mt.extensions == Map("source" -> "iana"),
          mt.parameters == Map("charset" -> "utf-8")
        )
      },
      test("provides default values") {
        val mt = MediaType("text", "plain")
        assertTrue(
          mt.compressible == false,
          mt.binary == false,
          mt.fileExtensions == Nil,
          mt.extensions == Map.empty[String, String],
          mt.parameters == Map.empty[String, String]
        )
      }
    ),
    suite("fullType")(
      test("combines mainType and subType") {
        val mt = MediaType("application", "json")
        assertTrue(mt.fullType == "application/json")
      },
      test("works with wildcards") {
        assertTrue(
          MediaType("*", "*").fullType == "*/*",
          MediaType("text", "*").fullType == "text/*"
        )
      }
    ),
    suite("matches")(
      test("exact match returns true") {
        val mt1 = MediaType("application", "json")
        val mt2 = MediaType("application", "json")
        assertTrue(mt1.matches(mt2))
      },
      test("wildcard mainType matches any") {
        val any  = MediaType("*", "*")
        val json = MediaType("application", "json")
        assertTrue(any.matches(json))
      },
      test("wildcard subType matches same mainType") {
        val textAny  = MediaType("text", "*")
        val textHtml = MediaType("text", "html")
        val appJson  = MediaType("application", "json")
        assertTrue(
          textAny.matches(textHtml),
          !textAny.matches(appJson)
        )
      },
      test("case insensitive matching") {
        val upper = MediaType("APPLICATION", "JSON")
        val lower = MediaType("application", "json")
        assertTrue(upper.matches(lower))
      },
      test("matches with ignoreParameters=true ignores parameters") {
        val mt1 = MediaType("text", "html", parameters = Map("charset" -> "utf-8"))
        val mt2 = MediaType("text", "html", parameters = Map("charset" -> "iso-8859-1"))
        assertTrue(
          mt1.matches(mt2, ignoreParameters = true),
          !mt1.matches(mt2, ignoreParameters = false)
        )
      },
      test("matches checks parameter subset") {
        val mt1 = MediaType("text", "html", parameters = Map("charset" -> "utf-8"))
        val mt2 = MediaType(
          "text",
          "html",
          parameters = Map("charset" -> "utf-8", "boundary" -> "xxx")
        )
        assertTrue(mt1.matches(mt2))
      }
    )
  )
}
