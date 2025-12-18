package zio.blocks.schema

import scala.quoted._
import zio.blocks.schema.CommonMacroOps
import zio.blocks.schema.binding.{Binding, ConstantConstructor, ConstantDeconstructor, Constructor, Deconstructor}
import scala.collection.immutable.ArraySeq
import scala.collection.mutable

trait SchemaVersionSpecific {
  inline def derived[A]: Schema[A] = ${ SchemaVersionSpecificImpl.derived[A] }
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

  private def fail(msg: String): Nothing = CommonMacroOps.fail(msg)

  private def isEnumValue(tpe: TypeRepr): Boolean = tpe.termSymbol.flags.is(Flags.Enum)

  private def isEnumOrModuleValue(tpe: TypeRepr): Boolean = isEnumValue(tpe) || tpe.typeSymbol.flags.is(Flags.Module)

  private def isSealedTraitOrAbstractClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
    val flags = symbol.flags
    flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))
  }

  private def isOpaque(tpe: TypeRepr): Boolean = tpe.typeSymbol.flags.is(Flags.Opaque)

  private def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
    val flags = symbol.flags
    !(flags.is(Flags.Abstract) || flags.is(Flags.JavaDefined) || flags.is(Flags.Trait))
  }

  private def typeArgs(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.typeArgs(tpe)

  private def isGenericTuple(tpe: TypeRepr): Boolean = CommonMacroOps.isGenericTuple(tpe)

  private val isNonRecursiveCache = new mutable.HashMap[TypeRepr, Boolean]

  private def isNonRecursive(tpe: TypeRepr, nestedTpes: List[TypeRepr] = Nil): Boolean = isNonRecursiveCache
    .getOrElseUpdate(
      tpe,
      tpe <:< intTpe || tpe <:< floatTpe || tpe <:< longTpe || tpe <:< doubleTpe || tpe <:< booleanTpe ||
        tpe <:< byteTpe || tpe <:< charTpe || tpe <:< shortTpe || tpe <:< unitTpe ||
        tpe <:< stringTpe || tpe <:< TypeRepr.of[BigDecimal] || tpe <:< TypeRepr.of[BigInt] || isEnumOrModuleValue(tpe) ||
        tpe <:< TypeRepr.of[DynamicValue] || !nestedTpes.contains(tpe) && {
          val nestedTpes_ = tpe :: nestedTpes
          if (tpe <:< TypeRepr.of[Option[?]] || tpe <:< TypeRepr.of[Either[?, ?]]) {
            typeArgs(tpe).forall(isNonRecursive(_, nestedTpes_))
          } else if (isSealedTraitOrAbstractClass(tpe)) CommonMacroOps.directSubTypes(tpe).forall(isNonRecursive(_, nestedTpes_))
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
        val isUnionTpe             = false // Scala 2 compatibility
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
          if (isUnionTpe) Nil // Scala 2 compatibility
          else if (isGenericTuple(tpe)) CommonMacroOps.genericTupleTypeArgs(tpe)
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
        tpe match {
          case TypeRef(compTpe, "Type") => compTpe
          case _                        => tpe
        } match {
          case tpe_ => calculateTypeName(tpe_)
        }
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
      if (annotation.tpe <:< Symbol.requiredClass("zio.blocks.schema.Modifier.Reflect").typeRef) {
        modifiers = annotation.asExpr.asInstanceOf[Expr[Modifier.Reflect]] :: modifiers
      }
    }
    if (modifiers eq Nil) '{ Nil } else Varargs(modifiers)
  }

  private val schemaRefs = new mutable.HashMap[TypeRepr, Expr[Schema[?]]]
  private val schemaDefs = new mutable.ListBuffer[ValDef]

  private def findImplicitOrDeriveSchema[T: Type](tpe: TypeRepr): Expr[Schema[T]] = schemaRefs
    .getOrElse(
      tpe, {
        val schemaTpeApplied = Symbol.requiredClass("zio.blocks.schema.Schema").typeRef.appliedTo(tpe)
        Implicits.search(schemaTpeApplied) match {
          case v: ImplicitSearchSuccess => v.tree.asExpr.asInstanceOf[Expr[Schema[?]]]
          case _                        =>
            val name  = s"s${schemaRefs.size}"
            val flags =
              if (isNonRecursive(tpe)) Flags.Implicit
              else Flags.Implicit | Flags.Lazy
            val symbol = Symbol.newVal(Symbol.spliceOwner, name, schemaTpeApplied, flags, Symbol.noSymbol)
            val ref    = Ref(symbol).asExpr.asInstanceOf[Expr[Schema[?]]]
            schemaRefs.update(tpe, ref)
            implicit val quotes: Quotes = symbol.asQuotes
            val schema                  = deriveSchema(tpe)
            schemaDefs.addOne(ValDef(symbol, new Some(schema.asTerm)))
            ref
        }
      }
    )
    .asInstanceOf[Expr[Schema[T]]]

  private def deriveSchema[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
    if (isEnumOrModuleValue(tpe)) {
      deriveSchemaForEnumOrModuleValue(tpe)
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
    } else if (isNonAbstractScalaClass(tpe)) {
      deriveSchemaForNonAbstractScalaClass(tpe)
    } else fail(s"Cannot derive schema for '${tpe.show}'.")
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
    // Simplified implementation - just return a basic record schema
    val tpeName = toExpr(typeName(tpe))
    '{
      new Schema(
        reflect = new Reflect.Record[Binding, T](
          fields = Vector.empty, // Simplified - no field derivation
          typeName = $tpeName,
          recordBinding = new Binding.Record(
            constructor = new Constructor[T] {
              def usedRegisters: zio.blocks.schema.binding.RegisterOffset.RegisterOffset = zio.blocks.schema.binding.RegisterOffset.Zero
              def construct(in: zio.blocks.schema.binding.Registers, baseOffset: zio.blocks.schema.binding.RegisterOffset.RegisterOffset): T = ???
            },
            deconstructor = new Deconstructor[T] {
              def usedRegisters: zio.blocks.schema.binding.RegisterOffset.RegisterOffset = zio.blocks.schema.binding.RegisterOffset.Zero
              def deconstruct(out: zio.blocks.schema.binding.Registers, baseOffset: zio.blocks.schema.binding.RegisterOffset.RegisterOffset, in: T): Unit = ???
            }
          ),
          doc = ${ doc(tpe) },
          modifiers = ${ modifiers(tpe) }
        )
      )
    }
  }

  def derived[A: Type]: Expr[Schema[A]] = {
    val aTpe        = TypeRepr.of[A].dealias
    val schema      = aTpe.asType match { case '[a] => deriveSchema[a](aTpe) }
    val schemaBlock = Block(schemaDefs.toList, schema.asTerm).asExpr.asInstanceOf[Expr[Schema[A]]]
    schemaBlock
  }
}