package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.{TypeName => SchemaTypeName}
import zio.blocks.schema.CommonMacroOps
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.NameTransformer

trait SchemaVersionSpecific {
  def derived[A]: Schema[A] = macro SchemaVersionSpecific.derived[A]
}

private object SchemaVersionSpecific {
  private[this] implicit val fullTermNameOrdering: Ordering[Array[String]] = new Ordering[Array[String]] {
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

  def derived[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[Schema[A]] = {
    import c.universe._
    import c.internal._

    def fail(msg: String): Nothing = CommonMacroOps.fail(c)(msg)

    def typeArgs(tpe: Type): List[Type] = CommonMacroOps.typeArgs(c)(tpe)

    def directSubTypes(tpe: Type): List[Type] = CommonMacroOps.directSubTypes(c)(tpe)

    def isEnumOrModuleValue(tpe: Type): Boolean = tpe.typeSymbol.isModuleClass

    def isSealedTraitOrAbstractClass(tpe: Type): Boolean = tpe.typeSymbol.isClass && {
      val classSymbol = tpe.typeSymbol.asClass
      classSymbol.isSealed && (classSymbol.isAbstract || classSymbol.isTrait)
    }

    def isNonAbstractScalaClass(tpe: Type): Boolean =
      tpe.typeSymbol.isClass && !tpe.typeSymbol.isAbstract && !tpe.typeSymbol.isJava

    def isJavaTime(tpe: Type): Boolean = tpe.typeSymbol.fullName.startsWith("java.time.") &&
      (tpe <:< typeOf[java.time.temporal.Temporal] || tpe <:< typeOf[java.time.temporal.TemporalAmount])

    def isOption(tpe: Type): Boolean = tpe <:< typeOf[Option[?]]

    def isCollection(tpe: Type): Boolean =
      tpe <:< typeOf[Iterable[?]] || tpe <:< typeOf[Iterator[?]] || tpe <:< typeOf[Array[?]]

    // Check if a type is a structural refinement type
    // e.g., { def name: String; def age: Int } or StructuralRecord { def name: String }
    // In Scala 2, `{ def name: String }` is shorthand for `AnyRef { def name: String }`
    def isStructuralRefinementType(tpe: Type): Boolean = tpe.dealias match {
      case RefinedType(parents, scope) =>
        // Accept refinements with non-empty scope (method declarations)
        // Parents should be AnyRef/Object or StructuralRecord
        scope.nonEmpty && scope.exists(_.isMethod) && parents.forall { p =>
          p <:< typeOf[StructuralRecord] || p =:= typeOf[AnyRef] || p =:= definitions.ObjectTpe
        }
      case _ => false
    }

    // Check if a type is a type alias (like `type AddressStructure = { def city: String }`)
    // In Scala 2, type aliases have an isAliasType flag and dealias to a different type
    def isTypeAlias(tpe: Type): Boolean = {
      val symbol = tpe.typeSymbol
      symbol.isType && symbol.asType.isAliasType && !(tpe.dealias =:= tpe)
    }

    // Extract field names and types from a refinement type
    // Returns fields in alphabetical order for normalized TypeName
    // Note: We don't dealias m.returnType to preserve type alias names for nested structural types.
    // This allows Schema[AddressStructure] to be found when processing { def address: AddressStructure }.
    def extractRefinementFields(tpe: Type): List[(String, Type)] = tpe.dealias match {
      case RefinedType(_, scope) =>
        scope.toList.collect {
          case m: MethodSymbol if m.isMethod && m.paramLists.isEmpty =>
            (m.name.decodedName.toString, m.returnType)
        }.sortBy(_._1)
      case _ => Nil
    }

    def isZioPreludeNewtype(tpe: Type): Boolean = tpe match {
      case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
        compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
      case _ => false
    }

    def zioPreludeNewtypeDealias(tpe: Type): Type = tpe match {
      case TypeRef(compTpe, _, _) =>
        compTpe.baseClasses.find(_.fullName == "zio.prelude.Newtype") match {
          case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
          case _         => cannotDealiasZioPreludeNewtype(tpe)
        }
      case _ => cannotDealiasZioPreludeNewtype(tpe)
    }

    def cannotDealiasZioPreludeNewtype(tpe: Type): Nothing = fail(s"Cannot dealias zio-prelude newtype '$tpe'.")

    def dealiasOnDemand(tpe: Type): Type =
      if (isZioPreludeNewtype(tpe)) zioPreludeNewtypeDealias(tpe)
      else tpe

    def companion(tpe: Type): Symbol = {
      val comp = tpe.typeSymbol.companion
      if (comp.isModule) comp
      else {
        val ownerChainOf = (s: Symbol) => Iterator.iterate(s)(_.owner).takeWhile(_ != NoSymbol).toArray.reverseIterator
        val path         = ownerChainOf(tpe.typeSymbol)
          .zipAll(ownerChainOf(enclosingOwner), NoSymbol, NoSymbol)
          .dropWhile(x => x._1 == x._2)
          .takeWhile(x => x._1 != NoSymbol)
          .map(x => x._1.name.toTermName)
        if (path.isEmpty) NoSymbol
        else c.typecheck(path.foldLeft[Tree](Ident(path.next()))(Select(_, _)), silent = true).symbol
      }
    }

    def primaryConstructor(tpe: Type): MethodSymbol = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(fail(s"Cannot find a primary constructor for '$tpe'"))

    val isNonRecursiveCache = new mutable.HashMap[Type, Boolean]

    def isNonRecursive(tpe: Type, nestedTpes: List[Type] = Nil): Boolean = isNonRecursiveCache.getOrElseUpdate(
      tpe,
      tpe <:< typeOf[String] || tpe <:< definitions.IntTpe || tpe <:< definitions.FloatTpe ||
        tpe <:< definitions.DoubleTpe || tpe <:< definitions.LongTpe || tpe <:< definitions.BooleanTpe ||
        tpe <:< definitions.ByteTpe || tpe <:< definitions.CharTpe || tpe <:< definitions.ShortTpe ||
        tpe <:< definitions.UnitTpe || tpe <:< typeOf[BigDecimal] || tpe <:< typeOf[BigInt] || isJavaTime(tpe) ||
        tpe <:< typeOf[java.util.Currency] || tpe <:< typeOf[java.util.UUID] || isEnumOrModuleValue(tpe) ||
        tpe <:< typeOf[DynamicValue] || !nestedTpes.contains(tpe) && {
          val nestedTpes_ = tpe :: nestedTpes
          if (isOption(tpe) || tpe <:< typeOf[Either[?, ?]] || isCollection(tpe)) {
            typeArgs(tpe).forall(isNonRecursive(_, nestedTpes_))
          } else if (isSealedTraitOrAbstractClass(tpe)) directSubTypes(tpe).forall(isNonRecursive(_, nestedTpes_))
          else if (isStructuralRefinementType(tpe)) {
            // Structural refinement types: check if all field types are non-recursive
            extractRefinementFields(tpe).forall { case (_, fTpe) => isNonRecursive(fTpe, nestedTpes_) }
          } else if (isTypeAlias(tpe)) {
            // Handle type aliases (like AddressStructure) by dealiasing and recursing
            isNonRecursive(tpe.dealias, nestedTpes_)
          } else if (isNonAbstractScalaClass(tpe)) {
            val tpeParams     = primaryConstructor(tpe).paramLists
            val tpeTypeArgs   = typeArgs(tpe)
            val tpeTypeParams =
              if (tpeTypeArgs ne Nil) tpe.typeSymbol.asClass.typeParams
              else Nil
            tpeParams.forall(_.forall { param =>
              var fTpe = param.asTerm.typeSignature.dealias
              if (tpeTypeArgs ne Nil) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
              isNonRecursive(fTpe, nestedTpes_)
            })
          } else if (isZioPreludeNewtype(tpe)) {
            isNonRecursive(zioPreludeNewtypeDealias(tpe), nestedTpes_)
          } else false
        }
    )

    val typeNameCache = new mutable.HashMap[Type, SchemaTypeName[?]]

    def typeName(tpe: Type): SchemaTypeName[?] = {
      def calculateTypeName(tpe: Type): SchemaTypeName[?] =
        if (tpe =:= typeOf[java.lang.String]) SchemaTypeName.string
        else {
          var packages  = List.empty[String]
          var values    = List.empty[String]
          val tpeSymbol = tpe.typeSymbol
          var name      = NameTransformer.decode(tpeSymbol.name.toString)
          val comp      = companion(tpe)
          var owner     =
            if (comp == null) tpeSymbol
            else if (comp == NoSymbol) {
              name += ".type"
              tpeSymbol.asClass.module
            } else comp
          while ({
            owner = owner.owner
            owner.owner != NoSymbol
          }) {
            val ownerName = NameTransformer.decode(owner.name.toString)
            if (owner.isPackage || owner.isPackageClass) packages = ownerName :: packages
            else values = ownerName :: values
          }
          new SchemaTypeName(new Namespace(packages, values), name, typeArgs(tpe).map(typeName))
        }

      typeNameCache.getOrElseUpdate(
        tpe,
        tpe match {
          case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
            var tpeName = calculateTypeName(compTpe)
            if (tpeName.name.endsWith(".type")) tpeName = tpeName.copy(name = tpeName.name.stripSuffix(".type"))
            tpeName
          case _ =>
            calculateTypeName(tpe)
        }
      )
    }

    def toTree(tpeName: SchemaTypeName[?]): Tree = {
      val packages = tpeName.namespace.packages.toList
      val values   = tpeName.namespace.values.toList
      val name     = tpeName.name
      val params   = tpeName.params.map(toTree).toList
      q"new TypeName(new Namespace($packages, $values), $name, $params)"
    }

    def modifiers(tpe: Type): List[Tree] = {
      val modifiers = new mutable.ListBuffer[Tree]
      tpe.typeSymbol.annotations.foreach { annotation =>
        val tree = annotation.tree
        val aTpe = tree.tpe
        if (aTpe <:< typeOf[Modifier.Reflect]) modifiers.addOne(q"new $aTpe(..${tree.children.tail})")
      }
      modifiers.toList
    }

    val schemaRefs = new mutable.HashMap[Type, Tree]
    val schemaDefs = new mutable.ListBuffer[Tree]

    def findImplicitOrDeriveSchema(tpe: Type): Tree = schemaRefs.getOrElse(
      tpe, {
        val schemaTpe = tq"_root_.zio.blocks.schema.Schema[$tpe]"
        var schema    = c.inferImplicitValue(c.typecheck(schemaTpe, c.TYPEmode).tpe)
        if (schema.nonEmpty) schema
        else {
          val name = TermName("s" + schemaRefs.size)
          val ref  = Ident(name)
          // adding the schema reference before schema derivation to avoid an endless loop on recursive data structures
          schemaRefs.update(tpe, ref)
          schema = deriveSchema(tpe)
          schemaDefs.addOne {
            if (isNonRecursive(tpe)) q"implicit val $name: $schemaTpe = $schema"
            else q"implicit lazy val $name: $schemaTpe = $schema"
          }
          ref
        }
      }
    )

    class FieldInfo(
      val name: String,
      val tpe: Type,
      val defaultValue: Option[Tree],
      val getter: MethodSymbol,
      val usedRegisters: RegisterOffset,
      val modifiers: List[Tree]
    )

    class ClassInfo(tpe: Type) {
      val tpeTypeArgs: List[Type]                                            = typeArgs(tpe)
      val (fieldInfos: List[List[FieldInfo]], usedRegisters: RegisterOffset) = {
        var getters     = Map.empty[String, MethodSymbol]
        var annotations = Map.empty[String, List[Tree]]
        tpe.members.foreach {
          case m: MethodSymbol if m.isParamAccessor =>
            getters = getters.updated(NameTransformer.decode(m.name.toString), m)
          case m: TermSymbol =>
            m.info: Unit // required to enforce the type information completeness and availability of annotations
            val modifiers = m.annotations.collect { case a if a.tree.tpe <:< typeOf[Modifier.Term] => a.tree }
            if (modifiers ne Nil) {
              annotations = annotations.updated(NameTransformer.decode(m.name.toString.trim), modifiers)
            }
          case _ =>
        }
        lazy val module   = companion(tpe).asModule
        val tpeTypeParams =
          if (tpeTypeArgs ne Nil) tpe.typeSymbol.asClass.typeParams
          else Nil
        var usedRegisters = RegisterOffset.Zero
        var idx           = 0
        (
          primaryConstructor(tpe).paramLists.map(_.map { param =>
            idx += 1
            val symbol = param.asTerm
            val name   = NameTransformer.decode(symbol.name.toString)
            var fTpe   = symbol.typeSignature.dealias
            if (tpeTypeArgs ne Nil) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
            val getter = getters.getOrElse(
              name,
              fail(s"Field or getter '$name' of '$tpe' should be defined as 'val' or 'var' in the primary constructor.")
            )
            val modifiers    = annotations.getOrElse(name, Nil)
            val defaultValue =
              if (symbol.isParamWithDefault) new Some(q"$module.${TermName("$lessinit$greater$default$" + idx)}")
              else {
                if (modifiers.exists(_.tpe <:< typeOf[Modifier.transient]) && !isOption(fTpe) && !isCollection(fTpe)) {
                  fail(s"Missing default value for transient field '$name' in '$tpe'")
                }
                None
              }
            val sTpe   = dealiasOnDemand(fTpe)
            val offset =
              if (sTpe <:< definitions.IntTpe) RegisterOffset(ints = 1)
              else if (sTpe <:< definitions.FloatTpe) RegisterOffset(floats = 1)
              else if (sTpe <:< definitions.LongTpe) RegisterOffset(longs = 1)
              else if (sTpe <:< definitions.DoubleTpe) RegisterOffset(doubles = 1)
              else if (sTpe <:< definitions.BooleanTpe) RegisterOffset(booleans = 1)
              else if (sTpe <:< definitions.ByteTpe) RegisterOffset(bytes = 1)
              else if (sTpe <:< definitions.CharTpe) RegisterOffset(chars = 1)
              else if (sTpe <:< definitions.ShortTpe) RegisterOffset(shorts = 1)
              else if (sTpe <:< definitions.UnitTpe) RegisterOffset.Zero
              else RegisterOffset(objects = 1)
            val fieldInfo = new FieldInfo(name, fTpe, defaultValue, getter, usedRegisters, modifiers)
            usedRegisters = RegisterOffset.add(usedRegisters, offset)
            fieldInfo
          }),
          usedRegisters
        )
      }

      def fields(sTpe: Type): List[Tree] = fieldInfos.flatMap(_.map { fieldInfo =>
        val fTpe     = fieldInfo.tpe
        val schema   = findImplicitOrDeriveSchema(fTpe)
        val isNonRec = isNonRecursive(fTpe)
        val name     = fieldInfo.name
        val ms       = fieldInfo.modifiers.map(modifier => q"new ${modifier.tpe}(..${modifier.children.tail})")
        fieldInfo.defaultValue match {
          case Some(dv) =>
            if (ms eq Nil) {
              if (isNonRec) q"$schema.reflect.defaultValue($dv).asTerm[$sTpe]($name): Term[Binding, $sTpe, _]"
              else
                q"new Reflect.Deferred(() => $schema.reflect.defaultValue($dv)).asTerm[$sTpe]($name): Term[Binding, $sTpe, _]"
            } else if (isNonRec)
              q"($schema.reflect.defaultValue($dv).asTerm[$sTpe]($name): Term[Binding, $sTpe, _]).copy(modifiers = $ms)"
            else {
              q"(new Reflect.Deferred(() => $schema.reflect.defaultValue($dv)).asTerm[$sTpe]($name): Term[Binding, $sTpe, _]).copy(modifiers = $ms)"
            }
          case _ =>
            if (ms eq Nil) {
              if (isNonRec) q"$schema.reflect.asTerm[$sTpe]($name): Term[Binding, $sTpe, _]"
              else q"new Reflect.Deferred(() => $schema.reflect).asTerm[$sTpe]($name): Term[Binding, $sTpe, _]"
            } else if (isNonRec)
              q"($schema.reflect.asTerm[$sTpe]($name): Term[Binding, $sTpe, _]).copy(modifiers = $ms)"
            else
              q"(new Reflect.Deferred(() => $schema.reflect).asTerm[$sTpe]($name): Term[Binding, $sTpe, _]).copy(modifiers = $ms)"
        }
      })

      def constructor: Tree = {
        val argss = fieldInfos.map(_.map { fieldInfo =>
          val fTpe    = fieldInfo.tpe
          val bytes   = RegisterOffset.getBytes(fieldInfo.usedRegisters)
          val objects = RegisterOffset.getObjects(fieldInfo.usedRegisters)
          if (fTpe =:= definitions.IntTpe) q"in.getInt(baseOffset, $bytes)"
          else if (fTpe =:= definitions.FloatTpe) q"in.getFloat(baseOffset, $bytes)"
          else if (fTpe =:= definitions.LongTpe) q"in.getLong(baseOffset, $bytes)"
          else if (fTpe =:= definitions.DoubleTpe) q"in.getDouble(baseOffset, $bytes)"
          else if (fTpe =:= definitions.BooleanTpe) q"in.getBoolean(baseOffset, $bytes)"
          else if (fTpe =:= definitions.ByteTpe) q"in.getByte(baseOffset, $bytes)"
          else if (fTpe =:= definitions.CharTpe) q"in.getChar(baseOffset, $bytes)"
          else if (fTpe =:= definitions.ShortTpe) q"in.getShort(baseOffset, $bytes)"
          else if (fTpe =:= definitions.UnitTpe) q"()"
          else {
            val sTpe = dealiasOnDemand(fTpe)
            if (sTpe <:< definitions.IntTpe) q"in.getInt(baseOffset, $bytes).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.FloatTpe) q"in.getFloat(baseOffset, $bytes).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.LongTpe) q"in.getLong(baseOffset, $bytes).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.DoubleTpe) q"in.getDouble(baseOffset, $bytes).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.BooleanTpe) q"in.getBoolean(baseOffset, $bytes).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.ByteTpe) q"in.getByte(baseOffset, $bytes).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.CharTpe) q"in.getChar(baseOffset, $bytes).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.ShortTpe) q"in.getShort(baseOffset, $bytes).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.UnitTpe) q"().asInstanceOf[$fTpe]"
            else q"in.getObject(baseOffset, $objects).asInstanceOf[$fTpe]"
          }
        })
        q"new $tpe(...$argss)"
      }

