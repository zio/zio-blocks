package zio.blocks.schema


trait CompanionOptics[S] {
  import scala.annotation.compileTimeOnly
  import scala.language.experimental.macros

  implicit class VariantExtension[A](a: A) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def when[B <: A]: B = ???
  }

  implicit class SequenceExtension[C[_], A](a: C[A]) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def each: A = ???
  }

  implicit class MapExtension[M[_, _], K, V](a: M[K, V]) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def eachKey: K = ???

    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def eachValue: V = ???
  }

  def $[A](path: S => A)(implicit schema: Schema[S]): Any = macro CompanionOptics.optic[S, A]

  def optic[A](path: S => A)(implicit schema: Schema[S]): Any = macro CompanionOptics.optic[S, A]

  def field[A](path: S => A)(implicit schema: Schema[S]): Lens[S, A] = macro CompanionOptics.field[S, A]

  def caseOf[A <: S](implicit schema: Schema[S]): Prism[S, A] = macro CompanionOptics.caseOf[S, A]
}

private object CompanionOptics {
  import scala.reflect.macros.whitebox
  import scala.reflect.macros.blackbox
  import scala.reflect.NameTransformer

  def optic[S: c.WeakTypeTag, A: c.WeakTypeTag](
    c: whitebox.Context
  )(path: c.Expr[S => A])(schema: c.Expr[Schema[S]]): c.Tree = {
    import c.universe._

    val sTpe = weakTypeOf[S].dealias

    def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => fail(s"Expected a lambda expression, got: ${showRaw(tree)}")
    }

    def toOptic(tree: c.Tree): c.Tree = tree match {
      case q"$_[..$_]($parent).each" =>
        val cTpe  = parent.tpe.dealias.widen
        val aTpe  = tree.tpe.dealias.widen
        val optic = toOptic(parent)
        if (optic.isEmpty) fail("Expected a path element preceding `.each`")
        else
          q"""$optic.asInstanceOf[_root_.zio.blocks.schema.Optic[$sTpe, $cTpe]]
                .apply($optic.focus.asSequenceUnknown.map { x =>
                  _root_.zio.blocks.schema.Traversal.seqValues(x.sequence)
                }.get.asInstanceOf[_root_.zio.blocks.schema.Traversal[$cTpe, $aTpe]])"""
      case q"$_[..$_]($parent).eachKey" =>
        val cTpe  = parent.tpe.dealias.widen
        val aTpe  = tree.tpe.dealias.widen
        val optic = toOptic(parent)
        if (optic.isEmpty) fail("Expected a path element preceding `.eachKey`")
        else
          q"""$optic.asInstanceOf[_root_.zio.blocks.schema.Optic[$sTpe, $cTpe]]
                .apply($optic.focus.asMapUnknown.map { x =>
                  _root_.zio.blocks.schema.Traversal.mapKeys(x.map)
                }.get.asInstanceOf[_root_.zio.blocks.schema.Traversal[$cTpe, $aTpe]])"""
      case q"$_[..$_]($parent).eachValue" =>
        val cTpe  = parent.tpe.dealias.widen
        val aTpe  = tree.tpe.dealias.widen
        val optic = toOptic(parent)
        if (optic.isEmpty) fail("Expected a path element preceding `.eachValue`")
        else
          q"""$optic.asInstanceOf[_root_.zio.blocks.schema.Optic[$sTpe, $cTpe]]
                .apply($optic.focus.asMapUnknown.map { x =>
                  _root_.zio.blocks.schema.Traversal.mapValues(x.map)
                }.get.asInstanceOf[_root_.zio.blocks.schema.Traversal[$cTpe, $aTpe]])"""
      case q"$parent.$child" =>
        val aTpe      = tree.tpe.dealias.widen
        val fieldName = NameTransformer.decode(child.toString)
        val optic     = toOptic(parent)
        if (optic.isEmpty) q"$schema.reflect.asRecord.flatMap(_.lensByName[$aTpe]($fieldName)).get"
        else q"$optic.apply($optic.focus.asRecord.flatMap(_.lensByName[$aTpe]($fieldName)).get)"
      case q"$_[..$_]($parent).when[$caseTree]" =>
        val aTpe     = caseTree.tpe.dealias
        val caseName = NameTransformer.decode(aTpe.typeSymbol.name.toString)
        val optic    = toOptic(parent)
        if (optic.isEmpty) q"$schema.reflect.asVariant.flatMap(_.prismByName[$aTpe]($caseName)).get"
        else q"$optic.apply($optic.focus.asVariant.flatMap(_.prismByName[$aTpe]($caseName)).get)"
      case _: Ident =>
        q""
      case tree =>
        fail(s"Expected a path element, got: ${showRaw(tree)}")
    }

    toOptic(toPathBody(path.tree))
  }

  def field[S: c.WeakTypeTag, A: c.WeakTypeTag](
    c: blackbox.Context
  )(path: c.Expr[S => A])(schema: c.Expr[Schema[S]]): c.Expr[Lens[S, A]] = {
    import c.universe._

    def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => fail(s"Expected a lambda expression, got: ${showRaw(tree)}")
    }

    toPathBody(path.tree) match {
      case q"$_.$child" =>
        val aTpe      = weakTypeOf[A].dealias
        val fieldName = NameTransformer.decode(child.toString)
        c.Expr[Lens[S, A]](q"$schema.reflect.asRecord.flatMap(_.lensByName[$aTpe]($fieldName)).get")
      case tree =>
        fail(s"Expected a path element, got: ${showRaw(tree)}")
    }
  }

  def caseOf[S: c.WeakTypeTag, A <: S: c.WeakTypeTag](
    c: blackbox.Context
  )(schema: c.Expr[Schema[S]]): c.Expr[Prism[S, A]] = {
    import c.universe._

    val aTpe     = weakTypeOf[A].dealias
    val caseName = NameTransformer.decode(aTpe.typeSymbol.name.toString)
    c.Expr[Prism[S, A]](q"$schema.reflect.asVariant.flatMap(_.prismByName[$aTpe]($caseName)).get")
  }
}
