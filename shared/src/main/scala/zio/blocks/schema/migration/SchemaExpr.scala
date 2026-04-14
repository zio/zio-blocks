package zio.blocks.schema.migration

/** Pure, serializable expression used for defaults and transforms.
    * For #519: primitive -> primitive only. No functions, no closures.
  */
sealed trait SchemaExpr[-In, +Out]
object SchemaExpr {
  case object DefaultValue extends SchemaExpr[Any, Any]
  final case class Const(value: Any) extends SchemaExpr[Any, Any]
}
