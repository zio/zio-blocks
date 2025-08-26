package zio.blocks.schema

import scala.collection.mutable
import scala.quoted._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._
import scala.annotation.tailrec
import scala.reflect.ClassTag

trait SchemaVersionSpecific {
  inline def derived[A]: Schema[A] = ${ SchemaVersionSpecific.derived }
}

private object SchemaVersionSpecific {
  private implicit val fullTermNameOrdering: Ordering[Array[String]] = new Ordering[Array[String]] {
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

    @tailrec
    def opaqueDealias(tpe: TypeRepr): TypeRepr = tpe match {
      case trTpe @ TypeRef(_, _) if trTpe.isOpaqueAlias => opaqueDealias(trTpe.translucentSuperType.dealias)
      case _                                            => tpe
    }

    def isUnion(tpe: TypeRepr): Boolean = tpe match {
      case OrType(_, _) => true
      case _            => false
    }

    def allUnionTypes(tpe: TypeRepr): List[TypeRepr] = tpe.dealias match {
      case OrType(left, right) => allUnionTypes(left) ++ allUnionTypes(right)
      case dealiased           => dealiased :: Nil
    }

    def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      !flags.is(Flags.Abstract) && !flags.is(Flags.JavaDefined) && !flags.is(Flags.Trait)
    }

    def isValueClass(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[AnyVal] && isNonAbstractScalaClass(tpe)

    def typeArgs(tpe: TypeRepr): List[TypeRepr] = tpe match {
      case AppliedType(_, typeArgs) => typeArgs.map(_.dealias)
      case _                        => Nil
    }

    def isGenericTuple(tpe: TypeRepr): Boolean = tpe match {
      case AppliedType(gtTpe, _) if gtTpe.dealias =:= TypeRepr.of[*:] => true
      case _                                                          => false
    }

    // Borrowed from an amazing work of Aleksander Rainko:
    // https://github.com/arainko/ducktape/blob/8d779f0303c23fd45815d3574467ffc321a8db2b/ducktape/src/main/scala/io/github/arainko/ducktape/internal/Structure.scala#L253-L270
    def genericTupleTypeArgs(t: Type[?]): List[TypeRepr] = t match {
      case '[head *: tail] => TypeRepr.of[head].dealias :: genericTupleTypeArgs(Type.of[tail])
      case _               => Nil
    }

    // Borrowed from an amazing work of Aleksander Rainko:
    // https://github.com/arainko/ducktape/blob/8d779f0303c23fd45815d3574467ffc321a8db2b/ducktape/src/main/scala/io/github/arainko/ducktape/internal/Structure.scala#L277-L295
    def normalizeTuple(tpe: TypeRepr): TypeRepr = {
      val typeArgs = genericTupleTypeArgs(tpe.asType)
      val size     = typeArgs.size
      if (size > 0 && size <= 22) defn.TupleClass(size).typeRef.appliedTo(typeArgs)
      else {
        typeArgs.foldRight(TypeRepr.of[EmptyTuple]) {
          val tupleCons = TypeRepr.of[*:]
          (curr, acc) => tupleCons.appliedTo(List(curr, acc))
        }
      }
    }

    def isNamedTuple(tpe: TypeRepr): Boolean = tpe match {
      case AppliedType(ntTpe, _) if ntTpe.dealias.typeSymbol.fullName == "scala.NamedTuple$.NamedTuple" => true
      case _                                                                                            => false
    }

    def isOption(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Option[?]]

    def isEither(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Either[?, ?]]

    def isDynamicValue(tpe: TypeRepr): Boolean = tpe =:= TypeRepr.of[DynamicValue]

    def isCollection(tpe: TypeRepr): Boolean =
      tpe <:< TypeRepr.of[Array[?]] || tpe.typeSymbol.fullName == "scala.IArray$package$.IArray" ||
        (tpe <:< TypeRepr.of[IterableOnce[?]] && tpe.typeSymbol.fullName.startsWith("scala.collection."))

    def directSubTypes(tpe: TypeRepr): List[TypeRepr] = {
      def resolveParentTypeArg(
        child: Symbol,
        nudeChildTypeArg: TypeRepr,
        parentTypeArg: TypeRepr,
        binding: Map[String, TypeRepr]
      ): Map[String, TypeRepr] =
        if (nudeChildTypeArg.typeSymbol.isTypeParam) {
          val paramName = nudeChildTypeArg.typeSymbol.name
          binding.get(paramName) match {
            case Some(existingBinding) =>
              if (existingBinding =:= parentTypeArg) binding
              else {
                fail(
                  s"Type parameter '$paramName' in class '${child.name}' appeared in the constructor of " +
                    s"'${tpe.show}' two times differently, with '${existingBinding.show}' and '${parentTypeArg.show}'"
                )
              }
            case _ =>
              binding.updated(paramName, parentTypeArg)
          }
        } else if (nudeChildTypeArg <:< parentTypeArg) binding
        else {
          (nudeChildTypeArg, parentTypeArg) match {
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
            case MethodType(_, _, _)           => nudeSubtype
            case PolyType(names, _, resPolyTp) =>
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
            case _ => fail(s"Cannot resolve free type parameters for ADT cases with base '${tpe.show}'.")
          }
        } else if (symbol.isTerm) Ref(symbol).tpe
        else fail(s"Cannot resolve free type parameters for ADT cases with base '${tpe.show}'.")
      }
    }

