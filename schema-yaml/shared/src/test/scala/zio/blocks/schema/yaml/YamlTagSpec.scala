package zio.blocks.schema.yaml

import zio.test._

object YamlTagSpec extends YamlBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlTag")(
    suite("fromString")(
      test("!!str") {
        assertTrue(YamlTag.fromString("!!str") == YamlTag.Str)
      },
      test("!!bool") {
        assertTrue(YamlTag.fromString("!!bool") == YamlTag.Bool)
      },
      test("!!int") {
        assertTrue(YamlTag.fromString("!!int") == YamlTag.Int)
      },
      test("!!float") {
        assertTrue(YamlTag.fromString("!!float") == YamlTag.Float)
      },
      test("!!null") {
        assertTrue(YamlTag.fromString("!!null") == YamlTag.Null)
      },
      test("!!seq") {
        assertTrue(YamlTag.fromString("!!seq") == YamlTag.Seq)
      },
      test("!!map") {
        assertTrue(YamlTag.fromString("!!map") == YamlTag.Map)
      },
      test("!!timestamp") {
        assertTrue(YamlTag.fromString("!!timestamp") == YamlTag.Timestamp)
      },
      test("custom tag") {
        assertTrue(YamlTag.fromString("!my-tag") == YamlTag.Custom("!my-tag"))
      }
    ),
    suite("toTagString")(
      test("Str") {
        assertTrue(YamlTag.toTagString(YamlTag.Str) == "!!str")
      },
      test("Bool") {
        assertTrue(YamlTag.toTagString(YamlTag.Bool) == "!!bool")
      },
      test("Int") {
        assertTrue(YamlTag.toTagString(YamlTag.Int) == "!!int")
      },
      test("Float") {
        assertTrue(YamlTag.toTagString(YamlTag.Float) == "!!float")
      },
      test("Null") {
        assertTrue(YamlTag.toTagString(YamlTag.Null) == "!!null")
      },
      test("Seq") {
        assertTrue(YamlTag.toTagString(YamlTag.Seq) == "!!seq")
      },
      test("Map") {
        assertTrue(YamlTag.toTagString(YamlTag.Map) == "!!map")
      },
      test("Timestamp") {
        assertTrue(YamlTag.toTagString(YamlTag.Timestamp) == "!!timestamp")
      },
      test("Custom") {
        assertTrue(YamlTag.toTagString(YamlTag.Custom("!custom")) == "!custom")
      }
    ),
    suite("round-trip")(
      test("all standard tags round-trip") {
        val tags = List(
          YamlTag.Str,
          YamlTag.Bool,
          YamlTag.Int,
          YamlTag.Float,
          YamlTag.Null,
          YamlTag.Seq,
          YamlTag.Map,
          YamlTag.Timestamp
        )
        assertTrue(tags.forall(t => YamlTag.fromString(YamlTag.toTagString(t)) == t))
      },
      test("custom tag round-trips") {
        val tag = YamlTag.Custom("!my-custom-tag")
        assertTrue(YamlTag.fromString(YamlTag.toTagString(tag)) == tag)
      }
    ),
    suite("val aliases")(
      test("str alias") {
        assertTrue(YamlTag.str == YamlTag.Str)
      },
      test("bool alias") {
        assertTrue(YamlTag.bool == YamlTag.Bool)
      },
      test("int alias") {
        assertTrue(YamlTag.int == YamlTag.Int)
      },
      test("float alias") {
        assertTrue(YamlTag.float == YamlTag.Float)
      },
      test("null alias") {
        assertTrue(YamlTag.`null` == YamlTag.Null)
      },
      test("seq alias") {
        assertTrue(YamlTag.seq == YamlTag.Seq)
      },
      test("map alias") {
        assertTrue(YamlTag.map == YamlTag.Map)
      },
      test("timestamp alias") {
        assertTrue(YamlTag.timestamp == YamlTag.Timestamp)
      }
    )
  )
}
