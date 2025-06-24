package zio.blocks.schema

trait CompanionOptics[S] {
  import scala.annotation.compileTimeOnly
  import scala.language.experimental.macros

  implicit class VariantExtension[A](a: A) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def when[B <: A]: B = ???
  }

  implicit class SequenceExtension[C[_], A](c: C[A]) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def each: A = ???
  }

  implicit class MapExtension[M[_, _], K, V](m: M[K, V]) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def eachKey: K = ???

    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def eachValue: V = ???
  }

  def $[A](path: S => A)(implicit schema: Schema[S]): Any = macro CompanionOptics.optic[S, A]

  def optic[A](path: S => A)(implicit schema: Schema[S]): Any = macro CompanionOptics.optic[S, A]
}

private object CompanionOptics {
  import scala.reflect.macros.whitebox
  import scala.reflect.NameTransformer

  def optic[S: c.WeakTypeTag, A: c.WeakTypeTag](
    c: whitebox.Context
  )(path: c.Expr[S => A])(schema: c.Expr[Schema[S]]): c.Tree = {
    import c.universe._

    def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => fail(s"Expected a lambda expression, got: ${showRaw(tree)}")
    }

    def toOptic(tree: c.Tree): c.Tree = tree match {
      case q"$_[..$_]($parent).each" =>
        val parentTpe  = parent.tpe.dealias.widen
        val elementTpe = tree.tpe.dealias.widen
        val optic      = toOptic(parent)
        if (optic.isEmpty) fail("Expected a path element preceding `.each`")
        else
          q"""$optic.apply($optic.focus.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.seqValues(x.sequence)
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $elementTpe]])"""
      case q"$_[..$_]($parent).eachKey" =>
        val parentTpe = parent.tpe.dealias.widen
        val keyTpe    = tree.tpe.dealias.widen
        val optic     = toOptic(parent)
        if (optic.isEmpty) fail("Expected a path element preceding `.eachKey`")
        else
          q"""$optic.apply($optic.focus.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.mapKeys(x.map)
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $keyTpe]])"""
      case q"$_[..$_]($parent).eachValue" =>
        val parentTpe = parent.tpe.dealias.widen
        val valueTpe  = tree.tpe.dealias.widen
        val optic     = toOptic(parent)
        if (optic.isEmpty) fail("Expected a path element preceding `.eachValue`")
        else
          q"""$optic.apply($optic.focus.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.mapValues(x.map)
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $valueTpe]])"""
      case q"$_[..$_]($parent).when[$caseTree]" =>
        val caseTpe  = caseTree.tpe.dealias
        val caseName = NameTransformer.decode(caseTpe.typeSymbol.name.toString)
        val optic    = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asVariant.flatMap(_.prismByName[$caseTpe]($caseName))
                .getOrElse(sys.error("Expected a variant"))"""
        } else {
          q"""$optic.apply($optic.focus.asVariant.flatMap(_.prismByName[$caseTpe]($caseName))
                .getOrElse(sys.error("Expected a variant")))"""
        }
      case q"$parent.$child" =>
        val childTpe  = tree.tpe.dealias.widen
        val fieldName = NameTransformer.decode(child.toString)
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asRecord.flatMap(_.lensByName[$childTpe]($fieldName))
                .getOrElse(sys.error("Expected a record"))"""
        } else {
          q"""$optic.apply($optic.focus.asRecord.flatMap(_.lensByName[$childTpe]($fieldName))
                .getOrElse(sys.error("Expected a record")))"""
        }
      case _: Ident =>
        q""
      case tree =>
        fail(s"Expected a path element, got: ${showRaw(tree)}")
    }

    toOptic(toPathBody(path.tree))
  }
}
