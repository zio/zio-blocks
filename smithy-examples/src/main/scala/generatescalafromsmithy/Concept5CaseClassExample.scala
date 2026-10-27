//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-smithy:0.0.56
//> using dep dev.zio::zio-blocks-codegen:0.0.56
//> using options -experimental
//> using file OrdersModel.scala
// Run from this folder:  scala-cli run Concept5CaseClassExample.scala OrdersModel.scala
// Or from smithy-examples:  sbt "runMain generatescalafromsmithy.Concept5CaseClassExample"

package generatescalafromsmithy

import zio.blocks.smithy._
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

/**
 * Generate Scala Code from a Smithy Model — 5. Generate Your First Case Class
 *
 * Turns the `Order` structure into a `CaseClass` and emits it as Scala 3
 * source.
 */
object Concept5CaseClassExample {
  def main(args: Array[String]): Unit = {
    // Setup from sections 3–4: the parsed model, the Order structure and the type mapping
    import OrdersModel.ordersSmithy

    val model: SmithyModel = SmithyModel.parse(ordersSmithy) match {
      case Right(parsed) => parsed
      case Left(error)   => throw new IllegalArgumentException(error.formatMessage)
    }

    val orderShape: StructureShape =
      model.shapes.collectFirst { case ShapeDefinition("Order", shape: StructureShape) => shape }.get

    def simpleTypeRef(name: String): Option[TypeRef] = name match {
      case "String"     => Some(TypeRef.String)
      case "Boolean"    => Some(TypeRef.Boolean)
      case "Byte"       => Some(TypeRef.Byte)
      case "Short"      => Some(TypeRef.Short)
      case "Integer"    => Some(TypeRef.Int)
      case "Long"       => Some(TypeRef.Long)
      case "Float"      => Some(TypeRef.Float)
      case "Double"     => Some(TypeRef.Double)
      case "BigInteger" => Some(TypeRef.BigInt)
      case "BigDecimal" => Some(TypeRef.BigDecimal)
      case "Timestamp"  => Some(TypeRef("java.time.Instant"))
      case "Blob"       => Some(TypeRef.of("zio.blocks.chunk.Chunk", TypeRef.Byte))
      case "Document"   => Some(TypeRef("zio.blocks.schema.DynamicValue"))
      case _            => None
    }

    def typeRefFor(model: SmithyModel, target: ShapeId): TypeRef =
      model.findShape(target.name).map(_.shape) match {
        case Some(list: ListShape) =>
          TypeRef.list(typeRefFor(model, list.member.target))
        case Some(map: MapShape) =>
          TypeRef.map(typeRefFor(model, map.key.target), typeRefFor(model, map.value.target))
        case Some(_: StructureShape) | Some(_: UnionShape) | Some(_: EnumShape) =>
          TypeRef(target.name)
        case Some(other) =>
          throw new IllegalArgumentException(s"Unsupported shape: ${other.name}")
        case None =>
          simpleTypeRef(target.name).getOrElse(throw new IllegalArgumentException(s"Unknown shape: $target"))
      }

    def docOf(traits: List[TraitApplication]): Option[String] =
      traits.collectFirst {
        case TraitApplication(ShapeId("smithy.api", "documentation"), Some(NodeValue.String(text))) => text
      }

    def fieldFor(model: SmithyModel, member: MemberDefinition): Field = {
      val fieldType = typeRefFor(model, member.target)
      if (member.traits.contains(TraitApplication.required)) Field(member.name, fieldType)
      else Field(member.name, TypeRef.optional(fieldType), defaultValue = Some("None"))
    }

    def caseClassFor(model: SmithyModel, name: String, shape: StructureShape): CaseClass =
      CaseClass(name, shape.members.map(fieldFor(model, _)), doc = docOf(shape.traits))

    println(
      ScalaEmitter.emit(ScalaFile(PackageDecl(model.namespace), types = List(caseClassFor(model, "Order", orderShape))))
    )
  }
}
