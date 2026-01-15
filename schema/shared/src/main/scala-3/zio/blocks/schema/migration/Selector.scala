package zio.blocks.schema.migration

import scala.language.dynamics
import scala.quoted.*
import zio.blocks.schema.*

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
 * @tparam A
 *   The type being navigated
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
   * The lambda must use only field access, `.each`, `.keys`, or `.values`
   * operations. Invalid paths will cause a compile-time error.
   */
  inline def apply[A]: SelectorBuilder[A] = new SelectorBuilder[A]

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
     * @param f
     *   A lambda of the form `_.field`, `_.field.nested`, `_.items.each`, etc.
     * @return
     *   A Selector with the corresponding DynamicOptic path
     */
    inline def apply[B](inline f: PathBuilder[A] => PathBuilder[B]): Selector[A] =
      ${ SelectorMacros.selectorImpl[A, B]('f) }
  }

  /**
   * Phantom type used in path expressions. Not instantiated at runtime. Uses
   * Dynamic to allow arbitrary field access that gets parsed by the macro.
   */
  sealed trait PathBuilder[A] extends Dynamic {
    def selectDynamic(name: String): PathBuilder[A]
    def each: PathBuilder[A]
    def keys: PathBuilder[A]
    def values: PathBuilder[A]
  }
}

private[migration] object SelectorMacros {

  def selectorImpl[A: Type, B: Type](
    f: Expr[Selector.PathBuilder[A] => Selector.PathBuilder[B]]
  )(using Quotes): Expr[Selector[A]] = {
    import quotes.reflect.*

    val segments  = extractPath(f.asTerm)
    val opticExpr = buildOpticPath(segments)

    '{ zio.blocks.schema.migration.Selector.FromPath[A]($opticExpr) }
  }

  private enum PathSegment {
    case Field(name: String)
    case Each
    case Keys
    case Values
  }

  private def extractPath(using Quotes)(tree: quotes.reflect.Term): List[PathSegment] = {
    import quotes.reflect.*

    def extractFromBody(paramName: String, body: Term): List[PathSegment] = {
      def extract(term: Term): List[PathSegment] = term match {
        // Base case: reached the parameter
        case Ident(name) if name == paramName =>
          Nil

        // Dynamic selectDynamic: _.selectDynamic("fieldName")
        case Apply(Select(qualifier, "selectDynamic"), List(Literal(StringConstant(fieldName)))) =>
          fieldName match {
            case "each"   => extract(qualifier) :+ PathSegment.Each
            case "keys"   => extract(qualifier) :+ PathSegment.Keys
            case "values" => extract(qualifier) :+ PathSegment.Values
            case name     => extract(qualifier) :+ PathSegment.Field(name)
          }

        // Field selection: _.fieldName
        case Select(qualifier, fieldName) =>
          fieldName match {
            case "each"   => extract(qualifier) :+ PathSegment.Each
            case "keys"   => extract(qualifier) :+ PathSegment.Keys
            case "values" => extract(qualifier) :+ PathSegment.Values
            case name     => extract(qualifier) :+ PathSegment.Field(name)
          }

        // Type application
        case TypeApply(inner, _) =>
          extract(inner)

        // Method application
        case Apply(inner, _) =>
          extract(inner)

        // Inlined expressions
        case Inlined(_, _, inner) =>
          extract(inner)

        case other =>
          report.errorAndAbort(
            s"Unsupported path expression. Expected field access like `_.field` but got: ${other.show}"
          )
      }

      extract(body)
    }

    tree match {
      // Lambda: (x => body)
      case Lambda(List(param), body) =>
        extractFromBody(param.name, body)

      // Block with lambda
      case Block(_, Lambda(List(param), body)) =>
        extractFromBody(param.name, body)

      // Inlined lambda
      case Inlined(_, _, inner) =>
        extractPath(inner)

      case other =>
        report.errorAndAbort(s"Expected a lambda expression like `_.field` but got: ${other.show}")
    }
  }

  private def buildOpticPath(using Quotes)(segments: List[PathSegment]): Expr[DynamicOptic] =
    segments.foldLeft[Expr[DynamicOptic]]('{ DynamicOptic.root }) { (acc, segment) =>
      segment match {
        case PathSegment.Field(name) =>
          val nameExpr = Expr(name)
          '{ $acc.field($nameExpr) }
        case PathSegment.Each   => '{ $acc.elements }
        case PathSegment.Keys   => '{ $acc.mapKeys }
        case PathSegment.Values => '{ $acc.mapValues }
      }
    }
}
