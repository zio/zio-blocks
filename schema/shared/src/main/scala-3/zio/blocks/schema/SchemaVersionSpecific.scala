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

    def allUnionTypes(tpe: TypeRepr): List[TypeRepr] = tpe.dealias match {
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

    def isGenericTuple(tpe: TypeRepr): Boolean = tpe match {
      case AppliedType(tTpe, _) if tTpe =:= TypeRepr.of[*:] => true
      case _                                                => false
    }

    def genericTupleTypeArgs(tpe: TypeRepr): List[TypeRepr] = tpe match {
      case AppliedType(_, List(typeArg, tail)) => typeArg.dealias :: genericTupleTypeArgs(tail)
      case _                                   => Nil
    }

    def isNamedTuple(tpe: TypeRepr): Boolean = tpe match {
      case AppliedType(ntTpe, _) if ntTpe =:= TypeRepr.of[NamedTuple.NamedTuple] => true
      case _                                                                     => false
    }

    def namedGenericTupleNames(tpe: TypeRepr): List[String] = tpe match {
      case AppliedType(_, List(ConstantType(StringConstant(name)), tail)) => name :: namedGenericTupleNames(tail)
      case _                                                              => Nil
    }

    def isOption(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Option[?]]

    def isEither(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Either[?, ?]]

    def isDynamicValue(tpe: TypeRepr): Boolean = tpe =:= TypeRepr.of[DynamicValue]

    def isCollection(tpe: TypeRepr): Boolean =
      tpe <:< TypeRepr.of[Iterable[?]] || tpe <:< TypeRepr.of[Iterator[?]] || tpe <:< TypeRepr.of[Array[?]] ||
        tpe.typeSymbol.fullName == "scala.IArray$package$.IArray"

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
            case other => fail(s"Cannot resolve free type parameters for ADT cases with base '${tpe.show}'.")
          }
        } else if (symbol.isTerm) Ref(symbol).tpe
        else fail(s"Cannot resolve free type parameters for ADT cases with base '${tpe.show}'.")
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
          else if (isGenericTuple(tpe)) {
            val nestedTpes_ = tpe :: nestedTpes
            genericTupleTypeArgs(tpe).forall(isNonRecursive(_, nestedTpes_))
          } else if (isSealedTraitOrAbstractClass(tpe)) directSubTypes(tpe).forall(isNonRecursive(_, nestedTpes))
          else if (isUnion(tpe)) allUnionTypes(tpe).forall(isNonRecursive(_, nestedTpes))
          else if (isNamedTuple(tpe)) {
            tpe match {
              case AppliedType(_, List(_, tTpe)) => isNonRecursive(tTpe, nestedTpes)
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

    def typeName(tpe: TypeRepr): (List[String], List[String], String) =
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

    def toBlock(terms: List[Term]): Expr[Unit] = {
      val size = terms.size
      if (size > 1) Block(terms.init, terms.last)
      else if (size > 0) terms.head
      else Literal(UnitConstant())
    }.asExprOf[Unit]

    def doc(tpe: TypeRepr)(using Quotes): Expr[Doc] =
      (if (isEnumValue(tpe)) tpe.termSymbol else tpe.typeSymbol).docstring
        .fold('{ Doc.Empty }.asExprOf[Doc])(s => '{ new Doc.Text(${ Expr(s) }) }.asExprOf[Doc])

    def modifiers(tpe: TypeRepr)(using Quotes): List[Expr[Modifier.config]] =
      (if (isEnumValue(tpe)) tpe.termSymbol else tpe.typeSymbol).annotations
        .filter(_.tpe =:= TypeRepr.of[Modifier.config])
        .collect { case Apply(_, List(Literal(StringConstant(k)), Literal(StringConstant(v)))) =>
          '{ Modifier.config(${ Expr(k) }, ${ Expr(v) }) }.asExprOf[Modifier.config]
        }
        .reverse

    val inferredSchemas   = new mutable.HashMap[TypeRepr, Expr[Schema[?]]]
    val derivedSchemaRefs = new mutable.HashMap[TypeRepr, Expr[Schema[?]]]
    val derivedSchemaDefs = new mutable.ListBuffer[ValDef]

    def findImplicitOrDeriveSchema[T: Type](using Quotes): Expr[Schema[T]] = {
      val tpe            = TypeRepr.of[T]
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
              val name = "s" + derivedSchemaRefs.size
              val flags =
                if (isNonRecursive(tpe)) Flags.Implicit
                else Flags.Implicit | Flags.Lazy
              val symbol = Symbol.newVal(Symbol.spliceOwner, name, schemaTpe, flags, Symbol.noSymbol)
              val ref    = Ref(symbol).asExprOf[Schema[?]]
              // adding the schema reference before schema derivation to avoid an endless loop on recursive data structures
              derivedSchemaRefs.update(tpe, ref)
              val schema = deriveSchema[T]
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

    abstract class TypeInfo[T: Type]()(using Quotes) {
      def usedRegisters: Expr[RegisterOffset]

      def fields[S: Type](nameOverrides: List[String] = Nil): Expr[Seq[zio.blocks.schema.Term[Binding, S, ?]]]

      def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset]): Expr[T]

      def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T]): Expr[Unit]

      def fieldOffset(tpe: TypeRepr): RegisterOffset =
        if (tpe =:= TypeRepr.of[Int]) RegisterOffset(ints = 1)
        else if (tpe =:= TypeRepr.of[Float]) RegisterOffset(floats = 1)
        else if (tpe =:= TypeRepr.of[Long]) RegisterOffset(longs = 1)
        else if (tpe =:= TypeRepr.of[Double]) RegisterOffset(doubles = 1)
        else if (tpe =:= TypeRepr.of[Boolean]) RegisterOffset(booleans = 1)
        else if (tpe =:= TypeRepr.of[Byte]) RegisterOffset(bytes = 1)
        else if (tpe =:= TypeRepr.of[Char]) RegisterOffset(chars = 1)
        else if (tpe =:= TypeRepr.of[Short]) RegisterOffset(shorts = 1)
        else if (tpe =:= TypeRepr.of[Unit]) RegisterOffset.Zero
        else if (tpe <:< TypeRepr.of[AnyRef] || tpe <:< TypeRepr.of[AnyVal]) RegisterOffset(objects = 1)
        else unsupportedFieldType(tpe)

      def fieldConstructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset], fieldInfo: FieldInfo): Expr[?] = {
        val fTpe      = fieldInfo.tpe
        lazy val bs   = Expr(RegisterOffset.getBytes(fieldInfo.usedRegisters))
        lazy val objs = Expr(RegisterOffset.getObjects(fieldInfo.usedRegisters))
        (if (fTpe =:= TypeRepr.of[Int]) '{ $in.getInt($baseOffset, $bs) }
         else if (fTpe =:= TypeRepr.of[Float]) '{ $in.getFloat($baseOffset, $bs) }
         else if (fTpe =:= TypeRepr.of[Long]) '{ $in.getLong($baseOffset, $bs) }
         else if (fTpe =:= TypeRepr.of[Double]) '{ $in.getDouble($baseOffset, $bs) }
         else if (fTpe =:= TypeRepr.of[Boolean]) '{ $in.getBoolean($baseOffset, $bs) }
         else if (fTpe =:= TypeRepr.of[Byte]) '{ $in.getByte($baseOffset, $bs) }
         else if (fTpe =:= TypeRepr.of[Char]) '{ $in.getChar($baseOffset, $bs) }
         else if (fTpe =:= TypeRepr.of[Short]) '{ $in.getShort($baseOffset, $bs) }
         else if (fTpe =:= TypeRepr.of[Unit]) '{ () }
         else if (fTpe <:< TypeRepr.of[AnyRef] || fTpe <:< TypeRepr.of[AnyVal]) {
           fTpe.asType match { case '[ft] => '{ $in.getObject($baseOffset, $objs).asInstanceOf[ft] } }
         } else unsupportedFieldType(fTpe))
      }

      def unsupportedFieldType(tpe: TypeRepr): Nothing = fail(s"Unsupported field type '${tpe.show}'.")
    }

    case class ClassInfo[T: Type]()(using Quotes) extends TypeInfo[T]() {
      private[this] val tpe                = TypeRepr.of[T]
      private[this] val tpeClassSymbol     = tpe.classSymbol.get
      private[this] val primaryConstructor = tpeClassSymbol.primaryConstructor
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
                  if (getters.isEmpty) {
                    fail(s"Cannot find '$name' parameter of '${tpe.show}' in the primary constructor.")
                  }
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
                val fieldInfo = FieldInfo(symbol, name, fTpe, defaultValue, getter, usedRegisters, isTransient, config)
                usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe))
                fieldInfo
            }
          }),
          Expr(usedRegisters)
        )
      }

      def fields[S: Type](nameOverrides: List[String] = Nil): Expr[Seq[zio.blocks.schema.Term[Binding, S, ?]]] = {
        val names = nameOverrides.toArray
        var idx   = -1
        Expr.ofSeq(fieldInfos.flatMap(_.map { fieldInfo =>
          val fTpe = fieldInfo.tpe
          fTpe.asType match {
            case '[ft] =>
              idx += 1
              var reflectExpr = '{ ${ findImplicitOrDeriveSchema[ft] }.reflect }
              reflectExpr = fieldInfo.defaultValue.fold(reflectExpr) { dv =>
                '{ $reflectExpr.defaultValue(${ dv.asExprOf[ft] }) }
              }
              if (!isNonRecursive(fTpe)) reflectExpr = '{ Reflect.Deferred(() => $reflectExpr) }
              var name = fieldInfo.name
              if (idx < names.length) name = names(idx)
              var fieldTermExpr = '{ $reflectExpr.asTerm[S](${ Expr(name) }) }
              var modifiers = fieldInfo.config.map { case (k, v) =>
                '{ Modifier.config(${ Expr(k) }, ${ Expr(v) }) }.asExprOf[Modifier.Term]
              }
              if (fieldInfo.isTransient) modifiers = modifiers :+ '{ Modifier.transient() }.asExprOf[Modifier.Term]
              if (modifiers.nonEmpty) fieldTermExpr = '{ $fieldTermExpr.copy(modifiers = ${ Expr.ofSeq(modifiers) }) }
              fieldTermExpr
          }
        }))
      }

      def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset]): Expr[T] = {
        val constructorNoTypes = Select(New(Inferred(tpe)), primaryConstructor)
        val constructor = typeArgs(tpe) match {
          case Nil      => constructorNoTypes
          case typeArgs => TypeApply(constructorNoTypes, typeArgs.map(Inferred(_)))
        }
        val argss = fieldInfos.map(_.map(fieldInfo => fieldConstructor(in, baseOffset, fieldInfo).asTerm))
        argss.tail.foldLeft(Apply(constructor, argss.head))((acc, args) => Apply(acc, args))
      }.asExprOf[T]

      def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T]): Expr[Unit] =
        toBlock(fieldInfos.flatMap(_.map { fieldInfo =>
          val fTpe      = fieldInfo.tpe
          val getter    = Select(in.asTerm, fieldInfo.getter)
          lazy val bs   = Expr(RegisterOffset.getBytes(fieldInfo.usedRegisters))
          lazy val objs = Expr(RegisterOffset.getObjects(fieldInfo.usedRegisters))
          (if (fTpe =:= TypeRepr.of[Int]) '{ $out.setInt($baseOffset, $bs, ${ getter.asExprOf[Int] }) }
           else if (fTpe =:= TypeRepr.of[Float]) '{ $out.setFloat($baseOffset, $bs, ${ getter.asExprOf[Float] }) }
           else if (fTpe =:= TypeRepr.of[Long]) '{ $out.setLong($baseOffset, $bs, ${ getter.asExprOf[Long] }) }
           else if (fTpe =:= TypeRepr.of[Double]) '{ $out.setDouble($baseOffset, $bs, ${ getter.asExprOf[Double] }) }
           else if (fTpe =:= TypeRepr.of[Boolean]) {
             '{ $out.setBoolean($baseOffset, $bs, ${ getter.asExprOf[Boolean] }) }
           } else if (fTpe =:= TypeRepr.of[Byte]) '{ $out.setByte($baseOffset, $bs, ${ getter.asExprOf[Byte] }) }
           else if (fTpe =:= TypeRepr.of[Char]) '{ $out.setChar($baseOffset, $bs, ${ getter.asExprOf[Char] }) }
           else if (fTpe =:= TypeRepr.of[Short]) '{ $out.setShort($baseOffset, $bs, ${ getter.asExprOf[Short] }) }
           else if (fTpe =:= TypeRepr.of[Unit]) '{ () }
           else if (fTpe <:< TypeRepr.of[AnyRef]) '{ $out.setObject($baseOffset, $objs, ${ getter.asExprOf[AnyRef] }) }
           else if (fTpe <:< TypeRepr.of[AnyVal]) {
             fTpe.asType match {
               case '[ft] =>
                 '{ $out.setObject($baseOffset, $objs, ${ getter.asExprOf[ft] }.asInstanceOf[AnyRef]) }
             }
           } else unsupportedFieldType(fTpe)).asTerm
        }))
    }

    case class GenericTupleInfo[T <: Tuple: Type]()(using Quotes) extends TypeInfo[T]() {
      val (fieldInfos: List[FieldInfo], usedRegisters: Expr[RegisterOffset]) = {
        val fTpes         = genericTupleTypeArgs(TypeRepr.of[T])
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

      def fields[S: Type](nameOverrides: List[String] = Nil): Expr[Seq[zio.blocks.schema.Term[Binding, S, ?]]] = {
        val names = nameOverrides.toArray
        Expr.ofSeq(fieldInfos.map {
          var idx = -1
          fieldInfo =>
            idx += 1
            val fTpe = fieldInfo.tpe
            fTpe.asType match {
              case '[ft] =>
                var reflectExpr = '{ ${ findImplicitOrDeriveSchema[ft] }.reflect }
                if (!isNonRecursive(fTpe)) reflectExpr = '{ Reflect.Deferred(() => $reflectExpr) }
                var name = fieldInfo.name
                if (idx < names.length) name = names(idx)
                '{ $reflectExpr.asTerm[S](${ Expr(name) }) }
            }
        })
      }

      def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset]): Expr[T] =
        Expr.ofTupleFromSeq(fieldInfos.map(fieldInfo => fieldConstructor(in, baseOffset, fieldInfo))).asExprOf[T]

      def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T]): Expr[Unit] =
        toBlock(fieldInfos.map {
          var idx = -1
          fieldInfo =>
            idx += 1
            val fTpe      = fieldInfo.tpe
            val getter    = Select.unique(in.asTerm, "productElement").appliedTo(Literal(IntConstant(idx))).asExprOf[Any]
            lazy val bs   = Expr(RegisterOffset.getBytes(fieldInfo.usedRegisters))
            lazy val objs = Expr(RegisterOffset.getObjects(fieldInfo.usedRegisters))
            (if (fTpe =:= TypeRepr.of[Int]) '{ $out.setInt($baseOffset, $bs, $getter.asInstanceOf[Int]) }
             else if (fTpe =:= TypeRepr.of[Float]) '{ $out.setFloat($baseOffset, $bs, $getter.asInstanceOf[Float]) }
             else if (fTpe =:= TypeRepr.of[Long]) '{ $out.setLong($baseOffset, $bs, $getter.asInstanceOf[Long]) }
             else if (fTpe =:= TypeRepr.of[Double]) '{ $out.setDouble($baseOffset, $bs, $getter.asInstanceOf[Double]) }
             else if (fTpe =:= TypeRepr.of[Boolean]) {
               '{ $out.setBoolean($baseOffset, $bs, $getter.asInstanceOf[Boolean]) }
             } else if (fTpe =:= TypeRepr.of[Byte]) '{ $out.setByte($baseOffset, $bs, $getter.asInstanceOf[Byte]) }
             else if (fTpe =:= TypeRepr.of[Char]) '{ $out.setChar($baseOffset, $bs, $getter.asInstanceOf[Char]) }
             else if (fTpe =:= TypeRepr.of[Short]) '{ $out.setShort($baseOffset, $bs, $getter.asInstanceOf[Short]) }
             else if (fTpe =:= TypeRepr.of[Unit]) '{ () }
             else if (fTpe <:< TypeRepr.of[AnyRef] || fTpe <:< TypeRepr.of[AnyVal]) {
               '{ $out.setObject($baseOffset, $objs, $getter.asInstanceOf[AnyRef]) }
             } else unsupportedFieldType(fTpe)).asTerm
        })
    }

    def deriveSchema[T: Type](using Quotes): Expr[Schema[T]] = {
      val tpe = TypeRepr.of[T]
      if (isEnumOrModuleValue(tpe)) {
        val (packages, values, name) = typeName(tpe)
        '{
          new Schema(
            reflect = new Reflect.Record[Binding, T](
              fields = Vector.empty,
              typeName = new TypeName(new Namespace(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) }),
              recordBinding = new Binding.Record(
                constructor = new ConstantConstructor[T](${
                  Ref(if (isEnumValue(tpe)) tpe.termSymbol else tpe.typeSymbol.companionModule).asExprOf[T]
                }),
                deconstructor = new ConstantDeconstructor[T]
              ),
              doc = ${ doc(tpe) },
              modifiers = ${ Expr.ofList(modifiers(tpe)) }
            )
          )
        }
      } else if (tpe <:< TypeRepr.of[Array[?]]) {
        val elementTpe = typeArgs(tpe).head
        elementTpe.asType match {
          case '[et] =>
            var reflectExpr = '{ ${ findImplicitOrDeriveSchema[et] }.reflect }
            val constructor =
              if (elementTpe <:< TypeRepr.of[AnyRef]) {
                val classTag =
                  Expr.summon[reflect.ClassTag[et]].getOrElse(fail(s"No ClassTag available for ${elementTpe.show}"))
                '{
                  implicit val ct: reflect.ClassTag[et] = $classTag
                  new SeqConstructor.ArrayConstructor {
                    override def newObjectBuilder[A](sizeHint: Int): Builder[A] =
                      new Builder(new Array[et](sizeHint).asInstanceOf[Array[A]], 0)
                  }
                }
              } else '{ SeqConstructor.arrayConstructor }
            '{
              new Schema(
                reflect = new Reflect.Sequence[Binding, et, Array](
                  element = $reflectExpr,
                  typeName = new TypeName(new Namespace(List("scala"), Nil), "Array"),
                  seqBinding = new Binding.Seq[Array, et](
                    constructor = $constructor,
                    deconstructor = SeqDeconstructor.arrayDeconstructor
                  )
                )
              )
            }
        }
      } else if (isGenericTuple(tpe)) {
        tpe.asType match {
          case '[
              type tt <: Tuple; tt] =>
            val genericTupleInfo         = new GenericTupleInfo[tt]()
            val (packages, values, name) = typeName(tpe)
            '{
              new Schema(
                reflect = new Reflect.Record[Binding, tt](
                  fields = Vector(${ genericTupleInfo.fields[tt]() }*),
                  typeName = new TypeName(new Namespace(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) }),
                  recordBinding = new Binding.Record(
                    constructor = new Constructor[tt] {
                      def usedRegisters: RegisterOffset = ${ genericTupleInfo.usedRegisters }

                      def construct(in: Registers, baseOffset: RegisterOffset): tt = ${
                        genericTupleInfo.constructor('in, 'baseOffset)
                      }
                    },
                    deconstructor = new Deconstructor[tt] {
                      def usedRegisters: RegisterOffset = ${ genericTupleInfo.usedRegisters }

                      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: tt): Unit = ${
                        genericTupleInfo.deconstructor('out, 'baseOffset, 'in)
                      }
                    }
                  )
                )
              )
            }
        }
      } else if (isSealedTraitOrAbstractClass(tpe) || isUnion(tpe)) {
        def toFullName(packages: List[String], values: List[String], name: String): Array[String] = {
          val fullName = new Array[String](packages.size + values.size + 1)
          var idx      = 0
          packages.foreach { p =>
            fullName(idx) = p
            idx += 1
          }
          values.foreach { p =>
            fullName(idx) = p
            idx += 1
          }
          fullName(idx) = name
          fullName
        }

        def toTermName(fullName: Array[String], from: Int): String = {
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
        val subTypes =
          if (isUnionType) allUnionTypes(tpe).distinct
          else directSubTypes(tpe)
        if (subTypes.isEmpty) fail(s"Cannot find sub-types for ADT base '${tpe.show}'.")
        var minFullName: Array[String] = null
        var maxFullName: Array[String] = null
        val fullNames = subTypes.map { sTpe =>
          val (packages, values, name) = typeName(sTpe)
          val fullName                 = toFullName(packages, values, name)
          if (minFullName eq null) {
            minFullName = fullName
            maxFullName = fullName
          } else {
            if (fullNameOrdering.compare(minFullName, fullName) > 0) minFullName = fullName
            if (fullNameOrdering.compare(maxFullName, fullName) < 0) maxFullName = fullName
          }
          fullName
        }
        val (packages, values, name) = typeName(tpe)
        if (!isUnionType) {
          val tpeFullName = toFullName(packages, values, name)
          if (fullNameOrdering.compare(minFullName, tpeFullName) > 0) minFullName = tpeFullName
          if (fullNameOrdering.compare(maxFullName, tpeFullName) < 0) maxFullName = tpeFullName
        }
        val maxCommonPrefixLength = {
          val minLength = Math.min(minFullName.length, maxFullName.length)
          var idx       = 0
          while (idx < minLength && minFullName(idx).compareTo(maxFullName(idx)) == 0) idx += 1
          idx
        }
        val cases = subTypes.zip(fullNames).map { case (sTpe, fullName) =>
          sTpe.asType match {
            case '[st] =>
              '{
                ${ findImplicitOrDeriveSchema[st] }.reflect.asTerm[T](${
                  Expr(toTermName(fullName, maxCommonPrefixLength))
                })
              }.asExprOf[zio.blocks.schema.Term[Binding, T, ? <: T]]
          }
        }
        val discrCases = subTypes.map {
          var idx = -1
          sTpe =>
            idx += 1
            CaseDef(Typed(Wildcard(), Inferred(sTpe)), None, Literal(IntConstant(idx)))
        }
        val matcherCases = subTypes.map { sTpe =>
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
          new Schema(
            reflect = new Reflect.Variant[Binding, T](
              cases = Vector(${ Expr.ofSeq(cases) }*),
              typeName = new TypeName(new Namespace(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) }),
              variantBinding = new Binding.Variant(
                discriminator = new Discriminator[T] {
                  def discriminate(a: T): Int = ${
                    val v = 'a
                    Match('{ $v: @scala.unchecked }.asTerm, discrCases).asExprOf[Int]
                  }
                },
                matchers = Matchers(${ Expr.ofSeq(matcherCases) }*)
              ),
              doc = ${ doc(tpe) },
              modifiers = ${ Expr.ofList(modifiers(tpe)) }
            )
          )
        }
      } else if (isNamedTuple(tpe)) {
        tpe match {
          case AppliedType(_, List(nTpe @ AppliedType(_, nameConstants), tTpe)) =>
            nTpe.asType match {
              case '[
                  type nt <: Tuple; nt] =>
                tTpe.asType match {
                  case '[
                      type tt <: Tuple; tt] =>
                    val (nameOverrides, typeInfo) =
                      if (isGenericTuple(tTpe)) (namedGenericTupleNames(nTpe), new GenericTupleInfo[tt]())
                      else (nameConstants.collect { case ConstantType(StringConstant(x)) => x }, new ClassInfo[tt]())
                    '{
                      new Schema(
                        reflect = new Reflect.Record[Binding, NamedTuple.NamedTuple[nt, tt]](
                          fields = Vector(${ typeInfo.fields[NamedTuple.NamedTuple[nt, tt]](nameOverrides) }*),
                          typeName = new TypeName(new Namespace(List("scala"), List("NamedTuple")), "NamedTuple"),
                          recordBinding = new Binding.Record(
                            constructor = new Constructor[NamedTuple.NamedTuple[nt, tt]] {
                              def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                              def construct(
                                in: Registers,
                                baseOffset: RegisterOffset
                              ): NamedTuple.NamedTuple[nt, tt] =
                                NamedTuple.apply[nt, tt](${ typeInfo.constructor('in, 'baseOffset) })
                            },
                            deconstructor = new Deconstructor[NamedTuple.NamedTuple[nt, tt]] {
                              def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                              def deconstruct(
                                out: Registers,
                                baseOffset: RegisterOffset,
                                in: NamedTuple.NamedTuple[nt, tt]
                              ): Unit = {
                                val t = in.toTuple
                                ${ typeInfo.deconstructor('out, 'baseOffset, 't) }
                              }
                            }
                          )
                        )
                      )
                    }
                }
            }
          case _ => fail(s"Cannot derive schema for '${tpe.show}'.")
        }
      } else if (isNonAbstractScalaClass(tpe)) {
        val classInfo                = new ClassInfo[T]()
        val (packages, values, name) = typeName(tpe)
        '{
          new Schema(
            reflect = new Reflect.Record[Binding, T](
              fields = Vector(${ classInfo.fields[T]() }*),
              typeName = new TypeName(new Namespace(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) }),
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
              modifiers = ${ Expr.ofList(modifiers(tpe)) }
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
