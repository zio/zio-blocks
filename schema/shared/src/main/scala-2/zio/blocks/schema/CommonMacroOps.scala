package zio.blocks.schema

import scala.collection.mutable
import scala.reflect.macros.blackbox
import scala.reflect.NameTransformer

private[schema] object CommonMacroOps {
  def fail(c: blackbox.Context)(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

  def typeArgs(c: blackbox.Context)(tpe: c.Type): List[c.Type] = tpe.typeArgs.map(_.dealias)

  def isEnumOrModuleValue(c: blackbox.Context)(tpe: c.Type): Boolean = tpe.typeSymbol.isModuleClass

  def isSealedTraitOrAbstractClass(c: blackbox.Context)(tpe: c.Type): Boolean = tpe.typeSymbol.isClass && {
    val classSymbol = tpe.typeSymbol.asClass
    classSymbol.isSealed && (classSymbol.isAbstract || classSymbol.isTrait)
  }

  def isNonAbstractScalaClass(c: blackbox.Context)(tpe: c.Type): Boolean =
    tpe.typeSymbol.isClass && !tpe.typeSymbol.isAbstract && !tpe.typeSymbol.isJava

  def subTypes(c: blackbox.Context)(tpe: c.Type): List[c.Type] = {
    import c.universe._

    val seen                                        = new mutable.HashSet[c.Type]
    val orderedLeaves                               = new mutable.ListBuffer[c.Type]
    val orderedIntermediates                        = new mutable.ListBuffer[c.Type]
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

    def directSubTypes(tpe: c.Type): List[c.Type] = {
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
          var classType   = classSymbol.toType
          if (tpeTypeArgs ne Nil) {
            val typeParams = classSymbol.typeParams
            classType = classType.substituteTypes(
              typeParams,
              typeParams.map { typeParam =>
                tpeParamsAndArgs.get(typeParam.toString) match {
                  case Some(typeArg) => typeArg
                  case _             =>
                    fail(c)(
                      s"Type parameter '${typeParam.name}' of '$symbol' can't be deduced from type arguments of '$tpe'."
                    )
                }
              }
            )
          }
          subTypes.addOne(classType)
        }
      subTypes.toList
    }

    def collectRecursively(tpe: c.Type): Unit =
      if (isNonAbstractScalaClass(c)(tpe)) {
        if (seen.add(tpe)) orderedLeaves.addOne(tpe)
      } else
        directSubTypes(tpe).foreach { subTpe =>
          if (isEnumOrModuleValue(c)(subTpe) || isNonAbstractScalaClass(c)(subTpe)) {
            if (seen.add(subTpe)) orderedLeaves.addOne(subTpe)
          } else if (isSealedTraitOrAbstractClass(c)(subTpe)) {
            collectRecursively(subTpe)
            if (seen.add(subTpe)) orderedIntermediates.addOne(subTpe)
          } else fail(c)("Only sealed intermediate traits or abstract classes are supported.")
        }

    collectRecursively(tpe)
    (orderedLeaves.addAll(orderedIntermediates)).toList
  }
}
