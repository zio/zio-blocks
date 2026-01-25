package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait ToStructuralVersionSpecific {
  implicit def toStructural[A]: ToStructural[A] = macro ToStructuralMacro.derived[A]
}

object ToStructuralMacro {
  def derived[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[ToStructural[A]] = {
    import c.universe._

    val aTpe = weakTypeOf[A].dealias

    if (!Platform.supportsReflection) {
      c.abort(
        c.enclosingPosition,
        s"""Cannot generate ToStructural[${aTpe}] on ${Platform.name}.
           |
           |Structural types require reflection which is only available on JVM.
           |Consider using a case class instead.""".stripMargin
      )
    }

    if (isRecursiveType(c)(aTpe)) {
      c.abort(
        c.enclosingPosition,
        s"""Cannot generate structural type for recursive type ${aTpe}.
           |
           |Structural types cannot represent recursive structures.
           |Scala's type system does not support infinite types.""".stripMargin
      )
    }

    if (isProductType(c)(aTpe)) {
      deriveForProduct[A](c)(aTpe)
    } else if (isTupleType(c)(aTpe)) {
      deriveForTuple[A](c)(aTpe)
    } else {
      c.abort(
        c.enclosingPosition,
        s"""Cannot generate ToStructural for ${aTpe}.
           |
           |Only product types (case classes) and tuples are currently supported.
           |Sum types (sealed traits) are not supported in Scala 2.""".stripMargin
      )
    }
  }

  private def isProductType(c: blackbox.Context)(tpe: c.universe.Type): Boolean =
    tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isCaseClass

  private def isTupleType(c: blackbox.Context)(tpe: c.universe.Type): Boolean =
    tpe.typeSymbol.fullName.startsWith("scala.Tuple")

  private def isRecursiveType(c: blackbox.Context)(tpe: c.universe.Type): Boolean = {
    import c.universe._

    def containsType(searchIn: Type, searchFor: Type, visited: Set[Type]): Boolean = {
      val dealiased = searchIn.dealias
      if (visited.contains(dealiased)) return false
      val newVisited = visited + dealiased

      if (dealiased =:= searchFor.dealias) return true

      val typeArgsContain = dealiased match {
        case TypeRef(_, _, args) if args.nonEmpty =>
          args.exists(arg => containsType(arg, searchFor, newVisited))
        case _ => false
      }

      if (typeArgsContain) return true

      val fields = dealiased.decls.collect {
        case m: MethodSymbol if m.isCaseAccessor => m.returnType.asSeenFrom(dealiased, dealiased.typeSymbol)
      }
      fields.exists(fieldTpe => containsType(fieldTpe, searchFor, newVisited))
    }

    val fields = tpe.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => m.returnType.asSeenFrom(tpe, tpe.typeSymbol)
    }
    fields.exists(fieldTpe => containsType(fieldTpe, tpe, Set.empty))
  }

  private def fullyUnpackType(c: blackbox.Context)(tpe: c.universe.Type): c.universe.Type = {
    import c.universe._

    val dealiased = tpe.dealias

    def isZioPreludeNewtype(t: Type): Boolean = t match {
      case TypeRef(prefix, _, _) =>
        prefix.typeSymbol.fullName.contains("zio.prelude.Newtype") ||
        prefix.typeSymbol.fullName.contains("zio.prelude.Subtype")
      case _ => false
    }

    def getNewtypeUnderlying(t: Type): Type = t match {
      case TypeRef(prefix, _, _) =>
        val baseTypes = prefix.baseClasses
        baseTypes
          .find(_.fullName.contains("zio.prelude.Newtype"))
          .orElse(
            baseTypes.find(_.fullName.contains("zio.prelude.Subtype"))
          ) match {
          case Some(baseSym) =>
            prefix.baseType(baseSym).typeArgs.headOption.map(_.dealias).getOrElse(t)
          case None => t
        }
      case _ => t
    }

    if (isZioPreludeNewtype(dealiased)) {
      fullyUnpackType(c)(getNewtypeUnderlying(dealiased))
    } else {
      dealiased match {
        case TypeRef(_, sym, args) if args.nonEmpty =>
          val unpackedArgs = args.map(fullyUnpackType(c)(_))
          if (unpackedArgs != args) appliedType(sym, unpackedArgs)
          else dealiased
        case _ => dealiased
      }
    }
  }

  private def deriveForProduct[A: c.WeakTypeTag](
    c: blackbox.Context
  )(aTpe: c.universe.Type): c.Expr[ToStructural[A]] = {
    import c.universe._

    val fields: List[(String, Type)] = aTpe.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor =>
        (m.name.toString, m.returnType.asSeenFrom(aTpe, aTpe.typeSymbol))
    }.toList

    val structuralTpe = buildStructuralType(c)(fields)

