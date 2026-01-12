package golem

private[golem] trait GolemPackageBase {
  // ---------------------------------------------------------------------------
  // Annotations (commonly used on agent traits / methods)
  // ---------------------------------------------------------------------------
  type agentDefinition     = runtime.annotations.agentDefinition
  type agentImplementation = runtime.annotations.agentImplementation
  type description         = runtime.annotations.description
  type prompt              = runtime.annotations.prompt

  type DurabilityMode = runtime.annotations.DurabilityMode
  val DurabilityMode: runtime.annotations.DurabilityMode.type = runtime.annotations.DurabilityMode

  // ---------------------------------------------------------------------------
  // Schema / data model
  // ---------------------------------------------------------------------------
  type GolemSchema[A] = data.GolemSchema[A]
  val GolemSchema: data.GolemSchema.type = data.GolemSchema

  type StructuredSchema = data.StructuredSchema
  val StructuredSchema: data.StructuredSchema.type = data.StructuredSchema

  type StructuredValue = data.StructuredValue
  val StructuredValue: data.StructuredValue.type = data.StructuredValue
}
