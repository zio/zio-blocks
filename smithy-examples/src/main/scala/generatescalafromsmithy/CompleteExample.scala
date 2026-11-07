//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-smithy:0.0.56
//> using dep dev.zio::zio-blocks-codegen:0.0.56
//> using options -experimental
// Run from this folder:  scala-cli run CompleteExample.scala
// Or from smithy-examples:  sbt "runMain generatescalafromsmithy.CompleteExample"

package generatescalafromsmithy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import zio.blocks.smithy._
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

/**
 * Generate Scala Code from a Smithy Model — Put the Generator Together
 *
 * Reads a Smithy model from a file, maps it to a `ScalaFile`, adds schema
 * derivation and writes the Scala 3 source of the generated types to
 * `Orders.scala`.
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {
    val modelPath: String    = if (args.length > 0) args(0) else "src/main/smithy/orders.smithy"
    val outputDir: String    = if (args.length > 1) args(1) else "target/generated-orders"
    val ordersSmithy: String = new String(Files.readAllBytes(Paths.get(modelPath)), StandardCharsets.UTF_8)

    val model: SmithyModel = SmithyModel.parse(ordersSmithy) match {
      case Right(parsed) => parsed
      case Left(error)   => throw new IllegalArgumentException(error.formatMessage)
    }

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

    def sealedTraitFor(model: SmithyModel, name: String, shape: UnionShape): SealedTrait = {
      val cases = shape.members.map { member =>
        val caseName = member.name.capitalize
        if (member.target.name == "Unit") SealedTraitCase.CaseObjectCase(caseName)
        else SealedTraitCase.CaseClassCase(CaseClass(caseName, List(Field("value", typeRefFor(model, member.target)))))
      }
      SealedTrait(name, cases = cases, doc = docOf(shape.traits))
    }

    def enumFor(name: String, shape: EnumShape): Enum =
      Enum(name, shape.members.map(member => EnumCase.SimpleCase(member.name)), doc = docOf(shape.traits))

    def scalaFileFor(model: SmithyModel): ScalaFile = {
      val types = model.shapes.collect {
        case ShapeDefinition(name, shape: StructureShape) => caseClassFor(model, name, shape)
        case ShapeDefinition(name, shape: UnionShape)     => sealedTraitFor(model, name, shape)
        case ShapeDefinition(name, shape: EnumShape)      => enumFor(name, shape)
      }
      ScalaFile(PackageDecl(model.namespace), types = types)
    }

    def withSchemas(file: ScalaFile, scala3: Boolean): ScalaFile = {
      val types = file.types.map {
        case caseClass: CaseClass if scala3 =>
          caseClass.copy(derives = List("Schema"))
        case caseClass: CaseClass =>
          val schema = ObjectMember.ValMember(
            "schema",
            TypeRef.of("Schema", TypeRef(caseClass.name)),
            "Schema.derived",
            isImplicit = true
          )
          caseClass.copy(companion = Some(CompanionObject(List(schema))))
        case other => other
      }
      file.copy(imports = Import.WildcardImport("zio.blocks.schema") :: file.imports, types = types)
    }

    val ordersFile: ScalaFile = scalaFileFor(model)
    val outputFile            = Paths.get(outputDir, "Orders.scala")
    Files.createDirectories(Paths.get(outputDir))
    Files.write(
      outputFile,
      ScalaEmitter.emit(withSchemas(ordersFile, scala3 = true), EmitterConfig.default).getBytes(StandardCharsets.UTF_8)
    )
    println(s"Generated ${ordersFile.types.map(_.name).mkString(", ")} into $outputFile")
  }
}
