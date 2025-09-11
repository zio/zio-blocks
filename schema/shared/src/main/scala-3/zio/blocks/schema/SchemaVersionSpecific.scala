package zio.blocks.schema

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.quoted._
import zio.blocks.schema.Term as SchemaTerm
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._

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

    val intTpe             = defn.IntClass.typeRef
    val floatTpe           = defn.FloatClass.typeRef
    val longTpe            = defn.LongClass.typeRef
    val doubleTpe          = defn.DoubleClass.typeRef
    val booleanTpe         = defn.BooleanClass.typeRef
    val byteTpe            = defn.ByteClass.typeRef
    val charTpe            = defn.CharClass.typeRef
    val shortTpe           = defn.ShortClass.typeRef
    val unitTpe            = defn.UnitClass.typeRef
    val anyRefTpe          = defn.AnyRefClass.typeRef
    val anyValTpe          = defn.AnyValClass.typeRef
    lazy val arrayOfAnyTpe = defn.ArrayClass.typeRef.appliedTo(defn.AnyClass.typeRef)
    lazy val newArrayOfAny = TypeApply(
      Select(New(TypeIdent(defn.ArrayClass)), defn.ArrayClass.primaryConstructor),
      List(Inferred(defn.AnyClass.typeRef))
    )

    def fail(msg: String): Nothing = report.errorAndAbort(msg, Position.ofMacroExpansion)

    def isEnumValue(tpe: TypeRepr): Boolean = tpe.termSymbol.flags.is(Flags.Enum)

    def isEnumOrModuleValue(tpe: TypeRepr): Boolean = isEnumValue(tpe) || tpe.typeSymbol.flags.is(Flags.Module)

    def isSealedTraitOrAbstractClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))
    }

    def isOpaque(tpe: TypeRepr): Boolean = tpe.typeSymbol.flags.is(Flags.Opaque)

    def opaqueDealias(tpe: TypeRepr): TypeRepr = {
      @tailrec
      def loop(tpe: TypeRepr): TypeRepr = tpe match {
        case trTpe @ TypeRef(_, _) =>
          if (trTpe.isOpaqueAlias) loop(trTpe.translucentSuperType.dealias)
          else tpe
        case AppliedType(atTpe, _)   => loop(atTpe.dealias)
        case TypeLambda(_, _, tlTpe) => loop(tlTpe.dealias)
        case _                       => tpe
      }

      val sTpe = loop(tpe)
      if (sTpe =:= tpe) fail(s"Cannot dealias opaque type: ${tpe.show}.")
      sTpe
    }

    def isZioPreludeNewtype(tpe: TypeRepr): Boolean = tpe match {
      case TypeRef(compTpe, "Type") => compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
      case _                        => false
    }

    def zioPreludeNewtypeDealias(tpe: TypeRepr): TypeRepr = {
      def cannotDealias(tpe: TypeRepr): Nothing = fail(s"Cannot dealias zio-prelude newtype: ${tpe.show}.")

      tpe match {
        case TypeRef(compTpe, _) =>
          compTpe.baseClasses.collectFirst {
            case cls if cls.fullName == "zio.prelude.Newtype" =>
              compTpe.baseType(cls) match {
                case AppliedType(_, List(typeArg)) => typeArg.dealias
                case _                             => cannotDealias(tpe)
              }
          }
            .getOrElse(cannotDealias(tpe))
        case _ => cannotDealias(tpe)
      }
    }

    def dealiasOnDemand(tpe: TypeRepr): TypeRepr =
      if (isOpaque(tpe)) opaqueDealias(tpe)
      else if (isZioPreludeNewtype(tpe)) zioPreludeNewtypeDealias(tpe)
      else tpe

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

    def isValueClass(tpe: TypeRepr): Boolean = tpe <:< anyValTpe && isNonAbstractScalaClass(tpe)

    def typeArgs(tpe: TypeRepr): List[TypeRepr] = tpe match {
      case AppliedType(_, typeArgs) => typeArgs.map(_.dealias)
      case _                        => Nil
    }

    def isGenericTuple(tpe: TypeRepr): Boolean = tpe match {
      case AppliedType(gtTpe, _) => gtTpe.dealias =:= TypeRepr.of[*:]
      case _                     => false
    }

    val genericTupleTypeArgsCache = new mutable.HashMap[TypeRepr, List[TypeRepr]]

    def genericTupleTypeArgs(tpe: TypeRepr): List[TypeRepr] = genericTupleTypeArgsCache.getOrElseUpdate(
      tpe, {
        // Borrowed from an amazing work of Aleksander Rainko:
        // https://github.com/arainko/ducktape/blob/8d779f0303c23fd45815d3574467ffc321a8db2b/ducktape/src/main/scala/io/github/arainko/ducktape/internal/Structure.scala#L253-L270
        def loop(t: Type[?]): List[TypeRepr] = t match {
          case '[h *: t] => TypeRepr.of[h].dealias :: loop(Type.of[t])
          case _         => Nil
        }

        loop(tpe.asType)
      }
    )

    // Borrowed from an amazing work of Aleksander Rainko:
    // https://github.com/arainko/ducktape/blob/8d779f0303c23fd45815d3574467ffc321a8db2b/ducktape/src/main/scala/io/github/arainko/ducktape/internal/Structure.scala#L277-L295
    def normalizeTuple(tpe: TypeRepr): TypeRepr = {
      val typeArgs = genericTupleTypeArgs(tpe)
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
      case AppliedType(ntTpe, _) => ntTpe.dealias.typeSymbol.fullName == "scala.NamedTuple$.NamedTuple"
      case _                     => false
    }

    def isIArray(tpe: TypeRepr): Boolean = tpe.typeSymbol.fullName == "scala.IArray$package$.IArray"

    def isCollection(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Array[?]] || isIArray(tpe) ||
      (tpe <:< TypeRepr.of[IterableOnce[?]] && tpe.typeSymbol.fullName.startsWith("scala.collection."))

    def directSubTypes(tpe: TypeRepr): List[TypeRepr] = tpe.typeSymbol.children.map { symbol =>
      if (symbol.isType) {
        val subtype = symbol.typeRef
        subtype.memberType(symbol.primaryConstructor) match {
          case MethodType(_, _, _)                                        => subtype
          case PolyType(names, _, MethodType(_, _, AppliedType(base, _))) =>
            base.appliedTo(names.map {
              val binding = typeArgs(subtype.baseType(tpe.typeSymbol))
                .zip(typeArgs(tpe))
                .foldLeft(Map.empty[String, TypeRepr]) { case (binding, (childTypeArg, parentTypeArg)) =>
                  val typeSymbol = childTypeArg.typeSymbol
                  if (typeSymbol.isTypeParam) binding.updated(typeSymbol.name, parentTypeArg)
                  else binding
                }
              name =>
                binding.getOrElse(
                  name,
                  fail(s"Type parameter '$name' of '$symbol' can't be deduced from type arguments of '${tpe.show}'.")
                )
            })
          case _ => cannotResolveTypeParameterOfADT(tpe)
        }
      } else if (symbol.isTerm) Ref(symbol).tpe
      else cannotResolveTypeParameterOfADT(tpe)
    }

    def cannotResolveTypeParameterOfADT(tpe: TypeRepr): Nothing =
      fail(s"Cannot resolve free type parameters for ADT cases with base '${tpe.show}'.")

    val isNonRecursiveCache = new mutable.HashMap[TypeRepr, Boolean]

    def isNonRecursive(tpe: TypeRepr, nestedTpes: List[TypeRepr] = Nil): Boolean = isNonRecursiveCache.getOrElseUpdate(
      tpe,
      tpe <:< TypeRepr.of[String] || tpe <:< intTpe || tpe <:< floatTpe || tpe <:< longTpe ||
        tpe <:< doubleTpe || tpe <:< booleanTpe || tpe <:< byteTpe || tpe <:< charTpe || tpe <:< shortTpe ||
        tpe <:< unitTpe || tpe <:< TypeRepr.of[BigDecimal] || tpe <:< TypeRepr.of[BigInt] ||
        tpe <:< TypeRepr.of[java.time.temporal.Temporal] || tpe <:< TypeRepr.of[java.time.temporal.TemporalAmount] ||
        tpe <:< TypeRepr.of[java.util.Currency] || tpe <:< TypeRepr.of[java.util.UUID] || isEnumOrModuleValue(tpe) ||
        tpe <:< TypeRepr.of[DynamicValue] || {
          if (tpe <:< TypeRepr.of[Option[?]] || tpe <:< TypeRepr.of[Either[?, ?]] || isCollection(tpe)) {
            typeArgs(tpe).forall(isNonRecursive(_, nestedTpes))
          } else if (isGenericTuple(tpe)) {
            val nestedTpes_ = tpe :: nestedTpes
            genericTupleTypeArgs(tpe).forall(isNonRecursive(_, nestedTpes_))
          } else if (isSealedTraitOrAbstractClass(tpe)) directSubTypes(tpe).forall(isNonRecursive(_, nestedTpes))
          else if (isUnion(tpe)) allUnionTypes(tpe).forall(isNonRecursive(_, nestedTpes))
          else if (isNamedTuple(tpe)) {
            tpe match {
              case AppliedType(_, List(_, tTpe)) => isNonRecursive(tTpe.dealias, nestedTpes)
              case _                             => false
            }
          } else if (isNonAbstractScalaClass(tpe)) {
            !nestedTpes.contains(tpe) && {
              val primaryConstructor = tpe.classSymbol.get.primaryConstructor
              val nestedTpes_        = tpe :: nestedTpes
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
          } else if (isOpaque(tpe)) {
            isNonRecursive(opaqueDealias(tpe), nestedTpes)
          } else isZioPreludeNewtype(tpe) && isNonRecursive(zioPreludeNewtypeDealias(tpe), nestedTpes)
        }
    )

    val typeNameCache = new mutable.HashMap[TypeRepr, TypeName[?]]

    def typeName[T: Type](tpe: TypeRepr): TypeName[T] = {
      def calculateTypeName(tpe: TypeRepr): TypeName[?] =
        if (tpe =:= TypeRepr.of[java.lang.String]) TypeName.string
        else if (isUnion(tpe)) new TypeName(new Namespace(Nil, Nil), "|", allUnionTypes(tpe).map(typeName))
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
                  val nTpe        = tpe1.dealias
                  val tTpe        = tpe2.dealias
                  val tpeNameArgs =
                    if (isGenericTuple(nTpe)) genericTupleTypeArgs(nTpe)
                    else typeArgs(nTpe)
                  var comma  = false
                  val labels = new java.lang.StringBuilder(name)
                  labels.append('[')
                  tpeNameArgs.foreach {
                    case ConstantType(StringConstant(x)) =>
                      if (comma) labels.append(',')
                      else comma = true
                      labels.append(x)
                    case _ =>
                  }
                  labels.append(']')
                  name = labels.toString
                  if (isGenericTuple(tTpe)) genericTupleTypeArgs(tTpe)
                  else typeArgs(tTpe)
                case _ => Nil
              }
            } else if (isGenericTuple(tpe)) genericTupleTypeArgs(tpe)
            else typeArgs(tpe)
          new TypeName(new Namespace(packages, values), name, tpeTypeArgs.map(typeName))
        }

      typeNameCache
        .getOrElseUpdate(
          tpe,
          calculateTypeName(tpe match {
            case TypeRef(compTpe, "Type") => compTpe
            case _                        => tpe
          })
        )
        .asInstanceOf[TypeName[T]]
    }

    def toExpr[T: Type](tpeName: TypeName[T])(using Quotes): Expr[TypeName[T]] = '{
      new TypeName(
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

    def doc(tpe: TypeRepr)(using Quotes): Expr[Doc] = {
      if (isEnumValue(tpe)) tpe.termSymbol
      else tpe.typeSymbol
    }.docstring
      .fold('{ Doc.Empty })(s => '{ new Doc.Text(${ Expr(s) }) })
      .asExprOf[Doc]

    def modifiers(tpe: TypeRepr)(using Quotes): Expr[List[Modifier.config]] = Expr.ofList {
      var config: List[Expr[Modifier.config]] = Nil
      {
        if (isEnumValue(tpe)) tpe.termSymbol
        else tpe.typeSymbol
      }.annotations.foreach { annotation =>
        if (annotation.tpe =:= TypeRepr.of[Modifier.config]) annotation match {
          case Apply(_, List(Literal(StringConstant(k)), Literal(StringConstant(v)))) =>
            config = '{ Modifier.config(${ Expr(k) }, ${ Expr(v) }) }.asExprOf[Modifier.config] :: config
        }
      }
      config
    }

    def summonClassTag[T: Type](using Quotes): Expr[ClassTag[T]] =
      Expr.summon[ClassTag[T]].getOrElse(fail(s"No ClassTag available for ${TypeRepr.of[T].show}"))

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
              val schema = deriveSchema(tpe)
              derivedSchemaDefs.addOne(ValDef(symbol, new Some(schema.asTerm.changeOwner(symbol))))
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
      def tpeTypeArgs: List[TypeRepr]

      def usedRegisters: Expr[RegisterOffset]

      def fields[S: Type](nameOverrides: Array[String])(using Quotes): Expr[Seq[SchemaTerm[Binding, S, ?]]]

      def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset])(using Quotes): Expr[T]

      def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Expr[Unit]

      def fieldOffset(fTpe: TypeRepr): RegisterOffset = {
        val sTpe = dealiasOnDemand(fTpe)
        if (sTpe <:< intTpe) RegisterOffset(ints = 1)
        else if (sTpe <:< floatTpe) RegisterOffset(floats = 1)
        else if (sTpe <:< longTpe) RegisterOffset(longs = 1)
        else if (sTpe <:< doubleTpe) RegisterOffset(doubles = 1)
        else if (sTpe <:< booleanTpe) RegisterOffset(booleans = 1)
        else if (sTpe <:< byteTpe) RegisterOffset(bytes = 1)
        else if (sTpe <:< charTpe) RegisterOffset(chars = 1)
        else if (sTpe <:< shortTpe) RegisterOffset(shorts = 1)
        else if (sTpe <:< unitTpe) RegisterOffset.Zero
        else if (sTpe <:< anyRefTpe || isValueClass(sTpe)) RegisterOffset(objects = 1)
        else unsupportedFieldType(fTpe)
      }

      def fieldConstructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset], fieldInfo: FieldInfo)(using
        Quotes
      ): Expr[?] = {
        val fTpe         = fieldInfo.tpe
        lazy val bytes   = Expr(RegisterOffset.getBytes(fieldInfo.usedRegisters))
        lazy val objects = Expr(RegisterOffset.getObjects(fieldInfo.usedRegisters))
        if (fTpe =:= intTpe) '{ $in.getInt($baseOffset, $bytes) }
        else if (fTpe =:= floatTpe) '{ $in.getFloat($baseOffset, $bytes) }
        else if (fTpe =:= longTpe) '{ $in.getLong($baseOffset, $bytes) }
        else if (fTpe =:= doubleTpe) '{ $in.getDouble($baseOffset, $bytes) }
        else if (fTpe =:= booleanTpe) '{ $in.getBoolean($baseOffset, $bytes) }
        else if (fTpe =:= byteTpe) '{ $in.getByte($baseOffset, $bytes) }
        else if (fTpe =:= charTpe) '{ $in.getChar($baseOffset, $bytes) }
        else if (fTpe =:= shortTpe) '{ $in.getShort($baseOffset, $bytes) }
        else if (fTpe =:= unitTpe) '{ () }
        else {
          fTpe.asType match {
            case '[ft] =>
              val sTpe = dealiasOnDemand(fTpe)
              if (sTpe <:< anyRefTpe || isValueClass(sTpe)) {
                '{ $in.getObject($baseOffset, $objects).asInstanceOf[ft] }
              } else {
                if (sTpe <:< intTpe) '{ $in.getInt($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< floatTpe) '{ $in.getFloat($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< longTpe) '{ $in.getLong($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< doubleTpe) '{ $in.getDouble($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< booleanTpe) '{ $in.getBoolean($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< byteTpe) '{ $in.getByte($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< charTpe) '{ $in.getChar($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< shortTpe) '{ $in.getShort($baseOffset, $bytes).asInstanceOf[ft] }
                else if (sTpe <:< unitTpe) '{ ().asInstanceOf[ft] }
                else unsupportedFieldType(fTpe)
              }
          }
        }
      }

      def unsupportedFieldType(tpe: TypeRepr): Nothing = fail(s"Unsupported field type '${tpe.show}'.")
    }

    case class ClassInfo[T: Type](tpe: TypeRepr) extends TypeInfo {
      private val tpeClassSymbol                                                   = tpe.classSymbol.get
      private val primaryConstructor                                               = tpeClassSymbol.primaryConstructor
      val tpeTypeArgs: List[TypeRepr]                                              = typeArgs(tpe)
      val (fieldInfos: List[List[FieldInfo]], usedRegisters: Expr[RegisterOffset]) = {
        val (tpeTypeParams, tpeParams) = primaryConstructor.paramSymss match {
          case tps :: ps if tps.exists(_.isTypeParam) => (tps, ps)
          case ps                                     => (Nil, ps)
        }
        lazy val companionClass     = tpe.typeSymbol.companionClass
        lazy val companionModuleRef = Ref(tpe.typeSymbol.companionModule)
        var usedRegisters           = RegisterOffset.Zero
        var idx                     = 0
        (
          tpeParams.map(_.map { symbol =>
            idx += 1
            var fTpe = tpe.memberType(symbol).dealias
            if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
            val name   = symbol.name
            var getter = tpeClassSymbol.fieldMember(name)
            if (!getter.exists) {
              val flags = Flags.FieldAccessor | Flags.ParamAccessor
              getter = tpeClassSymbol
                .methodMember(name)
                .collectFirst { case x if x.flags.is(flags) => x }
                .getOrElse(Symbol.noSymbol)
            }
            if (!getter.exists || getter.flags.is(Flags.PrivateLocal))
              fail(
                s"Field or getter '$name' of '${tpe.show}' should be defined as 'val' or 'var' in the primary constructor."
              )
            var isTransient                    = false
            var config: List[(String, String)] = Nil
            getter.annotations.foreach { annotation =>
              if (annotation.tpe =:= TypeRepr.of[Modifier.transient]) isTransient = true
              else if (annotation.tpe =:= TypeRepr.of[Modifier.config]) annotation match {
                case Apply(_, List(Literal(StringConstant(k)), Literal(StringConstant(v)))) => config = (k, v) :: config
                case _                                                                      =>
              }
            }
            val defaultValue =
              if (symbol.flags.is(Flags.HasDefault)) {
                (companionClass.methodMember("$lessinit$greater$default$" + idx) match {
                  case methodSymbol :: _ =>
                    val dvSelectNoTArgs = companionModuleRef.select(methodSymbol)
                    methodSymbol.paramSymss match {
                      case Nil                                                                  => new Some(dvSelectNoTArgs)
                      case List(params) if params.exists(_.isTypeParam) && tpeTypeArgs.nonEmpty =>
                        new Some(dvSelectNoTArgs.appliedToTypes(tpeTypeArgs))
                      case _ => None
                    }
                  case _ => None
                }).orElse(fail(s"Cannot find default value for '$symbol' in class '${tpe.show}'."))
              } else None
            val fieldInfo = new FieldInfo(symbol, name, fTpe, defaultValue, getter, usedRegisters, isTransient, config)
            usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe))
            fieldInfo
          }),
          Expr(usedRegisters)
        )
      }

      def fields[S: Type](nameOverrides: Array[String])(using Quotes): Expr[Seq[SchemaTerm[Binding, S, ?]]] = {
        var idx = -1
        Expr.ofSeq(fieldInfos.flatMap(_.map { fieldInfo =>
          val fTpe = fieldInfo.tpe
          fTpe.asType match {
            case '[ft] =>
              idx += 1
              var reflect = '{ ${ findImplicitOrDeriveSchema[ft](fTpe) }.reflect }
              reflect = fieldInfo.defaultValue.fold(reflect) { dv =>
                '{ $reflect.defaultValue(${ dv.asExprOf[ft] }) }
              }
              if (!isNonRecursive(fTpe)) reflect = '{ new Reflect.Deferred(() => $reflect) }
              var name = fieldInfo.name
              if (idx < nameOverrides.length) name = nameOverrides(idx)
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
        val constructor = Select(New(Inferred(tpe)), primaryConstructor).appliedToTypes(tpeTypeArgs)
        val argss       = fieldInfos.map(_.map(fieldInfo => fieldConstructor(in, baseOffset, fieldInfo).asTerm))
        argss.tail.foldLeft(Apply(constructor, argss.head))(Apply(_, _)).asExprOf[T]
      }

      def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Expr[Unit] =
        toBlock(fieldInfos.flatMap(_.map { fieldInfo =>
          val fTpe         = fieldInfo.tpe
          val getter       = Select(in.asTerm, fieldInfo.getter)
          lazy val bytes   = Expr(RegisterOffset.getBytes(fieldInfo.usedRegisters))
          lazy val objects = Expr(RegisterOffset.getObjects(fieldInfo.usedRegisters))
          {
            if (fTpe <:< intTpe) '{ $out.setInt($baseOffset, $bytes, ${ getter.asExprOf[Int] }) }
            else if (fTpe <:< floatTpe) '{ $out.setFloat($baseOffset, $bytes, ${ getter.asExprOf[Float] }) }
            else if (fTpe <:< longTpe) '{ $out.setLong($baseOffset, $bytes, ${ getter.asExprOf[Long] }) }
            else if (fTpe <:< doubleTpe) '{ $out.setDouble($baseOffset, $bytes, ${ getter.asExprOf[Double] }) }
            else if (fTpe <:< booleanTpe) '{ $out.setBoolean($baseOffset, $bytes, ${ getter.asExprOf[Boolean] }) }
            else if (fTpe <:< byteTpe) '{ $out.setByte($baseOffset, $bytes, ${ getter.asExprOf[Byte] }) }
            else if (fTpe <:< charTpe) '{ $out.setChar($baseOffset, $bytes, ${ getter.asExprOf[Char] }) }
            else if (fTpe <:< shortTpe) '{ $out.setShort($baseOffset, $bytes, ${ getter.asExprOf[Short] }) }
            else if (fTpe <:< unitTpe) '{ () }
            else if (fTpe <:< anyRefTpe) '{ $out.setObject($baseOffset, $objects, ${ getter.asExprOf[AnyRef] }) }
            else {
              val sTpe       = dealiasOnDemand(fTpe)
              val getterExpr = getter.asExprOf[Any]
              if (sTpe <:< intTpe) '{ $out.setInt($baseOffset, $bytes, $getterExpr.asInstanceOf[Int]) }
              else if (sTpe <:< floatTpe) '{ $out.setFloat($baseOffset, $bytes, $getterExpr.asInstanceOf[Float]) }
              else if (sTpe <:< longTpe) '{ $out.setLong($baseOffset, $bytes, $getterExpr.asInstanceOf[Long]) }
              else if (sTpe <:< doubleTpe) '{ $out.setDouble($baseOffset, $bytes, $getterExpr.asInstanceOf[Double]) }
              else if (sTpe <:< booleanTpe) '{ $out.setBoolean($baseOffset, $bytes, $getterExpr.asInstanceOf[Boolean]) }
              else if (sTpe <:< byteTpe) '{ $out.setByte($baseOffset, $bytes, $getterExpr.asInstanceOf[Byte]) }
              else if (sTpe <:< charTpe) '{ $out.setChar($baseOffset, $bytes, $getterExpr.asInstanceOf[Char]) }
              else if (sTpe <:< shortTpe) '{ $out.setShort($baseOffset, $bytes, $getterExpr.asInstanceOf[Short]) }
              else if (sTpe <:< unitTpe) '{ () }
              else if (sTpe <:< anyRefTpe || isValueClass(sTpe)) {
                '{ $out.setObject($baseOffset, $objects, $getterExpr.asInstanceOf[AnyRef]) }
              } else unsupportedFieldType(fTpe)
            }
          }.asTerm
        }))
    }

    case class GenericTupleInfo[T: Type](tpe: TypeRepr) extends TypeInfo {
      val tpeTypeArgs: List[TypeRepr]                                        = genericTupleTypeArgs(tpe)
      val (fieldInfos: List[FieldInfo], usedRegisters: Expr[RegisterOffset]) = {
        var usedRegisters = RegisterOffset.Zero
        (
          tpeTypeArgs.map {
            var idx = 0
            fTpe =>
              idx += 1
              val fieldInfo =
                new FieldInfo(Symbol.spliceOwner, s"_$idx", fTpe, None, Symbol.noSymbol, usedRegisters, false, Nil)
              usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe))
              fieldInfo
          },
          Expr(usedRegisters)
        )
      }

      def fields[S: Type](nameOverrides: Array[String])(using Quotes): Expr[Seq[SchemaTerm[Binding, S, ?]]] =
        Expr.ofSeq(fieldInfos.map {
          var idx = -1
          fieldInfo =>
            idx += 1
            val fTpe = fieldInfo.tpe
            fTpe.asType match {
              case '[ft] =>
                var reflect = '{ ${ findImplicitOrDeriveSchema[ft](fTpe) }.reflect }
                if (!isNonRecursive(fTpe)) reflect = '{ new Reflect.Deferred(() => $reflect) }
                var name = fieldInfo.name
                if (idx < nameOverrides.length) name = nameOverrides(idx)
                '{ $reflect.asTerm[S](${ Expr(name) }) }
            }
        })

      def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset])(using Quotes): Expr[T] =
        (if (fieldInfos.isEmpty) Expr(EmptyTuple)
         else {
           val symbol      = Symbol.newVal(Symbol.spliceOwner, "as", arrayOfAnyTpe, Flags.EmptyFlags, Symbol.noSymbol)
           val ref         = Ref(symbol)
           val update      = Select(ref, defn.Array_update)
           val assignments = fieldInfos.map {
             var idx = -1
             fieldInfo =>
               idx += 1
               Apply(update, List(Literal(IntConstant(idx)), fieldConstructor(in, baseOffset, fieldInfo).asTerm))
           }
           val valDef = ValDef(symbol, new Some(Apply(newArrayOfAny, List(Literal(IntConstant(fieldInfos.size))))))
           val block  = Block(valDef :: assignments, ref).asExprOf[Array[Any]]
           tpe.asType match {
             case '[tt] => '{ scala.runtime.TupleXXL.fromIArray($block.asInstanceOf[IArray[AnyRef]]).asInstanceOf[tt] }
           }
         }).asExprOf[T]

      def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Expr[Unit] =
        toBlock(fieldInfos.map {
          val productElement = Select.unique(in.asTerm, "productElement")
          var idx            = -1
          fieldInfo =>
            idx += 1
            val fTpe         = fieldInfo.tpe
            val sTpe         = dealiasOnDemand(fTpe)
            val getter       = productElement.appliedTo(Literal(IntConstant(idx))).asExprOf[Any]
            lazy val bytes   = Expr(RegisterOffset.getBytes(fieldInfo.usedRegisters))
            lazy val objects = Expr(RegisterOffset.getObjects(fieldInfo.usedRegisters))
            {
              if (sTpe <:< intTpe) '{ $out.setInt($baseOffset, $bytes, $getter.asInstanceOf[Int]) }
              else if (sTpe <:< floatTpe) '{ $out.setFloat($baseOffset, $bytes, $getter.asInstanceOf[Float]) }
              else if (sTpe <:< longTpe) '{ $out.setLong($baseOffset, $bytes, $getter.asInstanceOf[Long]) }
              else if (sTpe <:< doubleTpe) '{ $out.setDouble($baseOffset, $bytes, $getter.asInstanceOf[Double]) }
              else if (sTpe <:< booleanTpe) '{ $out.setBoolean($baseOffset, $bytes, $getter.asInstanceOf[Boolean]) }
              else if (sTpe <:< byteTpe) '{ $out.setByte($baseOffset, $bytes, $getter.asInstanceOf[Byte]) }
              else if (sTpe <:< charTpe) '{ $out.setChar($baseOffset, $bytes, $getter.asInstanceOf[Char]) }
              else if (sTpe <:< shortTpe) '{ $out.setShort($baseOffset, $bytes, $getter.asInstanceOf[Short]) }
              else if (sTpe <:< unitTpe) '{ () }
              else if (sTpe <:< anyRefTpe || isValueClass(sTpe)) {
                '{ $out.setObject($baseOffset, $objects, $getter.asInstanceOf[AnyRef]) }
              } else unsupportedFieldType(fTpe)
            }.asTerm
        })
    }

    def deriveSchema[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
      if (isEnumOrModuleValue(tpe)) {
        val tpeName = typeName(tpe)
        '{
          new Schema(
            reflect = new Reflect.Record[Binding, T](
              fields = Vector.empty,
              typeName = ${ toExpr(tpeName) },
              recordBinding = new Binding.Record(
                constructor = new ConstantConstructor(${
                  Ref(
                    if (isEnumValue(tpe)) tpe.termSymbol
                    else tpe.typeSymbol.companionModule
                  ).asExprOf[T]
                }),
                deconstructor = new ConstantDeconstructor
              ),
              doc = ${ doc(tpe) },
              modifiers = ${ modifiers(tpe) }
            )
          )
        }
      } else if (isCollection(tpe)) {
        if (tpe <:< TypeRepr.of[Array[?]]) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match {
            case '[et] =>
              val tpeName     = typeName[Array[et]](tpe)
              val constructor =
                if (eTpe <:< anyRefTpe) {
                  val classTag = summonClassTag[et]
                  '{
                    implicit val ct: ClassTag[et] = $classTag
                    new SeqConstructor.ArrayConstructor {
                      override def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                        new Builder(new Array[et](sizeHint).asInstanceOf[Array[B]], 0)
                    }
                  }
                } else '{ SeqConstructor.arrayConstructor }
              '{
                new Schema(
                  reflect = new Reflect.Sequence(
                    element = ${ findImplicitOrDeriveSchema[et](eTpe) }.reflect,
                    typeName = ${ toExpr(tpeName) },
                    seqBinding = new Binding.Seq(
                      constructor = $constructor,
                      deconstructor = SeqDeconstructor.arrayDeconstructor
                    )
                  )
                )
              }
          }
        } else if (isIArray(tpe)) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match {
            case '[et] =>
              val tpeName     = typeName[IArray[et]](tpe)
              val constructor =
                if (eTpe <:< anyRefTpe) {
                  val classTag = summonClassTag[et]
                  '{
                    implicit val ct: ClassTag[et] = $classTag
                    new SeqConstructor.IArrayConstructor {
                      override def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                        new Builder(new Array[et](sizeHint).asInstanceOf[Array[B]], 0)
                    }
                  }
                } else '{ SeqConstructor.iArrayConstructor }
              '{
                new Schema(
                  reflect = new Reflect.Sequence(
                    element = ${ findImplicitOrDeriveSchema[et](eTpe) }.reflect,
                    typeName = ${ toExpr(tpeName) },
                    seqBinding = new Binding.Seq(
                      constructor = $constructor,
                      deconstructor = SeqDeconstructor.iArrayDeconstructor
                    )
                  )
                )
              }
          }
        } else if (tpe <:< TypeRepr.of[ArraySeq[?]]) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match { case '[et] => '{ Schema.arraySeq(${ findImplicitOrDeriveSchema[et](eTpe) }) } }
        } else if (tpe <:< TypeRepr.of[List[?]]) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match { case '[et] => '{ Schema.list(${ findImplicitOrDeriveSchema[et](eTpe) }) } }
        } else if (tpe <:< TypeRepr.of[Map[?, ?]]) {
          val tpeTypeArgs = typeArgs(tpe)
          val kTpe        = tpeTypeArgs.head
          val vTpe        = tpeTypeArgs.last
          kTpe.asType match {
            case '[kt] =>
              vTpe.asType match {
                case '[vt] =>
                  '{ Schema.map(${ findImplicitOrDeriveSchema[kt](kTpe) }, ${ findImplicitOrDeriveSchema[vt](vTpe) }) }
              }
          }
        } else if (tpe <:< TypeRepr.of[Set[?]]) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match { case '[et] => '{ Schema.set(${ findImplicitOrDeriveSchema[et](eTpe) }) } }
        } else if (tpe <:< TypeRepr.of[Vector[?]]) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match { case '[et] => '{ Schema.vector(${ findImplicitOrDeriveSchema[et](eTpe) }) } }
        } else cannotDeriveSchema(tpe)
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
                  fields = Vector(${ typeInfo.fields[tt](Array.empty[String]) }*),
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

        val tpeName     = typeName(tpe)
        val isUnionType = isUnion(tpe)
        val subTypes    =
          if (isUnionType) allUnionTypes(tpe).distinct
          else directSubTypes(tpe)
        if (subTypes.isEmpty) fail(s"Cannot find sub-types for ADT base '${tpe.show}'.")
        val fullTermNames         = subTypes.map(sTpe => toFullTermName(typeName(sTpe)))
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
              }.asExprOf[SchemaTerm[Binding, T, ? <: T]]
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
                discriminator = new Discriminator {
                  def discriminate(a: T): Int = ${
                    val v = 'a
                    Match(
                      '{ $v: @scala.unchecked }.asTerm,
                      subTypes.map {
                        var idx = -1
                        sTpe =>
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
            val tpeName     = typeName(tpe)
            val nTpe        = tpe1.dealias
            val tpeNameArgs =
              if (isGenericTuple(nTpe)) genericTupleTypeArgs(nTpe)
              else typeArgs(nTpe)
            val nameOverrides = new Array[String](tpeNameArgs.size)
            tpeNameArgs.foreach {
              var idx = -1
              tpeNameArg =>
                idx += 1
                tpeNameArg match {
                  case ConstantType(StringConstant(x)) => nameOverrides(idx) = x
                  case _                               =>
                }
            }
            val tTpe = normalizeTuple(tpe2.dealias)
            tTpe.asType match {
              case '[tt] =>
                val typeInfo =
                  if (isGenericTuple(tTpe)) new GenericTupleInfo[tt](tTpe)
                  else new ClassInfo[tt](tTpe)
                val ref = Ref(Symbol.requiredModule("scala.NamedTuple"))
                '{
                  new Schema(
                    reflect = new Reflect.Record[Binding, T](
                      fields = Vector(${ typeInfo.fields[T](nameOverrides) }*),
                      typeName = ${ toExpr(tpeName) },
                      recordBinding = new Binding.Record(
                        constructor = new Constructor {
                          def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                          def construct(in: Registers, baseOffset: RegisterOffset): T = ${
                            if (typeInfo.tpeTypeArgs.nonEmpty) typeInfo.constructor('in, 'baseOffset).asExprOf[T]
                            else Select.unique(ref, "Empty").asExprOf[T]
                          }
                        },
                        deconstructor = new Deconstructor {
                          def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                          def deconstruct(out: Registers, baseOffset: RegisterOffset, in: T): Unit = ${
                            val valDef = ValDef(
                              Symbol.newVal(Symbol.spliceOwner, "t", tTpe, Flags.EmptyFlags, Symbol.noSymbol),
                              new Some(
                                Apply(Select.unique(ref, "toTuple").appliedToTypes(tpe.typeArgs), List('in.asTerm))
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
          case _ => cannotDeriveSchema(tpe)
        }
      } else if (isNonAbstractScalaClass(tpe)) {
        val tpeName   = typeName(tpe)
        val classInfo = new ClassInfo(tpe)
        '{
          new Schema(
            reflect = new Reflect.Record[Binding, T](
              fields = Vector(${ classInfo.fields(Array.empty[String]) }*),
              typeName = ${ toExpr(tpeName) },
              recordBinding = new Binding.Record(
                constructor = new Constructor {
                  def usedRegisters: RegisterOffset = ${ classInfo.usedRegisters }

                  def construct(in: Registers, baseOffset: RegisterOffset): T = ${
                    classInfo.constructor('in, 'baseOffset)
                  }
                },
                deconstructor = new Deconstructor {
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
      } else if (isOpaque(tpe)) {
        val sTpe = opaqueDealias(tpe)
        sTpe.asType match {
          case '[s] =>
            val tpeName = typeName[s](tpe)
            val schema  = findImplicitOrDeriveSchema[s](sTpe)
            '{ new Schema($schema.reflect.typeName(${ toExpr(tpeName) })).asInstanceOf[Schema[T]] }
        }
      } else if (isZioPreludeNewtype(tpe)) {
        val sTpe = zioPreludeNewtypeDealias(tpe)
        sTpe.asType match {
          case '[s] =>
            val tpeName = typeName[s](tpe)
            val schema  = findImplicitOrDeriveSchema[s](sTpe)
            '{ new Schema($schema.reflect.typeName(${ toExpr(tpeName) })).asInstanceOf[Schema[T]] }
        }
      } else cannotDeriveSchema(tpe)
    }.asExprOf[Schema[T]]

    def cannotDeriveSchema(tpe: TypeRepr): Nothing = fail(s"Cannot derive schema for '${tpe.show}'.")

    val aTpe        = TypeRepr.of[A].dealias
    val schema      = aTpe.asType match { case '[a] => deriveSchema[a](aTpe) }
    val schemaBlock = Block(derivedSchemaDefs.toList, schema.asTerm).asExprOf[Schema[A]]
    // report.info(s"Generated schema:\n${schemaBlock.show}", Position.ofMacroExpansion)
    schemaBlock
  }
}
