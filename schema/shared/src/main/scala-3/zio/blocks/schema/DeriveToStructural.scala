package zio.blocks.schema

import scala.quoted._
import zio.blocks.schema.binding.StructuralValue

object DeriveToStructural {
  def derivedImpl[A: Type](using Quotes): Expr[ToStructural[A]] = {
    import quotes.reflect._

    val tpe    = TypeRepr.of[A]
    val symbol = tpe.typeSymbol

    // Basic validation
    if (!symbol.isClassDef) {
      report.errorAndAbort(
        s"ToStructural derivation only supports class definitions (case classes or sealed traits), found: ${tpe.show}"
      )
    }
    // We allow Case classes and Sealed traits/classes
    val isCase       = symbol.flags.is(Flags.Case)
    val isSealedType = symbol.flags.is(Flags.Sealed)

    if (!isCase && !isSealedType) {
      report.errorAndAbort(s"ToStructural derivation requires a case class or a sealed trait/class, found: ${tpe.show}")
    }

    // --- Portable Helpers (Context-Aware) ---

    def isPrimitiveLike(using q: Quotes)(t: q.reflect.TypeRepr): Boolean = {
      import q.reflect._
      val pure = t.dealias
      pure =:= TypeRepr.of[Boolean] ||
      pure =:= TypeRepr.of[Byte] ||
      pure =:= TypeRepr.of[Short] ||
      pure =:= TypeRepr.of[Int] ||
      pure =:= TypeRepr.of[Long] ||
      pure =:= TypeRepr.of[Float] ||
      pure =:= TypeRepr.of[Double] ||
      pure =:= TypeRepr.of[Char] ||
      pure =:= TypeRepr.of[Unit] ||
      pure =:= TypeRepr.of[String] ||
      pure =:= TypeRepr.of[BigDecimal] ||
      pure =:= TypeRepr.of[BigInt] ||
      pure =:= TypeRepr.of[java.util.UUID] ||
      pure =:= TypeRepr.of[java.util.Currency] ||
      pure.typeSymbol.fullName.startsWith("java.time.")
    }

    def isCaseClass(using q: Quotes)(t: q.reflect.TypeRepr): Boolean = {
      import q.reflect._
      val sym = t.typeSymbol
      sym.isClassDef && sym.flags.is(Flags.Case) && !sym.flags.is(Flags.Sealed)
    }

    def isSealed(using q: Quotes)(t: q.reflect.TypeRepr): Boolean = {
      import q.reflect._
      val sym = t.typeSymbol
      sym.flags.is(Flags.Sealed) && (sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract) || sym.isClassDef)
    }

    def isTuple(using q: Quotes)(t: q.reflect.TypeRepr): Boolean =
      t.typeSymbol.fullName.startsWith("scala.Tuple")

    def isOption(using q: Quotes)(t: q.reflect.TypeRepr): Boolean = {
      import q.reflect._; t.dealias <:< TypeRepr.of[Option[_]]
    }
    def isList(using q: Quotes)(t: q.reflect.TypeRepr): Boolean = {
      import q.reflect._; t.dealias <:< TypeRepr.of[List[_]]
    }
    def isVector(using q: Quotes)(t: q.reflect.TypeRepr): Boolean = {
      import q.reflect._; t.dealias <:< TypeRepr.of[Vector[_]]
    }
    def isSeq(using q: Quotes)(t: q.reflect.TypeRepr): Boolean = {
      import q.reflect._; t.dealias <:< TypeRepr.of[Seq[_]]
    }
    def isSet(using q: Quotes)(t: q.reflect.TypeRepr): Boolean = {
      import q.reflect._; t.dealias <:< TypeRepr.of[Set[_]]
    }
    def isMap(using q: Quotes)(t: q.reflect.TypeRepr): Boolean = {
      import q.reflect._; t.dealias <:< TypeRepr.of[Map[_, _]]
    }
    def isEither(using q: Quotes)(t: q.reflect.TypeRepr): Boolean = {
      import q.reflect._; t.dealias <:< TypeRepr.of[Either[_, _]]
    }

    // --- Check Recursion (Outer Quotes) ---

