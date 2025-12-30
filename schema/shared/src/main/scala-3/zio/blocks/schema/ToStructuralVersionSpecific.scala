package zio.blocks.schema

import scala.quoted._

object ToStructuralVersionSpecific {
  inline given derived[A]: ToStructural.Aux[A, DynamicValue] = ${ derivedImpl[A] }

  inline given derivedTyped[A]: ToStructural[A] = ${ derivedTypedImpl[A] }

  private def derivedImpl[A: Type](using q: Quotes): Expr[ToStructural.Aux[A, DynamicValue]] = {
    import q.reflect._

    val tpe = TypeRepr.of[A]

    // Detect recursion to avoid infinite structural types
    def checkNonRecursive(t: TypeRepr, seen: Set[TypeRepr]): Unit =
      if (seen.contains(t)) report.throwError(s"Cannot generate structural type for recursive type: ${t.show}")
      else {
        val nested =
          if (t.typeArgs.nonEmpty) t.typeArgs
          else Nil
        nested.foreach(checkNonRecursive(_, seen + t))
      }

    checkNonRecursive(tpe, Set.empty)

    def repr(t: TypeRepr): String =
      if (t =:= TypeRepr.of[String]) "String"
      else if (t =:= TypeRepr.of[Int]) "Int"
      else if (t =:= TypeRepr.of[Long]) "Long"
      else if (t =:= TypeRepr.of[Double]) "Double"
      else if (t =:= TypeRepr.of[Float]) "Float"
      else if (t =:= TypeRepr.of[Boolean]) "Boolean"
      else if (t =:= TypeRepr.of[Char]) "Char"
      else if (t =:= TypeRepr.of[Byte]) "Byte"
      else if (t =:= TypeRepr.of[Short]) "Short"
      else if (t =:= TypeRepr.of[Unit]) "Unit"
      else
        t match {
          case AppliedType(tycon, args) if tycon.typeSymbol.fullName == "scala.Option" => s"Option[${repr(args.head)}]"
          case AppliedType(tycon, args) if args.nonEmpty                               =>
            val name  = tycon.typeSymbol.name
            val argsS = args.map(repr).mkString(",")
            s"$name[$argsS]"
          case tr: TypeRef if tr.typeSymbol.isClassDef =>
            // Attempt to gather constructor params for product types
            tr.classSymbol match {
              case Some(cls) if cls.primaryConstructor.paramSymss.nonEmpty =>
                val params = cls.primaryConstructor.paramSymss.flatten.map { p =>
                  val pname = p.name
                  val ptype = tr.memberType(p).dealias
                  pname -> repr(ptype)
                }
                // sort alphabetically by field name for determinism
                val sorted = params.sortBy(_._1)
                sorted.map { case (n, r) => s"$n:$r" }.mkString("{", ",", "}")
              case _ => tr.typeSymbol.name
            }
          case _ => t.show
        }

    val params = tpe.classSymbol
      .map(_.primaryConstructor.paramSymss.flatten.map { p =>
        val pname = p.name
        val ptype = tpe.memberType(p).dealias
        pname -> ptype
      })
      .getOrElse(Nil)

    // Sort fields alphabetically for deterministic refinement ordering
    val sortedParams = params.sortBy(_._1)

    // Emit per-field diagnostic info to help with macro hardening
    if (params.nonEmpty) {
      params.foreach { case (n, tp) =>
        report.info(s"Macro field: $n -> ${tp.show}")
      }
    }

    val normalized = repr(tpe)

    // generate a structural trait name (deterministic)
    val traitName = "Structural" + tpe.typeSymbol.name.replaceAll("[^A-Za-z0-9]", "")

    // Prepare field signatures for future typed-trait generation
    val fieldSignatures: List[(String, String)] = params.map { case (n, tp) =>
      n -> tp.show
    }

    if (fieldSignatures.nonEmpty)
      q.reflect.report.info(
        s"Field signatures for $traitName: ${fieldSignatures.map { case (n, t) => s"$n:$t" }.mkString(", ")} )"
      )

    q.reflect.report.info(s"Normalized type string: $normalized")

    val normalizedExpr = Expr(normalized)

    val tnVal: Expr[zio.blocks.schema.TypeName[zio.blocks.schema.DynamicValue]] =
      '{
        new zio.blocks.schema.TypeName[zio.blocks.schema.DynamicValue](
          zio.blocks.schema.Namespace.zioBlocksSchema,
          ${ normalizedExpr },
          Nil
        )
      }

