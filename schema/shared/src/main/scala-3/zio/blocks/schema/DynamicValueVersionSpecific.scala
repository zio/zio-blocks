package zio.blocks.schema

trait DynamicValueVersionSpecific extends Selectable {
  self: DynamicValue =>

  def selectDynamic(name: String): DynamicValue = self match {
    case DynamicValue.Record(fields) =>
      fields.collectFirst { case (n, v) if n == name => v }
        .getOrElse(throw new NoSuchFieldException(s"Field '$name' not found in record"))
    case DynamicValue.Map(entries) =>
      val key = DynamicValue.Primitive(PrimitiveValue.String(name))
      entries.collectFirst { case (k, v) if k == key => v }
        .getOrElse(throw new NoSuchFieldException(s"Key '$name' not found in map"))
    case DynamicValue.Variant(_, innerValue) =>
      (innerValue: DynamicValueVersionSpecific).selectDynamic(name)
    case DynamicValue.Primitive(_) =>
      throw new UnsupportedOperationException("Cannot access fields on a primitive value")
    case DynamicValue.Sequence(_) =>
      throw new UnsupportedOperationException("Cannot access fields on a sequence value")
  }
}
