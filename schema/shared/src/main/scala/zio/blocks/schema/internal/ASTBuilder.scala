package zio.blocks.schema.internal

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * Converts PathSegment intermediate representation to DynamicOptic.Node.
 *
 * This separation allows the parser to be tested independently of the
 * DynamicOptic types, and makes the conversion logic explicit and testable.
 */
object ASTBuilder {

  /**
   * Build a vector of DynamicOptic nodes from parsed path segments.
   */
  def build(segments: List[PathSegment]): Vector[DynamicOptic.Node] =
    segments.iterator.map(segmentToNode).toVector

  /**
   * Convert a single PathSegment to a DynamicOptic.Node.
   */
  def segmentToNode(seg: PathSegment): DynamicOptic.Node = seg match {
    case PathSegment.Field(name)       => DynamicOptic.Node.Field(name)
    case PathSegment.Index(n)          => DynamicOptic.Node.AtIndex(n)
    case PathSegment.Indices(ns)       => DynamicOptic.Node.AtIndices(ns)
    case PathSegment.Elements          => DynamicOptic.Node.Elements
    case PathSegment.MapKey(key)       => DynamicOptic.Node.AtMapKey(keyToDynamicValue(key))
    case PathSegment.MapKeys(keys)     => DynamicOptic.Node.AtMapKeys(keys.map(keyToDynamicValue))
    case PathSegment.MapValues         => DynamicOptic.Node.MapValues
    case PathSegment.MapKeysSelector   => DynamicOptic.Node.MapKeys
    case PathSegment.VariantCase(name) => DynamicOptic.Node.Case(name)
  }

  /**
   * Convert a MapKeyValue to a DynamicValue.Primitive.
   */
  private def keyToDynamicValue(key: MapKeyValue): DynamicValue = key match {
    case MapKeyValue.StringKey(v) => DynamicValue.Primitive(PrimitiveValue.String(v))
    case MapKeyValue.IntKey(v)    => DynamicValue.Primitive(PrimitiveValue.Int(v))
    case MapKeyValue.CharKey(v)   => DynamicValue.Primitive(PrimitiveValue.Char(v))
    case MapKeyValue.BoolKey(v)   => DynamicValue.Primitive(PrimitiveValue.Boolean(v))
  }
}
