package zio.blocks.schema.json

import zio.blocks.schema._
import zio.blocks.schema.json.JsonSchema._

object JsonSchemaDeriver {

  def derive(schema: Schema[_]): JsonSchema = derive(schema.reflect)

  private def derive(reflect: Reflect.Bound[_]): JsonSchema = reflect match {
    case p: Reflect.Primitive[_, _] => derivePrimitive(p.primitiveType)
    case r: Reflect.Record[_, _] => deriveRecord(r)
    case s: Reflect.Sequence[_, _, _] => deriveSequence(s)
    case m: Reflect.Map[_, _, _, _] => deriveMap(m)
    case v: Reflect.Variant[_, _] => deriveVariant(v)
    case w: Reflect.Wrapper[_, _, _] => derive(w.wrapped.asInstanceOf[Reflect.Bound[_]])
    case _: Reflect.Deferred[_, _] => ObjectSchema() // Recursive types not fully handled in this basic version
    case _ => ObjectSchema()
  }

  private def derivePrimitive(p: PrimitiveType[_]): JsonSchema = p match {
    case PrimitiveType.Unit => ObjectSchema(schemaType = Some(List(JsonType.Null)))
    case _: PrimitiveType.Boolean => ObjectSchema(schemaType = Some(List(JsonType.Boolean)))
    case _: PrimitiveType.Int | _: PrimitiveType.Long | _: PrimitiveType.Short | _: PrimitiveType.Byte | _: PrimitiveType.BigInt =>
       ObjectSchema(schemaType = Some(List(JsonType.Number)))
    case _: PrimitiveType.Float | _: PrimitiveType.Double | _: PrimitiveType.BigDecimal =>
       ObjectSchema(schemaType = Some(List(JsonType.Number)))
    case _ =>
       ObjectSchema(schemaType = Some(List(JsonType.String)))
  }

  private def deriveRecord(r: Reflect.Record[_, _]): JsonSchema = {
    val properties = r.fields.map { field =>
      field.name -> derive(field.value.asInstanceOf[Reflect.Bound[_]])
    }.toMap
    ObjectSchema(
      schemaType = Some(List(JsonType.Object)),
      properties = Some(properties),
      required = Some(r.fields.map(_.name).toList)
    )
  }

  private def deriveSequence(s: Reflect.Sequence[_, _, _]): JsonSchema = {
    ObjectSchema(
      schemaType = Some(List(JsonType.Array)),
      items = Some(derive(s.element.asInstanceOf[Reflect.Bound[_]]))
    )
  }

  private def deriveMap(m: Reflect.Map[_, _, _, _]): JsonSchema = {
    ObjectSchema(
      schemaType = Some(List(JsonType.Object)),
      additionalProperties = Some(derive(m.value.asInstanceOf[Reflect.Bound[_]]))
    )
  }
  
  private def deriveVariant(v: Reflect.Variant[_, _]): JsonSchema = {
      val cases = v.cases.map(c => derive(c.value.asInstanceOf[Reflect.Bound[_]])).toList
      ObjectSchema(oneOf = Some(cases))
  }
}
