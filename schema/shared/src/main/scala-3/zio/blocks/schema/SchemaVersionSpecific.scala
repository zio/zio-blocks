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
        symbol: Symbol,
        name: String,
        tpe: TypeRepr,
        defaultValue: Option[Term],
        const: (Expr[Registers], Expr[RegisterOffset]) => Term,
        deconst: (Expr[Registers], Expr[RegisterOffset], Expr[A]) => Term
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
        var fTpe = tpe.memberType(symbol).dealias
        if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
        fTpe.asType match {
          case '[ft] =>
            var getter = tpeClassSymbol.fieldMember(name)
            if (!getter.exists) {
              getter = tpeClassSymbol
                .methodMember(name)
                .find(_.flags.is(Flags.ParamAccessor))
                .getOrElse(fail(s"Cannot find '$name' parameter of '${tpe.show}' in the primary constructor."))
            }
            val defaultValue =
              if (symbol.flags.is(Flags.HasDefault)) {
                val dvMembers = tpeTypeSymbol.companionClass.methodMember("$lessinit$greater$default$" + i)
                if (dvMembers.isEmpty) fail(s"Cannot find default value for '$symbol' in class ${tpe.show}")
                val methodSymbol    = dvMembers.head
                val dvSelectNoTArgs = Ref(tpeTypeSymbol.companionModule).select(methodSymbol)
                val dvSelect = methodSymbol.paramSymss match {
                  case Nil =>
                    dvSelectNoTArgs
                  case List(params) if params.exists(_.isTypeParam) && tpeTypeArgs.nonEmpty =>
                    TypeApply(dvSelectNoTArgs, tpeTypeArgs.map(Inferred(_)))
                  case _ =>
                    fail(s"Cannot find default value for '$symbol' in class ${tpe.show}")
                }
                Some(dvSelect)
              } else None
            var offsetInc                                                         = RegisterOffset.Zero
            var const: (Expr[Registers], Expr[RegisterOffset]) => Term            = null
            var deconst: (Expr[Registers], Expr[RegisterOffset], Expr[A]) => Term = null
            if (fTpe =:= TypeRepr.of[Boolean] || fTpe =:= TypeRepr.of[java.lang.Boolean]) {
              offsetInc = RegisterOffset(booleans = 1)
              val bytes = RegisterOffset.getBytes(offset)
              const = (in, baseOffset) => '{ $in.getBoolean($baseOffset, ${ Expr(bytes) }) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{
                  $out.setBoolean($baseOffset, ${ Expr(bytes) }, ${ Select(in.asTerm, getter).asExprOf[Boolean] })
                }.asTerm
            } else if (fTpe =:= TypeRepr.of[Byte] || fTpe =:= TypeRepr.of[java.lang.Byte]) {
              offsetInc = RegisterOffset(bytes = 1)
              val bytes = RegisterOffset.getBytes(offset)
              const = (in, baseOffset) => '{ $in.getByte($baseOffset, ${ Expr(bytes) }) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setByte($baseOffset, ${ Expr(bytes) }, ${ Select(in.asTerm, getter).asExprOf[Byte] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Char] || fTpe =:= TypeRepr.of[java.lang.Character]) {
              offsetInc = RegisterOffset(chars = 1)
              val bytes = RegisterOffset.getBytes(offset)
              const = (in, baseOffset) => '{ $in.getChar($baseOffset, ${ Expr(bytes) }) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setChar($baseOffset, ${ Expr(bytes) }, ${ Select(in.asTerm, getter).asExprOf[Char] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Short] || fTpe =:= TypeRepr.of[java.lang.Short]) {
              offsetInc = RegisterOffset(shorts = 1)
              val bytes = RegisterOffset.getBytes(offset)
              const = (in, baseOffset) => '{ $in.getShort($baseOffset, ${ Expr(bytes) }) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setShort($baseOffset, ${ Expr(bytes) }, ${ Select(in.asTerm, getter).asExprOf[Short] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Float] || fTpe =:= TypeRepr.of[java.lang.Float]) {
              offsetInc = RegisterOffset(floats = 1)
              val bytes = RegisterOffset.getBytes(offset)
              const = (in, baseOffset) => '{ $in.getFloat($baseOffset, ${ Expr(bytes) }) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setFloat($baseOffset, ${ Expr(bytes) }, ${ Select(in.asTerm, getter).asExprOf[Float] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Int] || fTpe =:= TypeRepr.of[java.lang.Integer]) {
              offsetInc = RegisterOffset(ints = 1)
              val bytes = RegisterOffset.getBytes(offset)
              const = (in, baseOffset) => '{ $in.getInt($baseOffset, ${ Expr(bytes) }) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setInt($baseOffset, ${ Expr(bytes) }, ${ Select(in.asTerm, getter).asExprOf[Int] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Double] || fTpe =:= TypeRepr.of[java.lang.Double]) {
              offsetInc = RegisterOffset(doubles = 1)
              val bytes = RegisterOffset.getBytes(offset)
              const = (in, baseOffset) => '{ $in.getDouble($baseOffset, ${ Expr(bytes) }) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{
                  $out.setDouble($baseOffset, ${ Expr(bytes) }, ${ Select(in.asTerm, getter).asExprOf[Double] })
                }.asTerm
            } else if (fTpe =:= TypeRepr.of[Long] || fTpe =:= TypeRepr.of[java.lang.Long]) {
              offsetInc = RegisterOffset(longs = 1)
              val bytes = RegisterOffset.getBytes(offset)
              const = (in, baseOffset) => '{ $in.getLong($baseOffset, ${ Expr(bytes) }) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setLong($baseOffset, ${ Expr(bytes) }, ${ Select(in.asTerm, getter).asExprOf[Long] }) }.asTerm
            } else {
              offsetInc = RegisterOffset(objects = 1)
              val objects = RegisterOffset.getObjects(offset)
              const = (in, baseOffset) => '{ $in.getObject($baseOffset, ${ Expr(objects) }).asInstanceOf[ft] }.asTerm
              deconst = (out, baseOffset, in) =>
                '{
                  $out.setObject($baseOffset, ${ Expr(objects) }, ${ Select(in.asTerm, getter).asExprOf[AnyRef] })
                }.asTerm
            }
            offset = RegisterOffset.add(offset, offsetInc)
            FieldInfo(symbol, name, fTpe, defaultValue, const, deconst)
        }
      })
      val fields =
        fieldInfos.flatMap(_.map { fieldInfo =>
          fieldInfo.tpe.asType match {
            case '[ft] =>
              val nameExpr  = Expr(fieldInfo.name)
              val usingExpr = Expr.summon[Schema[ft]].get
              fieldInfo.defaultValue
                .fold('{ Schema[ft](using $usingExpr).reflect.asTerm[A]($nameExpr) }) { defaultValue =>
                  val defaultValueExpr = defaultValue.asExprOf[ft]
                  '{ Schema[ft](using $usingExpr).reflect.defaultValue($defaultValueExpr).asTerm[A]($nameExpr) }
                }
          }
        })

      def const(in: Expr[Registers], baseOffset: Expr[RegisterOffset])(using Quotes): Expr[A] = {
        val constructorNoTypes = Select(New(Inferred(tpe)), primaryConstructor)
        val constructor = typeArgs(tpe) match {
          case Nil      => constructorNoTypes
          case typeArgs => TypeApply(constructorNoTypes, typeArgs.map(Inferred(_)))
        }
        val argss = fieldInfos.map(_.map(fieldInfo => fieldInfo.const(in, baseOffset)))
        argss.tail.foldLeft(Apply(constructor, argss.head))((acc, args) => Apply(acc, args)).asExprOf[A]
      }

      def deconst(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[A])(using Quotes) = {
        val terms = fieldInfos.flatMap(_.map(fieldInfo => fieldInfo.deconst(out, baseOffset, in)))
        if (terms.size > 1) Block(terms.init, terms.last).asExprOf[Unit]
        else terms.head.asExprOf[Unit]
      }

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

                def construct(in: Registers, baseOffset: RegisterOffset): A = ${ const('in, 'baseOffset) }
              },
              deconstructor = new Deconstructor[A] {
                def usedRegisters: RegisterOffset = ${ Expr(offset) }

                def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A): Unit = ${
                  deconst('out, 'baseOffset, 'in)
                }
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
