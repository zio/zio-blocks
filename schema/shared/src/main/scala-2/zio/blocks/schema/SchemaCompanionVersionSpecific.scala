package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.CommonMacroOps
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.NameTransformer

trait SchemaCompanionVersionSpecific {
  def derived[A]: Schema[A] = macro SchemaCompanionVersionSpecific.derived[A]
}

private object SchemaCompanionVersionSpecific {
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

    def isZioPreludeNewtype(tpe: Type): Boolean = {
      def check(t: Type): Boolean = t match {
        case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
          compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
        case _ => false
      }
      check(tpe) || check(tpe.dealias)
    }

    def zioPreludeNewtypeDealias(tpe: Type): Type = {
      val effectiveTpe = tpe.dealias
      effectiveTpe match {
        case TypeRef(compTpe, _, _) =>
          compTpe.baseClasses.find(_.fullName == "zio.prelude.Newtype") match {
            case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
            case _         => cannotDealiasZioPreludeNewtype(tpe)
          }
        case _ => cannotDealiasZioPreludeNewtype(tpe)
      }
    }

    def cannotDealiasZioPreludeNewtype(tpe: Type): Nothing = fail(s"Cannot dealias zio-prelude newtype '$tpe'.")

    def buildTypeIdForZioPreludeNewtype(tpe: Type): Tree = {
      val effectiveTpe = tpe.dealias
      effectiveTpe match {
        case TypeRef(compTpe, _, Nil) =>
          val companionSym = compTpe.typeSymbol
          val newtypeName  = companionSym.name.decodedName.toString.stripSuffix("$")
          val ownerTree    = buildOwner(companionSym.owner)
          q"_root_.zio.blocks.typeid.TypeId.nominal[$tpe]($newtypeName, $ownerTree)"
        case _ =>
          fail(s"Cannot build TypeId for zio-prelude newtype '$tpe'. Expected TypeRef(compTpe, \"Type\", Nil).")
      }
    }

    def buildOwner(sym: Symbol): Tree = {
      def loop(s: Symbol, acc: List[Tree]): List[Tree] =
        if (s == NoSymbol || s.isPackageClass && s.fullName == "<root>" || s.fullName == "<empty>") {
          acc
        } else if (s.isPackage || s.isPackageClass) {
          val pkgName = s.name.decodedName.toString
          if (pkgName != "<root>" && pkgName != "<empty>") {
            loop(s.owner, q"_root_.zio.blocks.typeid.Owner.Package($pkgName)" :: acc)
          } else {
            acc
          }
        } else if (s.isModule || s.isModuleClass) {
          val termName = s.name.decodedName.toString.stripSuffix("$")
          loop(s.owner, q"_root_.zio.blocks.typeid.Owner.Term($termName)" :: acc)
        } else if (s.isClass || s.isType) {
          loop(s.owner, q"_root_.zio.blocks.typeid.Owner.Type(${s.name.decodedName.toString})" :: acc)
        } else {
          loop(s.owner, acc)
        }

      val segments = loop(sym, Nil)
      q"_root_.zio.blocks.typeid.Owner(_root_.scala.List(..$segments))"
    }

    def dealiasOnDemand(tpe: Type): Type =
      if (isZioPreludeNewtype(tpe)) zioPreludeNewtypeDealias(tpe)
      else if (isTypeAlias(tpe)) tpe.dealias
      else tpe

    def isTypeAlias(tpe: Type): Boolean = {
      val dealiased = tpe.dealias
      tpe.toString != dealiased.toString && !isZioPreludeNewtype(tpe)
    }

