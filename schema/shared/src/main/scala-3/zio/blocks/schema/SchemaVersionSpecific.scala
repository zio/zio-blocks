package zio.blocks.schema

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.quoted._
import zio.blocks.schema.{Term => SchemaTerm}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._
import zio.blocks.schema.CommonMacroOps

trait SchemaVersionSpecific {
  inline def derived[A]: Schema[A] = ${ SchemaVersionSpecificImpl.derived }
}

private object SchemaVersionSpecificImpl {
  def derived[A: Type](using Quotes): Expr[Schema[A]] = new SchemaVersionSpecificImpl().derived[A]

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
}

private class SchemaVersionSpecificImpl(using Quotes) {
  import quotes.reflect._
  import zio.blocks.schema.SchemaVersionSpecificImpl.fullTermNameOrdering

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
  private val newArrayOfAny         = Select(New(TypeIdent(arrayClass)), arrayClass.primaryConstructor).appliedToType(anyTpe)
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

  private def isZioPreludeNewtype(tpe: TypeRepr): Boolean = tpe match {
    case TypeRef(compTpe, "Type") => compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
    case _                        => false
  }

  private def zioPreludeNewtypeDealias(tpe: TypeRepr): TypeRepr = tpe match {
    case TypeRef(compTpe, _) =>
      compTpe.baseClasses.find(_.fullName == "zio.prelude.Newtype") match {
        case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
        case _         => cannotDealiasZioPreludeNewtype(tpe)
      }
    case _ => cannotDealiasZioPreludeNewtype(tpe)
  }

  private def cannotDealiasZioPreludeNewtype(tpe: TypeRepr): Nothing =
    fail(s"Cannot dealias zio-prelude newtype: ${tpe.show}.")

  private def isTypeRef(tpe: TypeRepr): Boolean = tpe match {
    case trTpe: TypeRef =>
      val typeSymbol = trTpe.typeSymbol
      typeSymbol.isTypeDef && typeSymbol.isAliasType
    case _ => false
  }

  // Structural type detection - check if type is a refinement with val members
  private def isStructuralType(tpe: TypeRepr): Boolean = {
    val fields = extractStructuralFields(tpe)
    fields.nonEmpty
  }

  // Extract fields from a structural/refinement type
  // Returns a list of (fieldName, fieldType) pairs
  private def extractStructuralFields(tpe: TypeRepr): List[(String, TypeRepr)] = {
    def collectFields(t: TypeRepr, acc: List[(String, TypeRepr)]): List[(String, TypeRepr)] = t match {
      case Refinement(parent, name, info) =>
        val updatedAcc = info match {
          case TypeBounds(_, _) => acc                      // Type member, skip
          case fieldType        => (name, fieldType) :: acc // Val member
        }
        collectFields(parent, updatedAcc)
      case _ => acc
    }
    collectFields(tpe, Nil)
  }

  private def typeRefDealias(tpe: TypeRepr): TypeRepr = tpe match {
    case trTpe: TypeRef =>
      val sTpe = trTpe.translucentSuperType.dealias
      if (sTpe == trTpe) cannotDealiasTypeRef(tpe)
      sTpe
    case _ => cannotDealiasTypeRef(tpe)
  }

  private def cannotDealiasTypeRef(tpe: TypeRepr): Nothing = fail(s"Cannot dealias type reference: ${tpe.show}.")

  private def dealiasOnDemand(tpe: TypeRepr): TypeRepr =
    if (isOpaque(tpe)) opaqueDealias(tpe)
    else if (isZioPreludeNewtype(tpe)) zioPreludeNewtypeDealias(tpe)
    else if (isTypeRef(tpe)) typeRefDealias(tpe)
    else tpe

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

