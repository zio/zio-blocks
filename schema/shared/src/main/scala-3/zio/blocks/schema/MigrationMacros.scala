package zio.blocks.schema

import scala.quoted._
import zio.blocks.schema.binding._

private[schema] object MigrationMacros {

  inline def fieldName[S, A](inline path: S => A): String = ${ fieldNameImpl('path) }

  inline def caseName[S, A](inline path: S => A): String = ${ caseNameImpl('path) }

  def fieldNameImpl[S: Type, A: Type](path: Expr[S => A])(using q: Quotes): Expr[String] = {
    import q.reflect._

    def extractName(term: Term): String = term match {
      case Inlined(_, _, body) => extractName(body)
      case Lambda(_, body) => extractName(body)
      case Select(_, name) => name
      case Block(List(DefDef(_, _, _, Some(body))), _) => extractName(body)
      case _ => report.errorAndAbort(s"Expected a field selector (e.g. _.fieldName), but got: ${term.show}")
    }

    Expr(extractName(path.asTerm))
  }

  def caseNameImpl[S: Type, A: Type](path: Expr[S => A])(using q: Quotes): Expr[String] = {
    import q.reflect._

    def extractCaseName(term: Term): String = term match {
      case Inlined(_, _, body) => extractCaseName(body)
      case Lambda(_, body) => extractCaseName(body)
      case TypeApply(Apply(TypeApply(method, _), List(_)), List(typeTree)) if method.symbol.name == "when" =>
        typeTree.tpe.typeSymbol.name
      case Select(_, name) => name
      case Block(List(DefDef(_, _, _, Some(body))), _) => extractCaseName(body)
      case _ => report.errorAndAbort(s"Expected a case selector (e.g. _.when[CaseName]), but got: ${term.show}")
    }

    Expr(extractCaseName(path.asTerm))
  }

  def derivedImpl[A: Type, B: Type](schemaA: Expr[Schema[A]], schemaB: Expr[Schema[B]])(using q: Quotes): Expr[Migration[A, B]] = {

    '{
      val reflectA = $schemaA.reflect
      val reflectB = $schemaB.reflect

      val recordA = reflectA.asRecord.getOrElse(throw new Exception("Migration.derived only supports records (case classes)"))
      val recordB = reflectB.asRecord.getOrElse(throw new Exception("Migration.derived only supports records (case classes)"))

      val fieldsA = recordA.fields.map(f => f.name -> f).toMap
      val fieldsB = recordB.fields.map(f => f.name -> f).toMap

      val actions = Vector.newBuilder[Migration[Any, Any]]

      // Fields to remove
      fieldsA.keys.foreach { name =>
        if (!fieldsB.contains(name)) {
          actions += Migration.RemoveField(name)
        }
      }

      // Fields to add
      recordB.fields.foreach { fieldB =>
        if (!fieldsA.contains(fieldB.name)) {
          val reflectB = fieldB.value.asInstanceOf[Reflect.Bound[Any]]
          val defaultValue = reflectB.getDefaultValue.map(v => reflectB.toDynamicValue(v))
            .getOrElse(throw new Exception(s"No default value for added field ${fieldB.name} in ${reflectB.typeName}"))
          actions += Migration.AddField(fieldB.name, defaultValue)
        }
      }

      // TODO: Handle renames if we have hints, but derived is usually automatic.
      // Reorder if necessary
      actions += Migration.ReorderFields(recordB.fields.map(_.name).toVector)

      actions.result().foldLeft[Migration[Any, Any]](Migration.Identity[Any]())((acc, action) => acc ++ action).asInstanceOf[Migration[A, B]]
    }
  }
}
