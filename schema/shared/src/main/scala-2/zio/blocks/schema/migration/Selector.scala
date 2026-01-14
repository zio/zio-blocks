package zio.blocks.schema.migration

import scala.language.dynamics
import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import zio.blocks.schema._

/**
 * A type-safe path selector for migrations.
 *
 * Selector provides compile-time validated paths into data structures,
 * converting lambda-style path expressions into DynamicOptic paths.
 *
 * Usage:
 * {{{
 * // Field access
 * Selector[Person](_.name)           // -> DynamicOptic.root.field("name")
 *
 * // Nested field access
 * Selector[Person](_.address.city)   // -> DynamicOptic.root.field("address").field("city")
 *
 * // Collection elements
 * Selector[Order](_.items.each)      // -> DynamicOptic.root.field("items").elements
 *
 * // Map keys and values
 * Selector[Config](_.data.keys)      // -> DynamicOptic.root.field("data").mapKeys
 * Selector[Config](_.data.values)    // -> DynamicOptic.root.field("data").mapValues
 * }}}
 *
 * @tparam A The type being navigated
 */
sealed trait Selector[A] {
  def path: DynamicOptic

  /**
   * Composes this selector with another.
   */
  def andThen[B](that: Selector[B]): Selector[A] = Selector.Composed(this, that)
}

object Selector {

  /**
   * Creates a selector from a lambda path expression.
   *
   * The lambda must use only field access, `.each`, `.keys`, or `.values` operations.
   * Invalid paths will cause a compile-time error.
   */
  def apply[A]: SelectorBuilder[A] = new SelectorBuilder[A]

  /**
   * Creates a root selector (empty path).
   */
  def root[A]: Selector[A] = Root[A]()

  /**
   * Creates a field selector.
   */
  def field[A](name: String): Selector[A] = Field[A](name)

  /**
   * Creates an elements selector for collections.
   */
  def each[A]: Selector[A] = Each[A]()

  /**
   * Creates a map keys selector.
   */
  def keys[A]: Selector[A] = Keys[A]()

  /**
   * Creates a map values selector.
   */
  def values[A]: Selector[A] = Values[A]()

  /**
   * Creates a selector from a pre-built DynamicOptic path.
   */
  def fromPath[A](optic: DynamicOptic): Selector[A] = FromPath[A](optic)

  // Internal implementations

  private[migration] final case class Root[A]() extends Selector[A] {
    val path: DynamicOptic = DynamicOptic.root
  }

  private[migration] final case class Field[A](name: String) extends Selector[A] {
    val path: DynamicOptic = DynamicOptic.root.field(name)
  }

  private[migration] final case class Each[A]() extends Selector[A] {
    val path: DynamicOptic = DynamicOptic.elements
  }

  private[migration] final case class Keys[A]() extends Selector[A] {
    val path: DynamicOptic = DynamicOptic.mapKeys
  }

  private[migration] final case class Values[A]() extends Selector[A] {
    val path: DynamicOptic = DynamicOptic.mapValues
  }

  private[migration] final case class Composed[A, B](first: Selector[A], second: Selector[B]) extends Selector[A] {
    val path: DynamicOptic = first.path(second.path)
  }

  private[migration] final case class FromPath[A](override val path: DynamicOptic) extends Selector[A]

  /**
   * Builder class that provides the macro-based apply method.
   */
  final class SelectorBuilder[A] {
    /**
     * Creates a selector from a lambda path expression.
     *
     * @param f A lambda of the form `_.field`, `_.field.nested`, `_.items.each`, etc.
     * @return A Selector with the corresponding DynamicOptic path
     */
    def apply[B](f: PathBuilder[A] => PathBuilder[B]): Selector[A] = macro SelectorMacros.selectorImpl[A, B]
  }

  /**
   * Phantom type used in path expressions. Not instantiated at runtime.
   * Uses Dynamic to allow arbitrary field access that gets parsed by the macro.
   */
  sealed trait PathBuilder[A] extends Dynamic {
    def selectDynamic(name: String): PathBuilder[A]
    def each: PathBuilder[A]
    def keys: PathBuilder[A]
    def values: PathBuilder[A]
  }
}

