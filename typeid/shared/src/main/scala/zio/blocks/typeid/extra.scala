package zio.blocks.typeid

/**
 * Represents a type or term level annotation.
 */
final case class Annotation(
  tpe: TypeRepr,
  args: List[AnnotationArg] = Nil
)

sealed trait AnnotationArg
object AnnotationArg {
  final case class ConstArg(value: Constant)                    extends AnnotationArg
  final case class ArrayArg(elements: List[AnnotationArg])      extends AnnotationArg
  final case class NestedArg(annotation: Annotation)            extends AnnotationArg
  final case class TypeArg(tpe: TypeRepr)                       extends AnnotationArg
  final case class NamedArg(name: String, value: AnnotationArg) extends AnnotationArg
}

/**
 * Represents a literal value at compile-time.
 */
sealed trait Constant

object Constant {
  final case class Int(value: scala.Int)           extends Constant
  final case class Long(value: scala.Long)         extends Constant
  final case class Float(value: scala.Float)       extends Constant
  final case class Double(value: scala.Double)     extends Constant
  final case class Char(value: scala.Char)         extends Constant
  final case class String(value: java.lang.String) extends Constant
  final case class Boolean(value: scala.Boolean)   extends Constant
  case object Null                                 extends Constant
  case object Unit                                 extends Constant
  final case class ClassOf(tpe: TypeRepr)          extends Constant
}

/**
 * Represents a member of a structural/refinement type.
 */
sealed trait Member {
  def name: String
}

object Member {
  final case class Val(
    name: String,
    tpe: TypeRepr,
    isMutable: Boolean = false,
    targetName: Option[String] = None,
    annotations: List[Annotation] = Nil
  ) extends Member

  final case class Def(
    name: String,
    typeParams: List[TypeParam] = Nil,
    paramClauses: List[ParamClause] = Nil,
    resultType: TypeRepr,
    targetName: Option[String] = None,
    annotations: List[Annotation] = Nil
  ) extends Member

  final case class TypeMember(
    name: String,
    typeParams: List[TypeParam] = Nil,
    bounds: TypeBounds,
    alias: Option[TypeRepr] = None,
    annotations: List[Annotation] = Nil
  ) extends Member
}
