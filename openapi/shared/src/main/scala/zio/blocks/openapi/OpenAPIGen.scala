package zio.blocks.openapi

import zio.blocks.schema.Schema

object OpenAPIGen {

  def schema[A](implicit s: Schema[A]): (ReferenceOr[SchemaObject], Map[String, SchemaObject]) = {
    val name = s.reflect.typeId.name
    val obj  = s.toOpenAPISchema
    val ref  = ReferenceOr.Ref(Reference(`$ref` = s"#/components/schemas/$name"))
    (ref, Map(name -> obj))
  }

  def schemas(ss: Schema[_]*): Map[String, SchemaObject] =
    ss.map(s => s.reflect.typeId.name -> SchemaObject.fromJsonSchema(s.toJsonSchema)).toMap
}
