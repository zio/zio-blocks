package zio.blocks.schema

import scala.reflect.macros.whitebox
import zio.blocks.schema.binding.StructuralValue

object DeriveToStructural {
  def derivedImpl[A: c.WeakTypeTag](c: whitebox.Context): c.Expr[ToStructural[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A]
    val symbol = tpe.typeSymbol

    if (!symbol.isClass || !symbol.asClass.isCaseClass) {
      c.abort(c.enclosingPosition, s"ToStructural derivation only supports case classes, found: $tpe")
    }

    // Extract fields from primary constructor
    val fields = tpe.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => m
    }.toList

    // (name, decodedName, type) pairs
    val fieldInfos = fields.map { f =>
      val name = f.name
      val decodedName = name.decodedName.toString.trim
      val returnType = f.returnType.asSeenFrom(tpe, symbol)
      (name, decodedName, returnType)
    }

    val structuralValueTpe = typeOf[StructuralValue]

    // Construct the type tree for the structural type: StructuralValue { def f1: T1; ... }
    val fieldDecls = fieldInfos.map { case (name, _, fieldTpe) =>
      q"def $name: $fieldTpe"
    }
    val structuralTypeTree = tq"$structuralValueTpe { ..$fieldDecls }"

    // Map construction for the runtime value
    val mapEntries = fieldInfos.map { case (name, decodedName, _) =>
      q"($decodedName, value.$name)"
    }

    // Implementation of the methods in the anonymous class
    val methodImpls = fieldInfos.map { case (name, decodedName, fieldTpe) =>
      q"def $name: $fieldTpe = values($decodedName).asInstanceOf[$fieldTpe]"
    }

    val tree = q"""
      new _root_.zio.blocks.schema.ToStructural[$tpe] {
        type StructuralType = $structuralTypeTree

        def toStructural(value: $tpe): StructuralType = {
          val map = _root_.scala.collection.immutable.Map[String, Any](..$mapEntries)
          new _root_.zio.blocks.schema.binding.StructuralValue(map) {
            ..$methodImpls
          }.asInstanceOf[StructuralType]
        }

        def structuralSchema(implicit schema: _root_.zio.blocks.schema.Schema[$tpe]): _root_.zio.blocks.schema.Schema[StructuralType] = {
          _root_.zio.blocks.schema.Schema.derived[StructuralType]
        }
      }
    """

    c.Expr[ToStructural[A]](tree)
  }
}
