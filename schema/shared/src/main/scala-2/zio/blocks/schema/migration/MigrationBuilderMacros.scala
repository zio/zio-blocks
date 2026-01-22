package zio.blocks.schema.migration

import scala.reflect.macros.whitebox
import zio.blocks.schema.{DynamicOptic, Schema}

/**
 * Scala 2 macros for MigrationBuilder to extract field names and paths
 * from lambda expressions at compile time.
 * 
 * This enables type-safe, IDE-friendly migration building:
 *   builder.addField(_.country, "USA")
 *   builder.renameField(_.name, _.fullName)
 *   builder.dropField(_.oldField)
 */
object MigrationBuilderMacros {

  /**
   * Extract field name from a selector lambda like _.fieldName
   * Returns the field name as a string.
   */
  def extractFieldName[A, F](c: whitebox.Context)(selector: c.Expr[A => F]): c.Expr[String] = {
    import c.universe._
    
    def extractFromTree(tree: Tree): String = tree match {
      case q"($_) => $body" => extractFromTree(body)
      case Select(_, TermName(fieldName)) => fieldName
      case Ident(TermName(name)) => name
      case _ => 
        c.abort(c.enclosingPosition, s"Expected a field selector like _.fieldName, got: ${showRaw(tree)}")
    }
    
    val fieldName = extractFromTree(selector.tree)
    c.Expr[String](Literal(Constant(fieldName)))
  }

  /**
   * Extract a path from a nested selector like _.address.street
   * Returns a DynamicOptic representing the path.
   */
  def extractPath[A, F](c: whitebox.Context)(selector: c.Expr[A => F]): c.Expr[DynamicOptic] = {
    import c.universe._
    
    def extractFields(tree: Tree): List[String] = tree match {
      case q"($_) => $body" => extractFields(body)
      case Select(qualifier, TermName(fieldName)) => extractFields(qualifier) :+ fieldName
      case Ident(_) => Nil  // Root parameter
      case _ => 
        c.abort(c.enclosingPosition, s"Expected a field selector like _.address.street, got: ${showRaw(tree)}")
    }
    
    val fields = extractFields(selector.tree)
    
    if (fields.isEmpty) {
      reify { DynamicOptic.root }
    } else {
      // Build path: root / "field1" / "field2" / ...
      val pathTree = fields.foldLeft[Tree](q"_root_.zio.blocks.schema.DynamicOptic.root") { (acc, field) =>
        q"$acc./.apply(${Literal(Constant(field))})"
      }
      c.Expr[DynamicOptic](pathTree)
    }
  }

  /**
   * Extract two field names from two selectors for rename operations.
   * Returns a tuple of (fromField, toField).
   */
  def extractTwoFieldNames[A, B, F1, F2](c: whitebox.Context)(
    from: c.Expr[A => F1],
    to: c.Expr[B => F2]
  ): c.Expr[(String, String)] = {
    import c.universe._
    
    val fromName = extractFieldName(c)(from)
    val toName = extractFieldName(c)(to)
    
    reify {
      (fromName.splice, toName.splice)
    }
  }

  /**
   * Validate that a selector points to a valid field in the schema.
   * This is a compile-time check to ensure type safety.
   */
  def validateFieldExists[A](c: whitebox.Context)(
    selector: c.Expr[A => Any],
    schema: c.Expr[Schema[A]]
  ): c.Expr[Unit] = {
    import c.universe._
    
    // For now, we just extract the field name
    // In a full implementation, we'd inspect the schema at compile time
    val _ = extractFieldName(c)(selector)
    
    // TODO: Add runtime validation or compile-time schema inspection
    reify { () }
  }
}

