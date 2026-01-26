package zio.blocks.schema

import zio.blocks.schema.json.JsonTestUtils._
import zio.test._

object ModifierSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ModifierSpec")(
    suite("Schema roundtrip")(
      test("transient roundtrips through DynamicValue") {
        val value = Modifier.transient()
        val dyn   = Schema[Modifier.transient].toDynamicValue(value)
        val back  = Schema[Modifier.transient].fromDynamicValue(dyn)
        assertTrue(back == Right(value))
      },
      test("rename roundtrips") {
        val value = Modifier.rename("newName")
        roundTrip(value: Modifier.Term, """{"rename":{"name":"newName"}}""")
      },
      test("alias roundtrips") {
        val value = Modifier.alias("aliasName")
        roundTrip(value: Modifier.Term, """{"alias":{"name":"aliasName"}}""")
      },
      test("config roundtrips") {
        val value = Modifier.config("key1", "value1")
        roundTrip(value: Modifier.Term, """{"config":{"key":"key1","value":"value1"}}""")
      }
    )
  )
}
