package zio.blocks.schema

import scala.reflect.macros.whitebox

object DeriveToStructural {
  def derivedImpl[A: c.WeakTypeTag](c: whitebox.Context): c.Expr[ToStructural[A]] = {
    import c.universe._

    def fail(msg: String): Nothing =
      c.abort(c.enclosingPosition, msg)

    val tpe    = weakTypeOf[A]
    val symbol = tpe.typeSymbol

    if (symbol.isClass && symbol.asClass.isSealed) {
      fail(
        s"""Cannot generate structural type for sum types in Scala 2 ($tpe).
           |Structural representation of sum types requires union types,
           |which are only available in Scala 3.
           |Consider upgrading to Scala 3 or using a different approach.""".stripMargin
      )
    }

    if (!symbol.isClass || !symbol.asClass.isCaseClass) {
      fail(s"ToStructural derivation only supports case classes, found: $tpe")
    }

    // --------- helpers ----------
    val toStructuralType = typeOf[ToStructural[_]].typeConstructor

    def isPrimitiveLike(t: Type): Boolean =
      t <:< definitions.BooleanTpe ||
        t <:< definitions.ByteTpe ||
        t <:< definitions.ShortTpe ||
        t <:< definitions.IntTpe ||
        t <:< definitions.LongTpe ||
        t <:< definitions.FloatTpe ||
        t <:< definitions.DoubleTpe ||
        t <:< definitions.CharTpe ||
        t <:< definitions.UnitTpe ||
        t =:= typeOf[String] ||
        t =:= typeOf[BigDecimal] ||
        t =:= typeOf[BigInt] ||
        t.typeSymbol.fullName.startsWith("java.time.") ||
        t =:= typeOf[java.util.UUID] ||
        t =:= typeOf[java.util.Currency]

    def isCaseClass(t: Type): Boolean =
      t.typeSymbol.isClass && t.typeSymbol.asClass.isCaseClass && !t.typeSymbol.asClass.isSealed

    def isTuple(t: Type): Boolean =
      t.typeSymbol.fullName.startsWith("scala.Tuple")

    def applied(t: Type, idx: Int): Type =
      t.typeArgs(idx).dealias

    def deriveToStructural(t: Type): Tree = {
      val wanted = appliedType(toStructuralType, List(t))
      c.inferImplicitValue(wanted) match {
        case EmptyTree => q"_root_.zio.blocks.schema.ToStructural.derived[$t]"
        case other     => other
      }
    }

    val rootType                                                  = tpe.dealias
    def failRecursion(loopType: Type, stack: List[Type]): Nothing = {
      val index          = stack.indexWhere(_ =:= loopType)
      val cycle          = if (index >= 0) stack.take(index + 1) else stack
      val recursiveTypes = (loopType :: cycle).filter(isCaseClass).map(_.typeSymbol.name.decodedName.toString).distinct

      if (recursiveTypes.size > 1) {
        fail(
          s"""Cannot generate structural type for mutually recursive types: ${recursiveTypes.mkString(", ")}.
             |Structural types cannot represent recursive structures.""".stripMargin
        )
      } else {
        fail(
          s"""Cannot generate structural type for recursive type ${loopType.typeSymbol.name.decodedName.toString}.
             |Structural types cannot represent recursive structures.
             |Scala's type system does not support infinite types.""".stripMargin
        )
      }
    }

    def checkRecursive(t: Type, stack: List[Type]): Unit = {
      val dealt = t.dealias
      if (stack.exists(_ =:= dealt)) failRecursion(dealt, stack)
      else if (isPrimitiveLike(dealt)) ()
      else if (
        dealt <:< typeOf[Option[_]] ||
        dealt <:< typeOf[List[_]] ||
        dealt <:< typeOf[Vector[_]] ||
        dealt <:< typeOf[Seq[_]] ||
        dealt <:< typeOf[Set[_]]
      ) {
        checkRecursive(applied(dealt, 0), dealt :: stack)
      } else if (dealt <:< typeOf[Map[_, _]]) {
        checkRecursive(applied(dealt, 0), dealt :: stack)
        checkRecursive(applied(dealt, 1), dealt :: stack)
      } else if (dealt <:< typeOf[Either[_, _]]) {
        checkRecursive(applied(dealt, 0), dealt :: stack)
        checkRecursive(applied(dealt, 1), dealt :: stack)
      } else if (isTuple(dealt)) {
        dealt.typeArgs.foreach(arg => checkRecursive(arg, dealt :: stack))
      } else if (isCaseClass(dealt)) {
        dealt.decls.collect { case m: MethodSymbol if m.isCaseAccessor => m }.foreach { f =>
          val fieldTpe = f.returnType.asSeenFrom(dealt, dealt.typeSymbol)
          checkRecursive(fieldTpe, dealt :: stack)
        }
      } // other unsupported shapes (like sealed) are already aborted earlier
    }

    checkRecursive(rootType, Nil)

    def structuralTypeTree(t: Type, seen: Set[Type]): Tree = {
      val dealt = t.dealias

      if (seen.contains(dealt)) tq"$dealt" // recursion handling will be added later
      else if (isPrimitiveLike(dealt)) {
        tq"$dealt"
      } else if (dealt <:< typeOf[Option[_]]) {
        val inner = structuralTypeTree(applied(dealt, 0), seen + dealt)
        tq"_root_.scala.Option[$inner]"
      } else if (dealt <:< typeOf[List[_]]) {
        val inner = structuralTypeTree(applied(dealt, 0), seen + dealt)
        tq"_root_.scala.List[$inner]"
      } else if (dealt <:< typeOf[Vector[_]]) {
        val inner = structuralTypeTree(applied(dealt, 0), seen + dealt)
        tq"_root_.scala.Vector[$inner]"
      } else if (dealt <:< typeOf[Seq[_]]) {
        val inner = structuralTypeTree(applied(dealt, 0), seen + dealt)
        tq"_root_.scala.Seq[$inner]"
      } else if (dealt <:< typeOf[Set[_]]) {
        val inner = structuralTypeTree(applied(dealt, 0), seen + dealt)
        tq"_root_.scala.collection.immutable.Set[$inner]"
      } else if (dealt <:< typeOf[Map[_, _]]) {
        val key   = structuralTypeTree(applied(dealt, 0), seen + dealt)
        val value = structuralTypeTree(applied(dealt, 1), seen + dealt)
        tq"_root_.scala.collection.immutable.Map[$key,$value]"
      } else if (dealt <:< typeOf[Either[_, _]]) {
        val l = structuralTypeTree(applied(dealt, 0), seen + dealt)
        val r = structuralTypeTree(applied(dealt, 1), seen + dealt)
        tq"_root_.scala.Either[$l,$r]"
      } else if (isTuple(dealt)) {
        val decls = dealt.typeArgs.zipWithIndex.map { case (arg, idx) =>
          val st = structuralTypeTree(arg, seen + dealt)
          q"def ${TermName(s"_${idx + 1}")}: $st"
        }
        tq"_root_.zio.blocks.schema.binding.StructuralValue { ..$decls }"
      } else if (isCaseClass(dealt)) {
        val fields = dealt.decls.collect {
          case m: MethodSymbol if m.isCaseAccessor => m
        }.toList
        val decls = fields.map { f =>
          val fieldTpe = f.returnType.asSeenFrom(dealt, dealt.typeSymbol)
          val st       = structuralTypeTree(fieldTpe, seen + dealt)
          q"def ${f.name}: $st"
        }
        tq"_root_.zio.blocks.schema.binding.StructuralValue { ..$decls }"
      } else tq"$dealt"
    }

    def convertExpr(expr: Tree, t: Type, seen: Set[Type]): Tree = {
      val dealt = t.dealias

      if (seen.contains(dealt)) expr
      else if (dealt <:< typeOf[Option[_]]) {
        val innerTpe = applied(dealt, 0)
        val body     = convertExpr(Ident(TermName("v")), innerTpe, seen + dealt)
        q"$expr.map(v => $body)"
      } else if (
        dealt <:< typeOf[List[_]] || dealt <:< typeOf[Vector[_]] || dealt <:< typeOf[Seq[_]] || dealt <:< typeOf[Set[_]]
      ) {
        val innerTpe = applied(dealt, 0)
        val body     = convertExpr(Ident(TermName("v")), innerTpe, seen + dealt)
        q"$expr.map(v => $body)"
      } else if (dealt <:< typeOf[Map[_, _]]) {
        val ktpe  = applied(dealt, 0)
        val vtpe  = applied(dealt, 1)
        val kbody = convertExpr(Ident(TermName("k")), ktpe, seen + dealt)
        val vbody = convertExpr(Ident(TermName("v")), vtpe, seen + dealt)
        q"$expr.map { case (k, v) => ($kbody, $vbody) }"
      } else if (dealt <:< typeOf[Either[_, _]]) {
        val ltpe  = applied(dealt, 0)
        val rtpe  = applied(dealt, 1)
        val lbody = convertExpr(Ident(TermName("l")), ltpe, seen + dealt)
        val rbody = convertExpr(Ident(TermName("r")), rtpe, seen + dealt)
        q"$expr match { case scala.util.Left(l) => scala.util.Left($lbody); case scala.util.Right(r) => scala.util.Right($rbody) }"
      } else if (isTuple(dealt)) {
        val entries = dealt.typeArgs.zipWithIndex.map { case (argTpe, idx) =>
          val nameLit = Literal(Constant(s"_${idx + 1}"))
          val value   = q"$expr.${TermName(s"_${idx + 1}")}"
          val conv    = convertExpr(value, argTpe, seen + dealt)
          q"$nameLit -> $conv"
        }
        q"new _root_.zio.blocks.schema.binding.StructuralValue(_root_.scala.collection.immutable.Map[String, Any](..$entries))"
      } else if (isCaseClass(dealt)) {
        val ts = deriveToStructural(dealt)
        q"$ts.toStructural($expr)"
      } else expr
    }

    val tree = q"""
      new _root_.zio.blocks.schema.ToStructural[$tpe] {
        type StructuralType = ${structuralTypeTree(tpe, Set.empty)}

        def toStructural(value: $tpe): StructuralType = {
          val values = _root_.scala.collection.immutable.Map[String, Any](..${tpe.decls.collect {
        case m: MethodSymbol if m.isCaseAccessor =>
          val decoded = m.name.decodedName.toString.trim
          q"$decoded -> ${convertExpr(q"value.${m.name}", m.returnType.asSeenFrom(tpe, symbol), Set(tpe))}"
      }.toList})
          new _root_.zio.blocks.schema.binding.StructuralValue(values).asInstanceOf[StructuralType]
        }

        def structuralSchema(implicit schema: _root_.zio.blocks.schema.Schema[$tpe]): _root_.zio.blocks.schema.Schema[StructuralType] = {
          _root_.zio.blocks.schema.Schema.derived[StructuralType]
        }
      }
    """

    c.Expr[ToStructural[A]](tree)
  }
}
