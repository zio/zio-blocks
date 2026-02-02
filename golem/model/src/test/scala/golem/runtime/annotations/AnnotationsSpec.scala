package golem.runtime.annotations

import zio.test._

object AnnotationsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment, Any] =
    suite("AnnotationsSpec")(
      test("DurabilityMode wire values and parsing") {
        assertTrue(DurabilityMode.Durable.wireValue() == "durable") &&
        assertTrue(DurabilityMode.Ephemeral.wireValue() == "ephemeral") &&
        assertTrue(DurabilityMode.Durable.toString == "durable") &&
        assertTrue(DurabilityMode.fromWireValue("durable") == Some(DurabilityMode.Durable)) &&
        assertTrue(DurabilityMode.fromWireValue("ephemeral") == Some(DurabilityMode.Ephemeral)) &&
        assertTrue(DurabilityMode.fromWireValue("unknown").isEmpty)
      },
      test("annotation classes can be constructed") {
        val a1 = new description("desc")
        val a2 = new prompt("prompt")
        val a3 = new agentImplementation()
        val a4 = new languageCode("en")
        val a5 = new mimeType("image/png")
        val a6 = new agentDefinition("MyAgent", DurabilityMode.Durable)
        val a7 = new agentDefinition()
        val a8 = new agentDefinition("Custom")

        assertTrue(
          a1.value == "desc",
          a2.value == "prompt",
          a3 != null,
          a4.value == "en",
          a5.value == "image/png",
          a6.typeName == "MyAgent",
          a6.mode == DurabilityMode.Durable,
          a7.typeName == "",
          a7.mode == DurabilityMode.Durable,
          a8.typeName == "Custom",
          a8.mode == DurabilityMode.Durable
        )
      }
    )
}
