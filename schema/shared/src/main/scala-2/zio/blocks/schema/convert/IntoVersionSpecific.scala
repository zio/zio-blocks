package zio.blocks.schema.convert

import zio.blocks.schema.CommonMacroOps
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.NameTransformer

trait IntoVersionSpecific {
  def derived[A, B]: Into[A, B] = macro IntoVersionSpecificImpl.derived[A, B]
}

private object IntoVersionSpecificImpl {
  def derived[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context): c.Expr[Into[A, B]] = {
    import c.universe._

    val aTpe = weakTypeOf[A].dealias
    val bTpe = weakTypeOf[B].dealias

    def fail(msg: String): Nothing = CommonMacroOps.fail(c)(msg)

    def typeArgs(tpe: Type): List[Type] = CommonMacroOps.typeArgs(c)(tpe)

    def isProductType(tpe: Type): Boolean =
      tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isCaseClass

    def primaryConstructor(tpe: Type): MethodSymbol = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(fail(s"Cannot find a primary constructor for '$tpe'"))

    // === Field Info ===

    class FieldInfo(
      val name: String,
      val tpe: Type,
      val index: Int,
      val getter: MethodSymbol
    )

    class ProductInfo(tpe: Type) {
      val tpeTypeArgs: List[Type] = typeArgs(tpe)

      val fields: List[FieldInfo] = {
        var getters = Map.empty[String, MethodSymbol]
        tpe.members.foreach {
          case m: MethodSymbol if m.isParamAccessor =>
            getters = getters.updated(NameTransformer.decode(m.name.toString), m)
          case _ =>
        }
        val tpeTypeParams =
          if (tpeTypeArgs ne Nil) tpe.typeSymbol.asClass.typeParams
          else Nil
        var idx = 0

        primaryConstructor(tpe).paramLists.flatten.map { param =>
          val symbol = param.asTerm
          val name   = NameTransformer.decode(symbol.name.toString)
          var fTpe   = symbol.typeSignature.dealias
          if (tpeTypeArgs ne Nil) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
          val getter = getters.getOrElse(
            name,
            fail(s"Field or getter '$name' of '$tpe' should be defined as 'val' or 'var' in the primary constructor.")
          )
          val fieldInfo = new FieldInfo(name, fTpe, idx, getter)
          idx += 1
          fieldInfo
        }
      }
    }

    // === Field Mapping ===

    class FieldMapping(
      val sourceField: FieldInfo,
      val targetField: FieldInfo
    )

    def matchFieldsByNameAndType(
      sourceInfo: ProductInfo,
      targetInfo: ProductInfo
    ): List[FieldMapping] = {
      targetInfo.fields.map { targetField =>
        sourceInfo.fields.find { sourceField =>
          sourceField.name == targetField.name && sourceField.tpe =:= targetField.tpe
        } match {
          case Some(sourceField) => new FieldMapping(sourceField, targetField)
          case None =>
            fail(
              s"Cannot derive Into[$aTpe, $bTpe]: " +
              s"no matching field found for '${targetField.name}: ${targetField.tpe}' in source type"
            )
        }
      }
    }

    // === Derivation ===

    def deriveProductToProduct(): c.Expr[Into[A, B]] = {
      val sourceInfo = new ProductInfo(aTpe)
      val targetInfo = new ProductInfo(bTpe)
      val fieldMappings = matchFieldsByNameAndType(sourceInfo, targetInfo)

      // Build constructor arguments: for each target field, read from source using getter
      val args = fieldMappings.map { mapping =>
        val getter = mapping.sourceField.getter
        q"a.$getter"
      }

      c.Expr[Into[A, B]](
        q"""
          new _root_.zio.blocks.schema.convert.Into[$aTpe, $bTpe] {
            def into(a: $aTpe): _root_.scala.Either[_root_.zio.blocks.schema.SchemaError, $bTpe] = {
              _root_.scala.Right(new $bTpe(..$args))
            }
          }
        """
      )
    }

    // === Main entry point ===

    if (!isProductType(aTpe) || !isProductType(bTpe)) {
      fail(s"Cannot derive Into[$aTpe, $bTpe]: only case class to case class is currently supported")
    }

    deriveProductToProduct()
  }
}
