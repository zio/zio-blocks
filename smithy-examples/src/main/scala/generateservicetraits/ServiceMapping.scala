package generateservicetraits

import zio.blocks.smithy._
import zio.blocks.codegen.ir._

/**
 * Generate Scala Service Traits from a Smithy Service: the mapping.
 *
 * Maps a Smithy service and its operations to codegen IR: data shapes to
 * case classes and enums, the operation errors to a sealed error type, and
 * each operation to one abstract method of a service trait.
 */
object ServiceMapping {

  def docOf(traits: List[TraitApplication]): Option[String] =
    traits.collectFirst {
      case TraitApplication(ShapeId("smithy.api", "documentation"), Some(NodeValue.String(text))) => text
    }

  def service(model: SmithyModel): ServiceShape =
    model.shapes.collectFirst { case ShapeDefinition(_, s: ServiceShape) => s }.getOrElse {
      throw new IllegalArgumentException("the model has no service shape")
    }

  def errorTypeName(svc: ServiceShape): String = svc.name.stripSuffix("Service") + "Error"

  def operation(model: SmithyModel, id: ShapeId): OperationShape =
    model.findShape(id.name).map(_.shape) match {
      case Some(op: OperationShape) => op
      case _                        => throw new IllegalArgumentException(s"Unknown operation: $id")
    }

  def operations(model: SmithyModel): List[OperationShape] =
    service(model).operations.map(operation(model, _))

  def typeRefFor(model: SmithyModel, target: ShapeId): TypeRef =
    model.findShape(target.name).map(_.shape) match {
      case Some(_: StructureShape) | Some(_: EnumShape) | Some(_: UnionShape) => TypeRef(target.name)
      case Some(other)                                                         =>
        throw new IllegalArgumentException(s"Unsupported shape: ${other.name}")
      case None =>
        target.name match {
          case "String"    => TypeRef.String
          case "Integer"   => TypeRef.Int
          case "Long"      => TypeRef.Long
          case "Boolean"   => TypeRef.Boolean
          case "Timestamp" => TypeRef("java.time.Instant")
          case other       => throw new IllegalArgumentException(s"Unknown shape: $other")
        }
    }

  def fieldFor(model: SmithyModel, member: MemberDefinition): Field = {
    val fieldType = typeRefFor(model, member.target)
    if (member.traits.contains(TraitApplication.required)) Field(member.name, fieldType)
    else Field(member.name, TypeRef.optional(fieldType), defaultValue = Some("None"))
  }

  def caseClassFor(model: SmithyModel, name: String, shape: StructureShape, extendsTypes: List[TypeRef]): CaseClass =
    CaseClass(
      name,
      shape.members.map(fieldFor(model, _)),
      extendsTypes = extendsTypes,
      derives = List("Schema"),
      doc = docOf(shape.traits)
    )

  def enumFor(name: String, shape: EnumShape): Enum =
    Enum(name, shape.members.map(member => EnumCase.SimpleCase(member.name)), doc = docOf(shape.traits))

  def errorNames(model: SmithyModel): List[String] =
    operations(model).flatMap(_.errors.map(_.name)).distinct

  def dataTypes(model: SmithyModel): List[TypeDefinition] = {
    val errors = errorNames(model)
    model.shapes.flatMap {
      case ShapeDefinition(name, _) if errors.contains(name) => None
      case ShapeDefinition(name, shape: StructureShape)      => Some(caseClassFor(model, name, shape, Nil))
      case ShapeDefinition(name, shape: EnumShape)           => Some(enumFor(name, shape))
      case _                                                 => None
    }
  }

  def errorTypes(model: SmithyModel): List[TypeDefinition] = {
    val errorType  = errorTypeName(service(model))
    val sealedType = SealedTrait(errorType, doc = Some("Every error an operation of the service can return."))
    val cases = errorNames(model).map { name =>
      model.findShape(name).map(_.shape) match {
        case Some(shape: StructureShape) =>
          caseClassFor(model, name, shape, List(TypeRef(errorType)))
        case _ => throw new IllegalArgumentException(s"Error is not a structure: $name")
      }
    }
    sealedType :: cases
  }

  def methodFor(model: SmithyModel, op: OperationShape): Method = {
    val errorType = errorTypeName(service(model))
    val params    = op.input.toList.map(in => ParamList(List(MethodParam("input", TypeRef(in.name)))))
    val output    = op.output.map(o => TypeRef(o.name)).getOrElse(TypeRef.Unit)
    val returnType =
      if (op.errors.isEmpty) output
      else TypeRef.of("Either", TypeRef(errorType), output)
    Method(
      name = op.name.take(1).toLowerCase + op.name.drop(1),
      params = params,
      returnType = returnType,
      doc = docOf(op.traits)
    )
  }

  def serviceTrait(model: SmithyModel): Trait = {
    val svc = service(model)
    Trait(
      svc.name,
      members = operations(model).map(op => ObjectMember.DefMember(methodFor(model, op))),
      doc = docOf(svc.traits)
    )
  }

  def scalaFile(model: SmithyModel): ScalaFile =
    ScalaFile(
      PackageDecl(model.namespace),
      imports = List(Import.WildcardImport("zio.blocks.schema")),
      types = (dataTypes(model) ++ errorTypes(model)) :+ serviceTrait(model)
    )
}
