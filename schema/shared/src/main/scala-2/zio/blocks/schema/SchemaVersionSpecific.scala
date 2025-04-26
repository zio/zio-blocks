package zio.blocks.schema

trait SchemaVersionSpecific {
  import scala.language.experimental.macros

  def derived[A]: Schema[A] = macro SchemaVersionSpecific.derived[A]
}

object SchemaVersionSpecific {
  import scala.reflect.macros.blackbox
  import scala.reflect.NameTransformer

  def derived[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[Schema[A]] = {
    import c.universe._
    import c.internal._
    import zio.blocks.schema.binding._

    def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    def isEnumOrModuleValue(tpe: Type): Boolean = tpe.typeSymbol.isModuleClass

    def isSealedTraitOrAbstractClass(tpe: Type): Boolean = tpe.typeSymbol.isClass && {
      val classSymbol = tpe.typeSymbol.asClass
      classSymbol.isSealed && (classSymbol.isAbstract || classSymbol.isTrait)
    }

    def isNonAbstractScalaClass(tpe: Type): Boolean =
      tpe.typeSymbol.isClass && !tpe.typeSymbol.isAbstract && !tpe.typeSymbol.isJava

    def typeArgs(tpe: Type): List[Type] = tpe.typeArgs.map(_.dealias)

    def companion(tpe: Type): Symbol = {
      val comp = tpe.typeSymbol.companion
      if (comp.isModule) comp
      else {
        val ownerChainOf = (s: Symbol) =>
          Iterator.iterate(s)(_.owner).takeWhile(x => x != NoSymbol).toVector.reverseIterator
        val path = ownerChainOf(tpe.typeSymbol)
          .zipAll(ownerChainOf(enclosingOwner), NoSymbol, NoSymbol)
          .dropWhile { case (x, y) => x == y }
          .takeWhile { case (x, _) => x != NoSymbol }
          .map { case (x, _) => x.name.toTermName }
        if (path.isEmpty) NoSymbol
        else c.typecheck(path.foldLeft[Tree](Ident(path.next()))(Select(_, _)), silent = true).symbol
      }
    }

    def directSubTypes(tpe: Type): Seq[Type] = {
      val tpeClass = tpe.typeSymbol.asClass
      tpeClass.knownDirectSubclasses.toSeq.sortBy(_.fullName).map { symbol =>
        val classSymbol = symbol.asClass
        val typeParams  = classSymbol.typeParams
        if (typeParams.isEmpty) classSymbol.toType
        else {
          val typeParamsAndArgs = tpeClass.typeParams.map(_.toString).zip(tpe.typeArgs).toMap
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

    def typeName(tpe: Type): Tree = {
      var packages = List.empty[String]
      var values   = List.empty[String]
      var name     = NameTransformer.decode(tpe.typeSymbol.name.toString)
      val comp     = companion(tpe)
      var owner =
        if (comp == null) tpe.typeSymbol
        else if (comp == NoSymbol) {
          name += ".type"
          tpe.typeSymbol.asClass.module
        } else comp
      while ({
        owner = owner.owner
        owner != NoSymbol
      }) {
        val ownerName = NameTransformer.decode(owner.name.toString)
        if (owner.isPackage || owner.isPackageClass) packages = ownerName :: packages
        else values = ownerName :: values
      }
      q"TypeName(Namespace(${packages.tail}, $values), $name)"
    }

    val tpe     = weakTypeOf[A].dealias
    val tpeName = typeName(tpe)
    val schema =
      if (isEnumOrModuleValue(tpe)) {
        q"""{
              import _root_.zio.blocks.schema._
              import _root_.zio.blocks.schema.binding._
              import _root_.zio.blocks.schema.binding.RegisterOffset._

              new Schema[$tpe](
                reflect = Reflect.Record[Binding, $tpe](
                  fields = _root_.scala.Nil,
                  typeName = $tpeName,
                  recordBinding = Binding.Record(
                    constructor = new Constructor[$tpe] {
                      def usedRegisters: RegisterOffset = 0

                      def construct(in: Registers, baseOffset: RegisterOffset): $tpe = ${tpe.typeSymbol.asClass.module}
                    },
                    deconstructor = Deconstructor.none.asInstanceOf[Deconstructor[$tpe]]
                  )
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
        val cases = subTypes.map {
          var i = -1
          sTpe =>
            i += 1
            q"Schema[$sTpe].reflect.asTerm(${"case" + i})"
        }
        val discrCases = subTypes.map {
          var i = -1
          sTpe =>
            i += 1
            cq"_: $sTpe @_root_.scala.unchecked => $i"
        }
        val matcherCases = subTypes.map { sTpe =>
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
                  cases = _root_.scala.Seq(..$cases),
                  typeName = $tpeName,
                  variantBinding = Binding.Variant(
                    discriminator = new Discriminator[$tpe] {
                      def discriminate(a: $tpe): Int = a match {
                        case ..$discrCases
                      }
                    },
                    matchers = Matchers(_root_.scala.Vector(..$matcherCases)),
                  )
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
          deconst: Tree
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
        lazy val module   = companion(tpe).asModule
        val tpeTypeParams = tpe.typeSymbol.asClass.typeParams
        val tpeParams     = primaryConstructor.paramLists
        val tpeTypeArgs   = typeArgs(tpe)
        var registersUsed = RegisterOffset.Zero
        var i             = 0
        val fieldInfos = tpeParams.map(_.map { param =>
          i += 1
          val symbol = param.asTerm
          val name   = NameTransformer.decode(symbol.name.toString)
          var fTpe   = symbol.typeSignature.dealias
          if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
          val defaultValue =
            if (symbol.isParamWithDefault) Some(q"$module.${TermName("$lessinit$greater$default$" + i)}")
            else None
          val getter =
            getters.getOrElse(name, fail(s"Cannot find '$name' parameter of '$tpe' in the primary constructor."))
          var const: Tree   = null
          var deconst: Tree = null
          val bytes         = RegisterOffset.getBytes(registersUsed)
          val objects       = RegisterOffset.getObjects(registersUsed)
          var offset        = RegisterOffset.Zero
          if (fTpe =:= typeOf[Boolean]) {
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
          FieldInfo(symbol, name, fTpe, defaultValue, const, deconst)
        })
        val fields = fieldInfos.flatMap(_.map { fieldInfo =>
          fieldInfo.defaultValue.fold(q"Schema[${fieldInfo.tpe}].reflect.asTerm(${fieldInfo.name})") { defaultValue =>
            q"Schema[${fieldInfo.tpe}].reflect.defaultValue($defaultValue).asTerm(${fieldInfo.name})"
          }
        })
        val const   = q"new $tpe(...${fieldInfos.map(_.map(fieldInfo => q"${fieldInfo.symbol} = ${fieldInfo.const}"))})"
        val deconst = fieldInfos.flatMap(_.map(_.deconst))
        q"""{
              import _root_.zio.blocks.schema._
              import _root_.zio.blocks.schema.binding._
              import _root_.zio.blocks.schema.binding.RegisterOffset._

              new Schema[$tpe](
                reflect = Reflect.Record[Binding, $tpe](
                  fields = _root_.scala.Seq(..$fields),
                  typeName = $tpeName,
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
                  )
                )
              )
            }"""
      } else {
        q"Schema[$tpe]"
      }
    // c.info(c.enclosingPosition, s"Generated schema for type '$tpe':\n${showCode(schema)}", force = true)
    c.Expr[Schema[A]](schema)
  }
}
