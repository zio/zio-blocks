package zio.blocks.codegen.ir

/**
 * Modifier for a parameter list, indicating whether it is normal, implicit, or
 * using.
 */
sealed trait ParamListModifier

object ParamListModifier {
  case object Normal   extends ParamListModifier
  case object Implicit extends ParamListModifier
  case object Using    extends ParamListModifier
}

/**
 * Wraps a list of method parameters with an optional modifier (implicit/using).
 *
 * @param params
 *   The parameters in this parameter list
 * @param modifier
 *   The modifier for this parameter list (defaults to Normal)
 */
final case class ParamList(
  params: List[MethodParam],
  modifier: ParamListModifier = ParamListModifier.Normal
)
