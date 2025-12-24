package zio.blocks.schema

import scala.quoted.*
import java.util.concurrent.atomic.AtomicReference

trait DerivedOptics[T] {
  protected val _opticsCache: AtomicReference[Any] = new AtomicReference(null)

  transparent inline def optics(using inline schema: Schema[T]): Any =
    ${ DerivedOpticsMacro.deriveOptics[T]('schema, '_opticsCache, false) }
}

trait DerivedOptics_[T] {
  protected val _opticsCache: AtomicReference[Any] = new AtomicReference(null)

  transparent inline def optics(using inline schema: Schema[T]): Any =
    ${ DerivedOpticsMacro.deriveOptics[T]('schema, '_opticsCache, true) }
}

trait SelectableOptics extends scala.Selectable {
  def selectDynamic(name: String): Any
}

object DerivedOpticsMacro {

  def deriveOptics[T: Type](
    schemaExpr: Expr[Schema[T]],
    cacheExpr: Expr[AtomicReference[Any]],
    useUnderscore: Boolean
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*

    val tpe    = TypeRepr.of[T]
    val symbol = tpe.typeSymbol

    def mkAccessorName(fieldName: String): String =
      if (useUnderscore) "_" + fieldName else fieldName

    def decapitalize(s: String): String =
      if (s.isEmpty) s
      else s.head.toLower.toString + s.tail

    def generateRecordOptics(recordSymbol: Symbol): Expr[Any] = {
      val fields = recordSymbol.caseFields

      if (fields.isEmpty) {
        report.errorAndAbort(s"Case class ${tpe.show} has no fields to generate optics for")
      }

      val baseType = TypeRepr.of[SelectableOptics]

      val refinedType = fields.foldLeft(baseType) { (acc, field) =>
        val accessorName = mkAccessorName(field.name)
        val fieldType    = tpe.memberType(field).widen
        val lensType     = TypeRepr.of[Lens].appliedTo(List(tpe, fieldType))
        Refinement(acc, accessorName, ByNameType(lensType))
      }

      refinedType.asType match {
        case '[refinedT] =>
          val fieldLensExprs: List[(String, Expr[Any])] = fields.map { field =>
            val fieldName    = field.name
            val accessorName = mkAccessorName(fieldName)
            val fieldType    = tpe.memberType(field).widen

            fieldType.asType match {
              case '[f] =>
                val lensExpr = '{
                  val record = $schemaExpr.reflect.asInstanceOf[_root_.zio.blocks.schema.Reflect.Record.Bound[T]]
                  record.fields.find(_.name == ${ Expr(fieldName) }) match {
                    case Some(term) =>
                      _root_.zio.blocks.schema.Lens[T, f](
                        record,
                        term.asInstanceOf[_root_.zio.blocks.schema.Term.Bound[T, f]]
                      )
                    case None =>
                      sys.error(s"Field '${${ Expr(fieldName) }}' not found in schema")
                  }
                }
                (accessorName, lensExpr)
            }
          }

          '{
            val cached = $cacheExpr.get()
            if (cached != null) {
              cached.asInstanceOf[refinedT]
            } else {
              val lensMap: Map[String, Any] = Map(
                ${
                  Expr.ofList(fieldLensExprs.map { case (name, expr) =>
                    '{ (${ Expr(name) }, $expr) }
                  })
                }*
              )

              val opticsInstance = new SelectableOptics {
                def selectDynamic(name: String): Any =
                  lensMap.getOrElse(
                    name,
                    sys.error(s"Unknown optics accessor: $name. Available: ${lensMap.keys.mkString(", ")}")
                  )
              }

              val result = opticsInstance.asInstanceOf[refinedT]
              $cacheExpr.compareAndSet(null, result)
              $cacheExpr.get().asInstanceOf[refinedT]
            }
          }
      }
    }

    def generateVariantOptics(variantSymbol: Symbol): Expr[Any] = {
      val children = variantSymbol.children.filter { child =>
        child.isClassDef || child.isValDef || child.flags.is(Flags.Module)
      }

      if (children.isEmpty) {
        report.errorAndAbort(s"Sealed trait/enum ${tpe.show} has no variants to generate optics for")
      }

      val baseType = TypeRepr.of[SelectableOptics]

      val childTypes: List[(Symbol, TypeRepr)] = children.map { child =>
        val childType =
          if (child.isClassDef || child.flags.is(Flags.Module)) child.typeRef
          else child.termRef.widen
        (child, childType)
      }

      val refinedType = childTypes.foldLeft(baseType) { case (acc, (child, childType)) =>
        val accessorName = mkAccessorName(decapitalize(child.name.stripSuffix("$")))
        val prismType    = TypeRepr.of[Prism].appliedTo(List(tpe, childType))
        Refinement(acc, accessorName, ByNameType(prismType))
      }

      refinedType.asType match {
        case '[refinedT] =>
          val prismExprs: List[(String, Expr[Any])] = childTypes.map { case (child, childType) =>
            val childName    = child.name.stripSuffix("$")
            val accessorName = mkAccessorName(decapitalize(childName))

            // FIX: Use underscore wildcard to ignore the unused type variable 'c'
            childType.asType match {
              case _ =>
                val prismExpr = '{
                  val variant = $schemaExpr.reflect.asInstanceOf[_root_.zio.blocks.schema.Reflect.Variant.Bound[T]]
                  variant.cases.find(_.name == ${ Expr(childName) }) match {
                    case Some(term) =>
                      // Construct Prism[T, T] to satisfy bounds, runtime cast handled by caller
                      _root_.zio.blocks.schema.Prism[T, T](
                        variant,
                        term.asInstanceOf[_root_.zio.blocks.schema.Term.Bound[T, T]]
                      )
                    case None =>
                      sys.error(s"Variant '${${ Expr(childName) }}' not found in schema")
                  }
                }
                (accessorName, prismExpr)
            }
          }

          '{
            val cached = $cacheExpr.get()
            if (cached != null) {
              cached.asInstanceOf[refinedT]
            } else {
              val prismMap: Map[String, Any] = Map(
                ${
                  Expr.ofList(prismExprs.map { case (name, expr) =>
                    '{ (${ Expr(name) }, $expr) }
                  })
                }*
              )

              val opticsInstance = new SelectableOptics {
                def selectDynamic(name: String): Any =
                  prismMap.getOrElse(
                    name,
                    sys.error(s"Unknown optics accessor: $name. Available: ${prismMap.keys.mkString(", ")}")
                  )
              }

              val result = opticsInstance.asInstanceOf[refinedT]
              $cacheExpr.compareAndSet(null, result)
              $cacheExpr.get().asInstanceOf[refinedT]
            }
          }
      }
    }

    val isCaseClass = symbol.isClassDef &&
      symbol.flags.is(Flags.Case) &&
      symbol.caseFields.nonEmpty

    val isSealedTrait = symbol.isClassDef &&
      (symbol.flags.is(Flags.Sealed) || symbol.flags.is(Flags.Enum))

    if (isCaseClass && !isSealedTrait) {
      generateRecordOptics(symbol)
    } else if (isSealedTrait) {
      generateVariantOptics(symbol)
    } else {
      report.errorAndAbort(
        s"DerivedOptics requires a case class or sealed trait/enum. " +
          s"Got: ${tpe.show}"
      )
    }
  }
}