    val res = '{
      new zio.blocks.schema.ToStructural[A] {
        type StructuralType = zio.blocks.schema.DynamicValue
        def apply(schema: zio.blocks.schema.Schema[A]): zio.blocks.schema.Schema[zio.blocks.schema.DynamicValue] = {
          val tn: zio.blocks.schema.TypeName[zio.blocks.schema.DynamicValue] = ${ tnVal }
          new zio.blocks.schema.Schema(zio.blocks.schema.Schema.dynamic.reflect.typeName(tn))
        }
      }
    }

    res.asExprOf[ToStructural.Aux[A, DynamicValue]]
  }

  private def derivedTypedImpl[A: Type](using q: Quotes): Expr[ToStructural[A]] = {
    import q.reflect.*

    val tpe = TypeRepr.of[A]

    // Detect recursion to avoid infinite structural types (same logic as derivedImpl)
    def checkNonRecursive(t: TypeRepr, seen: Set[TypeRepr]): Unit =
      if (seen.contains(t)) report.throwError(s"Cannot generate structural type for recursive type: ${t.show}")
      else {
        val nested =
          if (t.typeArgs.nonEmpty) t.typeArgs
          else Nil
        nested.foreach(checkNonRecursive(_, seen + t))
      }

    checkNonRecursive(tpe, Set.empty)

    // Collect constructor params
    val params = tpe.classSymbol
      .map(_.primaryConstructor.paramSymss.flatten.map { p =>
        val pname = p.name
        val ptype = tpe.memberType(p).dealias
        pname -> ptype
      })
      .getOrElse(Nil)

    // Sort params for deterministic ordering
    val sortedParams = params.sortBy(_._1)

    // Verify every field's TypeRepr can be turned into a quoted Type (`asType`).
    // If any field cannot be represented, fallback to the untyped `derived` implementation.
    val allSplicable: Boolean = sortedParams.forall { case (_, ptype) =>
      ptype.asType match {
        case '[t] => true
        case _    => false
      }
    }

    if (!allSplicable) {
      // Fallback to the untyped `derived` implementation (DynamicValue-backed)
      '{ zio.blocks.schema.ToStructuralVersionSpecific.derived[A] }
    } else {
      // Build a nested refinement type starting from AnyRef where each field is refined
      // to the actual constructor field type (we already checked splicability above).
      val base: TypeRepr = TypeRepr.of[Object]

      val refined: TypeRepr = sortedParams.foldLeft(base) { case (acc, (name, ptype)) =>
        Refinement(acc, name, ptype)
      }

      // Convert to a quoted Type; this should succeed because every field was splicable.
      // However, producing a truly-typed `Schema[GeneratedStructural]` is non-trivial
      // (it requires building a Schema instance whose type matches the structural
      // refinement). Casting `Schema.dynamic` is unsafe and defeats the purpose
      // of typed derivation. For now, fall back to the untyped `derived`
      // implementation which is safe and correct. Leave a diagnostic for future
      // work to implement proper typed schema construction.
      refined.asType match {
        case '[s] =>
          report.info(
            s"Typed structural derivation for ${tpe.show} succeeded at the type-level, but typed Schema construction is not yet implemented; falling back to DynamicValue-backed schema."
          )
          '{ zio.blocks.schema.ToStructuralVersionSpecific.derived[A] }
        case _ =>
          // Extremely unlikely given the pre-check, but fall back defensively.
          '{ zio.blocks.schema.ToStructuralVersionSpecific.derived[A] }
      }
    }
  }
}