    c.Expr[ToStructural[A]](
      q"""
        new _root_.zio.blocks.schema.ToStructural[$aTpe] {
          type StructuralType = $structuralTpe
          def apply(schema: _root_.zio.blocks.schema.Schema[$aTpe]): _root_.zio.blocks.schema.Schema[$structuralTpe] = {
            _root_.zio.blocks.schema.ToStructuralMacro.transformProductSchema[$aTpe, $structuralTpe](schema)
          }
        }
      """
    )
  }

  private def deriveForTuple[A: c.WeakTypeTag](c: blackbox.Context)(aTpe: c.universe.Type): c.Expr[ToStructural[A]] = {
    import c.universe._

    val typeArgs = aTpe.typeArgs

    val fields: List[(String, Type)] = typeArgs.zipWithIndex.map { case (tpe, idx) =>
      (s"_${idx + 1}", tpe)
    }

    if (fields.isEmpty) {
      c.abort(c.enclosingPosition, "Cannot generate structural type for empty tuple")
    }

    val structuralTpe = buildStructuralType(c)(fields)

    c.Expr[ToStructural[A]](
      q"""
        new _root_.zio.blocks.schema.ToStructural[$aTpe] {
          type StructuralType = $structuralTpe
          def apply(schema: _root_.zio.blocks.schema.Schema[$aTpe]): _root_.zio.blocks.schema.Schema[$structuralTpe] = {
            _root_.zio.blocks.schema.ToStructuralMacro.transformTupleSchema[$aTpe, $structuralTpe](schema)
          }
        }
      """
    )
  }

  private def buildStructuralType(c: blackbox.Context)(fields: List[(String, c.universe.Type)]): c.universe.Type = {
    import c.universe._

    if (fields.isEmpty) {
      return definitions.AnyRefTpe
    }

    val sortedFields = fields.sortBy(_._1)

    // Unpack opaque/newtype field types to their underlying primitives
    val unpackedFields = sortedFields.map { case (name, tpe) =>
      (name, fullyUnpackType(c)(tpe))
    }

    val refinedTypeTree = unpackedFields.foldLeft(tq"AnyRef": Tree) { case (parent, (name, tpe)) =>
      val methodName = TermName(name)
      tq"$parent { def $methodName: $tpe }"
    }

    c.typecheck(refinedTypeTree, c.TYPEmode).tpe.dealias
  }

  /**
   * Transform a product schema (case class) to its structural equivalent. This
   * is called at runtime from the generated code.
   */
  def transformProductSchema[A, S](schema: Schema[A]): Schema[S] =
    schema.reflect match {
      case record: Reflect.Record[Binding, A] @unchecked =>
        val binding = record.recordBinding.asInstanceOf[Binding.Record[A]]

        val fieldInfos = record.fields.map { field =>
          (field.name, field.value.asInstanceOf[Reflect.Bound[Any]])
        }

        val totalRegisters = binding.constructor.usedRegisters

        val typeName = normalizeTypeName(fieldInfos.toList.map { case (name, reflect) =>
          (name, reflect.typeName.name)
          (name, reflect.typeName.name)
        })

        new Schema[S](
          new Reflect.Record[Binding, S](
            fields = record.fields.map { field =>
              field.value.asInstanceOf[Reflect.Bound[Any]].asTerm[S](field.name)
            },
            typeName = new TypeName[S](new Namespace(Nil, Nil), typeName, Nil),
            recordBinding = new Binding.Record[S](
              constructor = new Constructor[S] {
                def usedRegisters: RegisterOffset = totalRegisters

                def construct(in: Registers, baseOffset: RegisterOffset): S = {
                  val nominal = binding.constructor.construct(in, baseOffset)
                  nominal.asInstanceOf[S]
                }
              },
              deconstructor = new Deconstructor[S] {
                def usedRegisters: RegisterOffset = totalRegisters

                def deconstruct(out: Registers, baseOffset: RegisterOffset, in: S): Unit =
                  binding.deconstructor.deconstruct(out, baseOffset, in.asInstanceOf[A])
              }
            ),
            doc = record.doc,
            modifiers = record.modifiers
          )
        )

      case _ =>
        throw new IllegalArgumentException(
          s"Cannot transform non-record schema to structural type"
        )
    }

  /**
   * Transform a tuple schema to its structural equivalent. This is called at
   * runtime from the generated code.
   */
  def transformTupleSchema[A, S](schema: Schema[A]): Schema[S] =
    schema.reflect match {
      case _: Reflect.Record[Binding, A] @unchecked =>
        transformProductSchema[A, S](schema)

      case _ =>
        throw new IllegalArgumentException(
          s"Cannot transform non-record schema to structural type"
        )
    }

  /**
   * Generate a normalized type name for a structural type. Fields are sorted
   * alphabetically for deterministic naming.
   */
  private def normalizeTypeName(fields: List[(String, String)]): String = {
    val sorted = fields.sortBy(_._1)
    sorted.map { case (name, typeName) =>
      s"$name:${simplifyTypeName(typeName)}"
    }.mkString("{", ",", "}")
  }

  /**
   * Simplify type names for display (e.g., "scala.Int" -> "Int")
   */
  private def simplifyTypeName(typeName: String): String =
    typeName
      .replace("scala.", "")
      .replace("java.lang.", "")
      .replace("Predef.", "")
}