    def companion(tpe: Type): Symbol = {
      val comp = tpe.typeSymbol.companion
      if (comp.isModule) comp
      else {
        val ownerChainOf = (s: Symbol) => Iterator.iterate(s)(_.owner).takeWhile(_ != NoSymbol).toArray.reverseIterator
        val path         = ownerChainOf(tpe.typeSymbol)
          .zipAll(ownerChainOf(c.internal.enclosingOwner), NoSymbol, NoSymbol)
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
          else if (isNonAbstractScalaClass(tpe)) {
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

    val fullTermNameCache = new mutable.HashMap[Type, Array[String]]

    def typeName(tpe: Type): SchemaTypeName[?] = CommonMacroOps.typeName(c)(typeNameCache, tpe)

    def toTree(tpeName: SchemaTypeName[?]): Tree = CommonMacroOps.toTree(c)(tpeName)

    def toFullTermName(tpe: Type): Array[String] = {
      def calculate(tpe: Type): Array[String] =
        if (tpe =:= typeOf[java.lang.String]) Array("java", "lang", "String")
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
          val result = new Array[String](packages.size + values.size + 1)
          var idx    = 0
          packages.foreach { p =>
            result(idx) = p
            idx += 1
          }
          values.foreach { v =>
            result(idx) = v
            idx += 1
          }
          result(idx) = name
          result
        }

      fullTermNameCache.getOrElseUpdate(
        tpe,
        tpe match {
          case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
            val result   = calculate(compTpe)
            val lastName = result(result.length - 1)
            if (lastName.endsWith(".type")) result(result.length - 1) = lastName.stripSuffix(".type")
            result
          case _ =>
            calculate(tpe)
        }
      )
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
              q"new Reflect.Deferred(() => $schema.reflect).defaultValue($dv).asTerm[$sTpe]($name)"
            } else {
              q"new Reflect.Deferred(() => $schema.reflect).defaultValue($dv).asTerm[$sTpe]($name).copy(modifiers = $ms)"
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
          val fTpe          = fieldInfo.tpe
          val usedRegisters = fieldInfo.usedRegisters
          if (fTpe =:= definitions.IntTpe) q"in.getInt(offset + $usedRegisters)"
          else if (fTpe =:= definitions.FloatTpe) q"in.getFloat(offset + $usedRegisters)"
          else if (fTpe =:= definitions.LongTpe) q"in.getLong(offset + $usedRegisters)"
          else if (fTpe =:= definitions.DoubleTpe) q"in.getDouble(offset + $usedRegisters)"
          else if (fTpe =:= definitions.BooleanTpe) q"in.getBoolean(offset + $usedRegisters)"
          else if (fTpe =:= definitions.ByteTpe) q"in.getByte(offset + $usedRegisters)"
          else if (fTpe =:= definitions.CharTpe) q"in.getChar(offset + $usedRegisters)"
          else if (fTpe =:= definitions.ShortTpe) q"in.getShort(offset + $usedRegisters)"
          else if (fTpe =:= definitions.UnitTpe) q"()"
          else {
            val sTpe = dealiasOnDemand(fTpe)
            if (sTpe <:< definitions.IntTpe) q"in.getInt(offset + $usedRegisters).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.FloatTpe) q"in.getFloat(offset + $usedRegisters).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.LongTpe) q"in.getLong(offset + $usedRegisters).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.DoubleTpe) q"in.getDouble(offset + $usedRegisters).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.BooleanTpe) q"in.getBoolean(offset + $usedRegisters).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.ByteTpe) q"in.getByte(offset + $usedRegisters).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.CharTpe) q"in.getChar(offset + $usedRegisters).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.ShortTpe) q"in.getShort(offset + $usedRegisters).asInstanceOf[$fTpe]"
            else if (sTpe <:< definitions.UnitTpe) q"().asInstanceOf[$fTpe]"
            else q"in.getObject(offset + $usedRegisters).asInstanceOf[$fTpe]"
          }
        })
        q"new $tpe(...$argss)"
      }

      def deconstructor: List[Tree] = fieldInfos.flatMap(_.map { fieldInfo =>
        val fTpe          = fieldInfo.tpe
        val getter        = fieldInfo.getter
        val usedRegisters = fieldInfo.usedRegisters
        if (fTpe <:< definitions.IntTpe) q"out.setInt(offset + $usedRegisters, in.$getter)"
        else if (fTpe <:< definitions.FloatTpe) q"out.setFloat(offset + $usedRegisters, in.$getter)"
        else if (fTpe <:< definitions.LongTpe) q"out.setLong(offset + $usedRegisters, in.$getter)"
        else if (fTpe <:< definitions.DoubleTpe) q"out.setDouble(offset + $usedRegisters, in.$getter)"
        else if (fTpe <:< definitions.BooleanTpe) q"out.setBoolean(offset + $usedRegisters, in.$getter)"
        else if (fTpe <:< definitions.ByteTpe) q"out.setByte(offset + $usedRegisters, in.$getter)"
        else if (fTpe <:< definitions.CharTpe) q"out.setChar(offset + $usedRegisters, in.$getter)"
        else if (fTpe <:< definitions.ShortTpe) q"out.setShort(offset + $usedRegisters, in.$getter)"
        else if (fTpe <:< definitions.UnitTpe) q"()"
        else if (fTpe <:< definitions.AnyRefTpe) q"out.setObject(offset + $usedRegisters, in.$getter)"
        else {
          val sTpe = dealiasOnDemand(fTpe)
          if (sTpe <:< definitions.IntTpe) {
            q"out.setInt(offset + $usedRegisters, in.$getter.asInstanceOf[_root_.scala.Int])"
          } else if (sTpe <:< definitions.FloatTpe) {
            q"out.setFloat(offset + $usedRegisters, in.$getter.asInstanceOf[_root_.scala.Float])"
          } else if (sTpe <:< definitions.LongTpe) {
            q"out.setLong(offset + $usedRegisters, in.$getter.asInstanceOf[_root_.scala.Long])"
          } else if (sTpe <:< definitions.DoubleTpe) {
            q"out.setDouble(offset + $usedRegisters, in.$getter.asInstanceOf[_root_.scala.Double])"
          } else if (sTpe <:< definitions.BooleanTpe) {
            q"out.setBoolean(offset + $usedRegisters, in.$getter.asInstanceOf[_root_.scala.Boolean])"
          } else if (sTpe <:< definitions.ByteTpe) {
            q"out.setByte(offset + $usedRegisters, in.$getter.asInstanceOf[_root_.scala.Byte])"
          } else if (sTpe <:< definitions.CharTpe) {
            q"out.setChar(offset + $usedRegisters, in.$getter.asInstanceOf[_root_.scala.Char])"
          } else if (sTpe <:< definitions.ShortTpe) {
            q"out.setShort(offset + $usedRegisters, in.$getter.asInstanceOf[_root_.scala.Short])"
          } else if (sTpe <:< definitions.UnitTpe) q"()"
          else q"out.setObject(offset + $usedRegisters, in.$getter.asInstanceOf[_root_.scala.AnyRef])"
        }
      })
    }

    def deriveSchema(tpe: Type): Tree =
      if (isEnumOrModuleValue(tpe)) {
        deriveSchemaForEnumOrModuleValue(tpe)
      } else if (isCollection(tpe)) {
        if (tpe <:< typeOf[Array[?]]) {
          val elementTpe = typeArgs(tpe).head
          val copyOfTpe  =
            if (elementTpe <:< definitions.UnitTpe) definitions.AnyRefTpe
            else elementTpe
          val schema = findImplicitOrDeriveSchema(elementTpe)
          q"""new Schema(
              reflect = new Reflect.Sequence(
                element = $schema.reflect,
                typeId = zio.blocks.typeid.TypeId.of[$tpe],
                seqBinding = new Binding.Seq(
                  constructor = new SeqConstructor.ArrayConstructor {
                    def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                      new Builder(new Array[$elementTpe](Math.max(sizeHint, 1)).asInstanceOf[Array[B]], 0)

                    def addObject[B](builder: ObjectBuilder[B], a: B): Unit = {
                      var buf = builder.buffer
                      val idx = builder.size
                      if (buf.length == idx) {
                        buf = java.util.Arrays.copyOf(buf.asInstanceOf[Array[$copyOfTpe]], idx << 1).asInstanceOf[Array[B]]
                        builder.buffer = buf
                      }
                      buf(idx) = a
                      builder.size = idx + 1
                    }

                    def resultObject[B](builder: ObjectBuilder[B]): Array[B] = {
                      val buf  = builder.buffer
                      val size = builder.size
                      if (buf.length == size) buf
                      else java.util.Arrays.copyOf(buf.asInstanceOf[Array[$copyOfTpe]], size).asInstanceOf[Array[B]]
                    }

                    def emptyObject[B]: Array[B] = Array.empty[$elementTpe].asInstanceOf[Array[B]]
                  },
                  deconstructor = SeqDeconstructor.arrayDeconstructor
                )
              )
            )"""
        } else if (tpe <:< typeOf[ArraySeq[?]]) {
          val elementTpe = typeArgs(tpe).head
          val copyOfTpe  =
            if (elementTpe <:< definitions.UnitTpe) definitions.AnyRefTpe
            else elementTpe
          val schema = findImplicitOrDeriveSchema(elementTpe)
          q"""new Schema(
              reflect = new Reflect.Sequence(
                element = $schema.reflect,
                typeId = zio.blocks.typeid.TypeId.of[$tpe],
                seqBinding = new Binding.Seq(
                  constructor = new SeqConstructor.ArraySeqConstructor {
                    def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                      new Builder(new Array[$elementTpe](Math.max(sizeHint, 1)).asInstanceOf[Array[B]], 0)

                    def addObject[B](builder: ObjectBuilder[B], a: B): Unit = {
                      var buf = builder.buffer
                      val idx = builder.size
                      if (buf.length == idx) {
                        buf = java.util.Arrays.copyOf(buf.asInstanceOf[Array[$copyOfTpe]], idx << 1).asInstanceOf[Array[B]]
                        builder.buffer = buf
                      }
                      buf(idx) = a
                      builder.size = idx + 1
                    }

                    def resultObject[B](builder: ObjectBuilder[B]): ArraySeq[B] = ArraySeq.unsafeWrapArray {
                      val buf  = builder.buffer
                      val size = builder.size
                      if (buf.length == size) buf
                      else java.util.Arrays.copyOf(buf.asInstanceOf[Array[$copyOfTpe]], size).asInstanceOf[Array[B]]
                    }

                    def emptyObject[B]: ArraySeq[B] = ArraySeq.empty[$elementTpe].asInstanceOf[ArraySeq[B]]
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
      } else if (isZioPreludeNewtype(tpe)) {
        val sTpe      = zioPreludeNewtypeDealias(tpe)
        val schema    = findImplicitOrDeriveSchema(sTpe)
        val newtypeId = buildTypeIdForZioPreludeNewtype(tpe)
        q"new Schema($schema.reflect.typeId($newtypeId.asInstanceOf[_root_.zio.blocks.typeid.TypeId[$sTpe]])).asInstanceOf[Schema[$tpe]]"
      } else if (isTypeAlias(tpe)) {
        val sTpe = tpe.dealias
        // Register the original type first to prevent circular implicit lookup
        val name = TermName("s" + schemaRefs.size)
        val ref  = Ident(name)
        schemaRefs.update(tpe, ref)
        // Also register the dealiased type to prevent finding the schema we're defining
        schemaRefs.update(sTpe, ref)
        val underlyingSchema = deriveSchema(sTpe)
        val schemaTpe        = tq"_root_.zio.blocks.schema.Schema[$tpe]"
        schemaDefs.addOne {
          q"implicit val $name: $schemaTpe = new Schema($underlyingSchema.reflect.typeId(_root_.zio.blocks.typeid.TypeId.of[$tpe].asInstanceOf[_root_.zio.blocks.typeid.TypeId[$sTpe]])).asInstanceOf[Schema[$tpe]]"
        }
        ref
      } else if (isNonAbstractScalaClass(tpe)) {
        deriveSchemaForNonAbstractScalaClass(tpe)
      } else cannotDeriveSchema(tpe)

    def deriveSchemaForEnumOrModuleValue(tpe: Type): Tree =
      q"""new Schema(
            reflect = new Reflect.Record[Binding, $tpe](
              fields = _root_.scala.Vector.empty,
              typeId = zio.blocks.typeid.TypeId.of[$tpe],
              recordBinding = new Binding.Record(
                constructor = new ConstantConstructor[$tpe](${tpe.typeSymbol.asClass.module}),
                deconstructor = new ConstantDeconstructor[$tpe]
              ),
              modifiers = ${modifiers(tpe)}
            )
          )"""

    def deriveSchemaForNonAbstractScalaClass(tpe: Type): Tree = {
      val classInfo = new ClassInfo(tpe)
      q"""new Schema(
            reflect = new Reflect.Record[Binding, $tpe](
              fields = _root_.scala.Vector(..${classInfo.fields(tpe)}),
              typeId = zio.blocks.typeid.TypeId.of[$tpe],
              recordBinding = new Binding.Record(
                constructor = new Constructor[$tpe] {
                  def usedRegisters: RegisterOffset = ${classInfo.usedRegisters}

                  def construct(in: Registers, offset: RegisterOffset): $tpe = ${classInfo.constructor}
                },
                deconstructor = new Deconstructor[$tpe] {
                  def usedRegisters: RegisterOffset = ${classInfo.usedRegisters}

                  def deconstruct(out: Registers, offset: RegisterOffset, in: $tpe): _root_.scala.Unit = {
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
      val fullTermNames         = subtypes.map(toFullTermName)
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
      q"""new Schema(
            reflect = new Reflect.Variant[Binding, $tpe](
              cases = _root_.scala.Vector(..$cases),
              typeId = zio.blocks.typeid.TypeId.of[$tpe],
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

    def cannotDeriveSchema(tpe: Type): Nothing = fail(
      s"Cannot derive schema for '$tpe'. Symbol: ${tpe.typeSymbol}, isTypeAlias: ${isTypeAlias(tpe)}, isZioPreludeNewtype: ${isZioPreludeNewtype(tpe)}"
    )

    val tpeA        = weakTypeOf[A]
    val schema      = deriveSchema(tpeA)
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