    val isNonRecursiveCache = new mutable.HashMap[TypeRepr, Boolean]

    def isNonRecursive(tpe: TypeRepr, nestedTpes: List[TypeRepr] = Nil): Boolean = isNonRecursiveCache.getOrElseUpdate(
      tpe,
      tpe <:< TypeRepr.of[String] || tpe <:< TypeRepr.of[Int] || tpe <:< TypeRepr.of[Float] ||
        tpe <:< TypeRepr.of[Long] || tpe <:< TypeRepr.of[Double] || tpe <:< TypeRepr.of[Boolean] ||
        tpe <:< TypeRepr.of[Byte] || tpe <:< TypeRepr.of[Char] || tpe <:< TypeRepr.of[Short] ||
        tpe <:< TypeRepr.of[BigDecimal] || tpe <:< TypeRepr.of[BigInt] || tpe <:< TypeRepr.of[Unit] ||
        tpe <:< TypeRepr.of[java.time.temporal.Temporal] || tpe <:< TypeRepr.of[java.time.temporal.TemporalAmount] ||
        tpe <:< TypeRepr.of[java.util.Currency] || tpe <:< TypeRepr.of[java.util.UUID] || isEnumOrModuleValue(tpe) ||
        isDynamicValue(tpe) || {
          if (isOption(tpe) || isEither(tpe) || isCollection(tpe)) typeArgs(tpe).forall(isNonRecursive(_, nestedTpes))
          else if (isGenericTuple(tpe)) {
            val nestedTpes_ = tpe :: nestedTpes
            genericTupleTypeArgs(tpe.asType).forall(isNonRecursive(_, nestedTpes_))
          } else if (isSealedTraitOrAbstractClass(tpe)) directSubTypes(tpe).forall(isNonRecursive(_, nestedTpes))
          else if (isUnion(tpe)) allUnionTypes(tpe).forall(isNonRecursive(_, nestedTpes))
          else if (isNamedTuple(tpe)) {
            tpe match {
              case AppliedType(_, List(_, tTpe)) => isNonRecursive(tTpe.dealias, nestedTpes)
              case _                             => false
            }
          } else {
            isNonAbstractScalaClass(tpe) && !nestedTpes.contains(tpe) && {
              val primaryConstructor = tpe.classSymbol.get.primaryConstructor
              primaryConstructor.exists && {
                val nestedTpes_ = tpe :: nestedTpes
                primaryConstructor.paramSymss match {
                  case tpeTypeParams :: tpeParams if tpeTypeParams.exists(_.isTypeParam) =>
                    val tpeTypeArgs = typeArgs(tpe)
                    tpeParams.forall(_.forall { symbol =>
                      val fTpe = tpe.memberType(symbol).dealias.substituteTypes(tpeTypeParams, tpeTypeArgs)
                      isNonRecursive(fTpe, nestedTpes_)
                    })
                  case tpeParams =>
                    tpeParams.forall(_.forall(symbol => isNonRecursive(tpe.memberType(symbol).dealias, nestedTpes_)))
                }
              }
            }
          }
        }
    )

    val typeNameCache = new mutable.HashMap[TypeRepr, TypeName[?]]

    def typeName[T: Type](tpe: TypeRepr): TypeName[T] = typeNameCache
      .getOrElseUpdate(
        tpe,
        if (tpe =:= TypeRepr.of[java.lang.String]) TypeName.string
        else if (tpe <:< TypeRepr.of[List[?]]) TypeName.list(typeName(typeArgs(tpe).head))
        else if (isUnion(tpe)) new TypeName(new Namespace(Nil, Nil), "|")
        else {
          var packages  = List.empty[String]
          var values    = List.empty[String]
          val tpeSymbol = tpe.typeSymbol
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
          val tpeTypeArgs =
            if (isNamedTuple(tpe)) {
              tpe match {
                case AppliedType(_, List(tpe1, tpe2)) =>
                  val nTpe   = tpe1.dealias
                  val tTpe   = tpe2.dealias
                  val labels = {
                    if (isGenericTuple(nTpe)) genericTupleTypeArgs(nTpe.asType)
                    else typeArgs(nTpe)
                  }.map { case ConstantType(StringConstant(x)) => x }
                  name = labels.mkString(name + "[", ",", "]")
                  if (isGenericTuple(tTpe)) genericTupleTypeArgs(tTpe.asType)
                  else typeArgs(tTpe)
                case _ => Nil
              }
            } else if (isGenericTuple(tpe)) genericTupleTypeArgs(tpe.asType)
            else if (isUnion(tpe)) allUnionTypes(tpe)
            else typeArgs(tpe)
          new TypeName(new Namespace(packages, values), name, tpeTypeArgs.map(typeName))
        }
      )
      .asInstanceOf[TypeName[T]]

