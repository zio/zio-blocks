package zio.blocks.schema.migration.macros

import zio.blocks.schema.migration.ToDynamicOptic
import zio.blocks.schema.DynamicOptic
import scala.reflect.macros.whitebox

/**
 * SelectorMacros provides compile-time transformation of Scala selector
 * functions into runtime DynamicOptic representations for Scala 2.
 */
object SelectorMacros {

  def materializeImpl[S: c.WeakTypeTag, A: c.WeakTypeTag](
    c: whitebox.Context
  )(selector: c.Expr[S => A]): c.Expr[ToDynamicOptic[S, A]] = {
    import c.universe._

    val sTpe = weakTypeOf[S]
    val aTpe = weakTypeOf[A]

    /**
     *   1. Recursive AST Parser Extracts path nodes while filtering out Scala 2
     *      specific synthetic nodes like 'apply'.
     */
    def parsePath(tree: Tree): List[DynamicOptic.Node] = tree match {
      // Step into lambda functions and blocks
      case Function(_, body) => parsePath(body)
      case Block(_, expr)    => parsePath(expr)

      // Handle field access and collection traversal
      case Select(qualifier, name) =>
        val fieldName = name.decodedName.toString.trim

        // [FIX] Skip 'apply' and lambda-generated synthetic names to avoid "Field 'apply' not found" errors
        if (fieldName == "apply" || fieldName.startsWith("lambda$")) {
          parsePath(qualifier)
        } else {
          val node = fieldName match {
            case "each" => DynamicOptic.Node.Elements
            case _      => DynamicOptic.Node.Field(fieldName)
          }
          parsePath(qualifier) :+ node
        }

      // Handle enum/sum-type refinement: _.payment.when[CreditCard]
      case TypeApply(Select(qualifier, TermName("when")), List(tpt)) =>
        val subtypeName = tpt.tpe.typeSymbol.name.decodedName.toString
        parsePath(qualifier) :+ DynamicOptic.Node.Case(subtypeName)

      // Base case: The root identifier (e.g., 'u' in u.name)
      case Ident(_) => List.empty

      // Catch-all for unsupported nodes to keep the recursion safe
      case _ => List.empty
    }

    val nodes = parsePath(selector.tree)

    /**
     *   2. Node Tree Reconstruction (Lifting) Transforms domain Nodes into
     *      Quasiquote trees for the final generated code. [FIX] Includes a
     *      catch-all case to satisfy Scala 2 exhaustivity requirements.
     */
    val nodeTrees = nodes.map {
      case DynamicOptic.Node.Field(name) =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($name)"

      case DynamicOptic.Node.Case(name) =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.Case($name)"

      case DynamicOptic.Node.Elements =>
        q"_root_.zio.blocks.schema.DynamicOptic.Node.Elements"

      // Fallback for other DynamicOptic nodes (AtIndex, MapKeys, etc.) if they ever appear
      case other =>
        c.abort(
          c.enclosingPosition,
          s"Unsupported migration selector node: ${other.getClass.getSimpleName}. " +
            "Currently supported: field access, .each, and .when[T]."
        )
    }

    /**
     *   3. Final Expression Generation Reconstructs the ToDynamicOptic instance
     *      with the extracted path.
     */
    c.Expr[ToDynamicOptic[S, A]](q"""
      new _root_.zio.blocks.schema.migration.ToDynamicOptic[$sTpe, $aTpe](
        _root_.zio.blocks.schema.DynamicOptic(
          _root_.zio.blocks.chunk.Chunk(..$nodeTrees)
        )
      )
    """)
  }
}
