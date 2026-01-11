package zio.blocks.schema

import scala.reflect.macros.blackbox

private[schema] object MigrationMacros {

  def fieldName[S, A](c: blackbox.Context)(path: c.Expr[S => A]): c.Expr[String] = {
    import c.universe._

    def extractName(tree: Tree): String = tree match {
      case q"($_) => $body"  => extractName(body)
      case q"$parent.$child" => child.decodedName.toString
      case Ident(name)       => name.decodedName.toString
      case _                 => c.abort(c.enclosingPosition, s"Expected a field selector (e.g. _.fieldName), but got: $tree")
    }

    c.Expr[String](q"${extractName(path.tree)}")
  }

  def caseName[S, A](c: blackbox.Context)(path: c.Expr[S => A]): c.Expr[String] = {
    import c.universe._

    def extractCaseName(tree: Tree): String = tree match {
      case q"($_) => $body"      => extractCaseName(body)
      case q"$_.when[$typeTree]" => typeTree.tpe.typeSymbol.name.toString
      case q"$parent.$child"     => child.decodedName.toString
      case Ident(name)           => name.decodedName.toString
      case _                     => c.abort(c.enclosingPosition, s"Expected a case selector (e.g. _.when[CaseName]), but got: $tree")
    }

    c.Expr[String](q"${extractCaseName(path.tree)}")
  }

  def addField[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](
    c: blackbox.Context
  )(selector: c.Expr[B => T], default: c.Expr[T])(schema: c.Expr[Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val name = fieldName(c)(selector)
    c.Expr[MigrationBuilder[A, B]](q"${c.prefix}.addField($name, $schema.toDynamicValue($default))")
  }

  def renameField[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](
    c: blackbox.Context
  )(from: c.Expr[A => T], to: c.Expr[B => T]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val fromName = fieldName(c)(from)
    val toName   = fieldName(c)(to)
    c.Expr[MigrationBuilder[A, B]](q"${c.prefix}.renameField($fromName, $toName)")
  }

  def removeField[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](
    c: blackbox.Context
  )(selector: c.Expr[A => T]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val name = fieldName(c)(selector)
    c.Expr[MigrationBuilder[A, B]](q"${c.prefix}.removeField($name)")
  }

  def renameCase[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](
    c: blackbox.Context
  )(from: c.Expr[A => T], to: c.Expr[B => T]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val fromName = caseName(c)(from)
    val toName   = caseName(c)(to)
    c.Expr[MigrationBuilder[A, B]](q"${c.prefix}.renameCase($fromName, $toName)")
  }

  def derivedImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: blackbox.Context
  )(schemaA: c.Expr[Schema[A]], schemaB: c.Expr[Schema[B]]): c.Expr[Migration[A, B]] = {
    import c.universe._

    val typeA = weakTypeOf[A]
    val typeB = weakTypeOf[B]

    c.Expr[Migration[A, B]](q"""{
        import _root_.zio.blocks.schema.Reflect
        import _root_.zio.blocks.schema.Migration

        val reflectA = $schemaA.reflect
        val reflectB = $schemaB.reflect

        val recordA = reflectA.asRecord.getOrElse(throw new Exception("Migration.derived only supports records (case classes)"))
        val recordB = reflectB.asRecord.getOrElse(throw new Exception("Migration.derived only supports records (case classes)"))

        val fieldsA = recordA.fields.map(f => f.name -> f).toMap
        val fieldsB = recordB.fields.map(f => f.name -> f).toMap

        val actions = Vector.newBuilder[Migration[Any, Any]]

        fieldsA.keys.foreach { name =>
          if (!fieldsB.contains(name)) {
            actions += Migration.RemoveField(name)
          }
        }

        recordB.fields.foreach { fieldB =>
          if (!fieldsA.contains(fieldB.name)) {
            val rb = fieldB.value.asInstanceOf[Reflect.Bound[Any]]
            val defaultValue = rb.getDefaultValue.map(v => rb.toDynamicValue(v))
              .getOrElse(throw new Exception("No default value for added field " + fieldB.name + " in " + reflectB.typeName))
            actions += Migration.AddField(fieldB.name, defaultValue)
          }
        }

        actions += Migration.ReorderFields(recordB.fields.map(_.name).toVector)

        actions.result().
          foldLeft[Migration[Any, Any]](Migration.Identity[Any]())((acc, action) => acc ++ action)
          .asInstanceOf[Migration[$typeA, $typeB]]
      }""")
  }
}
