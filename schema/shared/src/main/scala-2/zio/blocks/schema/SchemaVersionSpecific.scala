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

    def isRefinedType(tpe: Type): Boolean = tpe match {
      case RefinedType(_, _) => true
      case _                 => false
    }

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
          else if (isRefinedType(tpe)) {
            tpe.decls.filter(_.isMethod).forall { m =>
              isNonRecursive(m.asMethod.returnType, nestedTpes_)
            }
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
        else if (isRefinedType(tpe)) {
          val methods = tpe.decls.toList.collect {
            case m: MethodSymbol if m.isGetter || (m.paramLists.isEmpty && !m.isConstructor) => m
          }.sortBy(_.name.toString)

          val parts = methods.map { m =>
            val name        = NameTransformer.decode(m.name.toString)
            val retType     = m.returnType.dealias
            val retTypeName = typeName(retType).name
            s"$name: $retTypeName"
          }

          val normalizedName = parts.mkString("{", ", ", "}")
          new SchemaTypeName(new Namespace(List.empty, List.empty), normalizedName, Nil)
        } else {
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
              if (isNonRec) q"$schema.reflect.defaultValue($dv).asTerm[$sTpe]($name)"
              else q"new Reflect.Deferred(() => $schema.reflect.defaultValue($dv)).asTerm[$sTpe]($name)"
            } else if (isNonRec) q"$schema.reflect.defaultValue($dv).asTerm[$sTpe]($name).copy(modifiers = $ms)"
            else {
              q"new Reflect.Deferred(() => $schema.reflect.defaultValue($dv)).asTerm[$sTpe]($name).copy(modifiers = $ms)"
            }
          case _ =>
            if (ms eq Nil) {
              if (isNonRec) q"$schema.reflect.asTerm[$sTpe]($name)"
              else q"new Reflect.Deferred(() => $schema.reflect).asTerm[$sTpe]($name)"
            } else if (isNonRec) q"$schema.reflect.asTerm[$sTpe]($name).copy(modifiers = $ms)"
            else q"new Reflect.Deferred(() => $schema.reflect).asTerm[$sTpe]($name).copy(modifiers = $ms)"
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

    def deriveSchema(tpe: Type): Tree =
      if (isEnumOrModuleValue(tpe)) {
        deriveSchemaForEnumOrModuleValue(tpe)
      } else if (isCollection(tpe)) {
        if (tpe <:< typeOf[Array[?]]) {
          val elementTpe  = typeArgs(tpe).head
          val schema      = findImplicitOrDeriveSchema(elementTpe)
          val constructor =
            if (elementTpe <:< definitions.AnyRefTpe) {
              q"""new SeqConstructor.ArrayConstructor {
                  override def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                    new Builder(new Array[$elementTpe](sizeHint).asInstanceOf[Array[B]], 0)
                }"""
            } else q"SeqConstructor.arrayConstructor"
          val tpeName = toTree(typeName(tpe))
          q"""new Schema(
              reflect = new Reflect.Sequence(
                element = $schema.reflect,
                typeName = $tpeName.copy(params = List($schema.reflect.typeName)),
                seqBinding = new Binding.Seq(
                  constructor = $constructor,
                  deconstructor = SeqDeconstructor.arrayDeconstructor
                )
              )
            )"""
        } else if (tpe <:< typeOf[ArraySeq[?]]) {
          val schema = findImplicitOrDeriveSchema(typeArgs(tpe).head)
          q"Schema.arraySeq($schema)"
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
      } else if (isRefinedType(tpe)) {
        deriveSchemaForRefinedType(tpe)
      } else if (isSealedTraitOrAbstractClass(tpe)) {
        deriveSchemaForSealedTraitOrAbstractClass(tpe)
      } else if (isNonAbstractScalaClass(tpe)) {
        deriveSchemaForNonAbstractScalaClass(tpe)
      } else if (isZioPreludeNewtype(tpe)) {
        val schema  = findImplicitOrDeriveSchema(zioPreludeNewtypeDealias(tpe))
        val tpeName = toTree(typeName(tpe))
        q"new Schema($schema.reflect.typeName($tpeName)).asInstanceOf[Schema[$tpe]]"
      } else cannotDeriveSchema(tpe)

    def deriveSchemaForRefinedType(tpe: Type): Tree = {
      val (fields, totalRegisters) = {
        var usedRegisters = RegisterOffset.Zero
        val fieldDefs     = tpe.decls.toList.sortBy(_.name.toString).collect {
          case m: MethodSymbol if m.isGetter || (m.paramLists.isEmpty && !m.isConstructor) =>
            val name   = NameTransformer.decode(m.name.toString)
            val fTpe   = m.returnType.dealias
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

            val primitiveType =
              if (sTpe <:< definitions.BooleanTpe) q"StructuralFieldInfo.Boolean"
              else if (sTpe <:< definitions.ByteTpe) q"StructuralFieldInfo.Byte"
              else if (sTpe <:< definitions.ShortTpe) q"StructuralFieldInfo.Short"
              else if (sTpe <:< definitions.IntTpe) q"StructuralFieldInfo.Int"
              else if (sTpe <:< definitions.LongTpe) q"StructuralFieldInfo.Long"
              else if (sTpe <:< definitions.FloatTpe) q"StructuralFieldInfo.Float"
              else if (sTpe <:< definitions.DoubleTpe) q"StructuralFieldInfo.Double"
              else if (sTpe <:< definitions.CharTpe) q"StructuralFieldInfo.Char"
              else q"StructuralFieldInfo.Object"

            val fieldInfo = q"""
              StructuralFieldInfo(
                $name,
                ${usedRegisters},
                $primitiveType
              )
            """

            // For the schema derivation later
            val schemaRef   = findImplicitOrDeriveSchema(fTpe)
            val schemaField = if (isNonRecursive(fTpe)) {
              q"$schemaRef.reflect.asTerm[$tpe]($name)"
            } else {
              q"new Reflect.Deferred(() => $schemaRef.reflect).asTerm[$tpe]($name)"
            }

            usedRegisters = RegisterOffset.add(usedRegisters, offset)
            (fieldInfo, schemaField)
        }
        (fieldDefs, usedRegisters)
      }

      val structuralFields = fields.map(_._1)
      val schemaFields     = fields.map(_._2)

      val tpeName = toTree(typeName(tpe))

      q"""new Schema(
            reflect = new Reflect.Record[Binding, $tpe](
              fields = _root_.scala.Vector(..$schemaFields),
              typeName = $tpeName,
              recordBinding = new Binding.Record(
                constructor = new StructuralConstructor[$tpe](
                  _root_.scala.IndexedSeq(..$structuralFields),
                  $totalRegisters
                ),
                deconstructor = new StructuralDeconstructor[$tpe](
                   _root_.scala.IndexedSeq(..$structuralFields),
                   $totalRegisters
                )
              ),
              modifiers = _root_.scala.List.empty
            )
          )"""
    }

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
