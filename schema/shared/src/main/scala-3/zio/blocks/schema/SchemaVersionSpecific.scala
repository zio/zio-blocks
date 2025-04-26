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

    def isEnumOrModuleValue(tpe: TypeRepr): Boolean = tpe.isSingleton &&
      (tpe.typeSymbol.flags.is(Flags.Module) || tpe.termSymbol.flags.is(Flags.Enum))

    def isSealedTraitOrAbstractClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))
    }

    def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      !flags.is(Flags.Abstract) && !flags.is(Flags.JavaDefined) && !flags.is(Flags.Trait)
    }

    def typeArgs(tpe: TypeRepr): List[TypeRepr] = tpe match {
      case AppliedType(_, typeArgs) => typeArgs.map(_.dealias)
      case _                        => Nil
    }

    def directSubTypes(tpe: TypeRepr): Seq[TypeRepr] = {
      def resolveParentTypeArg(
        child: Symbol,
        fromNudeChildTarg: TypeRepr,
        parentTarg: TypeRepr,
        binding: Map[String, TypeRepr]
      ): Map[String, TypeRepr] =
        if (fromNudeChildTarg.typeSymbol.isTypeParam) { // TODO: check for paramRef instead ?
          val paramName = fromNudeChildTarg.typeSymbol.name
          binding.get(paramName) match {
            case None => binding.updated(paramName, parentTarg)
            case Some(oldBinding) =>
              if (oldBinding =:= parentTarg) binding
              else
                fail(
                  s"Type parameter $paramName' in class '${child.name}' appeared in the constructor of " +
                    s"'${tpe.show}' two times differently, with '${oldBinding.show}' and '${parentTarg.show}'"
                )
          }
        } else if (fromNudeChildTarg <:< parentTarg) {
          binding // TODO: assure parentTag is covariant, get covariance from type parameters
        } else {
          (fromNudeChildTarg, parentTarg) match {
            case (AppliedType(ctycon, ctargs), AppliedType(ptycon, ptargs)) =>
              ctargs.zip(ptargs).foldLeft(resolveParentTypeArg(child, ctycon, ptycon, binding)) { (b, e) =>
                resolveParentTypeArg(child, e._1, e._2, b)
              }
            case _ =>
              fail(
                s"Failed unification of type parameters of '${tpe.show}' from child '$child' - " +
                  s"'${fromNudeChildTarg.show}' and '${parentTarg.show}'"
              )
          }
        }

      def resolveParentTypeArgs(
        child: Symbol,
        nudeChildParentTags: List[TypeRepr],
        parentTags: List[TypeRepr],
        binding: Map[String, TypeRepr]
      ): Map[String, TypeRepr] =
        nudeChildParentTags.zip(parentTags).foldLeft(binding)((s, e) => resolveParentTypeArg(child, e._1, e._2, s))

      tpe.typeSymbol.children.map { sym =>
        if (sym.isType) {
          if (sym.name == "<local child>") { // problem - we have no other way to find this other return the name
            fail(
              s"Local child symbols are not supported, please consider change '${tpe.show}' or implement a " +
                "custom implicitly accessible schema"
            )
          }
          val nudeSubtype      = TypeIdent(sym).tpe
          val tpeArgsFromChild = typeArgs(nudeSubtype.baseType(tpe.typeSymbol))
          nudeSubtype.memberType(sym.primaryConstructor) match {
            case MethodType(_, _, _) => nudeSubtype
            case PolyType(names, bounds, resPolyTp) =>
              val tpBinding = resolveParentTypeArgs(sym, tpeArgsFromChild, typeArgs(tpe), Map.empty)
              val ctArgs = names.map { name =>
                tpBinding.getOrElse(
                  name,
                  fail(
                    s"Type parameter '$name' of '$sym' can't be deduced from " +
                      s"type arguments of ${tpe.show}. Please provide a custom implicitly accessible schema for it."
                  )
                )
              }
              val polyRes = resPolyTp match {
                case MethodType(_, _, resTp) => resTp
                case other                   => other // hope we have no multiple typed param lists yet.
              }
              if (ctArgs.isEmpty) polyRes
              else
                polyRes match {
                  case AppliedType(base, _)                       => base.appliedTo(ctArgs)
                  case AnnotatedType(AppliedType(base, _), annot) => AnnotatedType(base.appliedTo(ctArgs), annot)
                  case _                                          => polyRes.appliedTo(ctArgs)
                }
            case other => fail(s"Primary constructior for ${tpe.show} is not MethodType or PolyType but $other")
          }
        } else if (sym.isTerm) Ref(sym).tpe
        else {
          fail(
            "Only concrete (no free type parametes) type are supported for ADT cases. Please consider using of " +
              s"them for ADT with base '${tpe.show}' or provide a custom implicitly accessible schema for the ADT base."
          )
        }
      }
    }

    def typeName(tpe: TypeRepr): Expr[TypeName[A]] = {
      var packages = List.empty[String]
      var values   = List.empty[String]
      val name     = tpe.typeSymbol.name.toString
      var owner    = tpe.typeSymbol.owner
      while (owner != defn.RootClass) {
        val name = owner.name.toString
        if (owner.flags.is(Flags.Package)) packages = name :: packages
        else if (owner.flags.is(Flags.Module)) values = name.substring(0, name.length - 1) :: values
        else values = name :: values
        owner = owner.owner
      }
      '{ TypeName[A](Namespace(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) }) }.asExprOf[TypeName[A]]
    }

    val tpe     = TypeRepr.of[A].dealias
    val tpeName = typeName(tpe)
    val schema =
      if (isEnumOrModuleValue(tpe)) {
        '{
          new Schema[A](
            reflect = new Reflect.Record[Binding, A](
              fields = Nil,
              typeName = $tpeName,
              recordBinding = Binding.Record(
                constructor = new Constructor[A] {
                  def usedRegisters: RegisterOffset = 0

                  def construct(in: Registers, baseOffset: RegisterOffset): A = ${ Ref(tpe.termSymbol).asExprOf[A] }
                },
                deconstructor = new Deconstructor[A] {
                  def usedRegisters: RegisterOffset = 0

                  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A): Unit = ()
                }
              )
            )
          )
        }
      } else if (isSealedTraitOrAbstractClass(tpe)) {
        val subTypes = directSubTypes(tpe)
        if (subTypes.isEmpty) {
          fail(
            s"Cannot find sub-types for ADT base '$tpe'. " +
              "Please add them or provide an implicitly accessible schema for the ADT base."
          )
        }
        val cases = subTypes.map {
          var i = -1
          (sTpe: TypeRepr) =>
            sTpe.asType match {
              case '[st] =>
                i += 1
                val nameExpr  = Expr("case" + i)
                val usingExpr = Expr.summon[Schema[st]].get
                '{
                  Schema[st](using $usingExpr).reflect.asTerm[A]($nameExpr)
                }.asExprOf[zio.blocks.schema.Term[Binding, A, ? <: A]]
            }
        }

        def discr(a: Expr[A]) = Match(
          '{ $a: @scala.unchecked }.asTerm,
          subTypes.map {
            var i = -1
            (sTpe: TypeRepr) =>
              i += 1
              CaseDef(Typed(Wildcard(), Inferred(sTpe)), None, Expr(i).asTerm)
          }.toList
        ).asExprOf[Int]

        val matcherCases = subTypes.map { (sTpe: TypeRepr) =>
          sTpe.asType match {
            case '[st] =>
              '{
                new Matcher[st] {
                  def downcastOrNull(a: Any): st = (a: @scala.unchecked) match {
                    case x: st => x
                    case _     => null.asInstanceOf[st]
                  }
                }
              }.asExprOf[Matcher[? <: A]]
          }
        }
        '{
          new Schema[A](
            reflect = new Reflect.Variant[Binding, A](
              cases = ${ Expr.ofList(cases) },
              typeName = $tpeName,
              variantBinding = Binding.Variant(
                discriminator = new Discriminator[A] {
                  def discriminate(a: A): Int = ${ discr('a) }
                },
                matchers = Matchers(${ Expr.ofSeq(matcherCases) }*)
              )
            )
          )
        }
      } else if (isNonAbstractScalaClass(tpe)) {
        case class FieldInfo(
          symbol: Symbol,
          name: String,
          tpe: TypeRepr,
          defaultValue: Option[Term],
          const: (Expr[Registers], Expr[RegisterOffset]) => Term,
          deconst: (Expr[Registers], Expr[RegisterOffset], Expr[A]) => Term
        )

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
                  val dvMembers = tpe.typeSymbol.companionClass.methodMember("$lessinit$greater$default$" + i)
                  if (dvMembers.isEmpty) fail(s"Cannot find default value for '$symbol' in class ${tpe.show}")
                  val methodSymbol    = dvMembers.head
                  val dvSelectNoTArgs = Ref(tpe.typeSymbol.companionModule).select(methodSymbol)
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

        '{
          new Schema[A](
            reflect = new Reflect.Record[Binding, A](
              fields = ${ Expr.ofList(fields) },
              typeName = $tpeName,
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
                }
              )
            )
          )
        }
      } else fail(s"Cannot derive '${TypeRepr.of[Schema[_]].show}' for '${tpe.show}'.")
    // report.info(s"Generated schema for type '${tpe.show}':\n${schema.show}", Position.ofMacroExpansion)
    schema
  }
}
