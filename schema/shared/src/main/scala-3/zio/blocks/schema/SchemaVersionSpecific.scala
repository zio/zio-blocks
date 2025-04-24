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
      val tpeTypeArgs   = typeArgs(tpe)
      var registersUsed = RegisterOffset.Zero
      var i             = 0
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
            var const: (Expr[Registers], Expr[RegisterOffset]) => Term            = null
            var deconst: (Expr[Registers], Expr[RegisterOffset], Expr[A]) => Term = null
            val bytes                                                             = Expr(RegisterOffset.getBytes(registersUsed))
            val objects                                                           = Expr(RegisterOffset.getObjects(registersUsed))
            var offset                                                            = RegisterOffset.Zero
            if (fTpe =:= TypeRepr.of[Boolean]) {
              offset = RegisterOffset(booleans = 1)
              const = (in, baseOffset) => '{ $in.getBoolean($baseOffset, $bytes) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setBoolean($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Boolean] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Byte]) {
              offset = RegisterOffset(bytes = 1)
              const = (in, baseOffset) => '{ $in.getByte($baseOffset, $bytes) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setByte($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Byte] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Char]) {
              offset = RegisterOffset(chars = 1)
              const = (in, baseOffset) => '{ $in.getChar($baseOffset, $bytes) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setChar($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Char] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Short]) {
              offset = RegisterOffset(shorts = 1)
              const = (in, baseOffset) => '{ $in.getShort($baseOffset, $bytes) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setShort($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Short] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Float]) {
              offset = RegisterOffset(floats = 1)
              const = (in, baseOffset) => '{ $in.getFloat($baseOffset, $bytes) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setFloat($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Float] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Int]) {
              offset = RegisterOffset(ints = 1)
              const = (in, baseOffset) => '{ $in.getInt($baseOffset, $bytes) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setInt($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Int] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Double]) {
              offset = RegisterOffset(doubles = 1)
              const = (in, baseOffset) => '{ $in.getDouble($baseOffset, $bytes) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setDouble($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Double] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Long]) {
              offset = RegisterOffset(longs = 1)
              const = (in, baseOffset) => '{ $in.getLong($baseOffset, $bytes) }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setLong($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Long] }) }.asTerm
            } else {
              offset = RegisterOffset(objects = 1)
              const = (in, baseOffset) => '{ $in.getObject($baseOffset, $objects).asInstanceOf[ft] }.asTerm
              deconst = (out, baseOffset, in) =>
                '{ $out.setObject($baseOffset, $objects, ${ Select(in.asTerm, getter).asExprOf[AnyRef] }) }.asTerm
            }
            registersUsed = RegisterOffset.add(registersUsed, offset)
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
        val argss = fieldInfos.map(_.map(_.const(in, baseOffset)))
        argss.tail.foldLeft(Apply(constructor, argss.head))((acc, args) => Apply(acc, args))
      }.asExprOf[A]

      def deconst(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[A])(using Quotes): Expr[Unit] = {
        val terms = fieldInfos.flatMap(_.map(_.deconst(out, baseOffset, in)))
        if (terms.size > 1) Block(terms.init, terms.last)
        else terms.head
      }.asExprOf[Unit]

      val schema =
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
                  def usedRegisters: RegisterOffset = ${ Expr(registersUsed) }

                  def construct(in: Registers, baseOffset: RegisterOffset): A = ${ const('in, 'baseOffset) }
                },
                deconstructor = new Deconstructor[A] {
                  def usedRegisters: RegisterOffset = ${ Expr(registersUsed) }

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
        }
      // report.info(s"Generated schema for type '${tpe.show}':\n${schema.show}", Position.ofMacroExpansion)
      schema
    } else fail(s"Cannot derive '${TypeRepr.of[Schema[_]].show}' for '${tpe.show}'.")
  }
}