    def failRecursion(loopType: TypeRepr, stack: List[TypeRepr]): Nothing = {
      val typeName = loopType.typeSymbol.name

      val path             = stack.takeWhile(t => !(t =:= loopType)) :+ loopType
      val recursiveClasses =
        (loopType :: path)
          .filter(t =>
            (isCaseClass(t) || isSealed(t)) &&
              !isOption(t) && !isList(t) && !isVector(t) && !isSeq(t) && !isSet(t) && !isMap(t) && !isEither(
                t
              ) && !isTuple(
                t
              )
          )
          .map(_.typeSymbol.name)
          .distinct

      if (recursiveClasses.size > 1) {
        report.errorAndAbort(
          s"Cannot generate structural type: mutually recursive types detected: ${recursiveClasses.mkString(", ")}"
        )
      } else {
        report.errorAndAbort(
          s"Cannot generate structural type for recursive type $typeName. Structural types cannot represent recursive structures."
        )
      }
    }

    def checkRecursive(t: TypeRepr, stack: List[TypeRepr]): Unit = {
      val dealt = t.dealias
      if (stack.exists(_ =:= dealt)) failRecursion(dealt, stack)

      if (isPrimitiveLike(dealt)) ()
      else if (isOption(dealt) || isList(dealt) || isVector(dealt) || isSeq(dealt) || isSet(dealt)) {
        dealt.typeArgs.headOption.foreach(arg => checkRecursive(arg, dealt :: stack))
      } else if (isMap(dealt)) {
        dealt.typeArgs match {
          case k :: v :: _ =>
            checkRecursive(k, dealt :: stack)
            checkRecursive(v, dealt :: stack)
          case _ => ()
        }
      } else if (isEither(dealt)) {
        dealt.typeArgs match {
          case l :: r :: _ =>
            checkRecursive(l, dealt :: stack)
            checkRecursive(r, dealt :: stack)
          case _ => ()
        }
      } else if (isTuple(dealt)) {
        dealt.typeArgs.foreach(arg => checkRecursive(arg, dealt :: stack))
      } else if (isCaseClass(dealt)) {
        val fields = dealt.typeSymbol.caseFields
        fields.foreach { f =>
          val fieldTpe = dealt.memberType(f)
          checkRecursive(fieldTpe, dealt :: stack)
        }
      } else if (isSealed(dealt)) {
        // Check children
        val children = dealt.typeSymbol.children
        children.foreach { childSym =>
          if (childSym.isClassDef) {
            val childTpe = if (childSym.flags.is(Flags.Module)) {
              Ref(childSym).tpe
            } else {
              TypeIdent(childSym).tpe
            }
            // For now, simple recursion check on child raw type
            checkRecursive(childTpe, dealt :: stack)
          }
        }
      }
    }

    checkRecursive(tpe, Nil)

    // --- Structural Type Construction (Outer Quotes) ---

