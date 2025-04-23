package zio.blocks.schema

trait SchemaVersionSpecific {
  inline def derived[A]: Schema[A] = ${ SchemaVersionSpecific.derived }
}

object SchemaVersionSpecific {
  import scala.quoted._

  def derived[A: Type](using Quotes): Expr[Schema[A]] = {
    import quotes.reflect._
    import zio.blocks.schema.binding._
    import zio.blocks.schema.binding.RegisterOffset._

    def fail(msg: String): Nothing = report.errorAndAbort(msg, Position.ofMacroExpansion)

    def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { sym =>
      val flags = sym.flags
      !flags.is(Flags.Abstract) && !flags.is(Flags.JavaDefined) && !flags.is(Flags.Trait)
    }

    def typeArgs(tpe: TypeRepr): List[TypeRepr] = tpe match
      case AppliedType(_, typeArgs) => typeArgs.map(_.dealias)
      case _                        => Nil

    val tpe = TypeRepr.of[A].dealias
    if (isNonAbstractScalaClass(tpe)) {
      case class FieldInfo(
        name: String,
        tpe: TypeRepr,
        getter: Symbol,
        defaultValue: Option[Term],
        offset: RegisterOffset
      )

      val tpeTypeSymbol = tpe.typeSymbol
      val name          = tpeTypeSymbol.name.toString
      var values        = List.empty[String]
      var packages      = List.empty[String]
      var owner         = tpeTypeSymbol.owner
      while (owner != quotes.reflect.defn.RootClass) {
        val name = owner.name.toString
        if (owner.flags.is(Flags.Package)) packages = name :: packages
        else if (owner.flags.is(Flags.Module)) values = name.substring(0, name.length - 1) :: values
        else values = name :: values
        owner = owner.owner
      }
      val tpeClassSymbol     = tpe.classSymbol.get
      val primaryConstructor = tpeClassSymbol.primaryConstructor
      if (!primaryConstructor.exists) fail(s"Cannot find a primary constructor for '$tpe'")
      val (tpeTypeParams, tpeParams) = primaryConstructor.paramSymss match {
        case tps :: ps if tps.exists(_.isTypeParam) => (tps, ps)
        case ps                                     => (Nil, ps)
      }
      val tpeTypeArgs = typeArgs(tpe)
      var offset      = RegisterOffset.Zero
      var i           = 0
      val fieldInfos = tpeParams.map(_.map { symbol =>
        i += 1
        val name = symbol.name
        FieldInfo(
          name = name,
          offset = offset,
          tpe = {
            var fTpe = tpe.memberType(symbol).dealias
            if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
            offset = RegisterOffset.add(
              offset,
              if (fTpe =:= TypeRepr.of[Boolean] || fTpe =:= TypeRepr.of[java.lang.Boolean]) RegisterOffset(booleans = 1)
              else if (fTpe =:= TypeRepr.of[Byte] || fTpe =:= TypeRepr.of[java.lang.Byte]) RegisterOffset(bytes = 1)
              else if (fTpe =:= TypeRepr.of[Char] || fTpe =:= TypeRepr.of[java.lang.Character])
                RegisterOffset(chars = 1)
              else if (fTpe =:= TypeRepr.of[Short] || fTpe =:= TypeRepr.of[java.lang.Short]) RegisterOffset(shorts = 1)
              else if (fTpe =:= TypeRepr.of[Float] || fTpe =:= TypeRepr.of[java.lang.Float]) RegisterOffset(floats = 1)
              else if (fTpe =:= TypeRepr.of[Int] || fTpe =:= TypeRepr.of[java.lang.Integer]) RegisterOffset(ints = 1)
              else if (fTpe =:= TypeRepr.of[Double] || fTpe =:= TypeRepr.of[java.lang.Double])
                RegisterOffset(doubles = 1)
              else if (fTpe =:= TypeRepr.of[Long] || fTpe =:= TypeRepr.of[java.lang.Long]) RegisterOffset(longs = 1)
              else RegisterOffset(objects = 1)
            )
            fTpe
          },
          getter = {
            val fieldMember = tpeClassSymbol.fieldMember(name)
            if (fieldMember.exists) fieldMember
            else {
              tpeClassSymbol
                .methodMember(name)
                .find(_.flags.is(Flags.ParamAccessor))
                .getOrElse(fail(s"Cannot find '$name' parameter of '${tpe.show}' in the primary constructor."))
            }
          },
          defaultValue = {
            if (symbol.flags.is(Flags.HasDefault)) {
              val dvMembers = tpeTypeSymbol.companionClass.methodMember("$lessinit$greater$default$" + i)
              if (dvMembers.isEmpty) fail(s"Cannot find default value for '$symbol' in class ${tpe.show}")
              val methodSymbol    = dvMembers.head
              val dvSelectNoTArgs = Ref(tpeTypeSymbol.companionModule).select(methodSymbol)
              val dvSelect = methodSymbol.paramSymss match
                case Nil =>
                  dvSelectNoTArgs
                case List(params) if params.exists(_.isTypeParam) && tpeTypeArgs.nonEmpty =>
                  TypeApply(dvSelectNoTArgs, tpeTypeArgs.map(Inferred(_)))
                case _ =>
                  fail(s"Cannot find default value for '$symbol' in class ${tpe.show}")
              Some(dvSelect)
            } else None
          }
        )
      })
      val fields =
        fieldInfos.flatMap(_.map { fieldInfo =>
          fieldInfo.tpe.asType match
            case '[t] =>
              val nameExpr  = Expr(fieldInfo.name)
              val usingExpr = Expr.summon[Schema[t]].get
              fieldInfo.defaultValue
                .fold('{ Schema[t](using $usingExpr).reflect.asTerm[A]($nameExpr) }) { defaultValue =>
                  val defaultValueExpr = defaultValue.asExprOf[t]
                  '{ Schema[t](using $usingExpr).reflect.defaultValue($defaultValueExpr).asTerm[A]($nameExpr) }
                }
        })
      // TODO: use `fieldInfos` to generate remaining `Reflect.Record.fields` and `Reflect.Record.recordBinding`
      '{
        new Schema[A](
          reflect = new Reflect.Record[Binding, A](
            fields = ${ Expr.ofList(fields) },
            typeName = TypeName(
              namespace = Namespace(
                packages = ${ Expr(packages) },
                values = ${ Expr(values) }
              ),
              name = ${ Expr(name) }
            ),
            recordBinding = Binding.Record(
              constructor = new Constructor[A] {
                def usedRegisters: RegisterOffset = ${ Expr(offset) }

                def construct(in: Registers, baseOffset: RegisterOffset): A = ???
              },
              deconstructor = new Deconstructor[A] {
                def usedRegisters: RegisterOffset = ${ Expr(offset) }

                def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A): Unit = ???
              },
              defaultValue = None,
              examples = Nil
            ),
            doc = Doc.Empty,
            modifiers = Nil
          )
        )
      }.asExprOf[Schema[A]]
    } else fail(s"Cannot derive '${TypeRepr.of[Schema[_]].show}' for '${tpe.show}'.")
  }
}
