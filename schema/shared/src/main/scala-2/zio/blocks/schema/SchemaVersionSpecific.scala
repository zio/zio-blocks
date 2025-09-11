package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.{TypeName => SchemaTypeName}
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

    def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    def isEnumOrModuleValue(tpe: Type): Boolean = tpe.typeSymbol.isModuleClass

    def isSealedTraitOrAbstractClass(tpe: Type): Boolean = tpe.typeSymbol.isClass && {
      val classSymbol = tpe.typeSymbol.asClass
      classSymbol.isSealed && (classSymbol.isAbstract || classSymbol.isTrait)
    }

    def isNonAbstractScalaClass(tpe: Type): Boolean =
      tpe.typeSymbol.isClass && !tpe.typeSymbol.isAbstract && !tpe.typeSymbol.isJava

    def isValueClass(tpe: Type): Boolean = tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isDerivedValueClass

    def typeArgs(tpe: Type): List[Type] = tpe.typeArgs.map(_.dealias)

    def isCollection(tpe: Type): Boolean = tpe <:< typeOf[Array[?]] ||
      (tpe <:< typeOf[IterableOnce[?]] && tpe.typeSymbol.fullName.startsWith("scala.collection."))

    def isZioPreludeNewtype(tpe: Type): Boolean = tpe match {
      case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
        compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
      case _ => false
    }

    def zioPreludeNewtypeDealias(tpe: Type): Type = {
      def cannotDealias(tpe: Type): Nothing = fail(s"Cannot dealias zio-prelude newtype '$tpe'.")

      tpe match {
        case TypeRef(compTpe, _, _) =>
          compTpe.baseClasses.collectFirst {
            case cls if cls.fullName == "zio.prelude.Newtype" =>
              compTpe.baseType(cls) match {
                case TypeRef(_, _, List(typeArg)) => typeArg.dealias
                case _                            => cannotDealias(tpe)
              }
          }
            .getOrElse(cannotDealias(tpe))
        case _ => cannotDealias(tpe)
      }
    }

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

    implicit lazy val positionOrdering: Ordering[Symbol] =
      (x: Symbol, y: Symbol) => {
        val xPos  = x.pos
        val yPos  = y.pos
        val xFile = xPos.source.file.absolute
        val yFile = yPos.source.file.absolute
        var diff  = xFile.path.compareTo(yFile.path)
        if (diff == 0) diff = xFile.name.compareTo(yFile.name)
        if (diff == 0) diff = xPos.line.compareTo(yPos.line)
        if (diff == 0) diff = xPos.column.compareTo(yPos.column)
        if (diff == 0) {
          // make sorting stable in case of missing sources for sub-project or *.jar dependencies
          diff = NameTransformer.decode(x.fullName).compareTo(NameTransformer.decode(y.fullName))
        }
        diff
      }

    def directSubTypes(tpe: Type): List[Type] = {
      val tpeClass               = tpe.typeSymbol.asClass
      lazy val typeParamsAndArgs = tpeClass.typeParams.map(_.toString).zip(tpe.typeArgs).toMap
      tpeClass.knownDirectSubclasses.toArray
        .sortInPlace()
        .map { symbol =>
          val classSymbol = symbol.asClass
          val typeParams  = classSymbol.typeParams
          if (typeParams.isEmpty) classSymbol.toType
          else {
            classSymbol.toType.substituteTypes(
              typeParams,
              typeParams.map { typeParam =>
                typeParamsAndArgs.getOrElse(
                  typeParam.toString,
                  fail(
                    s"Type parameter '${typeParam.name}' of '$symbol' can't be deduced from type arguments of '$tpe'."
                  )
                )
              }
            )
          }
        }
        .toList
    }

    val isNonRecursiveCache = new mutable.HashMap[Type, Boolean]

    def isNonRecursive(tpe: Type, nestedTpes: List[Type] = Nil): Boolean = isNonRecursiveCache.getOrElseUpdate(
      tpe,
      tpe <:< typeOf[String] || tpe <:< definitions.IntTpe || tpe <:< definitions.FloatTpe ||
        tpe <:< definitions.DoubleTpe || tpe <:< definitions.LongTpe || tpe <:< definitions.BooleanTpe ||
        tpe <:< definitions.ByteTpe || tpe <:< definitions.CharTpe || tpe <:< definitions.ShortTpe ||
        tpe <:< definitions.UnitTpe || tpe <:< typeOf[BigDecimal] || tpe <:< typeOf[BigInt] ||
        tpe <:< typeOf[java.time.temporal.Temporal] || tpe <:< typeOf[java.time.temporal.TemporalAmount] ||
        tpe <:< typeOf[java.util.Currency] || tpe <:< typeOf[java.util.UUID] || isEnumOrModuleValue(tpe) ||
        tpe <:< typeOf[DynamicValue] || {
          if (tpe <:< typeOf[Option[?]] || tpe <:< typeOf[Either[?, ?]] || isCollection(tpe)) {
            typeArgs(tpe).forall(isNonRecursive(_, nestedTpes))
          } else if (isSealedTraitOrAbstractClass(tpe)) directSubTypes(tpe).forall(isNonRecursive(_, nestedTpes))
          else if (isNonAbstractScalaClass(tpe)) {
            !nestedTpes.contains(tpe) && {
              tpe.decls.collectFirst { case m: MethodSymbol if m.isPrimaryConstructor => m } match {
                case Some(primaryConstructor) =>
                  val nestedTpes_ = tpe :: nestedTpes
                  val tpeParams   = primaryConstructor.paramLists
                  val tpeTypeArgs = typeArgs(tpe)
                  if (tpeTypeArgs.isEmpty) {
                    tpeParams.forall(_.forall(param => isNonRecursive(param.asTerm.typeSignature.dealias, nestedTpes_)))
                  } else {
                    val tpeTypeParams = tpe.typeSymbol.asClass.typeParams
                    tpeParams.forall(_.forall { param =>
                      val fTpe = param.asTerm.typeSignature.dealias.substituteTypes(tpeTypeParams, tpeTypeArgs)
                      isNonRecursive(fTpe, nestedTpes_)
                    })
                  }
                case _ => false
              }
            }
          } else isZioPreludeNewtype(tpe) && isNonRecursive(zioPreludeNewtypeDealias(tpe), nestedTpes)
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
      val config = new mutable.ListBuffer[Tree]
      tpe.typeSymbol.annotations.foreach { annotation =>
        val tree = annotation.tree
        if (tree.tpe =:= typeOf[Modifier.config]) tree.children match {
          case List(_, Literal(Constant(k: String)), Literal(Constant(v: String))) =>
            config.addOne(q"Modifier.config($k, $v)")
          case _ =>
        }
      }
      config.toList
    }

    val inferredSchemas   = new mutable.HashMap[Type, Tree]
    val derivedSchemaRefs = new mutable.HashMap[Type, Ident]
    val derivedSchemaDefs = new mutable.ListBuffer[Tree]

    def findImplicitOrDeriveSchema(tpe: Type): Tree = {
      lazy val schemaTpe = tq"_root_.zio.blocks.schema.Schema[$tpe]"
      val inferredSchema =
        inferredSchemas.getOrElseUpdate(tpe, c.inferImplicitValue(c.typecheck(schemaTpe, c.TYPEmode).tpe))
      if (inferredSchema.nonEmpty) inferredSchema
      else {
        derivedSchemaRefs
          .getOrElse(
            tpe, {
              val name  = TermName("s" + derivedSchemaRefs.size)
              val ident = Ident(name)
              // adding the schema reference before schema derivation to avoid an endless loop on recursive data structures
              derivedSchemaRefs.update(tpe, ident)
              val schema = deriveSchema(tpe)
              derivedSchemaDefs.addOne {
                if (isNonRecursive(tpe)) q"implicit val $name: $schemaTpe = $schema"
                else q"implicit lazy val $name: $schemaTpe = $schema"
              }
              ident
            }
          )
      }
    }

    case class FieldInfo(
      symbol: Symbol,
      name: String,
      tpe: Type,
      defaultValue: Option[Tree],
      getter: MethodSymbol,
      usedRegisters: RegisterOffset,
      isTransient: Boolean,
      config: List[(String, String)]
    )

    case class ClassInfo(tpe: Type) {
      val tpeTypeArgs: List[Type]                                            = typeArgs(tpe)
      val (fieldInfos: List[List[FieldInfo]], usedRegisters: RegisterOffset) = {
        val primaryConstructor = tpe.decls.collectFirst {
          case m: MethodSymbol if m.isPrimaryConstructor => m
        }.getOrElse(fail(s"Cannot find a primary constructor for '$tpe'"))
        var getters     = Map.empty[String, MethodSymbol]
        var annotations = Map.empty[String, List[Annotation]]
        tpe.members.foreach {
          case m: MethodSymbol if m.isParamAccessor =>
            getters = getters.updated(NameTransformer.decode(m.name.toString), m)
          case m: TermSymbol =>
            m.info: Unit // required to enforce the type information completeness and availability of annotations
            val anns = m.annotations.filter(_.tree.tpe <:< typeOf[Modifier.Term])
            if (anns.nonEmpty) annotations = annotations.updated(NameTransformer.decode(m.name.toString.trim), anns)
          case _ =>
        }
        lazy val module        = companion(tpe).asModule
        lazy val tpeTypeParams = tpe.typeSymbol.asClass.typeParams
        var usedRegisters      = RegisterOffset.Zero
        var idx                = 0
        (
          primaryConstructor.paramLists.map(_.map { param =>
            idx += 1
            val symbol = param.asTerm
            val name   = NameTransformer.decode(symbol.name.toString)
            var fTpe   = symbol.typeSignature.dealias
            if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
            val getter = getters.getOrElse(
              name,
              fail(s"Field or getter '$name' of '$tpe' should be defined as 'val' or 'var' in the primary constructor.")
            )
            var isTransient = false
            val config      = new mutable.ListBuffer[(String, String)]
            annotations.getOrElse(name, Nil).foreach { annotation =>
              val tree = annotation.tree
              if (tree.tpe =:= typeOf[Modifier.transient]) isTransient = true
              else if (tree.tpe =:= typeOf[Modifier.config]) tree.children match {
                case List(_, Literal(Constant(k: String)), Literal(Constant(v: String))) => config.addOne((k, v))
                case _                                                                   =>
              }
            }
            val defaultValue =
              if (symbol.isParamWithDefault) new Some(q"$module.${TermName("$lessinit$greater$default$" + idx)}")
              else None
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
              else if (sTpe <:< definitions.AnyRefTpe || isValueClass(sTpe)) RegisterOffset(objects = 1)
              else unsupportedFieldType(fTpe)
            val fieldInfo =
              new FieldInfo(symbol, name, fTpe, defaultValue, getter, usedRegisters, isTransient, config.toList)
            usedRegisters = RegisterOffset.add(usedRegisters, offset)
            fieldInfo
          }),
          usedRegisters
        )
      }

      def fields(schemaTpe: Type): List[Tree] = fieldInfos.flatMap(_.map { fieldInfo =>
        val fTpe          = fieldInfo.tpe
        var reflect: Tree = q"${findImplicitOrDeriveSchema(fTpe)}.reflect"
        reflect = fieldInfo.defaultValue.fold(reflect)(dv => q"$reflect.defaultValue($dv)")
        if (!isNonRecursive(fTpe)) reflect = q"new Reflect.Deferred(() => $reflect)"
        var fieldTermTree = q"$reflect.asTerm[$schemaTpe](${fieldInfo.name})"
        var modifiers     = fieldInfo.config.map { case (k, v) => q"Modifier.config($k, $v)" }
        if (fieldInfo.isTransient) modifiers = modifiers :+ q"Modifier.transient()"
        if (modifiers.nonEmpty) fieldTermTree = q"$fieldTermTree.copy(modifiers = $modifiers)"
        fieldTermTree
      })

      def constructor: Tree = {
        val argss = fieldInfos.map(_.map { fieldInfo =>
          val fTpe         = fieldInfo.tpe
          lazy val bytes   = RegisterOffset.getBytes(fieldInfo.usedRegisters)
          lazy val objects = RegisterOffset.getObjects(fieldInfo.usedRegisters)
          val constructor  =
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
              if (sTpe <:< definitions.AnyRefTpe || isValueClass(sTpe)) {
                q"in.getObject(baseOffset, $objects).asInstanceOf[$fTpe]"
              } else {
                if (sTpe <:< definitions.IntTpe) q"in.getInt(baseOffset, $bytes).asInstanceOf[$fTpe]"
                else if (sTpe <:< definitions.FloatTpe) q"in.getFloat(baseOffset, $bytes).asInstanceOf[$fTpe]"
                else if (sTpe <:< definitions.LongTpe) q"in.getLong(baseOffset, $bytes).asInstanceOf[$fTpe]"
                else if (sTpe <:< definitions.DoubleTpe) q"in.getDouble(baseOffset, $bytes).asInstanceOf[$fTpe]"
                else if (sTpe <:< definitions.BooleanTpe) q"in.getBoolean(baseOffset, $bytes).asInstanceOf[$fTpe]"
                else if (sTpe <:< definitions.ByteTpe) q"in.getByte(baseOffset, $bytes).asInstanceOf[$fTpe]"
                else if (sTpe <:< definitions.CharTpe) q"in.getChar(baseOffset, $bytes).asInstanceOf[$fTpe]"
                else if (sTpe <:< definitions.ShortTpe) q"in.getShort(baseOffset, $bytes).asInstanceOf[$fTpe]"
                else if (sTpe <:< definitions.UnitTpe) q"().asInstanceOf[$fTpe]"
                else unsupportedFieldType(fTpe)
              }
            }
          q"${fieldInfo.symbol} = $constructor"
        })
        q"new $tpe(...$argss)"
      }

      def deconstructor: List[Tree] = fieldInfos.flatMap(_.map { fieldInfo =>
        val fTpe         = fieldInfo.tpe
        val getter       = fieldInfo.getter
        lazy val bytes   = RegisterOffset.getBytes(fieldInfo.usedRegisters)
        lazy val objects = RegisterOffset.getObjects(fieldInfo.usedRegisters)
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
        else if (isValueClass(fTpe)) {
          q"out.setObject(baseOffset, $objects, in.$getter.asInstanceOf[_root_.scala.AnyRef])"
        } else {
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
          else if (sTpe <:< definitions.AnyRefTpe || isValueClass(sTpe)) {
            q"out.setObject(baseOffset, $objects, in.$getter.asInstanceOf[_root_.scala.AnyRef])"
          } else unsupportedFieldType(fTpe)
        }
      })

      def unsupportedFieldType(tpe: Type): Nothing = fail(s"Unsupported field type '$tpe'.")
    }

    def deriveSchema(tpe: Type): Tree = {
      if (isEnumOrModuleValue(tpe)) {
        val tpeName = typeName(tpe)
        q"""new Schema(
              reflect = new Reflect.Record[Binding, $tpe](
                fields = _root_.scala.Vector.empty,
                typeName = ${toTree(tpeName)},
                recordBinding = new Binding.Record(
                  constructor = new ConstantConstructor[$tpe](${tpe.typeSymbol.asClass.module}),
                  deconstructor = new ConstantDeconstructor[$tpe]
                ),
                modifiers = ${modifiers(tpe)}
              )
            )"""
      } else if (isCollection(tpe)) {
        if (tpe <:< typeOf[Array[?]]) {
          val tpeName     = typeName(tpe)
          val elementTpe  = typeArgs(tpe).head
          val constructor =
            if (elementTpe <:< definitions.AnyRefTpe) {
              q"""new SeqConstructor.ArrayConstructor {
                  override def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                    new Builder(new Array[$elementTpe](sizeHint).asInstanceOf[Array[B]], 0)
                }"""
            } else q"SeqConstructor.arrayConstructor"
          q"""new Schema(
              reflect = new Reflect.Sequence[Binding, $elementTpe, _root_.scala.Array](
                element = ${findImplicitOrDeriveSchema(elementTpe)}.reflect,
                typeName = ${toTree(tpeName)},
                seqBinding = new Binding.Seq(
                  constructor = $constructor,
                  deconstructor = SeqDeconstructor.arrayDeconstructor
                )
              )
            )"""
        } else if (tpe <:< typeOf[ArraySeq[?]]) {
          q"Schema.arraySeq(${findImplicitOrDeriveSchema(typeArgs(tpe).head)})"
        } else if (tpe <:< typeOf[List[?]]) {
          q"Schema.list(${findImplicitOrDeriveSchema(typeArgs(tpe).head)})"
        } else if (tpe <:< typeOf[Map[?, ?]]) {
          val tpeTypeArgs = typeArgs(tpe)
          q"Schema.map(${findImplicitOrDeriveSchema(tpeTypeArgs.head)},${findImplicitOrDeriveSchema(tpeTypeArgs.last)})"
        } else if (tpe <:< typeOf[Set[?]]) {
          q"Schema.set(${findImplicitOrDeriveSchema(typeArgs(tpe).head)})"
        } else if (tpe <:< typeOf[Vector[?]]) {
          q"Schema.vector(${findImplicitOrDeriveSchema(typeArgs(tpe).head)})"
        } else cannotDeriveSchema(tpe)
      } else if (isSealedTraitOrAbstractClass(tpe)) {
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

        val tpeName  = typeName(tpe)
        val subTypes = directSubTypes(tpe)
        if (subTypes.isEmpty) fail(s"Cannot find sub-types for ADT base '$tpe'.")
        val fullTermNames         = subTypes.map(sTpe => toFullTermName(typeName(sTpe)))
        val maxCommonPrefixLength = {
          var minFullTermName = fullTermNames.min
          var maxFullTermName = fullTermNames.max
          val tpeFullTermName = toFullTermName(tpeName)
          minFullTermName = fullTermNameOrdering.min(minFullTermName, tpeFullTermName)
          maxFullTermName = fullTermNameOrdering.max(maxFullTermName, tpeFullTermName)
          val minLength = Math.min(minFullTermName.length, maxFullTermName.length)
          var idx       = 0
          while (idx < minLength && minFullTermName(idx).equals(maxFullTermName(idx))) idx += 1
          idx
        }
        val cases = subTypes.zip(fullTermNames).map { case (sTpe, fullName) =>
          q"${findImplicitOrDeriveSchema(sTpe)}.reflect.asTerm(${toShortTermName(fullName, maxCommonPrefixLength)})"
        }
        val discrCases = subTypes.map {
          var idx = -1
          sTpe =>
            idx += 1
            cq"_: $sTpe @_root_.scala.unchecked => $idx"
        }
        val matcherCases = subTypes.map { sTpe =>
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
                typeName = ${toTree(tpeName)},
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
      } else if (isNonAbstractScalaClass(tpe)) {
        val tpeName   = typeName(tpe)
        val classInfo = new ClassInfo(tpe)
        q"""new Schema(
              reflect = new Reflect.Record[Binding, $tpe](
                fields = _root_.scala.Vector(..${classInfo.fields(tpe)}),
                typeName = ${toTree(tpeName)},
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
      } else if (isZioPreludeNewtype(tpe)) {
        val tpeName = typeName(tpe)
        val schema  = findImplicitOrDeriveSchema(zioPreludeNewtypeDealias(tpe))
        q"new Schema($schema.reflect.typeName(${toTree(tpeName)})).asInstanceOf[Schema[$tpe]]"
      } else cannotDeriveSchema(tpe)
    }

    def cannotDeriveSchema(tpe: Type): Nothing = fail(s"Cannot derive schema for '$tpe'.")

    val schema      = deriveSchema(weakTypeOf[A].dealias)
    val schemaBlock =
      q"""{
            import _root_.zio.blocks.schema._
            import _root_.zio.blocks.schema.binding._
            import _root_.zio.blocks.schema.binding.RegisterOffset._

            ..$derivedSchemaDefs
            $schema
          }"""
    // c.info(c.enclosingPosition, s"Generated schema:\n${showCode(schemaBlock)}", force = true)
    c.Expr[Schema[A]](schemaBlock)
  }
}
