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
    import zio.blocks.schema.binding.RegisterOffset
    import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

    def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

    def isNonAbstractScalaClass(tpe: Type): Boolean =
      tpe.typeSymbol.isClass && !tpe.typeSymbol.isAbstract && !tpe.typeSymbol.isJava

    def typeArgs(tpe: Type): List[Type] = tpe.typeArgs.map(_.dealias)

    def companion(typeSymbol: Symbol): Symbol = {
      val comp = typeSymbol.companion
      if (comp.isModule) comp
      else {
        val ownerChainOf = (s: Symbol) =>
          Iterator.iterate(s)(_.owner).takeWhile(x => x != NoSymbol).toVector.reverseIterator
        val path = ownerChainOf(typeSymbol)
          .zipAll(ownerChainOf(enclosingOwner), NoSymbol, NoSymbol)
          .dropWhile { case (x, y) => x == y }
          .takeWhile { case (x, _) => x != NoSymbol }
          .map { case (x, _) => x.name.toTermName }
        if (path.isEmpty) NoSymbol
        else c.typecheck(path.foldLeft[Tree](Ident(path.next()))(Select(_, _)), silent = true).symbol
      }
    }

    val tpe = weakTypeOf[A].dealias
    if (isNonAbstractScalaClass(tpe)) {
      case class FieldInfo(
        name: String,
        tpe: Type,
        getter: Symbol,
        defaultValue: Option[Tree],
        offset: RegisterOffset
      )

      val tpeTypeSymbol = tpe.typeSymbol
      val name          = NameTransformer.decode(tpeTypeSymbol.name.toString)
      val comp          = companion(tpeTypeSymbol)
      var values        = List.empty[String]
      var packages      = List.empty[String]
      var owner         = (if (comp == null) tpeTypeSymbol else comp).owner
      while (owner != NoSymbol) {
        val name = NameTransformer.decode(owner.name.toString)
        if (owner.isPackage || owner.isPackageClass) packages = name :: packages
        else values = name :: values
        owner = owner.owner
      }
      packages = packages.tail
      val primaryConstructor = tpe.decls.collectFirst {
        case m: MethodSymbol if m.isPrimaryConstructor => m
      }.getOrElse(fail(s"Cannot find a primary constructor for '$tpe'"))
      var getters = Map.empty[String, MethodSymbol]
      tpe.members.foreach {
        case m: MethodSymbol if m.isParamAccessor =>
          getters = getters.updated(NameTransformer.decode(m.name.toString), m)
        case _ =>
      }
      val tpeTypeParams = tpeTypeSymbol.asClass.typeParams
      val tpeParams     = primaryConstructor.paramLists
      val tpeTypeArgs   = typeArgs(tpe)
      var offset        = RegisterOffset.Zero
      var i             = 0
      val fieldInfos = tpeParams.map(_.map { param =>
        i += 1
        val symbol = param.asTerm
        val name   = NameTransformer.decode(symbol.name.toString)
        FieldInfo(
          name = name,
          offset = offset,
          tpe = {
            var fTpe = symbol.typeSignature.dealias
            if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
            offset = RegisterOffset.add(
              offset,
              if (fTpe =:= typeOf[Boolean] || fTpe =:= typeOf[java.lang.Boolean]) RegisterOffset(booleans = 1)
              else if (fTpe =:= typeOf[Byte] || fTpe =:= typeOf[java.lang.Byte]) RegisterOffset(bytes = 1)
              else if (fTpe =:= typeOf[Char] || fTpe =:= typeOf[java.lang.Character]) RegisterOffset(chars = 1)
              else if (fTpe =:= typeOf[Short] || fTpe =:= typeOf[java.lang.Short]) RegisterOffset(shorts = 1)
              else if (fTpe =:= typeOf[Float] || fTpe =:= typeOf[java.lang.Float]) RegisterOffset(floats = 1)
              else if (fTpe =:= typeOf[Int] || fTpe =:= typeOf[java.lang.Integer]) RegisterOffset(ints = 1)
              else if (fTpe =:= typeOf[Double] || fTpe =:= typeOf[java.lang.Double]) RegisterOffset(doubles = 1)
              else if (fTpe =:= typeOf[Long] || fTpe =:= typeOf[java.lang.Long]) RegisterOffset(longs = 1)
              else RegisterOffset(objects = 1)
            )
            fTpe
          },
          getter =
            getters.getOrElse(name, fail(s"Cannot find '$name' parameter of '$tpe' in the primary constructor.")),
          defaultValue = {
            if (symbol.isParamWithDefault) Some(q"${comp.asModule}.${TermName("$lessinit$greater$default$" + i)}")
            else None
          }
        )
      })
      val fields = fieldInfos.flatMap(_.map { fieldInfo =>
        fieldInfo.defaultValue.fold(q"Schema[${fieldInfo.tpe}].reflect.asTerm(${fieldInfo.name})") { defaultValue =>
          q"Schema[${fieldInfo.tpe}].reflect.defaultValue($defaultValue).asTerm(${fieldInfo.name})"
        }
      })
      // TODO: use `fieldInfos` to generate remaining `Reflect.Record.fields` and `Reflect.Record.recordBinding`
      c.Expr[Schema[A]](
        q"""{
              import _root_.zio.blocks.schema._
              import _root_.zio.blocks.schema.binding._
              import _root_.zio.blocks.schema.binding.RegisterOffset._

              new Schema[$tpe](
                reflect = Reflect.Record[Binding, $tpe](
                  fields = Seq(..$fields),
                  typeName = TypeName(
                    namespace = Namespace(
                      packages = $packages,
                      values = $values
                    ),
                    name = $name
                  ),
                  recordBinding = Binding.Record(
                    constructor = new Constructor[$tpe] {
                      def usedRegisters: RegisterOffset = $offset

                      def construct(in: Registers, baseOffset: RegisterOffset): $tpe = ???
                    },
                    deconstructor = new Deconstructor[$tpe] {
                      def usedRegisters: RegisterOffset = $offset

                      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: $tpe): Unit = ???
                    },
                    defaultValue = _root_.scala.None,
                    examples = _root_.scala.Nil
                  ),
                  doc = Doc.Empty,
                  modifiers = _root_.scala.Nil
                )
              )
            }"""
      )
    } else fail(s"Cannot derive '${typeOf[Schema[_]]}' for '$tpe'.")
  }
}
