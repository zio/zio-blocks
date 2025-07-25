package zio.blocks.schema

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.quoted._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._

trait SchemaVersionSpecific {
  inline def derived[A]: Schema[A] = ${ SchemaVersionSpecific.derived }
}

private object SchemaVersionSpecific {
  private val isNonRecursiveCache = TrieMap.empty[Any, Boolean]
  private implicit val fullNameOrdering: Ordering[Array[String]] = new Ordering[Array[String]] {
    override def compare(x: Array[String], y: Array[String]): Int = {
      val minLen = Math.min(x.length, y.length)
      var idx    = 0
      while (idx < minLen) {
        val cmp = x(idx).compareTo(y(idx))
        if (cmp != 0) return cmp
        idx += 1
      }
      x.length.compare(y.length)
    }
  }

  def derived[A: Type](using Quotes): Expr[Schema[A]] = {
    import quotes.reflect._

    def fail(msg: String): Nothing = report.errorAndAbort(msg, Position.ofMacroExpansion)

    def isEnumValue(tpe: TypeRepr): Boolean = tpe.termSymbol.flags.is(Flags.Enum)

    def isEnumOrModuleValue(tpe: TypeRepr): Boolean = isEnumValue(tpe) || tpe.typeSymbol.flags.is(Flags.Module)

    def isSealedTraitOrAbstractClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))
    }

    def isUnion(tpe: TypeRepr): Boolean = tpe match {
      case OrType(_, _) => true
      case _            => false
    }

    def allUnionTypes(tpe: TypeRepr): Seq[TypeRepr] = tpe.dealias match {
      case OrType(left, right) => allUnionTypes(left) ++ allUnionTypes(right)
      case dealiased           => dealiased :: Nil
    }

    def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      !flags.is(Flags.Abstract) && !flags.is(Flags.JavaDefined) && !flags.is(Flags.Trait)
    }

    def typeArgs(tpe: TypeRepr): List[TypeRepr] = tpe match {
      case AppliedType(_, typeArgs) => typeArgs.map(_.dealias)
      case _                        => Nil
    }

    def isOption(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Option[?]]

    def isEither(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Either[?, ?]]

    def isDynamicValue(tpe: TypeRepr): Boolean = tpe =:= TypeRepr.of[DynamicValue]

    def isCollection(tpe: TypeRepr): Boolean =
      tpe <:< TypeRepr.of[Iterable[?]] || tpe <:< TypeRepr.of[Iterator[?]] || tpe <:< TypeRepr.of[Array[?]] ||
        tpe.typeSymbol.fullName == "scala.IArray$package$.IArray"

    def directSubTypes(tpe: TypeRepr): Seq[TypeRepr] = {
      def resolveParentTypeArg(
        child: Symbol,
        fromNudeChildTypeArg: TypeRepr,
        parentTypeArg: TypeRepr,
        binding: Map[String, TypeRepr]
      ): Map[String, TypeRepr] =
        if (fromNudeChildTypeArg.typeSymbol.isTypeParam) {
          val paramName = fromNudeChildTypeArg.typeSymbol.name
          binding.get(paramName) match {
            case Some(oldBinding) =>
              if (oldBinding =:= parentTypeArg) binding
              else fail(s"Failed unification of type parameters of '${tpe.show}'.")
            case _ => binding.updated(paramName, parentTypeArg)
          }
        } else if (fromNudeChildTypeArg <:< parentTypeArg) binding
        else {
          (fromNudeChildTypeArg, parentTypeArg) match {
            case (AppliedType(ctc, cta), AppliedType(ptc, pta)) =>
              cta.zip(pta).foldLeft(resolveParentTypeArg(child, ctc, ptc, binding)) { (b, e) =>
                resolveParentTypeArg(child, e._1, e._2, b)
              }
            case _ =>
              fail(s"Failed unification of type parameters of '${tpe.show}'.")
          }
        }

      tpe.typeSymbol.children.map { symbol =>
        if (symbol.isType) {
          val nudeSubtype = TypeIdent(symbol).tpe
          nudeSubtype.memberType(symbol.primaryConstructor) match {
            case MethodType(_, _, _) => nudeSubtype
            case PolyType(names, bounds, resPolyTp) =>
              val binding = typeArgs(nudeSubtype.baseType(tpe.typeSymbol))
                .zip(typeArgs(tpe))
                .foldLeft(Map.empty[String, TypeRepr])((b, e) => resolveParentTypeArg(symbol, e._1, e._2, b))
              val ctArgs = names.map { name =>
                binding.getOrElse(
                  name,
                  fail(s"Type parameter '$name' of '$symbol' can't be deduced from type arguments of '${tpe.show}'.")
                )
              }
              val polyRes = resPolyTp match {
                case MethodType(_, _, resTp) => resTp
                case _                       => resPolyTp
              }
              if (ctArgs.isEmpty) polyRes
              else {
                polyRes match {
                  case AppliedType(base, _)                       => base.appliedTo(ctArgs)
                  case AnnotatedType(AppliedType(base, _), annot) => AnnotatedType(base.appliedTo(ctArgs), annot)
                  case _                                          => polyRes.appliedTo(ctArgs)
                }
              }
            case other => fail(s"Cannot resolve free type parameters type for ADT cases with base '${tpe.show}'.")
          }
        } else if (symbol.isTerm) Ref(symbol).tpe
        else fail(s"Cannot resolve free type parameters type for ADT cases with base '${tpe.show}'.")
      }
    }

    def isNonRecursive(tpe: TypeRepr, nestedTpes: List[TypeRepr] = Nil): Boolean = isNonRecursiveCache.getOrElseUpdate(
      tpe,
      tpe =:= TypeRepr.of[String] || tpe =:= TypeRepr.of[Int] || tpe =:= TypeRepr.of[Float] ||
        tpe =:= TypeRepr.of[Long] || tpe =:= TypeRepr.of[Double] || tpe =:= TypeRepr.of[Boolean] ||
        tpe =:= TypeRepr.of[Byte] || tpe =:= TypeRepr.of[Char] || tpe =:= TypeRepr.of[Short] ||
        tpe =:= TypeRepr.of[BigDecimal] || tpe =:= TypeRepr.of[BigInt] || tpe =:= TypeRepr.of[Unit] ||
        tpe <:< TypeRepr.of[java.time.temporal.Temporal] || tpe <:< TypeRepr.of[java.time.temporal.TemporalAmount] ||
        tpe =:= TypeRepr.of[java.util.Currency] || tpe =:= TypeRepr.of[java.util.UUID] || isEnumOrModuleValue(tpe) ||
        isDynamicValue(tpe) || {
          if (isOption(tpe) || isEither(tpe) || isCollection(tpe)) typeArgs(tpe).forall(isNonRecursive(_, nestedTpes))
          else if (isSealedTraitOrAbstractClass(tpe)) directSubTypes(tpe).forall(isNonRecursive(_, nestedTpes))
          else if (isUnion(tpe)) allUnionTypes(tpe).forall(isNonRecursive(_, nestedTpes))
          else {
            isNonAbstractScalaClass(tpe) && !nestedTpes.contains(tpe) && {
              val primaryConstructor = tpe.classSymbol.get.primaryConstructor
              primaryConstructor.exists && {
                val (tpeTypeParams, tpeParams) = primaryConstructor.paramSymss match {
                  case tps :: ps if tps.exists(_.isTypeParam) => (tps, ps)
                  case ps                                     => (Nil, ps)
                }
                val nestedTpes_ = tpe :: nestedTpes
                val tpeTypeArgs = typeArgs(tpe)
                if (tpeTypeArgs.isEmpty) {
                  tpeParams.forall(_.forall(symbol => isNonRecursive(tpe.memberType(symbol).dealias, nestedTpes_)))
                } else {
                  tpeParams.forall(_.forall { symbol =>
                    val fTpe = tpe.memberType(symbol).dealias.substituteTypes(tpeTypeParams, tpeTypeArgs)
                    isNonRecursive(fTpe, nestedTpes_)
                  })
                }
              }
            }
          }
        }
    )

    def typeName(tpe: TypeRepr): (Seq[String], Seq[String], String) =
      if (isUnion(tpe)) (Nil, Nil, "|")
      else {
        var packages  = List.empty[String]
        var values    = List.empty[String]
        var tpeSymbol = tpe.typeSymbol
        var name      = tpeSymbol.name
        if (isEnumValue(tpe)) {
          name = tpe.termSymbol.name
          var ownerName = tpeSymbol.name
          if (tpeSymbol.flags.is(Flags.Module)) ownerName = ownerName.substring(0, ownerName.length - 1)
          values = ownerName :: values
        } else if (tpeSymbol.flags.is(Flags.Module)) name = name.substring(0, name.length - 1)
        if (tpeSymbol != Symbol.noSymbol) {
          var owner = tpeSymbol.owner
          while (owner != defn.RootClass) {
            val ownerName = owner.name
            if (owner.flags.is(Flags.Package)) packages = ownerName :: packages
            else if (owner.flags.is(Flags.Module)) values = ownerName.substring(0, ownerName.length - 1) :: values
            else values = ownerName :: values
            owner = owner.owner
          }
        }
        (packages, values, name)
      }

    def modifiers(tpe: TypeRepr)(using Quotes): Seq[Expr[Modifier.config]] =
      (if (isEnumValue(tpe)) tpe.termSymbol else tpe.typeSymbol).annotations
        .filter(_.tpe =:= TypeRepr.of[Modifier.config])
        .collect { case Apply(_, List(Literal(StringConstant(k)), Literal(StringConstant(v)))) =>
          '{ Modifier.config(${ Expr(k) }, ${ Expr(v) }) }.asExprOf[Modifier.config]
        }
        .reverse

    def doc(tpe: TypeRepr)(using Quotes): Expr[Doc] =
      (if (isEnumValue(tpe)) tpe.termSymbol else tpe.typeSymbol).docstring
        .fold('{ Doc.Empty }.asExprOf[Doc])(s => '{ new Doc.Text(${ Expr(s) }) }.asExprOf[Doc])

    val inferredSchemas   = new mutable.HashMap[TypeRepr, Expr[Schema[?]]]
    val derivedSchemaRefs = new mutable.HashMap[TypeRepr, Expr[Schema[?]]]
    val derivedSchemaDefs = new mutable.ListBuffer[ValDef]

    def findImplicitOrDeriveSchema[T: Type](using Quotes): Expr[Schema[T]] = {
      val tpe            = TypeRepr.of[T]
      lazy val schemaTpe = TypeRepr.of[Schema[T]]
      val inferredSchema = inferredSchemas.getOrElseUpdate(
        tpe,
        Implicits.search(schemaTpe) match
          case v: ImplicitSearchSuccess => v.tree.asExprOf[Schema[?]]
          case _                        => null
      )
      if (inferredSchema ne null) inferredSchema
      else {
        derivedSchemaRefs
          .getOrElse(
            tpe, {
              val name = "s" + derivedSchemaRefs.size
              val flags =
                if (isNonRecursive(tpe)) Flags.Implicit
                else Flags.Implicit | Flags.Lazy
              val symbol = Symbol.newVal(Symbol.spliceOwner, name, schemaTpe, flags, Symbol.noSymbol)
              val ref    = Ref(symbol).asExprOf[Schema[?]]
              derivedSchemaRefs.update(tpe, ref)
              val schema = deriveSchema[T]
              derivedSchemaDefs.addOne(ValDef(symbol, Some(schema.asTerm.changeOwner(symbol))))
              ref
            }
          )
      }
    }.asExprOf[Schema[T]]

    def deriveSchema[T: Type](using Quotes): Expr[Schema[T]] = {
      val tpe                      = TypeRepr.of[T]
      val (packages, values, name) = typeName(tpe)

      def unsupportedFieldType(tpe: TypeRepr): Nothing = fail(s"Unsupported field type '${tpe.show}'.")

      def maxCommonPrefixLength(typesWithFullNames: Seq[(TypeRepr, Array[String])]): Int = {
        var minFullName = typesWithFullNames.head._2
        var maxFullName = typesWithFullNames.last._2
        if (!isUnion(tpe)) {
          val tpeFullName = packages.toArray ++ values.toArray :+ name
          if (fullNameOrdering.compare(minFullName, tpeFullName) > 0) minFullName = tpeFullName
          if (fullNameOrdering.compare(maxFullName, tpeFullName) < 0) maxFullName = tpeFullName
        }
        val minLength = Math.min(minFullName.length, maxFullName.length)
        var idx       = 0
        while (idx < minLength && minFullName(idx).compareTo(maxFullName(idx)) == 0) idx += 1
        idx
      }

      if (isEnumOrModuleValue(tpe)) {
        '{
          new Schema[T](
            reflect = new Reflect.Record[Binding, T](
              fields = Vector.empty,
              typeName = new TypeName[T](new Namespace(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) }),
              recordBinding = new Binding.Record(
                constructor = new ConstantConstructor[T](${
                  Ref {
                    if (isEnumValue(tpe)) tpe.termSymbol
                    else tpe.typeSymbol.companionModule
                  }.asExprOf[T]
                }),
                deconstructor = new ConstantDeconstructor[T]
              ),
              doc = ${ doc(tpe) },
              modifiers = ${ Expr.ofSeq(modifiers(tpe)) }
            )
          )
        }
      } else if (tpe <:< TypeRepr.of[Array[?]]) {
        val elementTpe = typeArgs(tpe).head
        elementTpe.asType match {
          case '[et] =>
            val elementSchema = findImplicitOrDeriveSchema[et]
            if (elementTpe <:< TypeRepr.of[AnyRef]) {
              val classTag =
                Expr.summon[reflect.ClassTag[et]].getOrElse(fail(s"No ClassTag available for ${elementTpe.show}"))
              '{
                implicit val ct: reflect.ClassTag[et] = $classTag
                new Schema[Array[et]](
                  reflect = new Reflect.Sequence[Binding, et, Array](
                    element = $elementSchema.reflect,
                    typeName =
                      new TypeName[Array[et]](new Namespace(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) }),
                    seqBinding = new Binding.Seq[Array, et](
                      constructor = new SeqConstructor.ArrayConstructor {
                        override def newObjectBuilder[A](sizeHint: Int): Builder[A] =
                          new Builder(new Array[et](sizeHint).asInstanceOf[Array[A]], 0)
                      },
                      deconstructor = SeqDeconstructor.arrayDeconstructor
                    )
                  )
                )
              }
            } else {
              '{
                new Schema[Array[et]](
                  reflect = new Reflect.Sequence[Binding, et, Array](
                    element = $elementSchema.reflect,
                    typeName =
                      new TypeName[Array[et]](new Namespace(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) }),
                    seqBinding = new Binding.Seq[Array, et](
                      constructor = SeqConstructor.arrayConstructor,
                      deconstructor = SeqDeconstructor.arrayDeconstructor
                    )
                  )
                )
              }
            }
        }
      } else if (isSealedTraitOrAbstractClass(tpe) || isUnion(tpe)) {
        val subTypes =
          if (isUnion(tpe)) allUnionTypes(tpe).distinct
          else directSubTypes(tpe)
        if (subTypes.isEmpty) fail(s"Cannot find sub-types for ADT base '${tpe.show}'.")
        val subTypesWithFullNames = subTypes.map { sTpe =>
          val (packages, values, name) = typeName(sTpe)
          (sTpe, packages.toArray ++ values.toArray :+ name)
        }
        val length = maxCommonPrefixLength(subTypesWithFullNames.sortBy(_._2))
        val cases = subTypesWithFullNames.map { case (sTpe, fullName) =>
          sTpe.asType match {
            case '[st] =>
              val termName = fullName.drop(length).mkString(".")
              val sSchema  = findImplicitOrDeriveSchema[st]
              '{
                $sSchema.reflect.asTerm[T](${ Expr(termName) })
              }.asExprOf[zio.blocks.schema.Term[Binding, T, ? <: T]]
          }
        }

        def discr(a: Expr[T])(using Quotes) = Match(
          '{ $a: @scala.unchecked }.asTerm,
          subTypesWithFullNames.map {
            var idx = -1
            x =>
              idx += 1
              CaseDef(Typed(Wildcard(), Inferred(x._1)), None, Expr(idx).asTerm)
          }.toList
        ).asExprOf[Int]

        val matcherCases = subTypesWithFullNames.map { case (sTpe, _) =>
          sTpe.asType match {
            case '[st] =>
              '{
                new Matcher[st] {
                  def downcastOrNull(a: Any): st = (a: @scala.unchecked) match {
                    case x: st => x
                    case _     => null.asInstanceOf[st]
                  }
                }
              }.asExprOf[Matcher[? <: T]]
          }
        }
        '{
          new Schema[T](
            reflect = new Reflect.Variant[Binding, T](
              cases = Vector(${ Expr.ofSeq(cases) }*),
              typeName = new TypeName[T](new Namespace(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) }),
              variantBinding = new Binding.Variant(
                discriminator = new Discriminator[T] {
                  def discriminate(a: T): Int = ${ discr('a) }
                },
                matchers = Matchers(${ Expr.ofSeq(matcherCases) }*)
              ),
              doc = ${ doc(tpe) },
              modifiers = ${ Expr.ofSeq(modifiers(tpe)) }
            )
          )
        }
      } else if (isNonAbstractScalaClass(tpe)) {
        case class FieldInfo(
          symbol: Symbol,
          name: String,
          tpe: TypeRepr,
          defaultValue: Option[Term],
          getter: Symbol,
          registersUsed: RegisterOffset,
          isTransient: Boolean,
          config: List[(String, String)]
        )

        val tpeClassSymbol     = tpe.classSymbol.get
        val primaryConstructor = tpeClassSymbol.primaryConstructor
        if (!primaryConstructor.exists) fail(s"Cannot find a primary constructor for '${tpe.show}'.")
        val (tpeTypeParams, tpeParams) = primaryConstructor.paramSymss match {
          case tps :: ps if tps.exists(_.isTypeParam) => (tps, ps)
          case ps                                     => (Nil, ps)
        }
        val tpeTypeArgs   = typeArgs(tpe)
        var registersUsed = RegisterOffset.Zero
        var idx           = 0
        val fieldInfos = tpeParams.map(_.map { symbol =>
          idx += 1
          val name = symbol.name
          var fTpe = tpe.memberType(symbol).dealias
          if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
          fTpe.asType match {
            case '[ft] =>
              var getter = tpeClassSymbol.fieldMember(name)
              if (!getter.exists) {
                val getters = tpeClassSymbol
                  .methodMember(name)
                  .filter(_.flags.is(Flags.CaseAccessor | Flags.FieldAccessor | Flags.ParamAccessor))
                if (getters.isEmpty) fail(s"Cannot find '$name' parameter of '${tpe.show}' in the primary constructor.")
                getter = getters.head
              }
              val isTransient = getter.annotations.exists(_.tpe =:= TypeRepr.of[Modifier.transient])
              val config = getter.annotations
                .filter(_.tpe =:= TypeRepr.of[Modifier.config])
                .collect { case Apply(_, List(Literal(StringConstant(k)), Literal(StringConstant(v)))) => (k, v) }
                .reverse
              val defaultValue =
                if (symbol.flags.is(Flags.HasDefault)) {
                  (tpe.typeSymbol.companionClass.methodMember("$lessinit$greater$default$" + idx) match {
                    case methodSymbol :: _ =>
                      val dvSelectNoTArgs = Ref(tpe.typeSymbol.companionModule).select(methodSymbol)
                      methodSymbol.paramSymss match {
                        case Nil =>
                          Some(dvSelectNoTArgs)
                        case List(params) if params.exists(_.isTypeParam) && tpeTypeArgs.nonEmpty =>
                          Some(TypeApply(dvSelectNoTArgs, tpeTypeArgs.map(Inferred(_))))
                        case _ =>
                          None
                      }
                    case _ =>
                      None
                  }).orElse(fail(s"Cannot find default value for '$symbol' in class '${tpe.show}'."))
                } else None
              val offset =
                if (fTpe =:= TypeRepr.of[Int]) RegisterOffset(ints = 1)
                else if (fTpe =:= TypeRepr.of[Float]) RegisterOffset(floats = 1)
                else if (fTpe =:= TypeRepr.of[Long]) RegisterOffset(longs = 1)
                else if (fTpe =:= TypeRepr.of[Double]) RegisterOffset(doubles = 1)
                else if (fTpe =:= TypeRepr.of[Boolean]) RegisterOffset(booleans = 1)
                else if (fTpe =:= TypeRepr.of[Byte]) RegisterOffset(bytes = 1)
                else if (fTpe =:= TypeRepr.of[Char]) RegisterOffset(chars = 1)
                else if (fTpe =:= TypeRepr.of[Short]) RegisterOffset(shorts = 1)
                else if (fTpe =:= TypeRepr.of[Unit]) RegisterOffset.Zero
                else if (fTpe <:< TypeRepr.of[AnyRef]) RegisterOffset(objects = 1)
                else unsupportedFieldType(fTpe)
              val fieldInfo = FieldInfo(symbol, name, fTpe, defaultValue, getter, registersUsed, isTransient, config)
              registersUsed = RegisterOffset.add(registersUsed, offset)
              fieldInfo
          }
        })
        val fields =
          fieldInfos.flatMap(_.map { fieldInfo =>
            val fTpe = fieldInfo.tpe
            fTpe.asType match {
              case '[ft] =>
                val fSchema     = findImplicitOrDeriveSchema[ft]
                var reflectExpr = '{ $fSchema.reflect }
                reflectExpr = fieldInfo.defaultValue.fold(reflectExpr) { dv =>
                  '{ $reflectExpr.defaultValue(${ dv.asExprOf[ft] }) }
                }
                if (!isNonRecursive(fTpe)) reflectExpr = '{ Reflect.Deferred(() => $reflectExpr) }
                var fieldTermExpr = '{ $reflectExpr.asTerm[T](${ Expr(fieldInfo.name) }) }
                var modifiers = fieldInfo.config.map { case (k, v) =>
                  '{ Modifier.config(${ Expr(k) }, ${ Expr(v) }) }.asExprOf[Modifier.Term]
                }
                if (fieldInfo.isTransient) modifiers = modifiers :+ '{ Modifier.transient() }.asExprOf[Modifier.Term]
                if (modifiers.nonEmpty) fieldTermExpr = '{ $fieldTermExpr.copy(modifiers = ${ Expr.ofSeq(modifiers) }) }
                fieldTermExpr
            }
          })

        def const(in: Expr[Registers], baseOffset: Expr[RegisterOffset])(using Quotes): Expr[T] = {
          val constructorNoTypes = Select(New(Inferred(tpe)), primaryConstructor)
          val constructor = typeArgs(tpe) match {
            case Nil      => constructorNoTypes
            case typeArgs => TypeApply(constructorNoTypes, typeArgs.map(Inferred(_)))
          }
          val argss = fieldInfos.map(_.map { fieldInfo =>
            val fTpe         = fieldInfo.tpe
            lazy val bytes   = Expr(RegisterOffset.getBytes(fieldInfo.registersUsed))
            lazy val objects = Expr(RegisterOffset.getObjects(fieldInfo.registersUsed))
            if (fTpe =:= TypeRepr.of[Int]) '{ $in.getInt($baseOffset, $bytes) }.asTerm
            else if (fTpe =:= TypeRepr.of[Float]) '{ $in.getFloat($baseOffset, $bytes) }.asTerm
            else if (fTpe =:= TypeRepr.of[Long]) '{ $in.getLong($baseOffset, $bytes) }.asTerm
            else if (fTpe =:= TypeRepr.of[Double]) '{ $in.getDouble($baseOffset, $bytes) }.asTerm
            else if (fTpe =:= TypeRepr.of[Boolean]) '{ $in.getBoolean($baseOffset, $bytes) }.asTerm
            else if (fTpe =:= TypeRepr.of[Byte]) '{ $in.getByte($baseOffset, $bytes) }.asTerm
            else if (fTpe =:= TypeRepr.of[Char]) '{ $in.getChar($baseOffset, $bytes) }.asTerm
            else if (fTpe =:= TypeRepr.of[Short]) '{ $in.getShort($baseOffset, $bytes) }.asTerm
            else if (fTpe =:= TypeRepr.of[Unit]) '{ () }.asTerm
            else if (fTpe <:< TypeRepr.of[AnyRef]) {
              fTpe.asType match { case '[ft] => '{ $in.getObject($baseOffset, $objects).asInstanceOf[ft] }.asTerm }
            } else unsupportedFieldType(fTpe)
          })
          argss.tail.foldLeft(Apply(constructor, argss.head))((acc, args) => Apply(acc, args))
        }.asExprOf[T]

        def deconst(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Expr[Unit] = {
          val terms = fieldInfos.flatMap(_.map { fieldInfo =>
            val fTpe         = fieldInfo.tpe
            val getter       = fieldInfo.getter
            lazy val bytes   = Expr(RegisterOffset.getBytes(fieldInfo.registersUsed))
            lazy val objects = Expr(RegisterOffset.getObjects(fieldInfo.registersUsed))
            if (fTpe =:= TypeRepr.of[Int]) {
              '{ $out.setInt($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Int] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Float]) {
              '{ $out.setFloat($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Float] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Long]) {
              '{ $out.setLong($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Long] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Double]) {
              '{ $out.setDouble($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Double] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Boolean]) {
              '{ $out.setBoolean($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Boolean] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Byte]) {
              '{ $out.setByte($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Byte] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Char]) {
              '{ $out.setChar($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Char] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Short]) {
              '{ $out.setShort($baseOffset, $bytes, ${ Select(in.asTerm, getter).asExprOf[Short] }) }.asTerm
            } else if (fTpe =:= TypeRepr.of[Unit]) {
              '{ () }.asTerm
            } else if (fTpe <:< TypeRepr.of[AnyRef]) {
              '{ $out.setObject($baseOffset, $objects, ${ Select(in.asTerm, getter).asExprOf[AnyRef] }) }.asTerm
            } else unsupportedFieldType(fTpe)
          })
          val size = terms.size
          if (size > 1) Block(terms.init, terms.last)
          else if (size > 0) terms.head
          else Literal(UnitConstant())
        }.asExprOf[Unit]

        '{
          new Schema[T](
            reflect = new Reflect.Record[Binding, T](
              fields = Vector(${ Expr.ofSeq(fields) }*),
              typeName = new TypeName[T](new Namespace(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) }),
              recordBinding = new Binding.Record(
                constructor = new Constructor[T] {
                  def usedRegisters: RegisterOffset = ${ Expr(registersUsed) }

                  def construct(in: Registers, baseOffset: RegisterOffset): T = ${ const('in, 'baseOffset) }
                },
                deconstructor = new Deconstructor[T] {
                  def usedRegisters: RegisterOffset = ${ Expr(registersUsed) }

                  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: T): Unit = ${
                    deconst('out, 'baseOffset, 'in)
                  }
                }
              ),
              doc = ${ doc(tpe) },
              modifiers = ${ Expr.ofSeq(modifiers(tpe)) }
            )
          )
        }
      } else fail(s"Cannot derive schema for '${tpe.show}'.")
    }.asExprOf[Schema[T]]

    val schema      = TypeRepr.of[A].dealias.asType match { case '[t] => deriveSchema[t] }
    val schemaBlock = Block(derivedSchemaDefs.toList, schema.asTerm).asExprOf[Schema[A]]
    // report.info(s"Generated schema:\n${schemaBlock.show}", Position.ofMacroExpansion)
    schemaBlock
  }
}
