package zio.blocks.schema

import zio.blocks.schema.binding._
import scala.collection.concurrent.TrieMap
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
      val minLen = math.min(x.length, y.length)
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

    def directSubTypes(tpe: Type): Seq[Type] = {
      val tpeClass               = tpe.typeSymbol.asClass
      lazy val typeParamsAndArgs = tpeClass.typeParams.map(_.toString).zip(tpe.typeArgs).toMap
      tpeClass.knownDirectSubclasses.toSeq.map { symbol =>
        val classSymbol = symbol.asClass
        val typeParams  = classSymbol.typeParams
        if (typeParams.isEmpty) classSymbol.toType
        else {
          classSymbol.toType.substituteTypes(
            typeParams,
            typeParams.map { s =>
              typeParamsAndArgs.getOrElse(
                s.toString,
                fail(
                  s"Cannot resolve generic type(s) for `${classSymbol.toType}`. " +
                    s"Please provide an implicitly accessible schema for it."
                )
              )
            }
          )
        }
      }
    }

    def isNonRecursive(tpe: Type, nestedTpes: List[Type] = Nil): Boolean = isNonRecursiveCache.getOrElseUpdate(
      tpe,
      tpe =:= typeOf[String] || tpe =:= typeOf[Boolean] || tpe =:= typeOf[Byte] || tpe =:= typeOf[Char] ||
        tpe =:= typeOf[Short] || tpe =:= typeOf[Float] || tpe =:= typeOf[Int] || tpe =:= typeOf[Double] ||
        tpe =:= typeOf[Long] || tpe =:= typeOf[BigDecimal] || tpe =:= typeOf[BigInt] || tpe =:= typeOf[Unit] ||
        tpe <:< typeOf[java.time.temporal.Temporal] || tpe <:< typeOf[java.time.temporal.TemporalAmount] ||
        tpe =:= typeOf[java.util.Currency] || tpe =:= typeOf[java.util.UUID] || isEnumOrModuleValue(tpe) || {
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

    def modifiers(tpe: Type): Seq[Tree] = tpe.typeSymbol.annotations
      .filter(_.tree.tpe =:= typeOf[Modifier.config])
      .collect(_.tree.children match {
        case List(_, Literal(Constant(k: String)), Literal(Constant(v: String))) => q"Modifier.config($k, $v)"
      })

    def typeName(tpe: Type): (Seq[String], Seq[String], String) = {
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

    val tpe                      = weakTypeOf[A].dealias
    val (packages, values, name) = typeName(tpe)

    def maxCommonPrefixLength(typesWithFullNames: Seq[(Type, Array[String])]): Int = {
      var minFullName = typesWithFullNames.head._2
      var maxFullName = typesWithFullNames.last._2
      val tpeFullName = packages.toArray ++ values.toArray :+ name
      if (fullNameOrdering.compare(minFullName, tpeFullName) > 0) minFullName = tpeFullName
      if (fullNameOrdering.compare(maxFullName, tpeFullName) < 0) maxFullName = tpeFullName
      val minLength = Math.min(minFullName.length, maxFullName.length)
      var idx       = 0
      while (idx < minLength && minFullName(idx).compareTo(maxFullName(idx)) == 0) idx += 1
      idx
    }

    val schema =
      if (isEnumOrModuleValue(tpe)) {
        q"""{
              import _root_.zio.blocks.schema._
              import _root_.zio.blocks.schema.binding._
              import _root_.zio.blocks.schema.binding.RegisterOffset._

              new Schema[$tpe](
                reflect = Reflect.Record[Binding, $tpe](
                  fields = _root_.scala.Vector.empty,
                  typeName = TypeName(Namespace(_root_.scala.Seq(..$packages), _root_.scala.Seq(..$values)), $name),
                  recordBinding = Binding.Record(
                    constructor = new Constructor[$tpe] {
                      def usedRegisters: RegisterOffset = 0

                      def construct(in: Registers, baseOffset: RegisterOffset): $tpe = ${tpe.typeSymbol.asClass.module}
                    },
                    deconstructor = new Deconstructor[$tpe] {
                      def usedRegisters: RegisterOffset = 0

                      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: $tpe): Unit = ()
                    }
                  ),
                  modifiers = _root_.scala.Seq(..${modifiers(tpe)})
                )
              )
            }"""
      } else if (isSealedTraitOrAbstractClass(tpe)) {
        val subTypes = directSubTypes(tpe)
        if (subTypes.isEmpty) {
          fail(
            s"Cannot find sub-types for ADT base '$tpe'. " +
              "Please add them or provide an implicitly accessible schema for the ADT base."
          )
        }
        val subTypesWithFullNames = subTypes.map { sTpe =>
          val (packages, values, name) = typeName(sTpe)
          (sTpe, packages.toArray ++ values.toArray :+ name)
        }.sortBy(_._2)
        val length = maxCommonPrefixLength(subTypesWithFullNames)
        val cases = subTypesWithFullNames.map { case (sTpe, fullName) =>
          val termName = fullName.drop(length).mkString(".")
          q"Schema[$sTpe].reflect.asTerm($termName)"
        }
        val discrCases = subTypesWithFullNames.map {
          var idx = -1
          x =>
            idx += 1
            cq"_: ${x._1} @_root_.scala.unchecked => $idx"
        }
        val matcherCases = subTypesWithFullNames.map { case (sTpe, _) =>
          q"""new Matcher[$sTpe] {
                def downcastOrNull(a: Any): $sTpe = a match {
                  case x: $sTpe @_root_.scala.unchecked => x
                  case _ => null.asInstanceOf[$sTpe]
                }
              }"""
        }
        q"""{
              import _root_.zio.blocks.schema._
              import _root_.zio.blocks.schema.binding._

              new Schema[$tpe](
                reflect = Reflect.Variant[Binding, $tpe](
                  cases = _root_.scala.Vector(..$cases),
                  typeName = TypeName(Namespace(_root_.scala.Seq(..$packages), _root_.scala.Seq(..$values)), $name),
                  variantBinding = Binding.Variant(
                    discriminator = new Discriminator[$tpe] {
                      def discriminate(a: $tpe): Int = a match {
                        case ..$discrCases
                      }
                    },
                    matchers = Matchers(..$matcherCases),
                  ),
                  modifiers = _root_.scala.Seq(..${modifiers(tpe)})
                )
              )
            }"""
      } else if (isNonAbstractScalaClass(tpe)) {
        case class FieldInfo(
          symbol: Symbol,
          name: String,
          tpe: Type,
          defaultValue: Option[Tree],
          const: Tree,
          deconst: Tree,
          isTransient: Boolean,
          config: Seq[(String, String)]
        )

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
        val fieldInfos = tpeParams.map(_.map { param =>
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
          var const: Tree   = null
          var deconst: Tree = null
          val bytes         = RegisterOffset.getBytes(registersUsed)
          val objects       = RegisterOffset.getObjects(registersUsed)
          var offset        = RegisterOffset.Zero
          if (fTpe =:= typeOf[Unit]) {
            const = q"()"
            deconst = q"()"
          } else if (fTpe =:= typeOf[Boolean]) {
            offset = RegisterOffset(booleans = 1)
            const = q"in.getBoolean(baseOffset, $bytes)"
            deconst = q"out.setBoolean(baseOffset, $bytes, in.$getter)"
          } else if (fTpe =:= typeOf[Byte]) {
            offset = RegisterOffset(bytes = 1)
            const = q"in.getByte(baseOffset, $bytes)"
            deconst = q"out.setByte(baseOffset, $bytes, in.$getter)"
          } else if (fTpe =:= typeOf[Char]) {
            offset = RegisterOffset(chars = 1)
            const = q"in.getChar(baseOffset, $bytes)"
            deconst = q"out.setChar(baseOffset, $bytes, in.$getter)"
          } else if (fTpe =:= typeOf[Short]) {
            offset = RegisterOffset(shorts = 1)
            const = q"in.getShort(baseOffset, $bytes)"
            deconst = q"out.setShort(baseOffset, $bytes, in.$getter)"
          } else if (fTpe =:= typeOf[Float]) {
            offset = RegisterOffset(floats = 1)
            const = q"in.getFloat(baseOffset, $bytes)"
            deconst = q"out.setFloat(baseOffset, $bytes, in.$getter)"
          } else if (fTpe =:= typeOf[Int]) {
            offset = RegisterOffset(ints = 1)
            const = q"in.getInt(baseOffset, $bytes)"
            deconst = q"out.setInt(baseOffset, $bytes, in.$getter)"
          } else if (fTpe =:= typeOf[Double]) {
            offset = RegisterOffset(doubles = 1)
            const = q"in.getDouble(baseOffset, $bytes)"
            deconst = q"out.setDouble(baseOffset, $bytes, in.$getter)"
          } else if (fTpe =:= typeOf[Long]) {
            offset = RegisterOffset(longs = 1)
            const = q"in.getLong(baseOffset, $bytes)"
            deconst = q"out.setLong(baseOffset, $bytes, in.$getter)"
          } else {
            offset = RegisterOffset(objects = 1)
            const = q"in.getObject(baseOffset, $objects).asInstanceOf[$fTpe]"
            deconst = q"out.setObject(baseOffset, $objects, in.$getter)"
          }
          registersUsed = RegisterOffset.add(registersUsed, offset)
          FieldInfo(symbol, name, fTpe, defaultValue, const, deconst, isTransient, config)
        })
        val fields = fieldInfos.flatMap(_.map { fieldInfo =>
          val fTpe        = fieldInfo.tpe
          val name        = fieldInfo.name
          val reflectTree = q"Schema[$fTpe].reflect"
          var fieldTermTree = if (isNonRecursive(fTpe)) {
            fieldInfo.defaultValue.fold(q"$reflectTree.asTerm($name)") { dv =>
              q"$reflectTree.defaultValue($dv).asTerm($name)"
            }
          } else {
            fieldInfo.defaultValue.fold(q"Reflect.Deferred(() => $reflectTree).asTerm($name)") { dv =>
              q"Reflect.Deferred(() => $reflectTree.defaultValue($dv)).asTerm($name)"
            }
          }
          var modifiers = fieldInfo.config.map { case (k, v) => q"Modifier.config($k, $v)" }
          if (fieldInfo.isTransient) modifiers = modifiers :+ q"Modifier.transient()"
          if (modifiers.nonEmpty) fieldTermTree = q"$fieldTermTree.copy(modifiers = _root_.scala.Seq(..$modifiers))"
          fieldTermTree
        })
        val const   = q"new $tpe(...${fieldInfos.map(_.map(fieldInfo => q"${fieldInfo.symbol} = ${fieldInfo.const}"))})"
        val deconst = fieldInfos.flatMap(_.map(_.deconst))
        q"""{
              import _root_.zio.blocks.schema._
              import _root_.zio.blocks.schema.binding._
              import _root_.zio.blocks.schema.binding.RegisterOffset._

              new Schema[$tpe](
                reflect = Reflect.Record[Binding, $tpe](
                  fields = _root_.scala.Vector(..$fields),
                  typeName = TypeName(Namespace(_root_.scala.Seq(..$packages), _root_.scala.Seq(..$values)), $name),
                  recordBinding = Binding.Record(
                    constructor = new Constructor[$tpe] {
                      def usedRegisters: RegisterOffset = $registersUsed

                      def construct(in: Registers, baseOffset: RegisterOffset): $tpe = $const
                    },
                    deconstructor = new Deconstructor[$tpe] {
                      def usedRegisters: RegisterOffset = $registersUsed

                      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: $tpe): _root_.scala.Unit = {
                        ..$deconst
                      }
                    }
                  ),
                  modifiers = _root_.scala.Seq(..${modifiers(tpe)}),
                )
              )
            }"""
      } else fail(s"Cannot derive '${typeOf[Schema[_]]}' for '$tpe'.")
    // c.info(c.enclosingPosition, s"Generated schema for type '$tpe':\n${showCode(schema)}", force = true)
    c.Expr[Schema[A]](schema)
  }
}
