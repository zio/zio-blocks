package zio.blocks.schema

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.quoted._
import zio.blocks.schema.{Term => SchemaTerm}
import zio.blocks.typeid.{DynamicTypeId, TypeId}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._
import zio.blocks.schema.CommonMacroOps

trait SchemaCompanionVersionSpecific extends TypeIdSchemaInstances {
  inline def derived[A]: Schema[A] = ${ SchemaCompanionVersionSpecificImpl.derived }
}

private object SchemaCompanionVersionSpecificImpl {
  def derived[A: Type](using Quotes): Expr[Schema[A]] = new SchemaCompanionVersionSpecificImpl().derived[A]
}

private class SchemaCompanionVersionSpecificImpl(using Quotes) {
  import quotes.reflect._

  private val intTpe                = defn.IntClass.typeRef
  private val floatTpe              = defn.FloatClass.typeRef
  private val longTpe               = defn.LongClass.typeRef
  private val doubleTpe             = defn.DoubleClass.typeRef
  private val booleanTpe            = defn.BooleanClass.typeRef
  private val byteTpe               = defn.ByteClass.typeRef
  private val charTpe               = defn.CharClass.typeRef
  private val shortTpe              = defn.ShortClass.typeRef
  private val unitTpe               = defn.UnitClass.typeRef
  private val anyRefTpe             = defn.AnyRefClass.typeRef
  private val anyTpe                = defn.AnyClass.typeRef
  private val stringTpe             = defn.StringClass.typeRef
  private val schemaTpe             = Symbol.requiredClass("zio.blocks.schema.Schema").typeRef
  private val tupleTpe              = Symbol.requiredClass("scala.Tuple").typeRef
  private val arrayClass            = defn.ArrayClass
  private val arrayOfAnyTpe         = arrayClass.typeRef.appliedTo(anyTpe)
  private val newArray              = Select(New(TypeIdent(arrayClass)), arrayClass.primaryConstructor)
  private val newArrayOfAny         = newArray.appliedToType(anyTpe)
  private val wildcard              = TypeBounds(defn.NothingClass.typeRef, anyTpe)
  private val arrayOfWildcardTpe    = arrayClass.typeRef.appliedTo(wildcard)
  private val iterableOfWildcardTpe = Symbol.requiredClass("scala.collection.Iterable").typeRef.appliedTo(wildcard)
  private val iteratorOfWildcardTpe = Symbol.requiredClass("scala.collection.Iterator").typeRef.appliedTo(wildcard)
  private val modifierReflectTpe    = Symbol.requiredClass("zio.blocks.schema.Modifier.Reflect").typeRef
  private val modifierTermTpe       = Symbol.requiredClass("zio.blocks.schema.Modifier.Term").typeRef
  private val modifierTransientTpe  = Symbol.requiredClass("zio.blocks.schema.Modifier.transient").typeRef
  private val iArrayOfAnyRefTpe     = TypeRepr.of[IArray[AnyRef]]
  private val fromIArrayMethod      = Select.unique(Ref(Symbol.requiredModule("scala.runtime.TupleXXL")), "fromIArray")
  private val asInstanceOfMethod    = anyTpe.typeSymbol.declaredMethod("asInstanceOf").head
  private val productElementMethod  = tupleTpe.typeSymbol.methodMember("productElement").head
  private lazy val toTupleMethod    = Select.unique(Ref(Symbol.requiredModule("scala.NamedTuple")), "toTuple")

  private def fail(msg: String): Nothing = CommonMacroOps.fail(msg)

  private def isEnumValue(tpe: TypeRepr): Boolean = tpe.termSymbol.flags.is(Flags.Enum)

  private def isEnumOrModuleValue(tpe: TypeRepr): Boolean = isEnumValue(tpe) || tpe.typeSymbol.flags.is(Flags.Module)

  private def isSealedTraitOrAbstractClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
    val flags = symbol.flags
    flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))
  }

  private def isOpaque(tpe: TypeRepr): Boolean = tpe.typeSymbol.flags.is(Flags.Opaque)

  private def opaqueDealias(tpe: TypeRepr): TypeRepr = {
    @tailrec
    def loop(tpe: TypeRepr): TypeRepr = tpe match {
      case trTpe: TypeRef =>
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

  private def isNewtype(tpe: TypeRepr): Boolean = tpe match {
    case TypeRef(compTpe, "Type") =>
      compTpe.baseClasses.exists { sym =>
        val name = sym.fullName
        name == "zio.prelude.Newtype" || name == "neotype.Newtype"
      }
    case _ =>
      val dealiased = tpe.dealias
      if (dealiased != tpe) isNewtype(dealiased)
      else false
  }

  private def newtypeDealias(tpe: TypeRepr): TypeRepr = tpe match {
    case TypeRef(compTpe, _) =>
      compTpe.baseClasses.find { sym =>
        val name = sym.fullName
        name == "zio.prelude.Newtype" || name == "neotype.Newtype"
      } match {
        case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
        case _         =>
          val dealiased = tpe.dealias
          if (dealiased != tpe) newtypeDealias(dealiased)
          else cannotDealiasNewtype(tpe)
      }
    case _ =>
      val dealiased = tpe.dealias
      if (dealiased != tpe) newtypeDealias(dealiased)
      else cannotDealiasNewtype(tpe)
  }

  private def cannotDealiasNewtype(tpe: TypeRepr): Nothing =
    fail(s"Cannot dealias newtype: ${tpe.show}.")

  private def isTypeRef(tpe: TypeRepr): Boolean = tpe match {
    case trTpe: TypeRef =>
      val typeSymbol = trTpe.typeSymbol
      typeSymbol.isTypeDef && typeSymbol.isAliasType
    case _ => false
  }

  private def typeRefDealias(tpe: TypeRepr): TypeRepr = tpe match {
    case trTpe: TypeRef =>
      val sTpe = trTpe.translucentSuperType.dealias
      if (sTpe == trTpe) cannotDealiasTypeRef(tpe)
      sTpe
    case _ => cannotDealiasTypeRef(tpe)
  }

  private def cannotDealiasTypeRef(tpe: TypeRepr): Nothing = fail(s"Cannot dealias type reference: ${tpe.show}.")

  @tailrec
  private def dealiasOnDemand(tpe: TypeRepr): TypeRepr = {
    val sTpe =
      if (isNewtype(tpe)) newtypeDealias(tpe)
      else if (isOpaque(tpe)) opaqueDealias(tpe)
      else if (isTypeRef(tpe)) typeRefDealias(tpe)
      else tpe

    if (sTpe =:= tpe) tpe else dealiasOnDemand(sTpe)
  }

  private def isUnion(tpe: TypeRepr): Boolean = CommonMacroOps.isUnion(tpe)

  private def allUnionTypes(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.allUnionTypes(tpe)

  private def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
    val flags = symbol.flags
    !(flags.is(Flags.Abstract) || flags.is(Flags.JavaDefined) || flags.is(Flags.Trait))
  }

  private def typeArgs(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.typeArgs(tpe)

  private def isGenericTuple(tpe: TypeRepr): Boolean = CommonMacroOps.isGenericTuple(tpe)

  private val genericTupleTypeArgsCache = new mutable.HashMap[TypeRepr, List[TypeRepr]]

  private def genericTupleTypeArgs(tpe: TypeRepr): List[TypeRepr] =
    genericTupleTypeArgsCache.getOrElseUpdate(tpe, CommonMacroOps.genericTupleTypeArgs(tpe))

  private def normalizeGenericTuple(tpe: TypeRepr): TypeRepr =
    CommonMacroOps.normalizeGenericTuple(genericTupleTypeArgs(tpe))

  private def isNamedTuple(tpe: TypeRepr): Boolean = tpe.dealias match {
    case AppliedType(ntTpe, _) =>
      val name = ntTpe.typeSymbol.fullName
      name == "scala.NamedTuple$.NamedTuple" || name.endsWith(".NamedTuple")
    case _ => false
  }

  private def isJavaTime(tpe: TypeRepr): Boolean = tpe.typeSymbol.fullName.startsWith("java.time.") &&
    (tpe <:< TypeRepr.of[java.time.temporal.Temporal] || tpe <:< TypeRepr.of[java.time.temporal.TemporalAmount])

  private def isIArray(tpe: TypeRepr): Boolean = tpe.typeSymbol.fullName == "scala.IArray$package$.IArray"

  private def isOption(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Option[?]]

  private def isCollection(tpe: TypeRepr): Boolean =
    tpe <:< iterableOfWildcardTpe || tpe <:< iteratorOfWildcardTpe || tpe <:< arrayOfWildcardTpe || isIArray(tpe)

  private def directSubTypes(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.directSubTypes(tpe)

  private val isNonRecursiveCache = new mutable.HashMap[TypeRepr, Boolean]

  private def isNonRecursive(tpe: TypeRepr, nestedTpes: List[TypeRepr] = Nil): Boolean = isNonRecursiveCache
    .getOrElseUpdate(
      tpe,
      tpe <:< intTpe || tpe <:< floatTpe || tpe <:< longTpe || tpe <:< doubleTpe || tpe <:< booleanTpe ||
        tpe <:< byteTpe || tpe <:< charTpe || tpe <:< shortTpe || tpe <:< unitTpe ||
        tpe <:< stringTpe || tpe <:< TypeRepr.of[BigDecimal] || tpe <:< TypeRepr.of[BigInt] || isJavaTime(tpe) ||
        tpe <:< TypeRepr.of[java.util.Currency] || tpe <:< TypeRepr.of[java.util.UUID] || isEnumOrModuleValue(tpe) ||
        tpe <:< TypeRepr.of[DynamicValue] || !nestedTpes.contains(tpe) && {
          val nestedTpes_ = tpe :: nestedTpes
          if (isOption(tpe) || tpe <:< TypeRepr.of[Either[?, ?]] || isCollection(tpe)) {
            typeArgs(tpe).forall(isNonRecursive(_, nestedTpes_))
          } else if (isGenericTuple(tpe)) {
            genericTupleTypeArgs(tpe).forall(isNonRecursive(_, nestedTpes_))
          } else if (isSealedTraitOrAbstractClass(tpe)) directSubTypes(tpe).forall(isNonRecursive(_, nestedTpes_))
          else if (isUnion(tpe)) allUnionTypes(tpe).forall(isNonRecursive(_, nestedTpes_))
          else if (isNamedTuple(tpe)) isNonRecursive(typeArgs(tpe).last, nestedTpes_)
          else if (isNonAbstractScalaClass(tpe)) {
            val (tpeTypeArgs, tpeTypeParams, tpeParams) = tpe.classSymbol.get.primaryConstructor.paramSymss match {
              case tps :: ps if tps.exists(_.isTypeParam) => (typeArgs(tpe), tps, ps)
              case ps                                     => (Nil, Nil, ps)
            }
            tpeParams.forall(_.forall { symbol =>
              var fTpe = tpe.memberType(symbol).dealias
              if (tpeTypeArgs ne Nil) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
              isNonRecursive(fTpe, nestedTpes_)
            })
          } else if (isOpaque(tpe)) {
            isNonRecursive(opaqueDealias(tpe), nestedTpes_)
          } else if (isNewtype(tpe)) {
            isNonRecursive(newtypeDealias(tpe), nestedTpes_)
          } else if (isTypeRef(tpe)) {
            isNonRecursive(typeRefDealias(tpe), nestedTpes_)
          } else false
        }
    )

  private def doc(tpe: TypeRepr)(using Quotes): Expr[Doc] = {
    if (isEnumValue(tpe)) tpe.termSymbol
    else tpe.typeSymbol
  }.docstring
    .fold('{ Doc.Empty })(s => '{ new Doc.Text(${ Expr(s) }) })
    .asInstanceOf[Expr[Doc]]

  private def modifiers(tpe: TypeRepr)(using Quotes): Expr[Seq[Modifier.Reflect]] = {
    var modifiers: List[Expr[Modifier.Reflect]] = Nil
    {
      if (isEnumValue(tpe)) tpe.termSymbol
      else tpe.typeSymbol
    }.annotations.foreach { annotation =>
      if (annotation.tpe <:< modifierReflectTpe) {
        modifiers = annotation.asExpr.asInstanceOf[Expr[Modifier.Reflect]] :: modifiers
      }
    }
    if (modifiers eq Nil) '{ Nil } else Varargs(modifiers)
  }

  private def summonClassTag[T: Type](using Quotes): Expr[ClassTag[T]] =
    Expr.summon[ClassTag[T]].getOrElse(fail(s"No ClassTag available for ${TypeRepr.of[T].show}"))

  private val schemaRefs = new mutable.HashMap[TypeRepr, Expr[Schema[?]]]
  private val schemaDefs = new mutable.ListBuffer[ValDef]

  private def findImplicitOrDeriveSchema[T: Type](tpe: TypeRepr): Expr[Schema[T]] = schemaRefs
    .getOrElse(
      tpe, {
        val schemaTpeApplied = schemaTpe.appliedTo(tpe)
        Implicits.search(schemaTpeApplied) match {
          case v: ImplicitSearchSuccess => v.tree.asExpr.asInstanceOf[Expr[Schema[?]]]
          case _                        =>
            val name  = s"s${schemaRefs.size}"
            val flags =
              if (isNonRecursive(tpe)) Flags.Implicit
              else Flags.Implicit | Flags.Lazy
            val symbol = Symbol.newVal(Symbol.spliceOwner, name, schemaTpeApplied, flags, Symbol.noSymbol)
            val ref    = Ref(symbol).asExpr.asInstanceOf[Expr[Schema[?]]]
            // adding the schema reference before its derivation to avoid an endless loop on recursive data structures
            schemaRefs.update(tpe, ref)
            implicit val quotes: Quotes = symbol.asQuotes
            val schema                  = deriveSchema(tpe)
            schemaDefs.addOne(ValDef(symbol, new Some(schema.asTerm)))
            ref
        }
      }
    )
    .asInstanceOf[Expr[Schema[T]]]

  private class FieldInfo(
    val name: String,
    val tpe: TypeRepr,
    val originalTpe: Option[TypeRepr],
    val defaultValue: Option[Term],
    val getter: Symbol,
    val usedRegisters: RegisterOffset,
    val modifiers: List[Term]
  )

  private abstract class TypeInfo[T: Type] {
    def tpeTypeArgs: List[TypeRepr]

    def usedRegisters: Expr[RegisterOffset]

    def fields[S: Type](nameOverrides: Array[String])(using Quotes): Expr[Seq[SchemaTerm[Binding, S, ?]]]

    def constructor(in: Expr[Registers], offset: Expr[RegisterOffset])(using Quotes): Expr[T]

    def deconstructor(out: Expr[Registers], offset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Term

    def fieldOffset(fTpe: TypeRepr): RegisterOffset = {
      val sTpe = dealiasOnDemand(fTpe)
      val res  =
        if (isOption(fTpe)) RegisterOffset(objects = 1)
        else if (sTpe <:< intTpe) RegisterOffset(ints = 1)
        else if (sTpe <:< floatTpe) RegisterOffset(floats = 1)
        else if (sTpe <:< longTpe) RegisterOffset(longs = 1)
        else if (sTpe <:< doubleTpe) {
          // println("DEBUG: Allocating DOUBLE register")
          RegisterOffset(doubles = 1)
        } else if (sTpe <:< booleanTpe) RegisterOffset(booleans = 1)
        else if (sTpe <:< byteTpe) RegisterOffset(bytes = 1)
        else if (sTpe <:< charTpe) RegisterOffset(chars = 1)
        else if (sTpe <:< shortTpe) RegisterOffset(shorts = 1)
        else if (sTpe <:< unitTpe) RegisterOffset.Zero
        else RegisterOffset(objects = 1)
      // println(s"DEBUG: register result: $res")
      res
    }

    def fieldConstructor(in: Expr[Registers], offset: Expr[RegisterOffset], fieldInfo: FieldInfo)(using
      Quotes
    ): Term = {
      val fTpe          = fieldInfo.tpe
      val usedRegisters = Expr(fieldInfo.usedRegisters)
      fTpe.asType match {
        case '[ft] =>
          if (isOption(fTpe)) '{ $in.getObject($offset + $usedRegisters).asInstanceOf[ft] }
          else if (fTpe =:= intTpe) '{ $in.getInt($offset + $usedRegisters).asInstanceOf[ft] }
          else if (fTpe =:= floatTpe) '{ $in.getFloat($offset + $usedRegisters).asInstanceOf[ft] }
          else if (fTpe =:= longTpe) '{ $in.getLong($offset + $usedRegisters).asInstanceOf[ft] }
          else if (fTpe =:= doubleTpe) '{ $in.getDouble($offset + $usedRegisters).asInstanceOf[ft] }
          else if (fTpe =:= booleanTpe) '{ $in.getBoolean($offset + $usedRegisters).asInstanceOf[ft] }
          else if (fTpe =:= byteTpe) '{ $in.getByte($offset + $usedRegisters).asInstanceOf[ft] }
          else if (fTpe =:= charTpe) '{ $in.getChar($offset + $usedRegisters).asInstanceOf[ft] }
          else if (fTpe =:= shortTpe) '{ $in.getShort($offset + $usedRegisters).asInstanceOf[ft] }
          else if (fTpe =:= unitTpe) '{ ().asInstanceOf[ft] }
          else {
            val sTpe = dealiasOnDemand(fTpe)
            if (sTpe <:< intTpe) '{ $in.getInt($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< floatTpe) '{ $in.getFloat($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< longTpe) '{ $in.getLong($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< doubleTpe) '{ $in.getDouble($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< booleanTpe) '{ $in.getBoolean($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< byteTpe) '{ $in.getByte($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< charTpe) '{ $in.getChar($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< shortTpe) '{ $in.getShort($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< unitTpe) '{ ().asInstanceOf[ft] }
            else '{ $in.getObject($offset + $usedRegisters).asInstanceOf[ft] }
          }
      }
    }.asTerm

    def toBlock(terms: List[Term]): Term = {
      val size = terms.size
      if (size > 1) Block(terms.init, terms.last)
      else if (size > 0) terms.head
      else Literal(UnitConstant())
    }
  }

  private class ClassInfo[T: Type](tpe: TypeRepr) extends TypeInfo {
    private val tpeClassSymbol     = tpe.classSymbol.get
    private val primaryConstructor = tpeClassSymbol.primaryConstructor
    // caching of expensive calls of field and method member gathering
    private var fieldMembers: List[Symbol]                                       = null
    private var methodMembers: List[Symbol]                                      = null
    private var companionRefAndClass: (Ref, Symbol)                              = null
    val tpeTypeArgs: List[TypeRepr]                                              = typeArgs(tpe)
    val (fieldInfos: List[List[FieldInfo]], usedRegisters: Expr[RegisterOffset]) = {
      val (tpeTypeParams, tpeParams) = primaryConstructor.paramSymss match {
        case tps :: ps if tps.exists(_.isTypeParam) => (tps, ps)
        case ps                                     => (Nil, ps)
      }
      val caseFields    = tpeClassSymbol.caseFields
      var usedRegisters = RegisterOffset.Zero
      var idx           = 0
      (
        tpeParams.map(_.map { symbol =>
          idx += 1
          var fTpe    = tpe.memberType(symbol)
          val treeTpe = symbol.tree match {
            case valDef: ValDef => valDef.tpt.tpe
            case _              => TypeRepr.of[Nothing]
          }
          if (treeTpe.typeSymbol.isAliasType && fTpe =:= treeTpe) {
            fTpe = treeTpe
          }
          if (tpeTypeArgs ne Nil) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
          val name   = symbol.name
          val getter = caseFields
            .find(_.name == name)
            .orElse {
              if (fieldMembers eq null) fieldMembers = tpeClassSymbol.fieldMembers
              fieldMembers.find(_.name == name)
            }
            .orElse {
              if (methodMembers eq null) methodMembers = tpeClassSymbol.methodMembers
              methodMembers.find(member => member.name == name && member.flags.is(Flags.FieldAccessor))
            }
            .getOrElse(Symbol.noSymbol)
          if (!getter.exists || getter.flags.is(Flags.PrivateLocal)) fail {
            s"Field or getter '$name' of '${tpe.show}' should be defined as 'val' or 'var' in the primary constructor."
          }
          var modifiers: List[Term] = Nil
          getter.annotations.foreach { annotation =>
            val aTpe = annotation.tpe
            if (aTpe <:< modifierTermTpe) modifiers = annotation :: modifiers
          }
          val defaultValue = if (symbol.flags.is(Flags.HasDefault)) {
            if (companionRefAndClass eq null) {
              val tpeTypeSymbol = tpe.typeSymbol
              companionRefAndClass = (Ref(tpeTypeSymbol.companionModule), tpeTypeSymbol.companionClass)
            }
            val dvMethod        = companionRefAndClass._2.declaredMethod("$lessinit$greater$default$" + idx).head
            val dvSelectNoTypes = Select(companionRefAndClass._1, dvMethod)
            new Some(dvMethod.paramSymss match {
              case Nil                                          => dvSelectNoTypes
              case List(params) if params.exists(_.isTypeParam) => dvSelectNoTypes.appliedToTypes(tpeTypeArgs)
              case _                                            =>
                fail {
                  s"Default values of non-first parameter lists are not supported for '$symbol' in class '${tpe.show}'."
                }
            })
          } else {
            if (modifiers.exists(_.tpe <:< modifierTransientTpe) && !isOption(fTpe) && !isCollection(fTpe)) {
              fail(s"Missing default value for transient field '$name' in '${tpe.show}'")
            }
            None
          }
          val origTpe = symbol.tree match {
            case valDef: ValDef =>
              val t   = valDef.tpt.tpe
              val sym = t.typeSymbol
              // Only capture user-defined type aliases, not scala stdlib
              if (
                sym.exists && sym.isAliasType && !sym.fullName.startsWith("scala.") && !sym.fullName.startsWith("java.")
              ) {
                Some(t)
              } else None
            case _ => None
          }
          val fieldInfo = new FieldInfo(name, fTpe, origTpe, defaultValue, getter, usedRegisters, modifiers)
          usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe))
          fieldInfo
        }),
        Expr(usedRegisters)
      )
    }

    def fields[S: Type](nameOverrides: Array[String])(using Quotes): Expr[Seq[SchemaTerm[Binding, S, ?]]] = {
      var idx = -1
      Varargs(fieldInfos.flatMap(_.map { fieldInfo =>
        val fTpe = fieldInfo.tpe
        fTpe.asType match {
          case '[ft] =>
            idx += 1
            val baseSchema = findImplicitOrDeriveSchema[ft](fTpe)
            val schema     = fieldInfo.originalTpe match {
              case Some(origTpe) =>
                val tpeId = makeTypeId(origTpe)
                '{ new Schema($baseSchema.reflect.typeId($tpeId.asInstanceOf[TypeId[ft]])) }
              case None => baseSchema
            }
            val isNonRec = isNonRecursive(fTpe)
            val name     = Expr {
              if (idx < nameOverrides.length) nameOverrides(idx)
              else fieldInfo.name
            }
            val modifiers = fieldInfo.modifiers
            lazy val ms   = Varargs(modifiers.map(_.asExpr.asInstanceOf[Expr[Modifier.Term]]))
            fieldInfo.defaultValue match {
              case Some(dvSelect) =>
                val dv = dvSelect.asExpr.asInstanceOf[Expr[ft]]
                if (modifiers eq Nil) {
                  '{ new Reflect.Deferred(() => $schema.reflect).defaultValue($dv).asTerm[S]($name) }
                } else {
                  '{
                    new Reflect.Deferred(() => $schema.reflect)
                      .defaultValue($dv)
                      .asTerm[S]($name)
                      .copy(modifiers = $ms)
                  }
                }
              case _ =>
                if (modifiers eq Nil) {
                  if (isNonRec) '{ $schema.reflect.asTerm[S]($name) }
                  else '{ new Reflect.Deferred(() => $schema.reflect).asTerm[S]($name) }
                } else {
                  if (isNonRec) '{ $schema.reflect.asTerm[S]($name).copy(modifiers = $ms) }
                  else '{ new Reflect.Deferred(() => $schema.reflect).asTerm[S]($name).copy(modifiers = $ms) }
                }
            }
        }
      }))
    }

    def constructor(in: Expr[Registers], offset: Expr[RegisterOffset])(using Quotes): Expr[T] = {
      val constructor = Select(New(Inferred(tpe)), primaryConstructor).appliedToTypes(tpeTypeArgs)
      val argss       = fieldInfos.map(_.map(fieldConstructor(in, offset, _)))
      argss.tail.foldLeft(Apply(constructor, argss.head))(Apply(_, _)).asExpr.asInstanceOf[Expr[T]]
    }

    def deconstructor(out: Expr[Registers], offset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Term =
      toBlock(fieldInfos.flatMap(_.map { fieldInfo =>
        val fTpe          = fieldInfo.tpe
        val getter        = Select(in.asTerm, fieldInfo.getter).asExpr
        val usedRegisters = Expr(fieldInfo.usedRegisters)
        val expr          = fTpe.asType match {
          case '[ft] => {
            val _ = Type.of[ft]
            if (isOption(fTpe)) '{
              $out.setObject($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[AnyRef]] })
            }
            else if (fTpe <:< intTpe) '{ $out.setInt($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Int]] }) }
            else if (fTpe <:< floatTpe) {
              '{ $out.setFloat($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Float]] }) }
            } else if (fTpe <:< longTpe) {
              '{ $out.setLong($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Long]] }) }
            } else if (fTpe <:< doubleTpe) {
              '{ $out.setDouble($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Double]] }) }
            } else if (fTpe <:< booleanTpe) {
              '{ $out.setBoolean($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Boolean]] }) }
            } else if (fTpe <:< byteTpe) {
              '{ $out.setByte($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Byte]] }) }
            } else if (fTpe <:< charTpe) {
              '{ $out.setChar($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Char]] }) }
            } else if (fTpe <:< shortTpe) {
              '{ $out.setShort($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Short]] }) }
            } else if (fTpe <:< unitTpe) '{ () }
            else if (fTpe <:< anyRefTpe) {
              '{ $out.setObject($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[AnyRef]] }) }
            } else {
              val sTpe = dealiasOnDemand(fTpe)
              if (sTpe <:< intTpe) '{ $out.setInt($offset + $usedRegisters, $getter.asInstanceOf[Int]) }
              else if (sTpe <:< floatTpe) '{ $out.setFloat($offset + $usedRegisters, $getter.asInstanceOf[Float]) }
              else if (sTpe <:< longTpe) '{ $out.setLong($offset + $usedRegisters, $getter.asInstanceOf[Long]) }
              else if (sTpe <:< doubleTpe) {
                '{ $out.setDouble($offset + $usedRegisters, $getter.asInstanceOf[Double]) }
              } else if (sTpe <:< booleanTpe) {
                '{ $out.setBoolean($offset + $usedRegisters, $getter.asInstanceOf[Boolean]) }
              } else if (sTpe <:< byteTpe) '{ $out.setByte($offset + $usedRegisters, $getter.asInstanceOf[Byte]) }
              else if (sTpe <:< charTpe) '{ $out.setChar($offset + $usedRegisters, $getter.asInstanceOf[Char]) }
              else if (sTpe <:< shortTpe) '{ $out.setShort($offset + $usedRegisters, $getter.asInstanceOf[Short]) }
              else if (sTpe <:< unitTpe) '{ () }
              else '{ $out.setObject($offset + $usedRegisters, $getter.asInstanceOf[AnyRef]) }
            }
          }
        }
        val t = expr.asTerm
        t
      }))
  }

  private class GenericTupleInfo[T: Type](tpe: TypeRepr) extends TypeInfo {
    val tpeTypeArgs: List[TypeRepr]                                        = genericTupleTypeArgs(tpe)
    val (fieldInfos: List[FieldInfo], usedRegisters: Expr[RegisterOffset]) = {
      var usedRegisters = RegisterOffset.Zero
      (
        tpeTypeArgs.map {
          var idx = 0
          fTpe =>
            idx += 1
            val fieldInfo = new FieldInfo(s"_$idx", fTpe, None, None, Symbol.noSymbol, usedRegisters, Nil)
            usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe))
            fieldInfo
        },
        Expr(usedRegisters)
      )
    }

    def fields[S: Type](nameOverrides: Array[String])(using Quotes): Expr[Seq[SchemaTerm[Binding, S, ?]]] =
      Varargs(fieldInfos.map {
        var idx = -1
        fieldInfo =>
          idx += 1
          val fTpe = fieldInfo.tpe
          fTpe.asType match {
            case '[ft] =>
              val schema = findImplicitOrDeriveSchema[ft](fTpe)
              val name   = Expr {
                if (idx < nameOverrides.length) nameOverrides(idx)
                else fieldInfo.name
              }
              if (isNonRecursive(fTpe)) '{ $schema.reflect.asTerm[S]($name) }
              else '{ new Reflect.Deferred(() => $schema.reflect).asTerm[S]($name) }
          }
      })

    def constructor(in: Expr[Registers], offset: Expr[RegisterOffset])(using Quotes): Expr[T] =
      (if (fieldInfos eq Nil) Expr(EmptyTuple)
       else {
         val symbol      = Symbol.newVal(Symbol.spliceOwner, "xs", arrayOfAnyTpe, Flags.EmptyFlags, Symbol.noSymbol)
         val ref         = Ref(symbol)
         val update      = Select(ref, defn.Array_update)
         val assignments = fieldInfos.map {
           var idx = -1
           fieldInfo =>
             idx += 1
             Apply(update, List(Literal(IntConstant(idx)), fieldConstructor(in, offset, fieldInfo)))
         }
         val valDef   = ValDef(symbol, new Some(Apply(newArrayOfAny, List(Literal(IntConstant(fieldInfos.size))))))
         val block    = Block(valDef :: assignments, ref)
         val typeCast = Select(block, asInstanceOfMethod).appliedToType(iArrayOfAnyRefTpe)
         Select(Apply(fromIArrayMethod, List(typeCast)), asInstanceOfMethod).appliedToType(tpe).asExpr
       }).asInstanceOf[Expr[T]]

    def deconstructor(out: Expr[Registers], offset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Term =
      toBlock(fieldInfos.map {
        val productElement = Select(in.asTerm, productElementMethod)
        var idx            = -1
        fieldInfo =>
          idx += 1
          val fTpe          = fieldInfo.tpe
          val sTpe          = dealiasOnDemand(fTpe)
          val getter        = productElement.appliedTo(Literal(IntConstant(idx))).asExpr
          val usedRegisters = Expr(fieldInfo.usedRegisters)
          {
            if (sTpe <:< intTpe) '{ $out.setInt($offset + $usedRegisters, $getter.asInstanceOf[Int]) }
            else if (sTpe <:< floatTpe) '{ $out.setFloat($offset + $usedRegisters, $getter.asInstanceOf[Float]) }
            else if (sTpe <:< longTpe) '{ $out.setLong($offset + $usedRegisters, $getter.asInstanceOf[Long]) }
            else if (sTpe <:< doubleTpe) '{ $out.setDouble($offset + $usedRegisters, $getter.asInstanceOf[Double]) }
            else if (sTpe <:< booleanTpe) {
              '{ $out.setBoolean($offset + $usedRegisters, $getter.asInstanceOf[Boolean]) }
            } else if (sTpe <:< byteTpe) '{ $out.setByte($offset + $usedRegisters, $getter.asInstanceOf[Byte]) }
            else if (sTpe <:< charTpe) '{ $out.setChar($offset + $usedRegisters, $getter.asInstanceOf[Char]) }
            else if (sTpe <:< shortTpe) '{ $out.setShort($offset + $usedRegisters, $getter.asInstanceOf[Short]) }
            else if (sTpe <:< unitTpe) '{ () }
            else '{ $out.setObject($offset + $usedRegisters, $getter.asInstanceOf[AnyRef]) }
          }.asTerm
      })
  }

  private def deriveSchema[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
    val tpeId      = '{ TypeId.from[T] }
    val baseSchema = {
      if (isEnumOrModuleValue(tpe)) {
        deriveSchemaForEnumOrModuleValue(tpe)
      } else if (isCollection(tpe)) {
        if (tpe <:< arrayOfWildcardTpe) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match {
            case '[et] =>
              val schema   = findImplicitOrDeriveSchema[et](eTpe)
              val classTag = summonClassTag[et]
              val tpeId    = '{ TypeId.from[Array[et]] }
              '{
                implicit val ct: ClassTag[et] = $classTag
                new Schema(
                  reflect = new Reflect.Sequence(
                    element = $schema.reflect,
                    typeId = $tpeId,
                    seqBinding = new Binding.Seq(
                      constructor = new SeqConstructor.ArrayConstructor {
                        def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                          new Builder(new Array[et](Math.max(sizeHint, 1)).asInstanceOf[Array[B]], 0)

                        def addObject[B](builder: ObjectBuilder[B], a: B): Unit = {
                          val item = a.asInstanceOf[et]
                          $schema.reflect.asWrapperUnknown.foreach { primitive =>
                            primitive.wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[Any, Any]].wrap(item) match {
                              case Left(error) => throw new IllegalArgumentException(error)
                              case Right(_)    => ()
                            }
                          }
                          var buf = builder.buffer
                          val idx = builder.size
                          if (buf.length == idx) {
                            val xs     = buf.asInstanceOf[Array[et]]
                            val newLen = idx << 1
                            buf = ${ genArraysCopyOf[et](eTpe, 'xs, 'newLen) }.asInstanceOf[Array[B]]
                            builder.buffer = buf
                          }
                          buf(idx) = a
                          builder.size = idx + 1
                        }

                        def resultObject[B](builder: ObjectBuilder[B]): Array[B] = {
                          val buf  = builder.buffer
                          val size = builder.size
                          if (buf.length == size) buf
                          else {
                            val xs = buf.asInstanceOf[Array[et]]
                            ${ genArraysCopyOf[et](eTpe, 'xs, 'size) }.asInstanceOf[Array[B]]
                          }
                        }

                        def emptyObject[B]: Array[B] = Array.empty[et].asInstanceOf[Array[B]]
                      },
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
              val schema   = findImplicitOrDeriveSchema[et](eTpe)
              val classTag = summonClassTag[et]
              val tpeId    = '{ TypeId.from[IArray[et]] }
              '{
                implicit val ct: ClassTag[et] = $classTag
                new Schema(
                  reflect = new Reflect.Sequence(
                    element = $schema.reflect,
                    typeId = $tpeId,
                    seqBinding = new Binding.Seq(
                      constructor = new SeqConstructor.IArrayConstructor {
                        def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                          new Builder(new Array[et](Math.max(sizeHint, 1)).asInstanceOf[Array[B]], 0)

                        def addObject[B](builder: ObjectBuilder[B], a: B): Unit = {
                          val item = a.asInstanceOf[et]
                          $schema.reflect.asWrapperUnknown.foreach { primitive =>
                            primitive.wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[Any, Any]].wrap(item) match {
                              case Left(error) => throw new IllegalArgumentException(error)
                              case Right(_)    => ()
                            }
                          }
                          var buf = builder.buffer
                          val idx = builder.size
                          if (buf.length == idx) {
                            val xs     = buf.asInstanceOf[Array[et]]
                            val newLen = idx << 1
                            buf = ${ genArraysCopyOf[et](eTpe, 'xs, 'newLen) }.asInstanceOf[Array[B]]
                            builder.buffer = buf
                          }
                          buf(idx) = a
                          builder.size = idx + 1
                        }

                        def resultObject[B](builder: ObjectBuilder[B]): IArray[B] = IArray.unsafeFromArray {
                          val buf  = builder.buffer
                          val size = builder.size
                          if (buf.length == size) buf
                          else {
                            val xs = buf.asInstanceOf[Array[et]]
                            ${ genArraysCopyOf[et](eTpe, 'xs, 'size) }.asInstanceOf[Array[B]]
                          }
                        }

                        def emptyObject[B]: IArray[B] = IArray.empty[et].asInstanceOf[IArray[B]]
                      },
                      deconstructor = SeqDeconstructor.iArrayDeconstructor
                    )
                  )
                )
              }
          }
        } else if (tpe <:< TypeRepr.of[ArraySeq[?]]) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match {
            case '[et] =>
              val schema   = findImplicitOrDeriveSchema[et](eTpe)
              val classTag = summonClassTag[et]
              val tpeId    = '{ TypeId.from[ArraySeq[et]] }
              '{
                implicit val ct: ClassTag[et] = $classTag
                new Schema(
                  reflect = new Reflect.Sequence(
                    element = $schema.reflect,
                    typeId = $tpeId,
                    seqBinding = new Binding.Seq(
                      constructor = new SeqConstructor.ArraySeqConstructor {
                        def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                          new Builder(new Array[et](Math.max(sizeHint, 1)).asInstanceOf[Array[B]], 0)

                        def addObject[B](builder: ObjectBuilder[B], a: B): Unit = {
                          val item = a.asInstanceOf[et]
                          $schema.reflect.asWrapperUnknown.foreach { primitive =>
                            primitive.wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[Any, Any]].wrap(item) match {
                              case Left(error) => throw new IllegalArgumentException(error)
                              case Right(_)    => ()
                            }
                          }
                          var buf = builder.buffer
                          val idx = builder.size
                          if (buf.length == idx) {
                            val xs     = buf.asInstanceOf[Array[et]]
                            val newLen = idx << 1
                            buf = ${ genArraysCopyOf[et](eTpe, 'xs, 'newLen) }.asInstanceOf[Array[B]]
                            builder.buffer = buf
                          }
                          buf(idx) = a
                          builder.size = idx + 1
                        }

                        def resultObject[B](builder: ObjectBuilder[B]): ArraySeq[B] = ArraySeq.unsafeWrapArray {
                          val buf  = builder.buffer
                          val size = builder.size
                          if (buf.length == size) buf
                          else {
                            val xs = buf.asInstanceOf[Array[et]]
                            ${ genArraysCopyOf[et](eTpe, 'xs, 'size) }.asInstanceOf[Array[B]]
                          }
                        }

                        def emptyObject[B]: ArraySeq[B] = ArraySeq.empty[et].asInstanceOf[ArraySeq[B]]
                      },
                      deconstructor = SeqDeconstructor.arraySeqDeconstructor
                    )
                  )
                )
              }
          }
        } else if (tpe <:< TypeRepr.of[List[?]]) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match {
            case '[et] =>
              val schema = findImplicitOrDeriveSchema[et](eTpe)
              '{ Schema.list($schema) }
          }
        } else if (tpe <:< TypeRepr.of[Map[?, ?]]) {
          val tpeTypeArgs = typeArgs(tpe)
          val kTpe        = tpeTypeArgs.head
          val vTpe        = tpeTypeArgs.last
          kTpe.asType match {
            case '[kt] =>
              vTpe.asType match {
                case '[vt] =>
                  val kSchema = findImplicitOrDeriveSchema[kt](kTpe)
                  val vSchema = findImplicitOrDeriveSchema[vt](vTpe)
                  '{ Schema.map($kSchema, $vSchema) }
              }
          }
        } else if (tpe <:< TypeRepr.of[Set[?]]) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match {
            case '[et] =>
              val schema = findImplicitOrDeriveSchema[et](eTpe)
              '{ Schema.set($schema) }
          }
        } else if (tpe <:< TypeRepr.of[Vector[?]]) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match {
            case '[et] =>
              val schema = findImplicitOrDeriveSchema[et](eTpe)
              '{ Schema.vector($schema) }
          }
        } else if (tpe <:< TypeRepr.of[IndexedSeq[?]]) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match {
            case '[et] =>
              val schema = findImplicitOrDeriveSchema[et](eTpe)
              '{ Schema.indexedSeq($schema) }
          }
        } else if (tpe <:< TypeRepr.of[Seq[?]]) {
          val eTpe = typeArgs(tpe).head
          eTpe.asType match {
            case '[et] =>
              val schema = findImplicitOrDeriveSchema[et](eTpe)
              '{ Schema.seq($schema) }
          }
        } else cannotDeriveSchema(tpe)
      } else if (isGenericTuple(tpe)) {
        val tTpe = normalizeGenericTuple(tpe)
        tTpe.asType match {
          case '[tt] =>
            val typeInfo =
              if (isGenericTuple(tTpe)) new GenericTupleInfo[tt](tTpe)
              else new ClassInfo[tt](tTpe)
            val fields       = typeInfo.fields[tt](Array.empty[String])
            val tpeIdUntyped = makeTypeId(tTpe)
            val tpeId        = '{ $tpeIdUntyped.asInstanceOf[TypeId[tt]] }
            '{
              new Schema(
                reflect = new Reflect.Record[Binding, tt](
                  fields = Vector($fields*),
                  typeId = $tpeId,
                  recordBinding = new Binding.Record(
                    constructor = new Constructor[tt] {
                      def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                      def construct(in: Registers, offset: RegisterOffset): tt = ${
                        typeInfo.constructor('in, 'offset)
                      }
                    },
                    deconstructor = new Deconstructor[tt] {
                      def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                      def deconstruct(out: Registers, offset: RegisterOffset, in: tt): Unit = ${
                        typeInfo.deconstructor('out, 'offset, 'in).asExpr
                      }
                    }
                  )
                )
              )
            }
        }
      } else if (isOption(tpe)) {
        if (tpe <:< TypeRepr.of[None.type]) deriveSchemaForEnumOrModuleValue(tpe)
        else if (tpe <:< TypeRepr.of[Some[?]]) deriveSchemaForNonAbstractScalaClass(tpe)
        else {
          val vTpe = typeArgs(tpe).head
          if (vTpe =:= intTpe) '{ Schema.optionInt }
          else if (vTpe =:= floatTpe) '{ Schema.optionFloat }
          else if (vTpe =:= longTpe) '{ Schema.optionLong }
          else if (vTpe =:= doubleTpe) '{ Schema.optionDouble }
          else if (vTpe =:= booleanTpe) '{ Schema.optionBoolean }
          else if (vTpe =:= byteTpe) '{ Schema.optionByte }
          else if (vTpe =:= charTpe) '{ Schema.optionChar }
          else if (vTpe =:= shortTpe) '{ Schema.optionShort }
          else if (vTpe =:= unitTpe) '{ Schema.optionUnit }
          else if (vTpe <:< anyRefTpe && !isOpaque(vTpe) && !isNewtype(vTpe)) {
            vTpe.asType match {
              case '[vt] =>
                val schema = findImplicitOrDeriveSchema[vt & AnyRef](vTpe)
                '{ Schema.option($schema) }
            }
          } else deriveSchemaForSealedTraitOrAbstractClassOrUnion(tpe)
        }
      } else if (isSealedTraitOrAbstractClass(tpe) || isUnion(tpe)) {
        deriveSchemaForSealedTraitOrAbstractClassOrUnion(tpe)
      } else if (isNamedTuple(tpe)) {
        // Use tpe.dealias to get NamedTuple[N, V] type args even for type aliases
        val tpeTypeArgs = typeArgs(tpe.dealias)
        val nTpe        = tpeTypeArgs.head
        var tTpe        = tpeTypeArgs.last
        val nTypeArgs   =
          if (isGenericTuple(nTpe)) genericTupleTypeArgs(nTpe)
          else typeArgs(nTpe)
        val nameOverrides = new Array[String](nTypeArgs.size)
        var idx           = -1
        nTypeArgs.foreach { case ConstantType(StringConstant(str)) =>
          idx += 1
          nameOverrides(idx) = str
        }
        if (isGenericTuple(tTpe)) tTpe = normalizeGenericTuple(tTpe)
        tTpe.asType match {
          case '[tt] =>
            val typeInfo =
              if (isGenericTuple(tTpe)) new GenericTupleInfo[tt](tTpe)
              else new ClassInfo[tt](tTpe)
            val fields = typeInfo.fields[T](nameOverrides)
            // Use simple TypeId for base NamedTuple as per maintainer requirement
            // All NamedTuple variants should have the same base typeId
            // Construct manually since NamedTuple is not a regular type we can reference with TypeId.of
            val tpeId = '{
              zio.blocks.typeid
                .TypeId[Any](
                  zio.blocks.typeid.DynamicTypeId(
                    owner = zio.blocks.typeid.Owner(
                      List(
                        zio.blocks.typeid.Owner.Package("scala"),
                        zio.blocks.typeid.Owner.Type("NamedTuple")
                      )
                    ),
                    name = "NamedTuple",
                    typeParams = Nil,
                    kind = zio.blocks.typeid.TypeDefKind.Trait(isSealed = false, knownSubtypes = Nil),
                    parents = Nil,
                    args = Nil,
                    annotations = Nil
                  )
                )
                .asInstanceOf[zio.blocks.typeid.TypeId[T]]
            }
            '{
              new Schema(
                reflect = new Reflect.Record[Binding, T](
                  fields = Vector($fields*),
                  typeId = $tpeId,
                  recordBinding = new Binding.Record(
                    constructor = new Constructor {
                      def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                      def construct(in: Registers, offset: RegisterOffset): T = ${
                        typeInfo.constructor('in, 'offset).asInstanceOf[Expr[T]]
                      }
                    },
                    deconstructor = new Deconstructor {
                      def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                      def deconstruct(out: Registers, offset: RegisterOffset, in: T): Unit = ${
                        val value  = Apply(toTupleMethod.appliedToTypes(tpe.dealias.typeArgs), List('in.asTerm))
                        val symbol = Symbol.newVal(Symbol.spliceOwner, "t", tTpe, Flags.EmptyFlags, Symbol.noSymbol)
                        val valDef = ValDef(symbol, new Some(value))
                        val expr   = Ref(symbol).asExpr.asInstanceOf[Expr[tt]]
                        Block(List(valDef), typeInfo.deconstructor('out, 'offset, expr)).asExpr
                      }
                    }
                  )
                )
              )
            }
        }
      } else if (isNonAbstractScalaClass(tpe)) {
        deriveSchemaForNonAbstractScalaClass(tpe)
      } else if (isTypeRef(tpe)) {
        val sTpe = typeRefDealias(tpe)
        sTpe.asType match {
          case '[s] =>
            val schema = deriveSchema[s](sTpe)
            val tpeId  = makeTypeId(tpe)
            '{ new Schema($schema.reflect.typeId($tpeId.asInstanceOf[TypeId[s]])).asInstanceOf[Schema[T]] }
        }
      } else if (isOpaque(tpe)) {
        val sTpe = opaqueDealias(tpe)
        sTpe.asType match {
          case '[s] =>
            val schema = findImplicitOrDeriveSchema[s](sTpe)
            val tpeId  = makeTypeId(tpe)
            '{ new Schema($schema.reflect.typeId($tpeId.asInstanceOf[TypeId[s]])).asInstanceOf[Schema[T]] }
        }
      } else if (isNewtype(tpe)) {
        val sTpe = newtypeDealias(tpe)
        sTpe.asType match {
          case '[s] =>
            val schema = findImplicitOrDeriveSchema[s](sTpe)
            val tpeId  = makeTypeId(tpe)
            '{ new Schema($schema.reflect.typeId($tpeId.asInstanceOf[TypeId[s]])).asInstanceOf[Schema[T]] }
        }
      } else cannotDeriveSchema(tpe)
    }.asInstanceOf[Expr[Schema[T]]]
    // For generic tuples and newtypes, the TypeId is already correctly set
    // Don't override it with the original type's TypeId
    if (isGenericTuple(tpe) || isNewtype(tpe)) baseSchema
    else '{ new Schema($baseSchema.reflect.typeId($tpeId)).asInstanceOf[Schema[T]] }
  }

  private def makeTypeId(tpe: TypeRepr): Expr[TypeId[?]] = {
    // For ZIO Prelude newtypes/subtypes, return the base class TypeId (e.g., zio.prelude.Subtype.Type)
    // For neotype, return the concrete wrapper TypeId (they want the wrapper name, not the base class)
    def getZioPreludeBaseClass(t: TypeRepr): Option[Symbol] = t match {
      case TypeRef(compTpe, "Type") =>
        val classes = compTpe.baseClasses
        // Only handle ZIO Prelude types - neotype expects concrete wrapper names
        val zioPreludePriorities = List(
          "zio.prelude.Subtype",
          "zio.prelude.NewtypeCustom",
          "zio.prelude.Newtype"
        )
        zioPreludePriorities.flatMap(p => classes.find(_.fullName == p)).headOption
      case _ =>
        val dealiased = t.dealias
        if (dealiased != t) getZioPreludeBaseClass(dealiased)
        else None
    }

    def makeKind(sym: Symbol): Expr[zio.blocks.typeid.TypeDefKind] =
      if (sym.flags.is(Flags.Trait)) '{ zio.blocks.typeid.TypeDefKind.Trait() }
      else if (sym.flags.is(Flags.Case)) '{ zio.blocks.typeid.TypeDefKind.Class(isCase = true) }
      else if (sym.flags.is(Flags.Module)) '{ zio.blocks.typeid.TypeDefKind.Object }
      else '{ zio.blocks.typeid.TypeDefKind.Class() }

    def makeTypeParam(sym: Symbol): Expr[zio.blocks.typeid.TypeParam] = {
      val name     = sym.name
      val variance =
        if (sym.flags.is(Flags.Covariant)) '{ zio.blocks.typeid.Variance.Covariant }
        else if (sym.flags.is(Flags.Contravariant)) '{ zio.blocks.typeid.Variance.Contravariant }
        else '{ zio.blocks.typeid.Variance.Invariant }
      '{ zio.blocks.typeid.TypeParam(${ Expr(name) }, 0, $variance) }
    }

    def makeTypeReprExpr(t: TypeRepr): Expr[zio.blocks.typeid.TypeRepr] =
      if (t =:= TypeRepr.of[Any]) '{ zio.blocks.typeid.TypeRepr.AnyType }
      else if (t =:= TypeRepr.of[Nothing]) '{ zio.blocks.typeid.TypeRepr.NothingType }
      else {
        t.dealias match {
          case tr: TypeRef =>
            val sym = tr.typeSymbol
            if (sym == defn.AnyClass) '{ zio.blocks.typeid.TypeRepr.AnyType }
            else if (sym == defn.NothingClass) '{ zio.blocks.typeid.TypeRepr.NothingType }
            else {
              val id = makeDynamicTypeId(sym)
              '{ zio.blocks.typeid.TypeRepr.Ref($id, Nil) }
            }
          case AppliedType(base, args) =>
            base match {
              case tr: TypeRef =>
                val id       = makeDynamicTypeId(tr.typeSymbol)
                val argsExpr = Expr.ofList(args.map(makeTypeReprExpr))
                '{ zio.blocks.typeid.TypeRepr.Ref($id, $argsExpr) }
              case _ =>
                '{ zio.blocks.typeid.TypeRepr.Wildcard(zio.blocks.typeid.TypeBounds.empty) }
            }
          case _ =>
            '{ zio.blocks.typeid.TypeRepr.Wildcard(zio.blocks.typeid.TypeBounds.empty) }
        }
      }

    def makeDynamicTypeId(sym: Symbol): Expr[DynamicTypeId] = {
      val ownerExpr = makeOwner(sym.maybeOwner)
      val nameStr   = sym.name.stripSuffix("$")
      val nameExpr  = Expr(nameStr)
      val kindExpr  = makeKind(sym)
      '{
        DynamicTypeId(
          $ownerExpr,
          $nameExpr,
          Nil,
          $kindExpr,
          Nil,
          Nil,
          Nil
        )
      }
    }

    getZioPreludeBaseClass(tpe) match {
      case Some(baseSym) =>
        // Use the base class (e.g., zio.prelude.Subtype) as the owner, "Type" as the name
        val ownerExpr = makeOwner(baseSym)
        val nameExpr  = Expr("Type")
        '{
          TypeId(
            DynamicTypeId(
              $ownerExpr,
              $nameExpr,
              Nil,
              zio.blocks.typeid.TypeDefKind.Class(),
              Nil,
              Nil,
              Nil
            )
          )
        }
      case None =>
        // Extract alias name directly from TypeRef pattern to preserve alias identity
        // But use dealias.typeSymbol for type params and parents
        val (nameStr, typeArgs) = tpe match {
          case AppliedType(TypeRef(_, name), args) =>
            (name.stripSuffix("$"), args)
          case AppliedType(base, args) =>
            (base.typeSymbol.name.stripSuffix("$"), args)
          case TypeRef(_, name) =>
            (name.stripSuffix("$"), Nil)
          case _ =>
            (tpe.typeSymbol.name.stripSuffix("$"), Nil)
        }
        // Get owner from TypeRef prefix
        val ownerExpr = tpe match {
          case AppliedType(TypeRef(prefix, _), _) => makeOwnerFromPrefix(prefix)
          case TypeRef(prefix, _)                 => makeOwnerFromPrefix(prefix)
          case _                                  => makeOwner(tpe.typeSymbol.maybeOwner)
        }
        val nameExpr = Expr(nameStr)

        // Use the dealiased symbol for type params, parents, and kind
        val dealiasedSym = tpe.dealias.typeSymbol
        val kindExpr     = makeKind(dealiasedSym)

        // Extract type params from dealiased symbol (safe - it's a class/trait symbol)
        val tparams = if (!dealiasedSym.isNoSymbol && !dealiasedSym.isPackageDef) {
          dealiasedSym.typeMembers.filter(_.isTypeParam)
        } else Nil
        val typeParamsExpr = Expr.ofList(tparams.map(makeTypeParam))

        // Extract parents (baseClasses excluding self, Object, Any)
        val relevantParents = if (!dealiasedSym.isNoSymbol && !dealiasedSym.isPackageDef) {
          dealiasedSym.typeRef.baseClasses
            .filter(_ != dealiasedSym)
            .filter(_.name != "Object")
            .filter(_.name != "Any")
        } else Nil
        val parentsExpr = Expr.ofList(relevantParents.map(s => makeTypeReprExpr(s.typeRef)))

        // Use type args extracted from AppliedType
        val argsExpr = Expr.ofList(typeArgs.map(makeTypeReprExpr))

        '{
          TypeId(
            DynamicTypeId(
              $ownerExpr,
              $nameExpr,
              $typeParamsExpr,
              $kindExpr,
              $parentsExpr,
              $argsExpr,
              Nil
            )
          )
        }
    }
  }

  // Extract owner from TypeRef prefix to preserve local alias scope
  private def makeOwnerFromPrefix(prefix: TypeRepr): Expr[zio.blocks.typeid.Owner] =
    prefix match {
      case ThisType(tp)        => makeOwner(tp.typeSymbol)
      case TermRef(qual, name) =>
        val parent = makeOwnerFromPrefix(qual)
        '{ $parent / zio.blocks.typeid.Owner.Term(${ Expr(name.stripSuffix("$")) }) }
      case TypeRef(qual, name) =>
        val parent = makeOwnerFromPrefix(qual)
        '{ $parent / zio.blocks.typeid.Owner.Type(${ Expr(name.stripSuffix("$")) }) }
      case _ => makeOwner(prefix.typeSymbol)
    }

  private def makeOwner(sym: Symbol): Expr[zio.blocks.typeid.Owner] =
    if (sym == Symbol.noSymbol || sym == defn.RootClass || sym == defn.RootPackage) '{ zio.blocks.typeid.Owner.Root }
    else {
      val parent = makeOwner(sym.maybeOwner)
      val name   = sym.name.stripSuffix("$")

      if (sym.isPackageDef) '{ $parent / zio.blocks.typeid.Owner.Package(${ Expr(name) }) }
      else if (sym.flags.is(Flags.Module) || sym.isTerm) '{ $parent / zio.blocks.typeid.Owner.Term(${ Expr(name) }) }
      else '{ $parent / zio.blocks.typeid.Owner.Type(${ Expr(name) }) }
    }

  private def deriveSchemaForEnumOrModuleValue[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
    val tpeId = '{ TypeId.from[T] }
    '{
      new Schema(
        reflect = new Reflect.Record[Binding, T](
          fields = Vector.empty,
          typeId = $tpeId,
          recordBinding = new Binding.Record(
            constructor = new ConstantConstructor(${
              Ref(
                if (isEnumValue(tpe)) tpe.termSymbol
                else tpe.typeSymbol.companionModule
              ).asExpr.asInstanceOf[Expr[T]]
            }),
            deconstructor = new ConstantDeconstructor
          ),
          doc = ${ doc(tpe) },
          modifiers = ${ modifiers(tpe) }
        )
      )
    }
  }

  private def deriveSchemaForNonAbstractScalaClass[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
    val classInfo = new ClassInfo(tpe)
    val fields    = classInfo.fields(Array.empty[String])
    val tpeId     = '{ TypeId.from[T] }
    '{
      new Schema(
        reflect = new Reflect.Record[Binding, T](
          fields = Vector($fields*),
          typeId = $tpeId,
          recordBinding = new Binding.Record(
            constructor = new Constructor {
              def usedRegisters: RegisterOffset = ${ classInfo.usedRegisters }

              def construct(in: Registers, offset: RegisterOffset): T = ${
                classInfo.constructor('in, 'offset)
              }
            },
            deconstructor = new Deconstructor {
              def usedRegisters: RegisterOffset = ${ classInfo.usedRegisters }

              def deconstruct(out: Registers, offset: RegisterOffset, in: T): Unit = ${
                classInfo.deconstructor('out, 'offset, 'in).asExpr
              }
            }
          ),
          doc = ${ doc(tpe) },
          modifiers = ${ modifiers(tpe) }
        )
      )
    }
  }

  private def deriveSchemaForSealedTraitOrAbstractClassOrUnion[T: Type](
    tpe: TypeRepr
  )(using Quotes): Expr[Schema[T]] = {
    val subtypes =
      if (isUnion(tpe)) allUnionTypes(tpe)
      else directSubTypes(tpe)
    if (subtypes.isEmpty) fail(s"Cannot find sub-types for ADT base '${tpe.show}'.")
    val fullTermNames         = subtypes.map(sTpe => toFullTermName(sTpe))
    val maxCommonPrefixLength = {
      val minFullTermName = fullTermNames.min
      val maxFullTermName = fullTermNames.max
      val minLength       = Math.min(minFullTermName.length, maxFullTermName.length) - 1
      var idx             = 0
      while (idx < minLength && minFullTermName(idx).equals(maxFullTermName(idx))) idx += 1
      idx
    }
    val cases = Varargs(subtypes.zip(fullTermNames).map { case (sTpe, fullName) =>
      sTpe.asType match {
        case '[st] =>
          var modifiers: List[Term] = Nil
          sTpe.typeSymbol.annotations.foreach { annotation =>
            val aTpe = annotation.tpe
            if (aTpe <:< modifierTermTpe) modifiers = annotation :: modifiers
          }
          val caseName = Expr(toShortTermName(fullName, maxCommonPrefixLength))
          val schema   = findImplicitOrDeriveSchema[st](sTpe)
          (if (modifiers eq Nil) {
             '{ $schema.reflect.asTerm[T]($caseName) }
           } else {
             val ms = Varargs(modifiers.map(_.asExpr.asInstanceOf[Expr[Modifier.Term]]))
             '{ $schema.reflect.asTerm[T]($caseName).copy(modifiers = $ms) }
           }).asInstanceOf[Expr[SchemaTerm[Binding, T, ? <: T]]]
      }
    })
    val matcherCases = Varargs(subtypes.map { sTpe =>
      sTpe.asType match {
        case '[st] =>
          '{
            new Matcher[st] {
              def downcastOrNull(a: Any): st = (a: @scala.unchecked) match {
                case x: st => x
                case _     => null.asInstanceOf[st]
              }
            }
          }.asInstanceOf[Expr[Matcher[? <: T]]]
      }
    })
    val tpeId = '{ TypeId.from[T] }
    '{
      new Schema(
        reflect = new Reflect.Variant[Binding, T](
          cases = Vector($cases*),
          typeId = $tpeId,
          variantBinding = new Binding.Variant(
            discriminator = new Discriminator {
              def discriminate(a: T): Int = ${
                val v = 'a
                Match(
                  '{ $v: @scala.unchecked }.asTerm,
                  subtypes.map {
                    var idx = -1
                    sTpe =>
                      idx += 1
                      CaseDef(Typed(Wildcard(), Inferred(sTpe)), None, Literal(IntConstant(idx)))
                  }
                ).asExpr.asInstanceOf[Expr[Int]]
              }
            },
            matchers = Matchers($matcherCases*)
          ),
          doc = ${ doc(tpe) },
          modifiers = ${ modifiers(tpe) }
        )
      )
    }
  }

  private def genArraysCopyOf[T: Type](tpe: TypeRepr, x: Expr[Array[T]], newLen: Expr[Int])(using
    Quotes
  ): Expr[Array[T]] = {
    val tpeDealiased = tpe.dealias
    if (tpeDealiased =:= booleanTpe) '{
      val src = $x.asInstanceOf[Array[Boolean]]
      java.util.Arrays.copyOf(src, $newLen).asInstanceOf[Array[T]]
    }
    else if (tpeDealiased =:= byteTpe) '{
      val src = $x.asInstanceOf[Array[Byte]]
      java.util.Arrays.copyOf(src, $newLen).asInstanceOf[Array[T]]
    }
    else if (tpeDealiased =:= shortTpe) '{
      val src = $x.asInstanceOf[Array[Short]]
      java.util.Arrays.copyOf(src, $newLen).asInstanceOf[Array[T]]
    }
    else if (tpeDealiased =:= intTpe) '{
      val src = $x.asInstanceOf[Array[Int]]
      java.util.Arrays.copyOf(src, $newLen).asInstanceOf[Array[T]]
    }
    else if (tpeDealiased =:= longTpe) '{
      val src = $x.asInstanceOf[Array[Long]]
      java.util.Arrays.copyOf(src, $newLen).asInstanceOf[Array[T]]
    }
    else if (tpeDealiased =:= floatTpe) '{
      val src = $x.asInstanceOf[Array[Float]]
      java.util.Arrays.copyOf(src, $newLen).asInstanceOf[Array[T]]
    }
    else if (tpeDealiased =:= doubleTpe) '{
      val src = $x.asInstanceOf[Array[Double]]
      java.util.Arrays.copyOf(src, $newLen).asInstanceOf[Array[T]]
    }
    else if (tpeDealiased =:= charTpe) '{
      val src = $x.asInstanceOf[Array[Char]]
      java.util.Arrays.copyOf(src, $newLen).asInstanceOf[Array[T]]
    }
    else if (tpe <:< anyRefTpe) '{
      java.util.Arrays.copyOf($x.asInstanceOf[Array[AnyRef & T]], $newLen).asInstanceOf[Array[T]]
    }
    else
      '{
        val x1  = ${ genNewArray[T](newLen) }
        val len = java.lang.Math.min($x.length, $newLen)
        java.lang.System.arraycopy($x, 0, x1, 0, len)
        x1
      }
  }.asInstanceOf[Expr[Array[T]]]

  private def genNewArray[T: Type](size: Expr[Int])(using Quotes): Expr[Array[T]] =
    '{ new Array[T]($size)(using ${ summonClassTag[T] }) }

  private def toFullTermName(tpe: TypeRepr): List[String] = {
    var packages: List[String] = Nil
    var values: List[String]   = Nil
    var name: String           = null
    val tpeTypeSymbol          = tpe.typeSymbol
    name = tpeTypeSymbol.name
    if (isEnumValue(tpe)) {
      values = name :: values
      name = tpe.termSymbol.name
    } else if (tpeTypeSymbol.flags.is(Flags.Module)) name = name.substring(0, name.length - 1)
    var owner = tpeTypeSymbol.owner
    while (owner != defn.RootClass) {
      val ownerName = owner.name
      if (owner.flags.is(Flags.Package)) packages = ownerName :: packages
      else if (owner.flags.is(Flags.Module)) values = ownerName.substring(0, ownerName.length - 1) :: values
      else values = ownerName :: values
      owner = owner.owner
    }
    packages ++ values :+ name
  }

  private def toShortTermName(fullName: List[String], from: Int): String = {
    val str = new java.lang.StringBuilder
    var idx = from
    while (idx < fullName.length) {
      if (idx != from) str.append('.')
      str.append(fullName(idx))
      idx += 1
    }
    str.toString
  }

  private def cannotDeriveSchema(tpe: TypeRepr): Nothing = fail(s"Cannot derive schema for '${tpe.show}'.")

  def derived[A: Type]: Expr[Schema[A]] = {
    val aTpe = TypeRepr.of[A]
    // Use A directly from method signature to preserve type alias identity
    val schema      = deriveSchema[A](aTpe)
    val schemaBlock = Block(schemaDefs.toList, schema.asTerm).asExpr.asInstanceOf[Expr[Schema[A]]]
    // report.info(s"Generated schema:\n${schemaBlock.show}", Position.ofMacroExpansion)
    schemaBlock
  }
}
