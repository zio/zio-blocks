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
      case class FieldInfo(name: String, tpe: Type, getter: Symbol, defaultValue: Option[Tree])

      val tpeTypeSymbol = tpe.typeSymbol
      val name          = NameTransformer.decode(tpeTypeSymbol.name.toString)
      val comp          = companion(tpeTypeSymbol)
      var values        = List.empty[String]
      var packages      = List.empty[String]
      var owner         = comp.owner
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
      val tpeTypeParams = tpeTypeSymbol.asClass.typeParams
      val tpeParams     = primaryConstructor.paramLists
      val tpeTypeArgs   = typeArgs(tpe)
      var i             = 0
      val fieldInfos = tpeParams.map(_.map { param =>
        i += 1
        val symbol = param.asTerm
        val name   = symbol.name
        FieldInfo(
          name = NameTransformer.decode(name.toString),
          tpe = {
            val originFieldTpe = symbol.typeSignature.dealias
            if (tpeTypeArgs.isEmpty) originFieldTpe
            else originFieldTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
          },
          getter = {
            tpe.members
              .filter(_.name == name)
              .collectFirst {
                case m: MethodSymbol if m.isParamAccessor && m.isGetter =>
                  m
              }
              .getOrElse(fail(s"Cannot find '$name' parameter of '$tpe' in the primary constructor."))
          },
          defaultValue = {
            if (symbol.isParamWithDefault) Some(q"${comp.asModule}.${TermName("$lessinit$greater$default$" + i)}")
            else None
          }
        )
      })
      // TODO: use `fieldInfos` to generate remaining `Reflect.Record.fields` and `Reflect.Record.recordBinding`
      c.Expr[Schema[A]](
        q"""new _root_.zio.blocks.schema.Schema[$tpe](
              reflect = _root_.zio.blocks.schema.Reflect.Record[_root_.zio.blocks.schema.binding.Binding, $tpe](
                fields = Nil,
                typeName = TypeName(
                  namespace = Namespace(
                    packages = $packages,
                    values = $values
                  ),
                  name = $name
                ),
                recordBinding = Binding.Record(
                  constructor = null,
                  deconstructor = null,
                  defaultValue = None,
                  examples = Nil
                ),
                doc = Doc.Empty,
                modifiers = Nil
              )
            )"""
      )
    } else fail(s"Cannot derive '${typeOf[Schema[_]]}' for '$tpe'.")
  }
}
