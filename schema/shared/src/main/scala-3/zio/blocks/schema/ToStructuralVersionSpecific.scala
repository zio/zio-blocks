package zio.blocks.schema

import scala.quoted._

object ToStructuralVersionSpecific {
  inline given [A]: Schema.ToStructural[A] = ${ impl[A] }

  private def impl[A: Type](using q: Quotes): Expr[Schema.ToStructural[A]] = {
    import q.reflect._

    def isPrimitive(tpe: TypeRepr): Boolean =
      tpe =:= TypeRepr.of[String] || tpe =:= TypeRepr.of[Int] || tpe =:= TypeRepr.of[Long] ||
        tpe =:= TypeRepr.of[Double] || tpe =:= TypeRepr.of[Float] || tpe =:= TypeRepr.of[Boolean] ||
        tpe =:= TypeRepr.of[Byte] || tpe =:= TypeRepr.of[Short] || tpe =:= TypeRepr.of[Char] ||
        tpe =:= TypeRepr.of[Unit] || tpe =:= TypeRepr.of[BigInt] || tpe =:= TypeRepr.of[BigDecimal]

    def isNonRecursive(tpe: TypeRepr, seen: Set[Symbol]): Boolean = {
      if (isPrimitive(tpe)) true
      else tpe.typeSymbol match {
        case sym if seen(sym) => false
        case sym if sym.isNoSymbol => true
        case sym =>
          val nextSeen = seen + sym
          tpe.typeSymbol.primaryConstructor.paramSymss.flatten.forall { p =>
            val pTpe = tpe.memberType(p).dealias
            isNonRecursive(pTpe, nextSeen)
          }
      }
    }

    val tpe = TypeRepr.of[A]
    if (!isNonRecursive(tpe, Set.empty))
      report.errorAndAbort(s"Cannot generate structural type for recursive type ${tpe.show}. Structural types cannot represent recursive structures.")

    '{ new zio.blocks.schema.Schema.ToStructural[A] {
       type StructuralType = zio.blocks.schema.DynamicValue
       def apply(schema: zio.blocks.schema.Schema[A]) = zio.blocks.schema.Schema.dynamic
     } }
  }
}
