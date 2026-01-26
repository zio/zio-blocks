package zio.blocks.schema

import zio.blocks.schema.json.JsonTestUtils._
import zio.test._

object ModifierSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ModifierSpec")(
    suite("Modifier.Term roundtrips")(
      test("transient roundtrips through JSON") {
        val value = Modifier.transient()
        roundTrip(value: Modifier.Term, """{"transient":{}}""")
      },
      test("rename roundtrips through JSON") {
        val value = Modifier.rename("newName")
        roundTrip(value: Modifier.Term, """{"rename":{"name":"newName"}}""")
      },
      test("alias roundtrips through JSON") {
        val value = Modifier.alias("aliasName")
        roundTrip(value: Modifier.Term, """{"alias":{"name":"aliasName"}}""")
      },
      test("config roundtrips through JSON as Term") {
        val value = Modifier.config("key1", "value1")
        roundTrip(value: Modifier.Term, """{"config":{"key":"key1","value":"value1"}}""")
      }
    ),
    suite("Modifier.Reflect roundtrips")(
      test("config roundtrips through JSON as Reflect") {
        val value = Modifier.config("protobuf.field-id", "42")
        roundTrip(value: Modifier.Reflect, """{"config":{"key":"protobuf.field-id","value":"42"}}""")
      }
    ),
    suite("Top-level Modifier roundtrips")(
      test("transient roundtrips as Modifier") {
        val value: Modifier = Modifier.transient()
        roundTrip(value, """{"transient":{}}""")
      },
      test("rename roundtrips as Modifier") {
        val value: Modifier = Modifier.rename("fieldName")
        roundTrip(value, """{"rename":{"name":"fieldName"}}""")
      },
      test("alias roundtrips as Modifier") {
        val value: Modifier = Modifier.alias("altName")
        roundTrip(value, """{"alias":{"name":"altName"}}""")
      },
      test("config roundtrips as Modifier") {
        val value: Modifier = Modifier.config("json.name", "customName")
        roundTrip(value, """{"config":{"key":"json.name","value":"customName"}}""")
      }
    )
  )
}