      def deconstructor: List[Tree] = fieldInfos.flatMap(_.map { fieldInfo =>
        val fTpe    = fieldInfo.tpe
        val getter  = fieldInfo.getter
        val bytes   = RegisterOffset.getBytes(fieldInfo.usedRegisters)
        val objects = RegisterOffset.getObjects(fieldInfo.usedRegisters)
        if (fTpe <:< definitions.IntTpe) q"out.setInt(baseOffset, $bytes, in.$getter)"
        else if (fTpe <:< definitions.FloatTpe) q"out.setFloat(baseOffset, $bytes, in.$getter)"
        else if (fTpe <:< definitions.LongTpe) q"out.setLong(baseOffset, $bytes, in.$getter)"
        else if (fTpe <:< definitions.DoubleTpe) q"out.setDouble(baseOffset, $bytes, in.$getter)"
        else if (fTpe <:< definitions.BooleanTpe) q"out.setBoolean(baseOffset, $bytes, in.$getter)"
        else if (fTpe <:< definitions.ByteTpe) q"out.setByte(baseOffset, $bytes, in.$getter)"
        else if (fTpe <:< definitions.CharTpe) q"out.setChar(baseOffset, $bytes, in.$getter)"
        else if (fTpe <:< definitions.ShortTpe) q"out.setShort(baseOffset, $bytes, in.$getter)"
        else if (fTpe <:< definitions.UnitTpe) q"()"
        else if (fTpe <:< definitions.AnyRefTpe) q"out.setObject(baseOffset, $objects, in.$getter)"
        else {
          val sTpe = dealiasOnDemand(fTpe)
          if (sTpe <:< definitions.IntTpe) {
            q"out.setInt(baseOffset, $bytes, in.$getter.asInstanceOf[_root_.scala.Int])"
          } else if (sTpe <:< definitions.FloatTpe) {
            q"out.setFloat(baseOffset, $bytes, in.$getter.asInstanceOf[_root_.scala.Float])"
          } else if (sTpe <:< definitions.LongTpe) {
            q"out.setLong(baseOffset, $bytes, in.$getter.asInstanceOf[_root_.scala.Long])"
          } else if (sTpe <:< definitions.DoubleTpe) {
            q"out.setDouble(baseOffset, $bytes, in.$getter.asInstanceOf[_root_.scala.Double])"
          } else if (sTpe <:< definitions.BooleanTpe) {
            q"out.setBoolean(baseOffset, $bytes, in.$getter.asInstanceOf[_root_.scala.Boolean])"
          } else if (sTpe <:< definitions.ByteTpe) {
            q"out.setByte(baseOffset, $bytes, in.$getter.asInstanceOf[_root_.scala.Byte])"
          } else if (sTpe <:< definitions.CharTpe) {
            q"out.setChar(baseOffset, $bytes, in.$getter.asInstanceOf[_root_.scala.Char])"
          } else if (sTpe <:< definitions.ShortTpe) {
            q"out.setShort(baseOffset, $bytes, in.$getter.asInstanceOf[_root_.scala.Short])"
          } else if (sTpe <:< definitions.UnitTpe) q"()"
          else {
            q"out.setObject(baseOffset, $objects, in.$getter.asInstanceOf[_root_.scala.AnyRef])"
          }
        }
      })
    }

    // Helper function to recursively compute structural typename strings
    // Handles nested structural types by recursively expanding them
    def structuralTypeNameString(tpe: Type): String = {
      val dealt = tpe.dealias

      // Check if this is a structural refinement type
      if (isStructuralRefinementType(dealt)) {
        // Recursively extract and format fields
        val fields    = extractRefinementFields(dealt)
        val fieldStrs = fields.map { case (name, fTpe) =>
          val typeStr = structuralTypeNameString(fTpe)
          s"$name:$typeStr"
        }
        s"{${fieldStrs.mkString(",")}}"
      } else if (isTypeAlias(dealt)) {
        // If it's a type alias, check what it aliases to
        structuralTypeNameString(dealt.dealias)
      } else if (dealt.typeSymbol.fullName.startsWith("scala.Tuple")) {
        // Handle tuples: convert to structural form with _1, _2, _3, etc. fields
        val elements  = typeArgs(dealt)
        val fieldStrs = elements.zipWithIndex.map { case (elemTpe, idx) =>
          val fieldName = s"_${idx + 1}"
          val typeStr   = structuralTypeNameString(elemTpe)
          s"$fieldName:$typeStr"
        }
        s"{${fieldStrs.mkString(",")}}"
      } else {
        // For non-structural types, use the normal typename
        typeName(dealt).toSimpleName
      }
    }

    // RefinementInfo handles structural refinement types like:
    // StructuralRecord { def name: String; def age: Int }
    class RefinementInfo(tpe: Type) {
      val refinementFields: List[(String, Type)]                       = extractRefinementFields(tpe)
      val (fieldInfos: List[FieldInfo], usedRegisters: RegisterOffset) = {
        var usedRegisters = RegisterOffset.Zero
        (
          refinementFields.map { case (name, fTpe) =>
            val sTpe   = dealiasOnDemand(fTpe)
            val offset =
              if (sTpe <:< definitions.IntTpe) RegisterOffset(ints = 1)
              else if (sTpe <:< definitions.FloatTpe) RegisterOffset(floats = 1)
              else if (sTpe <:< definitions.LongTpe) RegisterOffset(longs = 1)
              else if (sTpe <:< definitions.DoubleTpe) RegisterOffset(doubles = 1)
              else if (sTpe <:< definitions.BooleanTpe) RegisterOffset(booleans = 1)
              else if (sTpe <:< definitions.ByteTpe) RegisterOffset(bytes = 1)
              else if (sTpe <:< definitions.CharTpe) RegisterOffset(chars = 1)
              else if (sTpe <:< definitions.ShortTpe) RegisterOffset(shorts = 1)
              else if (sTpe <:< definitions.UnitTpe) RegisterOffset.Zero
              else RegisterOffset(objects = 1)
            val fieldInfo = new FieldInfo(name, fTpe, None, null, usedRegisters, Nil)
            usedRegisters = RegisterOffset.add(usedRegisters, offset)
            fieldInfo
          },
          usedRegisters
        )
      }

      def fields(sTpe: Type): List[Tree] = fieldInfos.map { fieldInfo =>
        val fTpe     = fieldInfo.tpe
        val schema   = findImplicitOrDeriveSchema(fTpe)
        val isNonRec = isNonRecursive(fTpe)
        val name     = fieldInfo.name
        if (isNonRec) q"$schema.reflect.asTerm[$sTpe]($name): Term[Binding, $sTpe, _]"
        else q"new Reflect.Deferred(() => $schema.reflect).asTerm[$sTpe]($name): Term[Binding, $sTpe, _]"
      }

      // Compute field pairs for TypeName.structural
      // Uses recursive expansion for nested structural types
      def fieldPairs: List[(String, String)] = fieldInfos.map { fieldInfo =>
        val fTpe           = fieldInfo.tpe
        val simpleTypeName = structuralTypeNameString(fTpe)
        (fieldInfo.name, simpleTypeName)
      }

      // Constructor: Registers → StructuralRecord (wrapped as T)
      def constructor: Tree = {
        val entries = fieldInfos.map { fieldInfo =>
          val fTpe      = fieldInfo.tpe
          val bytes     = RegisterOffset.getBytes(fieldInfo.usedRegisters)
          val objects   = RegisterOffset.getObjects(fieldInfo.usedRegisters)
          val name      = fieldInfo.name
          val sTpe      = dealiasOnDemand(fTpe)
          val valueExpr =
            if (sTpe <:< definitions.IntTpe) q"in.getInt(baseOffset, $bytes)"
            else if (sTpe <:< definitions.FloatTpe) q"in.getFloat(baseOffset, $bytes)"
            else if (sTpe <:< definitions.LongTpe) q"in.getLong(baseOffset, $bytes)"
            else if (sTpe <:< definitions.DoubleTpe) q"in.getDouble(baseOffset, $bytes)"
            else if (sTpe <:< definitions.BooleanTpe) q"in.getBoolean(baseOffset, $bytes)"
            else if (sTpe <:< definitions.ByteTpe) q"in.getByte(baseOffset, $bytes)"
            else if (sTpe <:< definitions.CharTpe) q"in.getChar(baseOffset, $bytes)"
            else if (sTpe <:< definitions.ShortTpe) q"in.getShort(baseOffset, $bytes)"
            else if (sTpe <:< definitions.UnitTpe) q"()"
            else q"in.getObject(baseOffset, $objects)"
          q"($name, $valueExpr)"
        }
        q"new _root_.zio.blocks.schema.StructuralRecord(_root_.scala.collection.immutable.Map(..$entries)).asInstanceOf[$tpe]"
      }

      // Deconstructor: StructuralRecord → Registers (extract via selectDynamic)
      def deconstructor: List[Tree] = fieldInfos.map { fieldInfo =>
        val fTpe    = fieldInfo.tpe
        val name    = fieldInfo.name
        val bytes   = RegisterOffset.getBytes(fieldInfo.usedRegisters)
        val objects = RegisterOffset.getObjects(fieldInfo.usedRegisters)
        val sTpe    = dealiasOnDemand(fTpe)
        val getter  = q"in.asInstanceOf[_root_.zio.blocks.schema.StructuralRecord].selectDynamic($name)"
        if (sTpe <:< definitions.IntTpe) q"out.setInt(baseOffset, $bytes, $getter.asInstanceOf[_root_.scala.Int])"
        else if (sTpe <:< definitions.FloatTpe) {
          q"out.setFloat(baseOffset, $bytes, $getter.asInstanceOf[_root_.scala.Float])"
        } else if (sTpe <:< definitions.LongTpe) {
          q"out.setLong(baseOffset, $bytes, $getter.asInstanceOf[_root_.scala.Long])"
        } else if (sTpe <:< definitions.DoubleTpe) {
          q"out.setDouble(baseOffset, $bytes, $getter.asInstanceOf[_root_.scala.Double])"
        } else if (sTpe <:< definitions.BooleanTpe) {
          q"out.setBoolean(baseOffset, $bytes, $getter.asInstanceOf[_root_.scala.Boolean])"
        } else if (sTpe <:< definitions.ByteTpe) {
          q"out.setByte(baseOffset, $bytes, $getter.asInstanceOf[_root_.scala.Byte])"
        } else if (sTpe <:< definitions.CharTpe) {
          q"out.setChar(baseOffset, $bytes, $getter.asInstanceOf[_root_.scala.Char])"
        } else if (sTpe <:< definitions.ShortTpe) {
          q"out.setShort(baseOffset, $bytes, $getter.asInstanceOf[_root_.scala.Short])"
        } else if (sTpe <:< definitions.UnitTpe) q"()"
        else q"out.setObject(baseOffset, $objects, $getter.asInstanceOf[_root_.scala.AnyRef])"
      }
    }

    def deriveSchema(tpe: Type): Tree =
      if (isEnumOrModuleValue(tpe)) {
        deriveSchemaForEnumOrModuleValue(tpe)
      } else if (isCollection(tpe)) {
        if (tpe <:< typeOf[Array[?]]) {
          val elementTpe = typeArgs(tpe).head
          val schema     = findImplicitOrDeriveSchema(elementTpe)
          val tpeName    = toTree(typeName(tpe))
          q"""new Schema(
              reflect = new Reflect.Sequence(
                element = $schema.reflect,
                typeName = $tpeName.copy(params = List($schema.reflect.typeName)),
                seqBinding = new Binding.Seq(
                  constructor = new SeqConstructor.ArrayConstructor {
                    override def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                      new Builder(new Array[$elementTpe](sizeHint).asInstanceOf[Array[B]], 0)
                  },
                  deconstructor = SeqDeconstructor.arrayDeconstructor
                )
              )
            )"""
        } else if (tpe <:< typeOf[ArraySeq[?]]) {
          val elementTpe = typeArgs(tpe).head
          val schema     = findImplicitOrDeriveSchema(elementTpe)
          val tpeName    = toTree(typeName(tpe))
          q"""new Schema(
              reflect = new Reflect.Sequence(
                element = $schema.reflect,
                typeName = $tpeName.copy(params = List($schema.reflect.typeName)),
                seqBinding = new Binding.Seq(
                  constructor = new SeqConstructor.ArraySeqConstructor {
                    override def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                      new Builder(new Array[$elementTpe](sizeHint).asInstanceOf[Array[B]], 0)
                  },
                  deconstructor = SeqDeconstructor.arraySeqDeconstructor
                )
              )
            )"""
        } else if (tpe <:< typeOf[List[?]]) {
          val schema = findImplicitOrDeriveSchema(typeArgs(tpe).head)
          q"Schema.list($schema)"
        } else if (tpe <:< typeOf[Map[?, ?]]) {
          val tpeTypeArgs = typeArgs(tpe)
          val kSchema     = findImplicitOrDeriveSchema(tpeTypeArgs.head)
          val vSchema     = findImplicitOrDeriveSchema(tpeTypeArgs.last)
          q"Schema.map($kSchema,$vSchema)"
        } else if (tpe <:< typeOf[Set[?]]) {
          val schema = findImplicitOrDeriveSchema(typeArgs(tpe).head)
          q"Schema.set($schema)"
        } else if (tpe <:< typeOf[Vector[?]]) {
          val schema = findImplicitOrDeriveSchema(typeArgs(tpe).head)
          q"Schema.vector($schema)"
        } else if (tpe <:< typeOf[IndexedSeq[?]]) {
          val schema = findImplicitOrDeriveSchema(typeArgs(tpe).head)
          q"Schema.indexedSeq($schema)"
        } else if (tpe <:< typeOf[Seq[?]]) {
          val schema = findImplicitOrDeriveSchema(typeArgs(tpe).head)
          q"Schema.seq($schema)"
        } else cannotDeriveSchema(tpe)
      } else if (isOption(tpe)) {
        if (tpe <:< typeOf[None.type]) deriveSchemaForEnumOrModuleValue(tpe)
        else if (tpe <:< typeOf[Some[?]]) deriveSchemaForNonAbstractScalaClass(tpe)
        else {
          val vTpe = typeArgs(tpe).head
          if (vTpe =:= definitions.IntTpe) q"Schema.optionInt"
          else if (vTpe =:= definitions.FloatTpe) q"Schema.optionFloat"
          else if (vTpe =:= definitions.LongTpe) q"Schema.optionLong"
          else if (vTpe =:= definitions.DoubleTpe) q"Schema.optionDouble"
          else if (vTpe =:= definitions.BooleanTpe) q"Schema.optionBoolean"
          else if (vTpe =:= definitions.ByteTpe) q"Schema.optionByte"
          else if (vTpe =:= definitions.CharTpe) q"Schema.optionChar"
          else if (vTpe =:= definitions.ShortTpe) q"Schema.optionShort"
          else if (vTpe =:= definitions.UnitTpe) q"Schema.optionUnit"
          else if (vTpe <:< definitions.AnyRefTpe && !isZioPreludeNewtype(vTpe)) {
            val schema = findImplicitOrDeriveSchema(vTpe)
            q"Schema.option($schema)"
          } else deriveSchemaForSealedTraitOrAbstractClass(tpe)
        }
      } else if (isSealedTraitOrAbstractClass(tpe)) {
        deriveSchemaForSealedTraitOrAbstractClass(tpe)
      } else if (isStructuralRefinementType(tpe)) {
        deriveSchemaForRefinementType(tpe)
      } else if (isTypeAlias(tpe)) {
        // Handle type aliases (like AddressStructure) by dealiasing and recursively deriving
        deriveSchema(tpe.dealias)
      } else if (isNonAbstractScalaClass(tpe)) {
        deriveSchemaForNonAbstractScalaClass(tpe)
      } else if (isZioPreludeNewtype(tpe)) {
        val schema  = findImplicitOrDeriveSchema(zioPreludeNewtypeDealias(tpe))
        val tpeName = toTree(typeName(tpe))
        q"new Schema($schema.reflect.typeName($tpeName)).asInstanceOf[Schema[$tpe]]"
      } else cannotDeriveSchema(tpe)

    def deriveSchemaForEnumOrModuleValue(tpe: Type): Tree = {
      val tpeName = toTree(typeName(tpe))
      q"""new Schema(
            reflect = new Reflect.Record[Binding, $tpe](
              fields = _root_.scala.Vector.empty,
              typeName = $tpeName,
              recordBinding = new Binding.Record(
                constructor = new ConstantConstructor[$tpe](${tpe.typeSymbol.asClass.module}),
                deconstructor = new ConstantDeconstructor[$tpe]
              ),
              modifiers = ${modifiers(tpe)}
            )
          )"""
    }

    def deriveSchemaForNonAbstractScalaClass(tpe: Type): Tree = {
      val classInfo = new ClassInfo(tpe)
      val tpeName   = toTree(typeName(tpe))
      q"""new Schema(
            reflect = new Reflect.Record[Binding, $tpe](
              fields = _root_.scala.Vector(..${classInfo.fields(tpe)}),
              typeName = $tpeName,
              recordBinding = new Binding.Record(
                constructor = new Constructor[$tpe] {
                  def usedRegisters: RegisterOffset = ${classInfo.usedRegisters}

                  def construct(in: Registers, baseOffset: RegisterOffset): $tpe = ${classInfo.constructor}
                },
                deconstructor = new Deconstructor[$tpe] {
                  def usedRegisters: RegisterOffset = ${classInfo.usedRegisters}

                  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: $tpe): _root_.scala.Unit = {
                    ..${classInfo.deconstructor}
                  }
                }
              ),
              modifiers = ${modifiers(tpe)},
            )
          )"""
    }

    // Derive schema for structural refinement types like:
    // StructuralRecord { def name: String; def age: Int }
    def deriveSchemaForRefinementType(tpe: Type): Tree = {
      val refinementInfo = new RefinementInfo(tpe)
      val fieldPairs     = refinementInfo.fieldPairs
      q"""new Schema(
            reflect = new Reflect.Record[Binding, $tpe](
              fields = _root_.scala.Vector(..${refinementInfo.fields(tpe)}),
              typeName = TypeName.structural[$tpe]($fieldPairs),
              recordBinding = new Binding.Record(
                constructor = new Constructor[$tpe] {
                  def usedRegisters: RegisterOffset = ${refinementInfo.usedRegisters}

                  def construct(in: Registers, baseOffset: RegisterOffset): $tpe = ${refinementInfo.constructor}
                },
                deconstructor = new Deconstructor[$tpe] {
                  def usedRegisters: RegisterOffset = ${refinementInfo.usedRegisters}

                  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: $tpe): _root_.scala.Unit = {
                    ..${refinementInfo.deconstructor}
                  }
                }
              )
            )
          )"""
    }

    def deriveSchemaForSealedTraitOrAbstractClass(tpe: Type): Tree = {
      val subtypes = directSubTypes(tpe)
      if (subtypes.isEmpty) fail(s"Cannot find sub-types for ADT base '$tpe'.")
      val fullTermNames         = subtypes.map(sTpe => toFullTermName(typeName(sTpe)))
      val maxCommonPrefixLength = {
        val minFullTermName = fullTermNames.min
        val maxFullTermName = fullTermNames.max
        val minLength       = Math.min(minFullTermName.length, maxFullTermName.length) - 1
        var idx             = 0
        while (idx < minLength && minFullTermName(idx).equals(maxFullTermName(idx))) idx += 1
        idx
      }
      val cases = subtypes.zip(fullTermNames).map { case (sTpe, fullName) =>
        val modifiers = sTpe.typeSymbol.annotations.collect { case a if a.tree.tpe <:< typeOf[Modifier.Term] => a.tree }
        val caseName  = toShortTermName(fullName, maxCommonPrefixLength)
        val schema    = findImplicitOrDeriveSchema(sTpe)
        if (modifiers eq Nil) q"$schema.reflect.asTerm($caseName)"
        else {
          val ms = modifiers.map(modifier => q"new ${modifier.tpe}(..${modifier.children.tail})")
          q"$schema.reflect.asTerm($caseName).copy(modifiers = $ms)"
        }
      }
      val discrCases = subtypes.map {
        var idx = -1
        sTpe =>
          idx += 1
          cq"_: $sTpe @_root_.scala.unchecked => $idx"
      }
      val matcherCases = subtypes.map { sTpe =>
        q"""new Matcher[$sTpe] {
              def downcastOrNull(a: Any): $sTpe = a match {
                case x: $sTpe @_root_.scala.unchecked => x
                case _ => null.asInstanceOf[$sTpe]
              }
            }"""
      }
      val tpeName = toTree(typeName(tpe))
      q"""new Schema(
            reflect = new Reflect.Variant[Binding, $tpe](
              cases = _root_.scala.Vector(..$cases),
              typeName = $tpeName,
              variantBinding = new Binding.Variant(
                discriminator = new Discriminator[$tpe] {
                  def discriminate(a: $tpe): Int = a match {
                    case ..$discrCases
                  }
                },
                matchers = Matchers(..$matcherCases),
              ),
              modifiers = ${modifiers(tpe)}
            )
          )"""
    }

    def toFullTermName(tpeName: SchemaTypeName[?]): Array[String] = {
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

    def cannotDeriveSchema(tpe: Type): Nothing = fail(s"Cannot derive schema for '$tpe'.")

    val schema      = deriveSchema(weakTypeOf[A].dealias)
    val schemaBlock =
      q"""{
            import _root_.zio.blocks.schema._
            import _root_.zio.blocks.schema.binding._
            import _root_.zio.blocks.schema.binding.RegisterOffset._

            ..$schemaDefs
            $schema
          }"""
    // c.info(c.enclosingPosition, s"Generated schema:\n${showCode(schemaBlock)}", force = true)
    c.Expr[Schema[A]](schemaBlock)
  }
}
