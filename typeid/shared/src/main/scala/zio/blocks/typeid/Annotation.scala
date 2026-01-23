package zio.blocks.typeid

final case class Annotation(
  tpe: TypeRepr,
  args: List[AnnotationArg]
)

sealed trait AnnotationArg

object AnnotationArg {
  final case class ConstArg(value: Constant)                    extends AnnotationArg
  final case class ArrayArg(elements: List[AnnotationArg])      extends AnnotationArg
  final case class NestedArg(annotation: Annotation)            extends AnnotationArg
  final case class TypeArg(tpe: TypeRepr)                       extends AnnotationArg
  final case class NamedArg(name: String, value: AnnotationArg) extends AnnotationArg
}
