package zio.blocks.schema

trait DynamicOpticCompanionVersionSpecific {

  import DynamicOptic.Node

  // Schema instances derived automatically in Scala 3
  implicit lazy val elementsSchema: Schema[Node.Elements.type]   = Schema.derived
  implicit lazy val mapKeysSchema: Schema[Node.MapKeys.type]     = Schema.derived
  implicit lazy val mapValuesSchema: Schema[Node.MapValues.type] = Schema.derived
  implicit lazy val wrappedSchema: Schema[Node.Wrapped.type]     = Schema.derived
  implicit lazy val fieldSchema: Schema[Node.Field]              = Schema.derived
  implicit lazy val caseSchema: Schema[Node.Case]                = Schema.derived
  implicit lazy val atIndexSchema: Schema[Node.AtIndex]          = Schema.derived
  implicit lazy val atMapKeySchema: Schema[Node.AtMapKey]        = Schema.derived
  implicit lazy val atIndicesSchema: Schema[Node.AtIndices]      = Schema.derived
  implicit lazy val atMapKeysSchema: Schema[Node.AtMapKeys]      = Schema.derived
  implicit lazy val nodeSchema: Schema[Node]                     = Schema.derived
  implicit lazy val schema: Schema[DynamicOptic]                 = Schema.derived
}