  private def isNamedTuple(tpe: TypeRepr): Boolean = tpe match {
    case AppliedType(ntTpe, _) => ntTpe.typeSymbol.fullName == "scala.NamedTuple$.NamedTuple"
    case _                     => false
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
          } else if (isZioPreludeNewtype(tpe)) {
            isNonRecursive(zioPreludeNewtypeDealias(tpe), nestedTpes_)
          } else if (isStructuralType(tpe)) {
            extractStructuralFields(tpe).forall { case (_, fTpe) =>
              isNonRecursive(fTpe.dealias, nestedTpes_)
            }
          } else if (isTypeRef(tpe)) {
            isNonRecursive(typeRefDealias(tpe), nestedTpes_)
          } else false
        }
    )

  private val typeNameCache = new mutable.HashMap[TypeRepr, TypeName[?]]

  private def typeName[T: Type](tpe: TypeRepr, nestedTpes: List[TypeRepr] = Nil): TypeName[T] = {
    def calculateTypeName(tpe: TypeRepr): TypeName[?] =
      if (tpe =:= TypeRepr.of[java.lang.String]) TypeName.string
      else {
        var packages: List[String] = Nil
        var values: List[String]   = Nil
        var name: String           = null
        val isUnionTpe             = isUnion(tpe)
        if (isUnionTpe) name = "|"
        else {
          val tpeTypeSymbol = tpe.typeSymbol
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
        }
        val tpeTypeArgs =
          if (isUnionTpe) allUnionTypes(tpe)
          else if (isNamedTuple(tpe)) {
            val tpeTypeArgs = typeArgs(tpe)
            val nTpe        = tpeTypeArgs.head
            val tTpe        = tpeTypeArgs.last
            val nTypeArgs   =
              if (isGenericTuple(nTpe)) genericTupleTypeArgs(nTpe)
              else typeArgs(nTpe)
            var comma  = false
            val labels = new java.lang.StringBuilder(name)
            labels.append('[')
            nTypeArgs.foreach { case ConstantType(StringConstant(str)) =>
              if (comma) labels.append(',')
              else comma = true
              labels.append(str)
            }
            labels.append(']')
            name = labels.toString
            if (isGenericTuple(tTpe)) genericTupleTypeArgs(tTpe)
            else typeArgs(tTpe)
          } else if (isGenericTuple(tpe)) genericTupleTypeArgs(tpe)
          else typeArgs(tpe)
        new TypeName(
          new Namespace(packages, values),
          name,
          tpeTypeArgs.map { x =>
            if (nestedTpes.contains(x)) typeName[Any](anyTpe)
            else typeName(x, x :: nestedTpes)
          }
        )
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

  private def toExpr[T: Type](tpeName: TypeName[T])(using Quotes): Expr[TypeName[T]] = {
    val packages = Varargs(tpeName.namespace.packages.map(Expr(_)))
    val vs       = tpeName.namespace.values
    val values   = if (vs.isEmpty) '{ Nil } else Varargs(vs.map(Expr(_)))
    val name     = Expr(tpeName.name)
    val ps       = tpeName.params
    val params   = if (ps.isEmpty) '{ Nil } else Varargs(ps.map(param => toExpr(param.asInstanceOf[TypeName[T]])))
    '{ new TypeName[T](new Namespace($packages, $values), $name, $params) }
  }

  // Generate structural TypeName for a structural type
  private def structuralTypeName[T: Type](fields: List[(String, TypeRepr)])(using Quotes): Expr[TypeName[T]] = {
    val fieldExprs = fields.map { case (name, fTpe) =>
      val nameExpr     = Expr(name)
      val typeNameExpr = toExpr(typeName[Any](fTpe).asInstanceOf[TypeName[Any]])
      '{ ($nameExpr, $typeNameExpr) }
    }
    '{ TypeName.structural[T](Seq(${ Varargs(fieldExprs) }*)) }
  }

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
    val defaultValue: Option[Term],
    val getter: Symbol,
    val usedRegisters: RegisterOffset,
    val modifiers: List[Term]
  )

  private abstract class TypeInfo[T: Type] {
    def tpeTypeArgs: List[TypeRepr]

    def usedRegisters: Expr[RegisterOffset]

    def fields[S: Type](nameOverrides: Array[String])(using Quotes): Expr[Seq[SchemaTerm[Binding, S, ?]]]

    def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset])(using Quotes): Expr[T]

    def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Term

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
      else RegisterOffset(objects = 1)
    }

    def fieldConstructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset], fieldInfo: FieldInfo)(using
      Quotes
    ): Term = {
      val fTpe    = fieldInfo.tpe
      val bytes   = RegisterOffset.getBytes(fieldInfo.usedRegisters)
      val objects = RegisterOffset.getObjects(fieldInfo.usedRegisters)
      if (fTpe =:= intTpe) '{ $in.getInt($baseOffset, ${ Expr(bytes) }) }
      else if (fTpe =:= floatTpe) '{ $in.getFloat($baseOffset, ${ Expr(bytes) }) }
      else if (fTpe =:= longTpe) '{ $in.getLong($baseOffset, ${ Expr(bytes) }) }
      else if (fTpe =:= doubleTpe) '{ $in.getDouble($baseOffset, ${ Expr(bytes) }) }
      else if (fTpe =:= booleanTpe) '{ $in.getBoolean($baseOffset, ${ Expr(bytes) }) }
      else if (fTpe =:= byteTpe) '{ $in.getByte($baseOffset, ${ Expr(bytes) }) }
      else if (fTpe =:= charTpe) '{ $in.getChar($baseOffset, ${ Expr(bytes) }) }
      else if (fTpe =:= shortTpe) '{ $in.getShort($baseOffset, ${ Expr(bytes) }) }
      else if (fTpe =:= unitTpe) '{ () }
      else {
        fTpe.asType match {
          case '[ft] =>
            val sTpe = dealiasOnDemand(fTpe)
            if (sTpe <:< intTpe) '{ $in.getInt($baseOffset, ${ Expr(bytes) }).asInstanceOf[ft] }
            else if (sTpe <:< floatTpe) '{ $in.getFloat($baseOffset, ${ Expr(bytes) }).asInstanceOf[ft] }
            else if (sTpe <:< longTpe) '{ $in.getLong($baseOffset, ${ Expr(bytes) }).asInstanceOf[ft] }
            else if (sTpe <:< doubleTpe) '{ $in.getDouble($baseOffset, ${ Expr(bytes) }).asInstanceOf[ft] }
            else if (sTpe <:< booleanTpe) '{ $in.getBoolean($baseOffset, ${ Expr(bytes) }).asInstanceOf[ft] }
            else if (sTpe <:< byteTpe) '{ $in.getByte($baseOffset, ${ Expr(bytes) }).asInstanceOf[ft] }
            else if (sTpe <:< charTpe) '{ $in.getChar($baseOffset, ${ Expr(bytes) }).asInstanceOf[ft] }
            else if (sTpe <:< shortTpe) '{ $in.getShort($baseOffset, ${ Expr(bytes) }).asInstanceOf[ft] }
            else if (sTpe <:< unitTpe) '{ ().asInstanceOf[ft] }
            else '{ $in.getObject($baseOffset, ${ Expr(objects) }).asInstanceOf[ft] }
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
          var fTpe = tpe.memberType(symbol).dealias
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
          val fieldInfo = new FieldInfo(name, fTpe, defaultValue, getter, usedRegisters, modifiers)
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
            val schema   = findImplicitOrDeriveSchema[ft](fTpe)
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
                  if (isNonRec) '{ $schema.reflect.defaultValue($dv).asTerm[S]($name) }
                  else '{ new Reflect.Deferred(() => $schema.reflect).defaultValue($dv).asTerm[S]($name) }
                } else {
                  if (isNonRec) '{ $schema.reflect.defaultValue($dv).asTerm[S]($name).copy(modifiers = $ms) }
                  else {
                    '{
                      new Reflect.Deferred(() => $schema.reflect)
                        .defaultValue($dv)
                        .asTerm[S]($name)
                        .copy(modifiers = $ms)
                    }
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

    def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset])(using Quotes): Expr[T] = {
      val constructor = Select(New(Inferred(tpe)), primaryConstructor).appliedToTypes(tpeTypeArgs)
      val argss       = fieldInfos.map(_.map(fieldConstructor(in, baseOffset, _)))
      argss.tail.foldLeft(Apply(constructor, argss.head))(Apply(_, _)).asExpr.asInstanceOf[Expr[T]]
    }

    def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Term =
      toBlock(fieldInfos.flatMap(_.map { fieldInfo =>
        val fTpe    = fieldInfo.tpe
        val getter  = Select(in.asTerm, fieldInfo.getter).asExpr
        val bytes   = RegisterOffset.getBytes(fieldInfo.usedRegisters)
        val objects = RegisterOffset.getObjects(fieldInfo.usedRegisters)
        {
          if (fTpe <:< intTpe) '{ $out.setInt($baseOffset, ${ Expr(bytes) }, ${ getter.asInstanceOf[Expr[Int]] }) }
          else if (fTpe <:< floatTpe) {
            '{ $out.setFloat($baseOffset, ${ Expr(bytes) }, ${ getter.asInstanceOf[Expr[Float]] }) }
          } else if (fTpe <:< longTpe) {
            '{ $out.setLong($baseOffset, ${ Expr(bytes) }, ${ getter.asInstanceOf[Expr[Long]] }) }
          } else if (fTpe <:< doubleTpe) {
            '{ $out.setDouble($baseOffset, ${ Expr(bytes) }, ${ getter.asInstanceOf[Expr[Double]] }) }
          } else if (fTpe <:< booleanTpe) {
            '{ $out.setBoolean($baseOffset, ${ Expr(bytes) }, ${ getter.asInstanceOf[Expr[Boolean]] }) }
          } else if (fTpe <:< byteTpe) {
            '{ $out.setByte($baseOffset, ${ Expr(bytes) }, ${ getter.asInstanceOf[Expr[Byte]] }) }
          } else if (fTpe <:< charTpe) {
            '{ $out.setChar($baseOffset, ${ Expr(bytes) }, ${ getter.asInstanceOf[Expr[Char]] }) }
          } else if (fTpe <:< shortTpe) {
            '{ $out.setShort($baseOffset, ${ Expr(bytes) }, ${ getter.asInstanceOf[Expr[Short]] }) }
          } else if (fTpe <:< unitTpe) '{ () }
          else if (fTpe <:< anyRefTpe) {
            '{ $out.setObject($baseOffset, ${ Expr(objects) }, ${ getter.asInstanceOf[Expr[AnyRef]] }) }
          } else {
            val sTpe = dealiasOnDemand(fTpe)
            if (sTpe <:< intTpe) '{ $out.setInt($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Int]) }
            else if (sTpe <:< floatTpe) '{ $out.setFloat($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Float]) }
            else if (sTpe <:< longTpe) '{ $out.setLong($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Long]) }
            else if (sTpe <:< doubleTpe) {
              '{ $out.setDouble($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Double]) }
            } else if (sTpe <:< booleanTpe) {
              '{ $out.setBoolean($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Boolean]) }
            } else if (sTpe <:< byteTpe) '{ $out.setByte($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Byte]) }
            else if (sTpe <:< charTpe) '{ $out.setChar($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Char]) }
            else if (sTpe <:< shortTpe) '{ $out.setShort($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Short]) }
            else if (sTpe <:< unitTpe) '{ () }
            else '{ $out.setObject($baseOffset, ${ Expr(objects) }, $getter.asInstanceOf[AnyRef]) }
          }
        }.asTerm
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
            val fieldInfo = new FieldInfo(s"_$idx", fTpe, None, Symbol.noSymbol, usedRegisters, Nil)
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

    def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset])(using Quotes): Expr[T] =
      (if (fieldInfos eq Nil) Expr(EmptyTuple)
       else {
         val symbol      = Symbol.newVal(Symbol.spliceOwner, "xs", arrayOfAnyTpe, Flags.EmptyFlags, Symbol.noSymbol)
         val ref         = Ref(symbol)
         val update      = Select(ref, defn.Array_update)
         val assignments = fieldInfos.map {
           var idx = -1
           fieldInfo =>
             idx += 1
             Apply(update, List(Literal(IntConstant(idx)), fieldConstructor(in, baseOffset, fieldInfo)))
         }
         val valDef   = ValDef(symbol, new Some(Apply(newArrayOfAny, List(Literal(IntConstant(fieldInfos.size))))))
         val block    = Block(valDef :: assignments, ref)
         val typeCast = Select(block, asInstanceOfMethod).appliedToType(iArrayOfAnyRefTpe)
         Select(Apply(fromIArrayMethod, List(typeCast)), asInstanceOfMethod).appliedToType(tpe).asExpr
       }).asInstanceOf[Expr[T]]

    def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Term =
      toBlock(fieldInfos.map {
        val productElement = Select(in.asTerm, productElementMethod)
        var idx            = -1
        fieldInfo =>
          idx += 1
          val fTpe    = fieldInfo.tpe
          val sTpe    = dealiasOnDemand(fTpe)
          val getter  = productElement.appliedTo(Literal(IntConstant(idx))).asExpr
          val bytes   = RegisterOffset.getBytes(fieldInfo.usedRegisters)
          val objects = RegisterOffset.getObjects(fieldInfo.usedRegisters)
          {
            if (sTpe <:< intTpe) '{ $out.setInt($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Int]) }
            else if (sTpe <:< floatTpe) '{ $out.setFloat($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Float]) }
            else if (sTpe <:< longTpe) '{ $out.setLong($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Long]) }
            else if (sTpe <:< doubleTpe) {
              '{ $out.setDouble($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Double]) }
            } else if (sTpe <:< booleanTpe) {
              '{ $out.setBoolean($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Boolean]) }
            } else if (sTpe <:< byteTpe) '{ $out.setByte($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Byte]) }
            else if (sTpe <:< charTpe) '{ $out.setChar($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Char]) }
            else if (sTpe <:< shortTpe) '{ $out.setShort($baseOffset, ${ Expr(bytes) }, $getter.asInstanceOf[Short]) }
            else if (sTpe <:< unitTpe) '{ () }
            else '{ $out.setObject($baseOffset, ${ Expr(objects) }, $getter.asInstanceOf[AnyRef]) }
          }.asTerm
      })
  }

  private class StructuralTypeInfo[T: Type](tpe: TypeRepr) extends TypeInfo {
    // Extract fields from the structural type
    private val structuralFields: List[(String, TypeRepr)] = extractStructuralFields(tpe)

    val tpeTypeArgs: List[TypeRepr] = Nil

    val (fieldInfos: List[FieldInfo], usedRegisters: Expr[RegisterOffset]) = {
      var usedRegisters = RegisterOffset.Zero
      (
        structuralFields.map { case (name, fTpe) =>
          val fieldInfo = new FieldInfo(name, fTpe.dealias, None, Symbol.noSymbol, usedRegisters, Nil)
          usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe.dealias))
          fieldInfo
        },
        Expr(usedRegisters)
      )
    }

    def fields[S: Type](nameOverrides: Array[String])(using Quotes): Expr[Seq[SchemaTerm[Binding, S, ?]]] = {
      var idx = -1
      Varargs(fieldInfos.map { fieldInfo =>
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
    }

    def constructor(in: Expr[Registers], baseOffset: Expr[RegisterOffset])(using Quotes): Expr[T] = {
      // Build field infos for StructuralConstructor at compile time
      val fieldInfoExprs = fieldInfos.map { fieldInfo =>
        val fTpe          = fieldInfo.tpe
        val name          = Expr(fieldInfo.name)
        val offset        = Expr(fieldInfo.usedRegisters)
        val primitiveType = Expr(primitiveTypeCode(fTpe))
        '{ StructuralFieldInfo($name, $offset, $primitiveType) }
      }
      val fieldsExpr = '{ IndexedSeq(${ Varargs(fieldInfoExprs) }*) }
      '{
        val constructor = new StructuralConstructor[T]($fieldsExpr, $usedRegisters)
        constructor.construct($in, $baseOffset)
      }
    }

    def deconstructor(out: Expr[Registers], baseOffset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Term = {
      // Build field infos for StructuralDeconstructor at compile time
      val fieldInfoExprs = fieldInfos.map { fieldInfo =>
        val fTpe          = fieldInfo.tpe
        val name          = Expr(fieldInfo.name)
        val offset        = Expr(fieldInfo.usedRegisters)
        val primitiveType = Expr(primitiveTypeCode(fTpe))
        '{ StructuralFieldInfo($name, $offset, $primitiveType) }
      }
      val fieldsExpr = '{ IndexedSeq(${ Varargs(fieldInfoExprs) }*) }
      '{
        val deconstructor = new StructuralDeconstructor[T]($fieldsExpr, $usedRegisters)
        deconstructor.deconstruct($out, $baseOffset, $in)
      }.asTerm
    }

    // Returns primitive type code for StructuralFieldInfo
    private def primitiveTypeCode(fTpe: TypeRepr): Int = {
      val sTpe = dealiasOnDemand(fTpe)
      if (sTpe <:< booleanTpe) StructuralFieldInfo.Boolean
      else if (sTpe <:< byteTpe) StructuralFieldInfo.Byte
      else if (sTpe <:< shortTpe) StructuralFieldInfo.Short
      else if (sTpe <:< intTpe) StructuralFieldInfo.Int
      else if (sTpe <:< longTpe) StructuralFieldInfo.Long
      else if (sTpe <:< floatTpe) StructuralFieldInfo.Float
      else if (sTpe <:< doubleTpe) StructuralFieldInfo.Double
      else if (sTpe <:< charTpe) StructuralFieldInfo.Char
      else StructuralFieldInfo.Object
    }
  }

  private def deriveSchema[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
    if (isEnumOrModuleValue(tpe)) {
      deriveSchemaForEnumOrModuleValue(tpe)
    } else if (isCollection(tpe)) {
      if (tpe <:< arrayOfWildcardTpe) {
        val eTpe = typeArgs(tpe).head
        eTpe.asType match {
          case '[et] =>
            val schema      = findImplicitOrDeriveSchema[et](eTpe)
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
            val tpeName = toExpr(typeName[Array[et]](tpe))
            '{
              new Schema(
                reflect = new Reflect.Sequence(
                  element = $schema.reflect,
                  typeName = $tpeName.copy(params = List($schema.reflect.typeName)),
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
            val schema      = findImplicitOrDeriveSchema[et](eTpe)
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
            val tpeName = toExpr(typeName[IArray[et]](tpe))
            '{
              new Schema(
                reflect = new Reflect.Sequence(
                  element = $schema.reflect,
                  typeName = $tpeName.copy(params = List($schema.reflect.typeName)),
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
        eTpe.asType match {
          case '[et] =>
            val schema = findImplicitOrDeriveSchema[et](eTpe)
            '{ Schema.arraySeq($schema) }
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
          val fields  = typeInfo.fields[tt](Array.empty[String])
          val tpeName = toExpr(typeName[tt](tTpe))
          '{
            new Schema(
              reflect = new Reflect.Record[Binding, tt](
                fields = Vector($fields*),
                typeName = $tpeName,
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
                      typeInfo.deconstructor('out, 'baseOffset, 'in).asExpr
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
        else if (vTpe <:< anyRefTpe && !isOpaque(vTpe) && !isZioPreludeNewtype(vTpe)) {
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
      val tpeTypeArgs = typeArgs(tpe)
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
          val fields  = typeInfo.fields[T](nameOverrides)
          val tpeName = toExpr(typeName[T](tpe))
          '{
            new Schema(
              reflect = new Reflect.Record[Binding, T](
                fields = Vector($fields*),
                typeName = $tpeName,
                recordBinding = new Binding.Record(
                  constructor = new Constructor {
                    def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                    def construct(in: Registers, baseOffset: RegisterOffset): T = ${
                      typeInfo.constructor('in, 'baseOffset).asInstanceOf[Expr[T]]
                    }
                  },
                  deconstructor = new Deconstructor {
                    def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                    def deconstruct(out: Registers, baseOffset: RegisterOffset, in: T): Unit = ${
                      val value  = Apply(toTupleMethod.appliedToTypes(tpe.typeArgs), List('in.asTerm))
                      val symbol = Symbol.newVal(Symbol.spliceOwner, "t", tTpe, Flags.EmptyFlags, Symbol.noSymbol)
                      val valDef = ValDef(symbol, new Some(value))
                      val expr   = Ref(symbol).asExpr.asInstanceOf[Expr[tt]]
                      Block(List(valDef), typeInfo.deconstructor('out, 'baseOffset, expr)).asExpr
                    }
                  }
                )
              )
            )
          }
      }
    } else if (isNonAbstractScalaClass(tpe)) {
      deriveSchemaForNonAbstractScalaClass(tpe)
    } else if (isOpaque(tpe)) {
      val sTpe = opaqueDealias(tpe)
      sTpe.asType match {
        case '[s] =>
          val schema  = findImplicitOrDeriveSchema[s](sTpe)
          val tpeName = toExpr(typeName[T](tpe).asInstanceOf[TypeName[s]])
          '{ new Schema($schema.reflect.typeName($tpeName)).asInstanceOf[Schema[T]] }
      }
    } else if (isZioPreludeNewtype(tpe)) {
      val sTpe = zioPreludeNewtypeDealias(tpe)
      sTpe.asType match {
        case '[s] =>
          val schema  = findImplicitOrDeriveSchema[s](sTpe)
          val tpeName = toExpr(typeName[T](tpe).asInstanceOf[TypeName[s]])
          '{ new Schema($schema.reflect.typeName($tpeName)).asInstanceOf[Schema[T]] }
      }
    } else if (isStructuralType(tpe)) {
      deriveSchemaForStructuralType(tpe)
    } else if (isTypeRef(tpe)) {
      val sTpe = typeRefDealias(tpe)
      sTpe.asType match { case '[s] => deriveSchema[s](sTpe) }
    } else cannotDeriveSchema(tpe)
  }.asInstanceOf[Expr[Schema[T]]]

  private def deriveSchemaForEnumOrModuleValue[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
    val tpeName = toExpr(typeName(tpe))
    '{
      new Schema(
        reflect = new Reflect.Record[Binding, T](
          fields = Vector.empty,
          typeName = $tpeName,
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
    val tpeName   = toExpr(typeName(tpe))
    '{
      new Schema(
        reflect = new Reflect.Record[Binding, T](
          fields = Vector($fields*),
          typeName = $tpeName,
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
                classInfo.deconstructor('out, 'baseOffset, 'in).asExpr
              }
            }
          ),
          doc = ${ doc(tpe) },
          modifiers = ${ modifiers(tpe) }
        )
      )
    }
  }

  private def deriveSchemaForStructuralType[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
    val structuralTypeInfo = new StructuralTypeInfo[T](tpe)
    val fields             = structuralTypeInfo.fields[T](Array.empty[String])
    val structuralFields   = extractStructuralFields(tpe)
    val tpeName            = structuralTypeName[T](structuralFields)
    '{
      new Schema(
        reflect = new Reflect.Record[Binding, T](
          fields = Vector($fields*),
          typeName = $tpeName,
          recordBinding = new Binding.Record(
            constructor = new Constructor {
              def usedRegisters: RegisterOffset = ${ structuralTypeInfo.usedRegisters }

              def construct(in: Registers, baseOffset: RegisterOffset): T = ${
                structuralTypeInfo.constructor('in, 'baseOffset)
              }
            },
            deconstructor = new Deconstructor {
              def usedRegisters: RegisterOffset = ${ structuralTypeInfo.usedRegisters }

              def deconstruct(out: Registers, baseOffset: RegisterOffset, in: T): Unit = ${
                structuralTypeInfo.deconstructor('out, 'baseOffset, 'in).asExpr
              }
            }
          )
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
    val fullTermNames         = subtypes.map(sTpe => toFullTermName(typeName(sTpe)))
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
    val tpeName = toExpr(typeName(tpe))
    '{
      new Schema(
        reflect = new Reflect.Variant[Binding, T](
          cases = Vector($cases*),
          typeName = $tpeName,
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

  private def toFullTermName(tpeName: TypeName[?]): Array[String] = {
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

  private def toShortTermName(fullName: Array[String], from: Int): String = {
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
    val aTpe        = TypeRepr.of[A].dealias
    val schema      = aTpe.asType match { case '[a] => deriveSchema[a](aTpe) }
    val schemaBlock = Block(schemaDefs.toList, schema.asTerm).asExpr.asInstanceOf[Expr[Schema[A]]]
    // report.info(s"Generated schema:\n${schemaBlock.show}", Position.ofMacroExpansion)
    schemaBlock
  }
}
