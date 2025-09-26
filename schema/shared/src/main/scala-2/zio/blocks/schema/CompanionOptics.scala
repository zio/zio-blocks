package zio.blocks.schema

trait CompanionOptics[S] {
  import scala.annotation.compileTimeOnly
  import scala.language.experimental.macros

  implicit class ValueExtension[A](a: A) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def when[B <: A]: B = ???

    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def wrapped[B]: B = ???
  }

  implicit class SequenceExtension[C[_], A](c: C[A]) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def at(index: Int): A = ???

    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def atIndices(indices: Int*): A = ???

    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def each: A = ???
  }

  implicit class MapExtension[M[_, _], K, V](m: M[K, V]) {
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def atKey(key: K): V = ???

    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def atKeys(keys: K*): V = ???

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

  def optic[S, A](c: whitebox.Context)(path: c.Expr[S => A])(schema: c.Expr[Schema[S]]): c.Tree = {
    import c.universe._

    def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => fail(s"Expected a lambda expression, got: '$tree'")
    }

    def typeArgs(tpe: Type): List[Type] = tpe.typeArgs.map(_.dealias)

    implicit val positionOrdering: Ordering[Symbol] =
      (x: Symbol, y: Symbol) => {
        val xPos  = x.pos
        val yPos  = y.pos
        val xFile = xPos.source.file.absolute
        val yFile = yPos.source.file.absolute
        var diff  = xFile.path.compareTo(yFile.path)
        if (diff == 0) diff = xFile.name.compareTo(yFile.name)
        if (diff == 0) diff = xPos.line.compareTo(yPos.line)
        if (diff == 0) diff = xPos.column.compareTo(yPos.column)
        if (diff == 0) {
          // make sorting stable in case of missing sources for sub-project or *.jar dependencies
          diff = NameTransformer.decode(x.fullName).compareTo(NameTransformer.decode(y.fullName))
        }
        diff
      }

    def directSubTypes(tpe: Type): List[Type] = {
      val tpeClass         = tpe.typeSymbol.asClass
      val tpeTypeArgs      = typeArgs(tpe)
      val tpeParamsAndArgs =
        if (tpeTypeArgs ne Nil) tpeClass.typeParams.map(_.toString).zip(tpeTypeArgs).toMap
        else Map.empty[String, Type]
      tpeClass.knownDirectSubclasses.toArray
        .sortInPlace()
        .map { symbol =>
          val classSymbol = symbol.asClass
          val typeParams  = classSymbol.typeParams
          val classType   = classSymbol.toType
          if (typeParams eq Nil) classType
          else {
            classType.substituteTypes(
              typeParams,
              typeParams.map { typeParam =>
                tpeParamsAndArgs.getOrElse(
                  typeParam.toString,
                  fail(
                    s"Type parameter '${typeParam.name}' of '$symbol' can't be deduced from type arguments of '$tpe'."
                  )
                )
              }
            )
          }
        }
        .toList
    }

    def toOptic(tree: c.Tree): c.Tree = tree match {
      case q"$_[..$_]($parent).each" =>
        val parentTpe  = parent.tpe.widen.dealias
        val elementTpe = tree.tpe.widen.dealias
        val optic      = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.seqValues(x.sequence)
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $elementTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.seqValues(x.sequence)
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $elementTpe]])"""
        }
      case q"$_[..$_]($parent).eachKey" =>
        val parentTpe = parent.tpe.widen.dealias
        val keyTpe    = tree.tpe.widen.dealias
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.mapKeys(x.map)
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $keyTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.mapKeys(x.map)
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $keyTpe]])"""
        }
      case q"$_[..$_]($parent).eachValue" =>
        val parentTpe = parent.tpe.widen.dealias
        val valueTpe  = tree.tpe.widen.dealias
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.mapValues(x.map)
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $valueTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.mapValues(x.map)
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $valueTpe]])"""
        }
      case q"$_[..$_]($parent).when[$caseTree]" =>
        val parentTpe = parent.tpe.widen.dealias
        val caseTpe   = caseTree.tpe.dealias
        val caseIdx   = directSubTypes(parentTpe).indexWhere(_ =:= caseTpe, 0)
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asVariant.flatMap(_.prismByIndex[$caseTpe]($caseIdx))
                .getOrElse(sys.error("Expected a variant"))"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asVariant.flatMap(_.prismByIndex[$caseTpe]($caseIdx))
                .getOrElse(sys.error("Expected a variant")))"""
        }
      case q"$_[..$_]($parent).wrapped[$wrappedTree]" =>
        val parentTpe  = parent.tpe.widen.dealias
        val wrappedTpe = wrappedTree.tpe.dealias
        val optic      = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asWrapperUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.wrapped(x.wrapper.asInstanceOf[Reflect.Wrapper[Binding, $parentTpe, $wrappedTpe]])
              }
              .getOrElse(sys.error("Expected a wrapper"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $wrappedTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asWrapperUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.wrapped(x.wrapper.asInstanceOf[Reflect.Wrapper[Binding, $parentTpe, $wrappedTpe]])
              }
              .getOrElse(sys.error("Expected a wrapper"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $wrappedTpe]])"""
        }
      case q"$_[..$_]($parent).at(..$args)" if args.size == 1 && args.head.tpe.widen.dealias <:< definitions.IntTpe =>
        val parentTpe  = parent.tpe.widen.dealias
        val elementTpe = tree.tpe.widen.dealias
        val optic      = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.at(x.sequence, ${args.head})
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $elementTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.at(x.sequence, ${args.head})
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $elementTpe]])"""
        }
      case q"$_[..$_]($parent).atKey(..$args)" if args.size == 1 =>
        val parentTpe = parent.tpe.widen.dealias
        val valueTpe  = tree.tpe.widen.dealias
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.atKey(x.map, ${args.head}.asInstanceOf[x.KeyType])
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $valueTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.atKey(x.map, ${args.head}.asInstanceOf[x.KeyType])
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $valueTpe]])"""
        }
      case q"$_[..$_]($parent).atIndices(..$args)"
          if args.nonEmpty && args.forall(_.tpe.widen.dealias <:< definitions.IntTpe) =>
        val parentTpe  = parent.tpe.widen.dealias
        val elementTpe = tree.tpe.widen.dealias
        val optic      = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.atIndices(x.sequence, $args)
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $elementTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.atIndices(x.sequence, $args)
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $elementTpe]])"""
        }
      case q"$_[..$_]($parent).atKeys(..$args)" if args.nonEmpty =>
        val parentTpe = parent.tpe.widen.dealias
        val valueTpe  = tree.tpe.widen.dealias
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.atKeys(x.map, $args.asInstanceOf[Seq[x.KeyType]])
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $valueTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.atKeys(x.map, $args.asInstanceOf[Seq[x.KeyType]])
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $valueTpe]])"""
        }
      case q"$parent.$child" =>
        val childTpe  = tree.tpe.widen.dealias
        val fieldName = NameTransformer.decode(child.toString)
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asRecord.flatMap(_.lensByName[$childTpe]($fieldName))
                .getOrElse(sys.error("Expected a record"))"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asRecord.flatMap(_.lensByName[$childTpe]($fieldName))
                .getOrElse(sys.error("Expected a record")))"""
        }
      case _: Ident =>
        q""
      case tree =>
        fail(
          s"Expected path elements: .<field>, .when[<T>], .at(<index>), .atIndices(<indices>), .atKey(<key>), .atKeys(<keys>), .each, .eachKey, .eachValue, or .wrapped[<T>], got: '$tree'"
        )
    }

    val optic = toOptic(toPathBody(path.tree))
    // c.info(c.enclosingPosition, s"Generated optic:\n${showCode(optic)}", force = true)
    optic
  }
}