private[migration] object SelectorMacros {

  def selectorImpl[A: c.WeakTypeTag, B](c: whitebox.Context)(
    f: c.Expr[Selector.PathBuilder[A] => Selector.PathBuilder[B]]
  ): c.Expr[Selector[A]] = {
    import c.universe._

    val path = extractPath(c)(f.tree)
    val opticTree = buildOpticPath(c)(path)

    c.Expr[Selector[A]](
      q"new _root_.zio.blocks.schema.migration.Selector.FromPath[${weakTypeOf[A]}]($opticTree)"
    )
  }

  private sealed trait PathSegment
  private object PathSegment {
    case class Field(name: String) extends PathSegment
    case object Each extends PathSegment
    case object Keys extends PathSegment
    case object Values extends PathSegment
  }

  private def extractPath(c: whitebox.Context)(tree: c.Tree): List[PathSegment] = {
    import c.universe._

    tree match {
      case Function(List(param), body) =>
        extractPathFromBody(c)(param.name, body)
      case Block(Nil, Function(List(param), body)) =>
        extractPathFromBody(c)(param.name, body)
      case Block(stats, Function(List(param), body)) if stats.forall(isNonMutatingStatement(c)(_)) =>
        extractPathFromBody(c)(param.name, body)
      case _ =>
        c.abort(c.enclosingPosition, s"Expected a lambda expression like `_.field` but got: ${showRaw(tree)}")
    }
  }

  private def isNonMutatingStatement(c: whitebox.Context)(tree: c.Tree): Boolean = {
    import c.universe._
    tree match {
      case _: Import => true
      case _         => false
    }
  }

  private def extractPathFromBody(c: whitebox.Context)(paramName: c.universe.TermName, body: c.Tree): List[PathSegment] = {
    import c.universe._

    def extract(tree: Tree): List[PathSegment] = tree match {
      // Base case: reached the parameter
      case Ident(name) if name == paramName =>
        Nil

      // Dynamic selectDynamic: _.selectDynamic("fieldName")
      case Apply(Select(qualifier, TermName("selectDynamic")), List(Literal(Constant(fieldName: String)))) =>
        fieldName match {
          case "each"   => extract(qualifier) :+ PathSegment.Each
          case "keys"   => extract(qualifier) :+ PathSegment.Keys
          case "values" => extract(qualifier) :+ PathSegment.Values
          case name     => extract(qualifier) :+ PathSegment.Field(name)
        }

      // Field selection: _.fieldName or qualified.fieldName
      case Select(qualifier, TermName(fieldName)) =>
        fieldName match {
          case "each"   => extract(qualifier) :+ PathSegment.Each
          case "keys"   => extract(qualifier) :+ PathSegment.Keys
          case "values" => extract(qualifier) :+ PathSegment.Values
          case name     => extract(qualifier) :+ PathSegment.Field(name)
        }

      // Handle apply with type params: _.field[T]
      case TypeApply(inner, _) =>
        extract(inner)

      // Handle method application: _.method()
      case Apply(Select(qualifier, _), _) =>
        extract(qualifier)

      case Apply(inner, _) =>
        extract(inner)

      case _ =>
        c.abort(c.enclosingPosition,
          s"Unsupported path expression. Expected field access like `_.field` but got: ${showCode(tree)}")
    }

    extract(body)
  }

  private def buildOpticPath(c: whitebox.Context)(segments: List[PathSegment]): c.Tree = {
    import c.universe._

    segments.foldLeft[Tree](q"_root_.zio.blocks.schema.DynamicOptic.root") { (acc, segment) =>
      segment match {
        case PathSegment.Field(name) => q"$acc.field($name)"
        case PathSegment.Each        => q"$acc.elements"
        case PathSegment.Keys        => q"$acc.mapKeys"
        case PathSegment.Values      => q"$acc.mapValues"
      }
    }
  }
}
