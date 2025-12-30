package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object ToStructuralVersionSpecific {
  implicit def materialize[A]: ToStructural.Aux[A, DynamicValue] = macro materializeImpl[A]

  def materializeImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[ToStructural.Aux[A, DynamicValue]] = {
    import c.universe._

    val tpe = weakTypeOf[A]

    // simple recursion check: fail if the type references itself directly
    def containsRecursive(tp: Type, seen: Set[Type]): Boolean =
      if (seen.contains(tp)) true
      else {
        val nested = tp match {
          case TypeRef(_, _, args) => args
          case _                   => Nil
        }
        nested.exists(containsRecursive(_, seen + tp))
      }

    if (containsRecursive(tpe, Set.empty)) {
      c.abort(c.enclosingPosition, s"Cannot generate structural type for recursive type: ${tpe}")
    }

    def repr(tp: Type): String =
      if (tp =:= typeOf[String]) "String"
      else if (tp =:= typeOf[Int]) "Int"
      else if (tp =:= typeOf[Long]) "Long"
      else if (tp =:= typeOf[Double]) "Double"
      else if (tp =:= typeOf[Float]) "Float"
      else if (tp =:= typeOf[Boolean]) "Boolean"
      else if (tp =:= typeOf[Char]) "Char"
      else if (tp =:= typeOf[Byte]) "Byte"
      else if (tp =:= typeOf[Short]) "Short"
      else if (tp =:= typeOf[Unit]) "Unit"
      else
        tp match {
          case TypeRef(_, sym, args) if args.nonEmpty =>
            val name  = sym.name.toString
            val argsS = args.map(repr).mkString(",")
            s"$name[$argsS]"
          case TypeRef(_, sym, _) =>
            // product types: attempt to inspect primary constructor params
            // Only call `asClass` when the symbol is a class; for other symbols
            // (type aliases, type params) fall back to `dealias` or the symbol
            // name to avoid crashing the macro on non-class symbols.
            if (sym.isClass) {
              val cls = sym.asClass
              if (cls.isCaseClass) {
                val ctor   = cls.primaryConstructor.asMethod
                val params = ctor.paramLists.flatten.map { p =>
                  val pname = p.name.toString
                  val ptype = tp.member(p.name) match {
                    case s: TermSymbol => s.typeSignatureIn(tp)
                    case _             => NoType
                  }
                  pname -> repr(ptype)
                }
                val sorted = params.sortBy(_._1)
                sorted.map { case (n, r) => s"$n:$r" }.mkString("{", ",", "}")
              } else sym.name.toString
            } else {
              // Non-class symbol (e.g., type alias). Attempt to resolve the alias
              // to its underlying type and render that; if unresolved, use name.
              val dealiased = tp.dealias
              if (dealiased != tp) repr(dealiased) else sym.name.toString
            }
          case _ => tp.toString
        }

    val normalized = repr(tpe)

    val tree =
      q"new zio.blocks.schema.ToStructural[${tpe}] { type StructuralType = zio.blocks.schema.DynamicValue; def apply(schema: zio.blocks.schema.Schema[${tpe}]): zio.blocks.schema.Schema[zio.blocks.schema.DynamicValue] = { val tn = new zio.blocks.schema.TypeName(zio.blocks.schema.Namespace.zioBlocksSchema, ${Literal(Constant(normalized))}, Nil); new zio.blocks.schema.Schema(zio.blocks.schema.Schema.dynamic.reflect.typeName(tn)) } }"

    c.Expr[ToStructural.Aux[A, DynamicValue]](tree)
  }
}