    def toExpr[T: Type](tpeName: TypeName[T])(using Quotes): Expr[TypeName[T]] = '{
      new TypeName[T](
        namespace = new Namespace(
          packages = ${ Expr.ofList(tpeName.namespace.packages.map(Expr(_))) },
          values = ${ Expr.ofList(tpeName.namespace.values.map(Expr(_))) }
        ),
        name = ${ Expr(tpeName.name) },
        params = ${ Expr.ofList(tpeName.params.map(x => toExpr(x.asInstanceOf[TypeName[T]]))) }
      )
    }

    def toBlock(terms: List[Term]): Expr[Unit] = {
      val size = terms.size
      if (size > 1) Block(terms.init, terms.last)
      else if (size > 0) terms.head
      else Literal(UnitConstant())
    }.asExprOf[Unit]

    def doc(tpe: TypeRepr)(using Quotes): Expr[Doc] =
      (if (isEnumValue(tpe)) tpe.termSymbol else tpe.typeSymbol).docstring
        .fold('{ Doc.Empty }.asExprOf[Doc])(s => '{ new Doc.Text(${ Expr(s) }) }.asExprOf[Doc])

    def modifiers(tpe: TypeRepr)(using Quotes): Expr[List[Modifier.config]] = Expr.ofList(
      (if (isEnumValue(tpe)) tpe.termSymbol else tpe.typeSymbol).annotations
        .filter(_.tpe =:= TypeRepr.of[Modifier.config])
        .collect { case Apply(_, List(Literal(StringConstant(k)), Literal(StringConstant(v)))) =>
          '{ Modifier.config(${ Expr(k) }, ${ Expr(v) }) }.asExprOf[Modifier.config]
        }
        .reverse
    )

    val inferredSchemas   = new mutable.HashMap[TypeRepr, Expr[Schema[?]]]
    val derivedSchemaRefs = new mutable.HashMap[TypeRepr, Expr[Schema[?]]]
    val derivedSchemaDefs = new mutable.ListBuffer[ValDef]

    def findImplicitOrDeriveSchema[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
      lazy val schemaTpe = TypeRepr.of[Schema[T]]
      val inferredSchema = inferredSchemas.getOrElseUpdate(
        tpe,
        Implicits.search(schemaTpe) match {
          case v: ImplicitSearchSuccess => v.tree.asExprOf[Schema[?]]
          case _                        => null
        }
      )
      if (inferredSchema ne null) inferredSchema
      else {
        derivedSchemaRefs
          .getOrElse(
            tpe, {
              val name  = "s" + derivedSchemaRefs.size
              val flags =
                if (isNonRecursive(tpe)) Flags.Implicit
                else Flags.Implicit | Flags.Lazy
              val symbol = Symbol.newVal(Symbol.spliceOwner, name, schemaTpe, flags, Symbol.noSymbol)
              val ref    = Ref(symbol).asExprOf[Schema[?]]
              // adding the schema reference before schema derivation to avoid an endless loop on recursive data structures
              derivedSchemaRefs.update(tpe, ref)
              val schema = deriveSchema[T](tpe)
              derivedSchemaDefs.addOne(ValDef(symbol, Some(schema.asTerm.changeOwner(symbol))))
              ref
            }
          )
      }
    }.asExprOf[Schema[T]]

    case class FieldInfo(
      symbol: Symbol,
      name: String,
      tpe: TypeRepr,
      defaultValue: Option[Term],
      getter: Symbol,
      usedRegisters: RegisterOffset,
      isTransient: Boolean,
      config: List[(String, String)]
    )

    abstract class TypeInfo[T: Type] {
      def usedRegisters: Expr[RegisterOffset]

      def fields[S: Type](nameOverrides: List[String] = Nil)(using
        Quotes
      ): Expr[Seq[zio.blocks.schema.Term[Binding, S, ?]]]

      def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset])(using Quotes): Expr[T]

      def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Expr[Unit]

      def fieldOffset(tpe: TypeRepr): RegisterOffset = {
        val sTpe = opaqueDealias(tpe)
        if (sTpe <:< TypeRepr.of[Int]) RegisterOffset(ints = 1)
        else if (sTpe <:< TypeRepr.of[Float]) RegisterOffset(floats = 1)
        else if (sTpe <:< TypeRepr.of[Long]) RegisterOffset(longs = 1)
        else if (sTpe <:< TypeRepr.of[Double]) RegisterOffset(doubles = 1)
        else if (sTpe <:< TypeRepr.of[Boolean]) RegisterOffset(booleans = 1)
        else if (sTpe <:< TypeRepr.of[Byte]) RegisterOffset(bytes = 1)
        else if (sTpe <:< TypeRepr.of[Char]) RegisterOffset(chars = 1)
        else if (sTpe <:< TypeRepr.of[Short]) RegisterOffset(shorts = 1)
        else if (sTpe <:< TypeRepr.of[Unit]) RegisterOffset.Zero
        else if (sTpe <:< TypeRepr.of[AnyRef] || isValueClass(sTpe)) RegisterOffset(objects = 1)
        else unsupportedFieldType(sTpe)
      }

      def fieldConstructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset], fieldInfo: FieldInfo)(using
        Quotes
      ): Expr[?] = {
        val fTpe         = fieldInfo.tpe
        lazy val bytes   = Expr(RegisterOffset.getBytes(fieldInfo.usedRegisters))
        lazy val objects = Expr(RegisterOffset.getObjects(fieldInfo.usedRegisters))
        if (fTpe =:= TypeRepr.of[Int]) '{ $in.getInt($baseOffset, $bytes) }
        else if (fTpe =:= TypeRepr.of[Float]) '{ $in.getFloat($baseOffset, $bytes) }
        else if (fTpe =:= TypeRepr.of[Long]) '{ $in.getLong($baseOffset, $bytes) }
        else if (fTpe =:= TypeRepr.of[Double]) '{ $in.getDouble($baseOffset, $bytes) }
        else if (fTpe =:= TypeRepr.of[Boolean]) '{ $in.getBoolean($baseOffset, $bytes) }
        else if (fTpe =:= TypeRepr.of[Byte]) '{ $in.getByte($baseOffset, $bytes) }
        else if (fTpe =:= TypeRepr.of[Char]) '{ $in.getChar($baseOffset, $bytes) }
        else if (fTpe =:= TypeRepr.of[Short]) '{ $in.getShort($baseOffset, $bytes) }
        else if (fTpe =:= TypeRepr.of[Unit]) '{ () }
        else {
          fTpe.asType match {
            case '[ft] =>
              val sTpe = opaqueDealias(fTpe)
              if (sTpe <:< TypeRepr.of[AnyRef] || isValueClass(sTpe)) {
                '{ $in.getObject($baseOffset, $objects).asInstanceOf[ft] }
              } else {
                if (sTpe <:< TypeRepr.of[Int]) '{ $in.getInt($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< TypeRepr.of[Float]) '{ $in.getFloat($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< TypeRepr.of[Long]) '{ $in.getLong($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< TypeRepr.of[Double]) '{ $in.getDouble($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< TypeRepr.of[Boolean]) '{ $in.getBoolean($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< TypeRepr.of[Byte]) '{ $in.getByte($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< TypeRepr.of[Char]) '{ $in.getChar($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< TypeRepr.of[Short]) '{ $in.getShort($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< TypeRepr.of[Unit]) '{ ().asInstanceOf[ft] }
                else unsupportedFieldType(sTpe)
              }
          }
        }
      }

      def unsupportedFieldType(tpe: TypeRepr): Nothing = fail(s"Unsupported field type '${tpe.show}'.")
    }

    case class ClassInfo[T: Type](tpe: TypeRepr) extends TypeInfo {
      private val tpeClassSymbol     = tpe.classSymbol.get
      private val primaryConstructor = tpeClassSymbol.primaryConstructor
      if (!primaryConstructor.exists) fail(s"Cannot find a primary constructor for '${tpe.show}'.")
      val (fieldInfos: List[List[FieldInfo]], usedRegisters: Expr[RegisterOffset]) = {
        val (tpeTypeParams, tpeParams) = primaryConstructor.paramSymss match {
          case tps :: ps if tps.exists(_.isTypeParam) => (tps, ps)
          case ps                                     => (Nil, ps)
        }
        val tpeTypeArgs   = typeArgs(tpe)
        var usedRegisters = RegisterOffset.Zero
        var idx           = 0
        (
          tpeParams.map(_.map { symbol =>
            idx += 1
            var fTpe = tpe.memberType(symbol).dealias
            if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
            val name   = symbol.name
            var getter = tpeClassSymbol.fieldMember(name)
            if (!getter.exists) {
              val getters = tpeClassSymbol
                .methodMember(name)
                .filter(_.flags.is(Flags.CaseAccessor | Flags.FieldAccessor | Flags.ParamAccessor))
              if (getters.isEmpty) fail(s"Cannot find '$name' parameter of '${tpe.show}' in the primary constructor.")
              getter = getters.head
            }
            val isTransient = getter.annotations.exists(_.tpe =:= TypeRepr.of[Modifier.transient])
            val config      = getter.annotations
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
            val fieldInfo = FieldInfo(symbol, name, fTpe, defaultValue, getter, usedRegisters, isTransient, config)
            usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe))
            fieldInfo
          }),
          Expr(usedRegisters)
        )
      }

      def fields[S: Type](
        nameOverrides: List[String] = Nil
      )(using Quotes): Expr[Seq[zio.blocks.schema.Term[Binding, S, ?]]] = {
        val names = nameOverrides.toArray
        var idx   = -1
        Expr.ofSeq(fieldInfos.flatMap(_.map { fieldInfo =>
          val fTpe = fieldInfo.tpe
          fTpe.asType match {
            case '[ft] =>
              idx += 1
              var reflect = '{ ${ findImplicitOrDeriveSchema[ft](fTpe) }.reflect }
              reflect = fieldInfo.defaultValue.fold(reflect) { dv =>
                '{ $reflect.defaultValue(${ dv.asExprOf[ft] }) }
              }
              if (!isNonRecursive(fTpe)) reflect = '{ Reflect.Deferred(() => $reflect) }
              var name = fieldInfo.name
              if (idx < names.length) name = names(idx)
              var fieldTermExpr = '{ $reflect.asTerm[S](${ Expr(name) }) }
              var modifiers     = fieldInfo.config.map { case (k, v) =>
                '{ Modifier.config(${ Expr(k) }, ${ Expr(v) }) }.asExprOf[Modifier.Term]
              }
              if (fieldInfo.isTransient) modifiers = modifiers :+ '{ Modifier.transient() }.asExprOf[Modifier.Term]
              if (modifiers.nonEmpty) fieldTermExpr = '{ $fieldTermExpr.copy(modifiers = ${ Expr.ofSeq(modifiers) }) }
              fieldTermExpr
          }
        }))
      }

      def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset])(using Quotes): Expr[T] = {
        val constructorNoTypes = Select(New(Inferred(tpe)), primaryConstructor)
        val constructor        = typeArgs(tpe) match {
          case Nil      => constructorNoTypes
          case typeArgs => TypeApply(constructorNoTypes, typeArgs.map(Inferred(_)))
        }
        val argss = fieldInfos.map(_.map(fieldInfo => fieldConstructor(in, baseOffset, fieldInfo).asTerm))
        argss.tail.foldLeft(Apply(constructor, argss.head))(Apply(_, _))
      }.asExprOf[T]

      def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Expr[Unit] =
        toBlock(fieldInfos.flatMap(_.map { fieldInfo =>
          val fTpe         = fieldInfo.tpe
          val getter       = Select(in.asTerm, fieldInfo.getter)
          lazy val bytes   = Expr(RegisterOffset.getBytes(fieldInfo.usedRegisters))
          lazy val objects = Expr(RegisterOffset.getObjects(fieldInfo.usedRegisters))
          {
            if (fTpe <:< TypeRepr.of[Int]) '{ $out.setInt($baseOffset, $bytes, ${ getter.asExprOf[Int] }) }
            else if (fTpe <:< TypeRepr.of[Float]) '{ $out.setFloat($baseOffset, $bytes, ${ getter.asExprOf[Float] }) }
            else if (fTpe <:< TypeRepr.of[Long]) '{ $out.setLong($baseOffset, $bytes, ${ getter.asExprOf[Long] }) }
            else if (fTpe <:< TypeRepr.of[Double]) {
              '{ $out.setDouble($baseOffset, $bytes, ${ getter.asExprOf[Double] }) }
            } else if (fTpe <:< TypeRepr.of[Boolean]) {
              '{ $out.setBoolean($baseOffset, $bytes, ${ getter.asExprOf[Boolean] }) }
            } else if (fTpe <:< TypeRepr.of[Byte]) '{ $out.setByte($baseOffset, $bytes, ${ getter.asExprOf[Byte] }) }
            else if (fTpe <:< TypeRepr.of[Char]) '{ $out.setChar($baseOffset, $bytes, ${ getter.asExprOf[Char] }) }
            else if (fTpe <:< TypeRepr.of[Short]) '{ $out.setShort($baseOffset, $bytes, ${ getter.asExprOf[Short] }) }
            else if (fTpe <:< TypeRepr.of[Unit]) '{ () }
            else if (fTpe <:< TypeRepr.of[AnyRef]) {
              '{ $out.setObject($baseOffset, $objects, ${ getter.asExprOf[AnyRef] }) }
            } else {
              val sTpe = opaqueDealias(fTpe)
              if (sTpe <:< TypeRepr.of[Int]) {
                '{ $out.setInt($baseOffset, $bytes, ${ getter.asExprOf[Any] }.asInstanceOf[Int]) }
              } else if (sTpe <:< TypeRepr.of[Float]) {
                '{ $out.setFloat($baseOffset, $bytes, ${ getter.asExprOf[Any] }.asInstanceOf[Float]) }
              } else if (sTpe <:< TypeRepr.of[Long]) {
                '{ $out.setLong($baseOffset, $bytes, ${ getter.asExprOf[Any] }.asInstanceOf[Long]) }
              } else if (sTpe <:< TypeRepr.of[Double]) {
                '{ $out.setDouble($baseOffset, $bytes, ${ getter.asExprOf[Any] }.asInstanceOf[Double]) }
              } else if (sTpe <:< TypeRepr.of[Boolean]) {
                '{ $out.setBoolean($baseOffset, $bytes, ${ getter.asExprOf[Any] }.asInstanceOf[Boolean]) }
              } else if (sTpe <:< TypeRepr.of[Byte]) {
                '{ $out.setByte($baseOffset, $bytes, ${ getter.asExprOf[Any] }.asInstanceOf[Byte]) }
              } else if (sTpe <:< TypeRepr.of[Char]) {
                '{ $out.setChar($baseOffset, $bytes, ${ getter.asExprOf[Any] }.asInstanceOf[Char]) }
              } else if (sTpe <:< TypeRepr.of[Short]) {
                '{ $out.setShort($baseOffset, $bytes, ${ getter.asExprOf[Any] }.asInstanceOf[Short]) }
              } else if (sTpe <:< TypeRepr.of[Unit]) '{ () }
              else if (sTpe <:< TypeRepr.of[AnyRef] || isValueClass(sTpe)) {
                '{ $out.setObject($baseOffset, $objects, ${ getter.asExprOf[Any] }.asInstanceOf[AnyRef]) }
              } else unsupportedFieldType(sTpe)
            }
          }.asTerm
        }))
    }

    case class GenericTupleInfo[T: Type](tpe: TypeRepr) extends TypeInfo {
      val (fieldInfos: List[FieldInfo], usedRegisters: Expr[RegisterOffset]) = {
        val fTpes         = genericTupleTypeArgs(tpe.asType)
        val noSymbol      = Symbol.noSymbol
        var usedRegisters = RegisterOffset.Zero
        (
          fTpes.map {
            var idx = 0
            fTpe =>
              idx += 1
              val fieldInfo = FieldInfo(noSymbol, s"_$idx", fTpe, None, noSymbol, usedRegisters, false, Nil)
              usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe))
              fieldInfo
          },
          Expr(usedRegisters)
        )
      }

      def fields[S: Type](
        nameOverrides: List[String] = Nil
      )(using Quotes): Expr[Seq[zio.blocks.schema.Term[Binding, S, ?]]] =
        Expr.ofSeq(fieldInfos.map {
          val names = nameOverrides.toArray
          var idx   = -1
          fieldInfo =>
            idx += 1
            val fTpe = fieldInfo.tpe
            fTpe.asType match {
              case '[ft] =>
                var reflect = '{ ${ findImplicitOrDeriveSchema[ft](fTpe) }.reflect }
                if (!isNonRecursive(fTpe)) reflect = '{ Reflect.Deferred(() => $reflect) }
                var name = fieldInfo.name
                if (idx < names.length) name = names(idx)
                '{ $reflect.asTerm[S](${ Expr(name) }) }
            }
        })

      def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset])(using Quotes): Expr[T] =
        (if (fieldInfos.isEmpty) Expr(EmptyTuple)
         else {
           val args        = fieldInfos.map(fieldInfo => fieldConstructor(in, baseOffset, fieldInfo))
           val sym         = Symbol.newVal(Symbol.spliceOwner, "as", TypeRepr.of[Array[Any]], Flags.EmptyFlags, Symbol.noSymbol)
           val ref         = Ref(sym).asExprOf[Array[Any]]
           val valDef      = ValDef(sym, Some('{ new Array[Any](${ Expr(fieldInfos.size) }) }.asTerm))
           val assignments = args.map {
             var i = -1
             term =>
               i += 1
               '{ $ref(${ Expr(i) }) = ${ term.asExprOf[Any] } }.asTerm
           }
           val block = Block(valDef :: assignments, ref.asTerm).asExprOf[Array[Any]]
           tpe.asType match {
             case '[tt] => '{ scala.runtime.TupleXXL.fromIArray($block.asInstanceOf[IArray[Object]]).asInstanceOf[tt] }
           }
         }).asExprOf[T]

      def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Expr[Unit] =
        toBlock(fieldInfos.map {
          var idx = -1
          fieldInfo =>
            idx += 1
            val sTpe         = opaqueDealias(fieldInfo.tpe)
            val getter       = Select.unique(in.asTerm, "productElement").appliedTo(Literal(IntConstant(idx))).asExprOf[Any]
            lazy val bytes   = Expr(RegisterOffset.getBytes(fieldInfo.usedRegisters))
            lazy val objects = Expr(RegisterOffset.getObjects(fieldInfo.usedRegisters))
            {
              if (sTpe <:< TypeRepr.of[Int]) '{ $out.setInt($baseOffset, $bytes, $getter.asInstanceOf[Int]) }
              else if (sTpe <:< TypeRepr.of[Float]) {
                '{ $out.setFloat($baseOffset, $bytes, $getter.asInstanceOf[Float]) }
              } else if (sTpe <:< TypeRepr.of[Long]) '{ $out.setLong($baseOffset, $bytes, $getter.asInstanceOf[Long]) }
              else if (sTpe <:< TypeRepr.of[Double]) {
                '{ $out.setDouble($baseOffset, $bytes, $getter.asInstanceOf[Double]) }
              } else if (sTpe <:< TypeRepr.of[Boolean]) {
                '{ $out.setBoolean($baseOffset, $bytes, $getter.asInstanceOf[Boolean]) }
              } else if (sTpe <:< TypeRepr.of[Byte]) '{ $out.setByte($baseOffset, $bytes, $getter.asInstanceOf[Byte]) }
              else if (sTpe <:< TypeRepr.of[Char]) '{ $out.setChar($baseOffset, $bytes, $getter.asInstanceOf[Char]) }
              else if (sTpe <:< TypeRepr.of[Short]) '{ $out.setShort($baseOffset, $bytes, $getter.asInstanceOf[Short]) }
              else if (sTpe <:< TypeRepr.of[Unit]) '{ () }
              else if (sTpe <:< TypeRepr.of[AnyRef] || isValueClass(sTpe)) {
                '{ $out.setObject($baseOffset, $objects, $getter.asInstanceOf[AnyRef]) }
              } else unsupportedFieldType(sTpe)
            }.asTerm
        })
    }

    def deriveSchema[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
      if (isEnumOrModuleValue(tpe)) {
        val tpeName = typeName[T](tpe)
        '{
          new Schema(
            reflect = new Reflect.Record[Binding, T](
              fields = Vector.empty,
              typeName = ${ toExpr(tpeName) },
              recordBinding = new Binding.Record(
                constructor = new ConstantConstructor[T](${
                  Ref(if (isEnumValue(tpe)) tpe.termSymbol else tpe.typeSymbol.companionModule).asExprOf[T]
                }),
                deconstructor = new ConstantDeconstructor[T]
              ),
              doc = ${ doc(tpe) },
              modifiers = ${ modifiers(tpe) }
            )
          )
        }
      } else if (tpe <:< TypeRepr.of[Array[?]]) {
        val eTpe = typeArgs(tpe).head
        eTpe.asType match {
          case '[et] =>
            val tpeName     = typeName[Array[et]](tpe)
            val constructor =
              if (eTpe <:< TypeRepr.of[AnyRef]) {
                val classTag = Expr.summon[ClassTag[et]].getOrElse(fail(s"No ClassTag available for ${eTpe.show}"))
                '{
                  implicit val ct: ClassTag[et] = $classTag
                  new SeqConstructor.ArrayConstructor {
                    override def newObjectBuilder[A0](sizeHint: Int): Builder[A0] =
                      new Builder(new Array[et](sizeHint).asInstanceOf[Array[A0]], 0)
                  }
                }
              } else '{ SeqConstructor.arrayConstructor }
            '{
              new Schema(
                reflect = new Reflect.Sequence[Binding, et, Array](
                  element = ${ findImplicitOrDeriveSchema[et](eTpe) }.reflect,
                  typeName = ${ toExpr(tpeName) },
                  seqBinding = new Binding.Seq[Array, et](
                    constructor = $constructor,
                    deconstructor = SeqDeconstructor.arrayDeconstructor
                  )
                )
              )
            }
        }
      } else if (isGenericTuple(tpe)) {
        val tTpe = normalizeTuple(tpe)
        tTpe.asType match {
          case '[tt] =>
            val tTpeName = typeName[tt](tTpe)
            val typeInfo =
              if (isGenericTuple(tTpe)) new GenericTupleInfo[tt](tTpe)
              else new ClassInfo[tt](tTpe)
            '{
              new Schema(
                reflect = new Reflect.Record[Binding, tt](
                  fields = Vector(${ typeInfo.fields[tt]() }*),
                  typeName = ${ toExpr(tTpeName) },
                  recordBinding = new Binding.Record(
                    constructor = new Constructor[tt] {
                      def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                      def construct(in: Registers, baseOffset: RegisterOffset): tt = ${
                        typeInfo.constructor('in, 'baseOffset)
                      }
                    },
                    deconstructor = new Deconstructor[tt] {
                      def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: tt): Unit = ${
                        typeInfo.deconstructor('out, 'baseOffset, 'in)
                      }
                    }
                  )
                )
              )
            }
        }
      } else if (isSealedTraitOrAbstractClass(tpe) || isUnion(tpe)) {
        def toFullTermName(tpeName: TypeName[?]): Array[String] = {
          val packages     = tpeName.namespace.packages
          val values       = tpeName.namespace.values
          val fullTermName = new Array[String](packages.size + values.size + 1)
          var idx          = 0
          packages.foreach { p =>
            fullTermName(idx) = p
            idx += 1
          }
          values.foreach { p =>
            fullTermName(idx) = p
            idx += 1
          }
          fullTermName(idx) = tpeName.name
          fullTermName
        }

        def toShortTermName(fullName: Array[String], from: Int): String = {
          val str = new java.lang.StringBuilder
          var idx = from
          while (idx < fullName.length) {
            if (idx != from) str.append('.')
            str.append(fullName(idx))
            idx += 1
          }
          str.toString
        }

        val isUnionType = isUnion(tpe)
        val subTypes    =
          if (isUnionType) allUnionTypes(tpe).distinct
          else directSubTypes(tpe)
        if (subTypes.isEmpty) fail(s"Cannot find sub-types for ADT base '${tpe.show}'.")
        val fullTermNames         = subTypes.map(sTpe => toFullTermName(typeName(sTpe)))
        val tpeName               = typeName(tpe)
        val maxCommonPrefixLength = {
          var minFullTermName = fullTermNames.min
          var maxFullTermName = fullTermNames.max
          if (!isUnionType) {
            val tpeFullTermName = toFullTermName(tpeName)
            minFullTermName = fullTermNameOrdering.min(minFullTermName, tpeFullTermName)
            maxFullTermName = fullTermNameOrdering.max(maxFullTermName, tpeFullTermName)
          }
          val minLength = Math.min(minFullTermName.length, maxFullTermName.length)
          var idx       = 0
          while (idx < minLength && minFullTermName(idx).equals(maxFullTermName(idx))) idx += 1
          idx
        }
        val cases = Expr.ofSeq(subTypes.zip(fullTermNames).map { case (sTpe, fullName) =>
          sTpe.asType match {
            case '[st] =>
              '{
                ${ findImplicitOrDeriveSchema[st](sTpe) }.reflect.asTerm[T](${
                  Expr(toShortTermName(fullName, maxCommonPrefixLength))
                })
              }.asExprOf[zio.blocks.schema.Term[Binding, T, ? <: T]]
          }
        })
        val matcherCases = Expr.ofSeq(subTypes.map { sTpe =>
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
        })
        '{
          new Schema(
            reflect = new Reflect.Variant[Binding, T](
              cases = Vector($cases*),
              typeName = ${ toExpr(tpeName) },
              variantBinding = new Binding.Variant(
                discriminator = new Discriminator[T] {
                  def discriminate(a: T): Int = ${
                    val v   = 'a
                    var idx = -1
                    Match(
                      '{ $v: @scala.unchecked }.asTerm,
                      subTypes.map { sTpe =>
                        idx += 1
                        CaseDef(Typed(Wildcard(), Inferred(sTpe)), None, Literal(IntConstant(idx)))
                      }
                    ).asExprOf[Int]
                  }
                },
                matchers = Matchers($matcherCases*)
              ),
              doc = ${ doc(tpe) },
              modifiers = ${ modifiers(tpe) }
            )
          )
        }
      } else if (isNamedTuple(tpe)) {
        tpe match {
          case AppliedType(_, List(tpe1, tpe2)) =>
            val tpeName       = typeName(tpe)
            val nTpe          = tpe1.dealias
            val nameOverrides = {
              if (isGenericTuple(nTpe)) genericTupleTypeArgs(nTpe.asType)
              else typeArgs(nTpe)
            }.map { case ConstantType(StringConstant(x)) => x }
            val tTpe = normalizeTuple(tpe2.dealias)
            tTpe.asType match {
              case '[tt] =>
                val typeInfo =
                  if (isGenericTuple(tTpe)) new GenericTupleInfo[tt](tTpe)
                  else new ClassInfo[tt](tTpe)
                '{
                  new Schema(
                    reflect = new Reflect.Record[Binding, T](
                      fields = Vector(${ typeInfo.fields[T](nameOverrides) }*),
                      typeName = ${ toExpr(tpeName) },
                      recordBinding = new Binding.Record(
                        constructor = new Constructor[T] {
                          def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                          def construct(in: Registers, baseOffset: RegisterOffset): T = ${
                            typeInfo.constructor('in, 'baseOffset).asExprOf[T]
                          }
                        },
                        deconstructor = new Deconstructor[T] {
                          def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: T): Unit = ${
                            val valDef = ValDef(
                              Symbol.newVal(Symbol.spliceOwner, "t", tTpe, Flags.EmptyFlags, Symbol.noSymbol),
                              Some(
                                Apply(
                                  Select
                                    .unique(Ref(Symbol.requiredModule("scala.NamedTuple")), "toTuple")
                                    .appliedToTypeTrees(tpe.typeArgs.map { typeArg =>
                                      typeArg.asType match
                                        case '[t] => TypeTree.of[t]
                                    }),
                                  List('in.asTerm)
                                )
                              )
                            )
                            Block(
                              List(valDef),
                              typeInfo.deconstructor('out, 'baseOffset, Ref(valDef.symbol).asExprOf[tt]).asTerm
                            ).asExprOf[Unit]
                          }
                        }
                      )
                    )
                  )
                }
            }
          case _ => fail(s"Cannot derive schema for '${tpe.show}'.")
        }
      } else if (isNonAbstractScalaClass(tpe)) {
        val tpeName   = typeName(tpe)
        val classInfo = new ClassInfo[T](tpe)
        '{
          new Schema(
            reflect = new Reflect.Record[Binding, T](
              fields = Vector(${ classInfo.fields[T]() }*),
              typeName = ${ toExpr(tpeName) },
              recordBinding = new Binding.Record(
                constructor = new Constructor[T] {
                  def usedRegisters: RegisterOffset = ${ classInfo.usedRegisters }

                  def construct(in: Registers, baseOffset: RegisterOffset): T = ${
                    classInfo.constructor('in, 'baseOffset)
                  }
                },
                deconstructor = new Deconstructor[T] {
                  def usedRegisters: RegisterOffset = ${ classInfo.usedRegisters }

                  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: T): Unit = ${
                    classInfo.deconstructor('out, 'baseOffset, 'in)
                  }
                }
              ),
              doc = ${ doc(tpe) },
              modifiers = ${ modifiers(tpe) }
            )
          )
        }
      } else {
        val sTpe = opaqueDealias(tpe)
        if (sTpe =:= tpe) fail(s"Cannot derive schema for '${tpe.show}'.")
        sTpe.asType match {
          case '[s] => '{ ${ findImplicitOrDeriveSchema[s](sTpe) }.asInstanceOf[Schema[T]] }
        }
      }
    }.asExprOf[Schema[T]]

    val aTpe        = TypeRepr.of[A].dealias
    val schema      = aTpe.asType match { case '[a] => deriveSchema[a](aTpe) }
    val schemaBlock = Block(derivedSchemaDefs.toList, schema.asTerm).asExprOf[Schema[A]]
    // report.info(s"Generated schema:\n${schemaBlock.show}", Position.ofMacroExpansion)
    schemaBlock
  }
}
