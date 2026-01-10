package zio.blocks.schema.migration.optic

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.language.implicitConversions

object SelectorMacro {

  // Implicit Conversion: Function -> Selector
  implicit def materialize[S, A](f: S => A): Selector[S, A] = macro materializeImpl[S, A]

  def materializeImpl[S: c.WeakTypeTag, A: c.WeakTypeTag](c: blackbox.Context)(f: c.Expr[S => A]): c.Expr[Selector[S, A]] = {
    import c.universe._

    def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    def extractSteps(tree: Tree): List[Tree] = {
      tree match {
        case Select(qualifier, name) =>
          val fieldName = name.toString.trim
          val step = q"_root_.zio.blocks.schema.migration.optic.OpticStep.Field($fieldName)"
          extractSteps(qualifier) :+ step
        
        case Apply(Select(qualifier, TermName("selectDynamic")), List(Literal(Constant(name: String)))) =>
          val step = q"_root_.zio.blocks.schema.migration.optic.OpticStep.Field($name)"
          extractSteps(qualifier) :+ step

        case Apply(Select(qualifier, TermName("apply")), List(Literal(Constant(value)))) =>
           val step = value match {
             case i: Int    => q"_root_.zio.blocks.schema.migration.optic.OpticStep.Index($i)"
             case s: String => q"_root_.zio.blocks.schema.migration.optic.OpticStep.Key($s)"
             case _         => fail("Unsupported apply argument.")
           }
           extractSteps(qualifier) :+ step

        case Ident(TermName(_)) => List.empty
        case Typed(expr, _) => extractSteps(expr)
        case Function(_, body) => extractSteps(body)
        case Block(_, expr) => extractSteps(expr)
        case other => fail(s"Invalid selector syntax: ${show(other)}")
      }
    }

    val steps = extractSteps(f.tree)
    val vectorExpr = q"scala.collection.immutable.Vector(..$steps)"

    c.Expr[Selector[S, A]](
      q"""
        new _root_.zio.blocks.schema.migration.optic.Selector[${weakTypeOf[S]}, ${weakTypeOf[A]}] {
          def path = _root_.zio.blocks.schema.migration.optic.DynamicOptic($vectorExpr)
        }
      """
    )
  }

  // Helper for tests: Fix unused param warning
  def translate[S, A](f: S => A)(implicit s: Selector[S, A]): DynamicOptic = {
    val _ = f // 🔥 FIX: Explicitly ignore f
    s.path
  }
}