    def transformType(t: TypeRepr, seen: Set[TypeRepr]): TypeRepr = {
      val dealt = t.dealias
      if (seen.exists(_ =:= dealt)) dealt
      else if (isPrimitiveLike(dealt)) dealt
      else if (isOption(dealt)) {
        val inner = transformType(dealt.typeArgs.head, seen + dealt)
        TypeRepr.of[Option].appliedTo(inner)
      } else if (isList(dealt)) {
        val inner = transformType(dealt.typeArgs.head, seen + dealt)
        TypeRepr.of[List].appliedTo(inner)
      } else if (isVector(dealt)) {
        val inner = transformType(dealt.typeArgs.head, seen + dealt)
        TypeRepr.of[Vector].appliedTo(inner)
      } else if (isSeq(dealt)) {
        val inner = transformType(dealt.typeArgs.head, seen + dealt)
        TypeRepr.of[Seq].appliedTo(inner)
      } else if (isSet(dealt)) {
        val inner = transformType(dealt.typeArgs.head, seen + dealt)
        TypeRepr.of[Set].appliedTo(inner)
      } else if (isMap(dealt)) {
        val k = transformType(dealt.typeArgs(0), seen + dealt)
        val v = transformType(dealt.typeArgs(1), seen + dealt)
        TypeRepr.of[Map].appliedTo(List(k, v))
      } else if (isEither(dealt)) {
        val l = transformType(dealt.typeArgs(0), seen + dealt)
        val r = transformType(dealt.typeArgs(1), seen + dealt)
        TypeRepr.of[Either].appliedTo(List(l, r))
      } else if (isTuple(dealt)) {
        val args = dealt.typeArgs.zipWithIndex.map { case (arg, idx) =>
          (s"_${idx + 1}", transformType(arg, seen + dealt))
        }
        val base = TypeRepr.of[Selectable]
        args.foldLeft(base) { case (parent, (name, fTpe)) =>
          Refinement(parent, name, fTpe)
        }
      } else if (isCaseClass(dealt)) {
        val fields = dealt.typeSymbol.caseFields.map { f =>
          val name = f.name
          val fTpe = dealt.memberType(f)
          (name, transformType(fTpe, seen + dealt))
        }
        val base = TypeRepr.of[Selectable]
        fields.foldLeft(base) { case (parent, (name, fTpe)) =>
          Refinement(parent, name, fTpe)
        }
      } else if (isSealed(dealt)) {
        // Union of children structural types
        val children   = dealt.typeSymbol.children
        val childTypes = children.map { childSym =>
          if (childSym.flags.is(Flags.Module)) {
            // Object
            Ref(childSym).tpe
          } else {
            // Class
            val cType = TypeIdent(childSym).tpe
            // Handle generics if possible
            val typeParams =
              cType.typeSymbol.primaryConstructor.paramSymss.headOption.map(_.filter(_.isTypeParam)).getOrElse(Nil)
            if (typeParams.size == dealt.typeArgs.size && dealt.typeArgs.nonEmpty) {
              cType.appliedTo(dealt.typeArgs)
            } else {
              cType
            }
          }
        }

        if (childTypes.isEmpty) {
          TypeRepr.of[Nothing]
        } else {
          val structuralChildren = childTypes.map(ct => transformType(ct, seen + dealt))
          structuralChildren.reduce((a, b) => OrType(a, b))
        }
      } else {
        dealt
      }
    }

    val structuralTypeRepr = transformType(tpe, Set.empty)

    // --- Expression Transformation (Portable) ---

