package zio.blocks.schema

trait SchemaVersionSpecific {
  inline def derived[A]: Schema[A] = ${ SchemaVersionSpecific.derived }
}

object SchemaVersionSpecific {
  import scala.quoted._

  def derived[A: Type](using Quotes): Expr[Schema[A]] = {
    import quotes.reflect._
    import zio.blocks.schema.binding.Binding

    def fail(msg: String): Nothing = report.errorAndAbort(msg, Position.ofMacroExpansion)

    def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { sym =>
      val flags = sym.flags
      !flags.is(Flags.Abstract) && !flags.is(Flags.JavaDefined) && !flags.is(Flags.Trait)
    }

    def toName(sym: Symbol): (List[String], List[String], String) = {
      var values   = List.empty[String]
      var packages = List.empty[String]
      var owner    = sym.owner
      while (owner != quotes.reflect.defn.RootClass) {
        val name = owner.name.toString
        if (owner.flags.is(Flags.Package)) packages = name :: packages
        else if (owner.flags.is(Flags.Module)) values = name.substring(0, name.length - 1) :: values
        else values = name :: values
        owner = owner.owner
      }
      (packages, values, sym.name.toString)
    }

    val tpe = TypeRepr.of[A].dealias
    if (isNonAbstractScalaClass(tpe)) {
      case class FieldInfo(name: String, tpe: TypeRepr, getter: Symbol)

      val tpeTypeSym  = tpe.typeSymbol
      val tpeName     = toName(tpeTypeSym)
      val tpeClassSym = tpe.classSymbol.get
      val primaryConstructor =
        if (tpeClassSym.primaryConstructor.exists) tpeClassSym.primaryConstructor
        else fail(s"Cannot find a primary constructor for '$tpe'")
      val tpeTypeArgs = tpe match
        case AppliedType(_, typeArgs) => typeArgs
        case _                        => Nil
      val (tpeTypeParams, tpeParams) = primaryConstructor.paramSymss match {
        case tps :: ps if tps.exists(_.isTypeParam) => (tps, ps)
        case ps                                     => (Nil, ps)
      }
      val fieldInfos = tpeParams.map(_.map { sym =>
        val name = sym.name
        FieldInfo(
          name = name,
          tpe = {
            val originFieldType = tpe.memberType(sym).dealias
            if (tpeTypeArgs.isEmpty) originFieldType
            else originFieldType.substituteTypes(tpeTypeParams, tpeTypeArgs)
          },
          getter = {
            val fieldMember = tpeClassSym.fieldMember(name)
            if (fieldMember.exists) fieldMember
            else {
              tpeClassSym
                .methodMember(name)
                .find(_.flags.is(Flags.ParamAccessor | Flags.CaseAccessor))
                .getOrElse(fail(s"Cannot find '$name' parameter of '${tpe.show}' in the primary constructor."))
            }
          }
        )
      })
      // TODO: use `fieldInfos` to generate remaining `Reflect.Record.fields` and `Reflect.Record.recordBinding`
      '{
        new Schema[A](
          reflect = new Reflect.Record[Binding, A](
            fields = Nil,
            typeName = TypeName(
              namespace = Namespace(
                packages = ${ Expr(tpeName._1) },
                values = ${ Expr(tpeName._2) }
              ),
              name = ${ Expr(tpeName._3) }
            ),
            recordBinding = null,
            doc = Doc.Empty,
            modifiers = Nil
          )
        )
      }.asExprOf[Schema[A]]
    } else fail(s"Cannot derive '${TypeRepr.of[Schema[_]].show}' for '${tpe.show}'.")
  }
}
