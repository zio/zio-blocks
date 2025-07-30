package zio.blocks.schema

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.RegisterOffset
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.NameTransformer

trait SchemaVersionSpecific {
  def derived[A]: Schema[A] = macro SchemaVersionSpecific.derived[A]
}

private object SchemaVersionSpecific {
  private[this] val isNonRecursiveCache = TrieMap.empty[Any, Boolean]
  private[this] implicit val fullNameOrdering: Ordering[Array[String]] = new Ordering[Array[String]] {
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

    def typeArgs(tpe: Type): List[Type] = tpe.typeArgs.map(_.dealias)

    def isOption(tpe: Type): Boolean = tpe <:< typeOf[Option[_]]

    def isEither(tpe: Type): Boolean = tpe <:< typeOf[Either[_, _]]

    def isDynamicValue(tpe: Type): Boolean = tpe =:= typeOf[DynamicValue]

    def isCollection(tpe: Type): Boolean =
      tpe <:< typeOf[Iterable[_]] || tpe <:< typeOf[Iterator[_]] || tpe <:< typeOf[Array[_]]

    def companion(tpe: Type): Symbol = {
      val comp = tpe.typeSymbol.companion
      if (comp.isModule) comp
      else {
        val ownerChainOf = (s: Symbol) => Iterator.iterate(s)(_.owner).takeWhile(_ != NoSymbol).toArray.reverseIterator
        val path = ownerChainOf(tpe.typeSymbol)
          .zipAll(ownerChainOf(enclosingOwner), NoSymbol, NoSymbol)
          .dropWhile(x => x._1 == x._2)
          .takeWhile(x => x._1 != NoSymbol)
          .map(x => x._1.name.toTermName)
        if (path.isEmpty) NoSymbol
        else c.typecheck(path.foldLeft[Tree](Ident(path.next()))(Select(_, _)), silent = true).symbol
      }
    }

    implicit lazy val positionOrdering: Ordering[c.universe.Symbol] =
      (x: c.universe.Symbol, y: c.universe.Symbol) => {
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

    def isNonRecursive(tpe: Type, nestedTpes: List[Type] = Nil): Boolean = isNonRecursiveCache.getOrElseUpdate(
      tpe,
      tpe =:= typeOf[String] || tpe =:= definitions.IntTpe || tpe =:= definitions.FloatTpe ||
        tpe =:= definitions.DoubleTpe || tpe =:= definitions.LongTpe || tpe =:= definitions.BooleanTpe ||
        tpe =:= definitions.ByteTpe || tpe =:= definitions.CharTpe || tpe =:= definitions.ShortTpe ||
        tpe =:= typeOf[BigDecimal] || tpe =:= typeOf[BigInt] || tpe =:= definitions.UnitTpe ||
        tpe <:< typeOf[java.time.temporal.Temporal] || tpe <:< typeOf[java.time.temporal.TemporalAmount] ||
        tpe =:= typeOf[java.util.Currency] || tpe =:= typeOf[java.util.UUID] || isEnumOrModuleValue(tpe) ||
        isDynamicValue(tpe) || {
          if (isOption(tpe) || isEither(tpe) || isCollection(tpe)) typeArgs(tpe).forall(isNonRecursive(_, nestedTpes))
          else if (isSealedTraitOrAbstractClass(tpe)) directSubTypes(tpe).forall(isNonRecursive(_, nestedTpes))
          else {
            isNonAbstractScalaClass(tpe) && !nestedTpes.contains(tpe) && {
              tpe.decls.collectFirst { case m: MethodSymbol if m.isPrimaryConstructor => m } match {
                case Some(primaryConstructor) =>
                  val tpeParams   = primaryConstructor.paramLists
                  val nestedTpes_ = tpe :: nestedTpes
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
          }
        }
    )

    def typeName(tpe: Type): (List[String], List[String], String) = {
      var packages  = List.empty[String]
      var values    = List.empty[String]
      val tpeSymbol = tpe.typeSymbol
      var name      = NameTransformer.decode(tpeSymbol.name.toString)
      val comp      = companion(tpe)
      var owner =
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
      (packages, values, name)
    }

    def modifiers(tpe: Type): List[Tree] = tpe.typeSymbol.annotations
      .filter(_.tree.tpe =:= typeOf[Modifier.config])
      .collect(_.tree.children match {
        case List(_, Literal(Constant(k: String)), Literal(Constant(v: String))) => q"Modifier.config($k, $v)"
      })

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
      registersUsed: RegisterOffset,
      isTransient: Boolean,
      config: List[(String, String)]
    )

    case class ClassInfo(tpe: Type) {
      val (fieldInfos: List[List[FieldInfo]], registersUsed: RegisterOffset) = {
        val primaryConstructor = tpe.decls.collectFirst {
          case m: MethodSymbol if m.isPrimaryConstructor => m
        }.getOrElse(fail(s"Cannot find a primary constructor for '$tpe'"))
        var getters = Map.empty[String, MethodSymbol]
        tpe.members.foreach {
          case m: MethodSymbol if m.isParamAccessor =>
            getters = getters.updated(NameTransformer.decode(m.name.toString), m)
          case _ =>
        }
        var annotations = Map.empty[String, List[Annotation]]
        tpe.members.foreach {
          case m: TermSymbol =>
            m.info: Unit // to enforce the type information completeness and availability of annotations
            val anns = m.annotations.filter(_.tree.tpe <:< typeOf[Modifier.Term])
            if (anns.nonEmpty) annotations = annotations.updated(NameTransformer.decode(m.name.toString.trim), anns)
          case _ =>
        }
        lazy val module   = companion(tpe).asModule
        val tpeTypeParams = tpe.typeSymbol.asClass.typeParams
        val tpeParams     = primaryConstructor.paramLists
        val tpeTypeArgs   = typeArgs(tpe)
        var registersUsed = RegisterOffset.Zero
        var idx           = 0
        (
          tpeParams.map(_.map { param =>
            idx += 1
            val symbol = param.asTerm
            val name   = NameTransformer.decode(symbol.name.toString)
            var fTpe   = symbol.typeSignature.dealias
            if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
            val getter =
              getters.getOrElse(name, fail(s"Cannot find '$name' parameter of '$tpe' in the primary constructor."))
            val anns        = annotations.getOrElse(name, Nil)
            val isTransient = anns.exists(_.tree.tpe =:= typeOf[Modifier.transient])
            val config = anns
              .filter(_.tree.tpe =:= typeOf[Modifier.config])
              .collect(_.tree.children match {
                case List(_, Literal(Constant(k: String)), Literal(Constant(v: String))) => (k, v)
              })
            val defaultValue =
              if (symbol.isParamWithDefault) Some(q"$module.${TermName("$lessinit$greater$default$" + idx)}")
              else None
            val offset =
              if (fTpe =:= definitions.IntTpe) RegisterOffset(ints = 1)
              else if (fTpe =:= definitions.FloatTpe) RegisterOffset(floats = 1)
              else if (fTpe =:= definitions.LongTpe) RegisterOffset(longs = 1)
              else if (fTpe =:= definitions.DoubleTpe) RegisterOffset(doubles = 1)
              else if (fTpe =:= definitions.BooleanTpe) RegisterOffset(booleans = 1)
              else if (fTpe =:= definitions.ByteTpe) RegisterOffset(bytes = 1)
              else if (fTpe =:= definitions.CharTpe) RegisterOffset(chars = 1)
              else if (fTpe =:= definitions.ShortTpe) RegisterOffset(shorts = 1)
              else if (fTpe =:= definitions.UnitTpe) RegisterOffset.Zero
              else if (fTpe <:< definitions.AnyRefTpe) RegisterOffset(objects = 1)
              else unsupportedFieldType(fTpe)
            val fieldInfo = FieldInfo(symbol, name, fTpe, defaultValue, getter, registersUsed, isTransient, config)
            registersUsed = RegisterOffset.add(registersUsed, offset)
            fieldInfo
          }),
          registersUsed
        )
      }

      def const: Tree = {
        val argss = fieldInfos.map(_.map { fieldInfo =>
          val fTpe         = fieldInfo.tpe
          lazy val bytes   = RegisterOffset.getBytes(fieldInfo.registersUsed)
          lazy val objects = RegisterOffset.getObjects(fieldInfo.registersUsed)
          val const =
            if (fTpe =:= definitions.IntTpe) q"in.getInt(baseOffset, $bytes)"
            else if (fTpe =:= definitions.FloatTpe) q"in.getFloat(baseOffset, $bytes)"
            else if (fTpe =:= definitions.LongTpe) q"in.getLong(baseOffset, $bytes)"
            else if (fTpe =:= definitions.DoubleTpe) q"in.getDouble(baseOffset, $bytes)"
            else if (fTpe =:= definitions.BooleanTpe) q"in.getBoolean(baseOffset, $bytes)"
            else if (fTpe =:= definitions.ByteTpe) q"in.getByte(baseOffset, $bytes)"
            else if (fTpe =:= definitions.CharTpe) q"in.getChar(baseOffset, $bytes)"
            else if (fTpe =:= definitions.ShortTpe) q"in.getShort(baseOffset, $bytes)"
            else if (fTpe =:= definitions.UnitTpe) q"()"
            else if (fTpe <:< definitions.AnyRefTpe) q"in.getObject(baseOffset, $objects).asInstanceOf[$fTpe]"
            else unsupportedFieldType(fTpe)
          q"${fieldInfo.symbol} = $const"
        })
        q"new $tpe(...$argss)"
      }

      def deconst: List[Tree] = fieldInfos.flatMap(_.map { fieldInfo =>
        val fTpe         = fieldInfo.tpe
        val getter       = fieldInfo.getter
        lazy val bytes   = RegisterOffset.getBytes(fieldInfo.registersUsed)
        lazy val objects = RegisterOffset.getObjects(fieldInfo.registersUsed)
        if (fTpe =:= definitions.IntTpe) q"out.setInt(baseOffset, $bytes, in.$getter)"
        else if (fTpe =:= definitions.FloatTpe) q"out.setFloat(baseOffset, $bytes, in.$getter)"
        else if (fTpe =:= definitions.LongTpe) q"out.setLong(baseOffset, $bytes, in.$getter)"
        else if (fTpe =:= definitions.DoubleTpe) q"out.setDouble(baseOffset, $bytes, in.$getter)"
        else if (fTpe =:= definitions.BooleanTpe) q"out.setBoolean(baseOffset, $bytes, in.$getter)"
        else if (fTpe =:= definitions.ByteTpe) q"out.setByte(baseOffset, $bytes, in.$getter)"
        else if (fTpe =:= definitions.CharTpe) q"out.setChar(baseOffset, $bytes, in.$getter)"
        else if (fTpe =:= definitions.ShortTpe) q"out.setShort(baseOffset, $bytes, in.$getter)"
        else if (fTpe =:= definitions.UnitTpe) q"()"
        else if (fTpe <:< definitions.AnyRefTpe) q"out.setObject(baseOffset, $objects, in.$getter)"
        else unsupportedFieldType(fTpe)
      })

      def unsupportedFieldType(tpe: Type): Nothing = fail(s"Unsupported field type '$tpe'.")
    }

    def deriveSchema(tpe: Type): Tree = {
      if (isEnumOrModuleValue(tpe)) {
        val (packages, values, name) = typeName(tpe)
        q"""new Schema(
              reflect = new Reflect.Record[Binding, $tpe](
                fields = _root_.scala.Vector.empty,
                typeName = new TypeName(new Namespace($packages, $values), $name),
                recordBinding = new Binding.Record(
                  constructor = new ConstantConstructor[$tpe](${tpe.typeSymbol.asClass.module}),
                  deconstructor = new ConstantDeconstructor[$tpe]
                ),
                modifiers = ${modifiers(tpe)}
              )
            )"""
      } else if (tpe <:< typeOf[Array[_]]) {
        val elementTpe    = typeArgs(tpe).head
        val elementSchema = findImplicitOrDeriveSchema(elementTpe)
        if (elementTpe <:< definitions.AnyRefTpe) {
          q"""new Schema(
                reflect = new Reflect.Sequence[Binding, $elementTpe, _root_.scala.Array](
                  element = $elementSchema.reflect,
                  typeName = new TypeName(new Namespace(List("scala"), Nil), "Array"),
                  seqBinding = new Binding.Seq[_root_.scala.Array, $elementTpe](
                    constructor = new SeqConstructor.ArrayConstructor {
                      override def newObjectBuilder[A](sizeHint: Int): Builder[A] =
                        new Builder(new Array[$elementTpe](sizeHint).asInstanceOf[Array[A]], 0)
                    },
                    deconstructor = SeqDeconstructor.arrayDeconstructor
                  )
                )
              )"""
        } else {
          q"""new Schema(
                reflect = new Reflect.Sequence[Binding, $elementTpe, _root_.scala.Array](
                  element = $elementSchema.reflect,
                  typeName = new TypeName(new Namespace(List("scala"), Nil), "Array"),
                  seqBinding = new Binding.Seq[_root_.scala.Array, $elementTpe](
                    constructor = SeqConstructor.arrayConstructor,
                    deconstructor = SeqDeconstructor.arrayDeconstructor
                  )
                )
              )"""
        }
      } else if (isSealedTraitOrAbstractClass(tpe)) {
        val subTypes = directSubTypes(tpe)
        if (subTypes.isEmpty) fail(s"Cannot find sub-types for ADT base '$tpe'.")
        val fullNames = subTypes.map { sTpe =>
          val (packages, values, name) = typeName(sTpe)
          packages.toArray ++ values.toArray :+ name
        }
        val (packages, values, name) = typeName(tpe)
        val maxCommonPrefixLength = {
          var minFullName = fullNames.min
          var maxFullName = fullNames.max
          val tpeFullName = packages.toArray ++ values.toArray :+ name
          if (fullNameOrdering.compare(minFullName, tpeFullName) > 0) minFullName = tpeFullName
          if (fullNameOrdering.compare(maxFullName, tpeFullName) < 0) maxFullName = tpeFullName
          val minLength = Math.min(minFullName.length, maxFullName.length)
          var idx       = 0
          while (idx < minLength && minFullName(idx).compareTo(maxFullName(idx)) == 0) idx += 1
          idx
        }
        val cases = subTypes.zip(fullNames).map { case (sTpe, fullName) =>
          val termName = fullName.drop(maxCommonPrefixLength).mkString(".")
          val sSchema  = findImplicitOrDeriveSchema(sTpe)
          q"$sSchema.reflect.asTerm($termName)"
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
                typeName = new TypeName(new Namespace($packages, $values), $name),
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
        val classInfo = new ClassInfo(tpe)
        val fields = classInfo.fieldInfos.flatMap(_.map { fieldInfo =>
          val fTpe              = fieldInfo.tpe
          val fSchema           = findImplicitOrDeriveSchema(fTpe)
          var reflectTree: Tree = q"$fSchema.reflect"
          reflectTree = fieldInfo.defaultValue.fold(reflectTree)(dv => q"$reflectTree.defaultValue($dv)")
          if (!isNonRecursive(fTpe)) reflectTree = q"Reflect.Deferred(() => $reflectTree)"
          var fieldTermTree = q"$reflectTree.asTerm[$tpe](${fieldInfo.name})"
          var modifiers     = fieldInfo.config.map { case (k, v) => q"Modifier.config($k, $v)" }
          if (fieldInfo.isTransient) modifiers = modifiers :+ q"Modifier.transient()"
          if (modifiers.nonEmpty) fieldTermTree = q"$fieldTermTree.copy(modifiers = $modifiers)"
          fieldTermTree
        })
        val (packages, values, name) = typeName(tpe)
        q"""new Schema(
              reflect = new Reflect.Record[Binding, $tpe](
                fields = _root_.scala.Vector(..$fields),
                typeName = new TypeName(new Namespace($packages, $values), $name),
                recordBinding = new Binding.Record(
                  constructor = new Constructor[$tpe] {
                    def usedRegisters: RegisterOffset = ${classInfo.registersUsed}

                    def construct(in: Registers, baseOffset: RegisterOffset): $tpe = ${classInfo.const}
                  },
                  deconstructor = new Deconstructor[$tpe] {
                    def usedRegisters: RegisterOffset = ${classInfo.registersUsed}

                    def deconstruct(out: Registers, baseOffset: RegisterOffset, in: $tpe): _root_.scala.Unit = {
                      ..${classInfo.deconst}
                    }
                  }
                ),
                modifiers = ${modifiers(tpe)},
              )
            )"""
      } else fail(s"Cannot derive schema for '$tpe'.")
    }

    val schema = deriveSchema(weakTypeOf[A].dealias)
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