    def transformExpr(expr: Expr[Any], tpe: Type[_])(using q: Quotes): Expr[Any] = {
      import q.reflect._
      val dealt = TypeRepr.of(using tpe).dealias

      if (isPrimitiveLike(dealt)) expr
      else if (isOption(dealt)) {
        val innerTpe = dealt.typeArgs.head
        tpe match {
          case '[t] =>
            val optExpr = expr.asExprOf[Option[Any]]
            innerTpe.asType match {
              case '[i] =>
                '{ $optExpr.map(v => ${ transformExpr('v, Type.of[i]) }) }
            }
        }
      } else if (isList(dealt) || isVector(dealt) || isSeq(dealt) || isSet(dealt)) {
        val innerTpe = dealt.typeArgs.head
        val colExpr  = expr.asExprOf[Iterable[Any]]
        innerTpe.asType match {
          case '[i] =>
            val mapped = '{ $colExpr.map(v => ${ transformExpr('v, Type.of[i]) }) }

            if (isList(dealt)) '{ $mapped.toList }
            else if (isVector(dealt)) '{ $mapped.toVector }
            else if (isSet(dealt)) '{ $mapped.toSet }
            else '{ $mapped.toSeq }
        }
      } else if (isMap(dealt)) {
        val kTpe    = dealt.typeArgs(0)
        val vTpe    = dealt.typeArgs(1)
        val mapExpr = expr.asExprOf[Map[Any, Any]]
        (kTpe.asType, vTpe.asType) match {
          case ('[k], '[v]) =>
            '{
              $mapExpr.map { case (key, value) =>
                (${ transformExpr('key, Type.of[k]) }, ${ transformExpr('value, Type.of[v]) })
              }
            }
          case _ => report.errorAndAbort("Unexpected type structure for Map")
        }
      } else if (isEither(dealt)) {
        val lTpe       = dealt.typeArgs(0)
        val rTpe       = dealt.typeArgs(1)
        val eitherExpr = expr.asExprOf[Either[Any, Any]]
        (lTpe.asType, rTpe.asType) match {
          case ('[l], '[r]) =>
            '{
              $eitherExpr match {
                case Left(v)  => Left(${ transformExpr('v, Type.of[l]) })
                case Right(v) => Right(${ transformExpr('v, Type.of[r]) })
              }
            }
          case _ => report.errorAndAbort("Unexpected type structure for Either")
        }
      } else if (isTuple(dealt)) {
        val args      = dealt.typeArgs.zipWithIndex
        val tupleExpr = expr.asTerm

        val entries = args.map { case (argTpe, idx) =>
          val name     = s"_${idx + 1}"
          val fieldVal = Select.unique(tupleExpr, name).asExpr
          argTpe.asType match {
            case '[a] =>
              val converted = transformExpr(fieldVal, Type.of[a])
              '{ ${ Expr(name) } -> $converted }
          }
        }

        '{ new StructuralValue(Map(${ Varargs(entries) }*)) }

      } else if (isCaseClass(dealt)) {
        tpe match {
          case '[d] =>
            Expr.summon[ToStructural[d]] match {
              case Some(ts) => '{ $ts.toStructural($expr.asInstanceOf[d]) }
              case None     =>
                '{ ToStructural.derived[d].toStructural($expr.asInstanceOf[d]) }
            }
        }
      } else if (isSealed(dealt)) {
        val children = dealt.typeSymbol.children
        if (children.isEmpty) expr
        else {
          val term = expr.asTerm

          val cases = children.map { childSym =>
            val childTpe = if (childSym.flags.is(Flags.Module)) {
              Ref(childSym).tpe
            } else {
              val cType      = TypeIdent(childSym).tpe
              val typeParams =
                cType.typeSymbol.primaryConstructor.paramSymss.headOption.map(_.filter(_.isTypeParam)).getOrElse(Nil)
              if (typeParams.size == dealt.typeArgs.size && dealt.typeArgs.nonEmpty) {
                cType.appliedTo(dealt.typeArgs)
              } else {
                cType
              }
            }

            childTpe.asType match {
              case '[c] =>
                val bindName   = "x"
                val bindSymbol = Symbol.newBind(Symbol.spliceOwner, bindName, Flags.EmptyFlags, childTpe)
                val pattern    = Bind(bindSymbol, Typed(Wildcard(), TypeTree.of[c]))

                val body = childTpe.asType match {
                  case '[ct] =>
                    val ref = Ref(bindSymbol).asExprOf[ct]
                    transformExpr(ref, Type.of[ct]).asTerm
                }

                CaseDef(pattern, None, body)
            }
          }

          Match(term, cases).asExpr
        }
      } else expr
    }

    // Main conversion logic
    structuralTypeRepr.asType match {
      case '[st] =>
        '{
          new ToStructural[A] {
            type StructuralType = st

            def toStructural(value: A): StructuralType =
              ${
                if (symbol.fullName.startsWith("scala.Tuple")) {
                  // Tuples are case classes but need special handling via transformExpr
                  transformExpr('value, Type.of[A])
                } else if (symbol.flags.is(Flags.Case)) {
                  // Manual deconstruction for Case Class Root to avoid infinite recursion
                  val fields  = symbol.caseFields
                  val entries = fields.map { f =>
                    val name     = f.name
                    val fieldVal = Select('value.asTerm, f).asExpr
                    val fTpe     = tpe.memberType(f)
                    fTpe.asType match {
                      case '[ft] =>
                        val converted = transformExpr(fieldVal, Type.of[ft])
                        '{ ${ Expr(name) } -> $converted }
                    }
                  }
                  '{ new StructuralValue(Map(${ Varargs(entries) }*)) }
                } else {
                  // Sealed trait - rely on transformExpr which handles dispatch to children
                  transformExpr('value, Type.of[A])
                }
              }.asInstanceOf[StructuralType]

            def structuralSchema(implicit schema: Schema[A]): Schema[StructuralType] =
              Schema.derived[st]
          }
        }
    }
  }
}
