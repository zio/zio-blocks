package zio.blocks.schema

import zio.blocks.typeid.{DynamicTypeId, Owner, StandardTypes, TypeDefKind, TypeId, TypeParam}
import scala.collection.mutable
import scala.reflect.macros.blackbox
import scala.reflect.NameTransformer

private[schema] object CommonMacroOps {
  def fail(c: blackbox.Context)(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

  def typeArgs(c: blackbox.Context)(tpe: c.Type): List[c.Type] = tpe.typeArgs.map(_.dealias)

  def companion(c: blackbox.Context)(tpe: c.Type): c.Symbol = {
    import c.universe._

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

  def directSubTypes(c: blackbox.Context)(tpe: c.Type): List[c.Type] = {
    import c.universe._

    implicit val positionOrdering: Ordering[Symbol] =
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
    val tpeClass         = tpe.typeSymbol.asClass
    val tpeTypeArgs      = typeArgs(c)(tpe)
    var tpeParamsAndArgs = Map.empty[String, Type]
    if (tpeTypeArgs ne Nil) {
      tpeClass.typeParams.zip(tpeTypeArgs).foreach { case (typeParam, tpeTypeArg) =>
        tpeParamsAndArgs = tpeParamsAndArgs.updated(typeParam.toString, tpeTypeArg)
      }
    }
    val subTypes = new mutable.ListBuffer[Type]
    tpeClass.knownDirectSubclasses.toArray
      .sortInPlace()
      .foreach { symbol =>
        val classSymbol = symbol.asClass
        // For modules (case objects), use the singleton type (.type) to preserve
        // the specific type (e.g., Status.Active.type instead of Status)
        var classType = if (classSymbol.isModuleClass) classSymbol.module.typeSignature else classSymbol.toType
        if (tpeTypeArgs ne Nil) {
          val typeParams = classSymbol.typeParams
          if (typeParams.nonEmpty) {
            // Get the child's base type of the parent sealed trait
            // e.g., for VarStress[B] extends Stress[B], baseType(Stress) = Stress[B]
            val childBaseType     = classType.baseType(tpeClass)
            val childBaseTypeArgs = typeArgs(c)(childBaseType)

            // Build mapping from child's type params to parent's type args
            // by matching child's base type args with parent's type args
            var childParamsToArgs = Map.empty[String, Type]
            childBaseTypeArgs.zip(tpeTypeArgs).foreach { case (baseArg, parentArg) =>
              baseArg match {
                case TypeRef(_, sym, Nil) if sym.isType =>
                  childParamsToArgs = childParamsToArgs.updated(sym.name.toString, parentArg)
                case _ =>
                // baseArg is a concrete type, not a type param - no mapping needed
              }
            }

            classType = classType.substituteTypes(
              typeParams,
              typeParams.map { typeParam =>
                childParamsToArgs.get(typeParam.name.toString) match {
                  case Some(typeArg) => typeArg
                  case _             =>
                    // Fall back to parent's mapping for shared type params
                    tpeParamsAndArgs.get(typeParam.toString) match {
                      case Some(typeArg) => typeArg
                      case _             =>
                        fail(c)(
                          s"Type parameter '${typeParam.name}' of '$symbol' can't be deduced from type arguments of '$tpe'."
                        )
                    }
                }
              }
            )
          }
        }
        subTypes.addOne(classType)
      }
    subTypes.toList
  }

  def typeId(
    c: blackbox.Context
  )(typeIdCache: mutable.HashMap[c.Type, TypeId[?]], tpe: c.Type): TypeId[?] = {
    import c.universe._

    def calculateTypeId(tpe: Type): TypeId[?] =
      if (tpe =:= typeOf[java.lang.String]) StandardTypes.string
      else {
        var packages  = List.empty[String]
        var values    = List.empty[String]
        val tpeSymbol = tpe.typeSymbol
        var name      = NameTransformer.decode(tpeSymbol.name.toString)
        val comp      = companion(c)(tpe)
        var ownerSym  =
          if (comp == null) tpeSymbol
          else if (comp == NoSymbol) {
            name += ".type"
            tpeSymbol.asClass.module
          } else comp
        while ({
          ownerSym = ownerSym.owner
          ownerSym.owner != NoSymbol
        }) {
          val ownerName = NameTransformer.decode(ownerSym.name.toString)
          if (ownerSym.isPackage || ownerSym.isPackageClass) packages = ownerName :: packages
          else values = ownerName :: values
        }

        val owner = Owner((packages.map(Owner.Package(_)) ::: values.map(Owner.Term(_))).toList)
        val tArgs = typeArgs(c)(tpe).map(ta => typeId(c)(typeIdCache, ta))

        new TypeId(
          DynamicTypeId(
            owner,
            name,
            tArgs.zipWithIndex.map { case (arg, i) =>
              TypeParam(arg.show, i, zio.blocks.typeid.Variance.Invariant, zio.blocks.typeid.TypeBounds.empty)
            },
            TypeDefKind.Class(),
            Nil,
            Nil
          )
        )
      }

    typeIdCache.getOrElseUpdate(
      tpe,
      tpe match {
        case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
          // This is a ZIO Prelude newtype pattern: TypeRef(companionType, Type, Nil)
          // Extract the companion object name and owner chain
          compTpe match {
            case SingleType(_, termSym) if termSym.isModule =>
              val objName = NameTransformer.decode(termSym.name.toString).stripSuffix("$")

              var packages = List.empty[String]
              var values = List.empty[String]
              var ownerSym = termSym.owner
              while (ownerSym != NoSymbol && ownerSym.owner != NoSymbol) {
                val ownerName = NameTransformer.decode(ownerSym.name.toString)
                if (ownerSym.isPackage || ownerSym.isPackageClass) {
                  packages = ownerName :: packages
                } else {
                  values = ownerName :: values
                }
                ownerSym = ownerSym.owner
              }

              val owner = Owner((packages.map(Owner.Package(_)) ::: values.map(Owner.Term(_))).toList)
              new TypeId(
                DynamicTypeId(
                  owner,
                  objName,
                  Nil,
                  TypeDefKind.Class(),
                  Nil,
                  Nil
                )
              )
            case _ =>
              var tTypeId = calculateTypeId(compTpe)
              if (tTypeId.name.endsWith(".type"))
                tTypeId = TypeId(tTypeId.dynamic.copy(name = tTypeId.name.stripSuffix(".type")))
              tTypeId
          }
        case _ =>
          calculateTypeId(tpe)
      }
    )
  }

  def typeIdToTree(c: blackbox.Context)(tid: TypeId[?]): c.Tree = {
    import c.universe._

    val dynamic       = tid.dynamic
    val ownerSegments = dynamic.owner.segments.map {
      case Owner.Package(name) => q"_root_.zio.blocks.typeid.Owner.Package($name)"
      case Owner.Term(name)    => q"_root_.zio.blocks.typeid.Owner.Term($name)"
      case Owner.Type(name)    => q"_root_.zio.blocks.typeid.Owner.Type($name)"
      case Owner.Local(name)   => q"_root_.zio.blocks.typeid.Owner.Local($name)"
    }
    val typeParams = dynamic.typeParams.map { tp =>
      q"_root_.zio.blocks.typeid.TypeParam(${tp.name}, ${tp.index}, _root_.zio.blocks.typeid.Variance.Invariant, _root_.zio.blocks.typeid.TypeBounds.empty)"
    }

    q"""new _root_.zio.blocks.typeid.TypeId(
      _root_.zio.blocks.typeid.DynamicTypeId(
        _root_.zio.blocks.typeid.Owner(scala.List(..$ownerSegments)),
        ${dynamic.name},
        scala.List(..$typeParams),
        _root_.zio.blocks.typeid.TypeDefKind.Class(),
        scala.Nil,
        scala.Nil
      )
    )"""
  }
}
