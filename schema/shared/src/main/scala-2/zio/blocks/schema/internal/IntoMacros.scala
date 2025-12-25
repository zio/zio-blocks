package zio.blocks.schema.internal

import zio.blocks.schema.SchemaError
import scala.reflect.macros.blackbox

class IntoMacros(val c: blackbox.Context) {
  import c.universe._

  def deriveImpl[A: c.WeakTypeTag, B: c.WeakTypeTag]: c.Expr[zio.blocks.schema.Into[A, B]] = {
    val tpeA = weakTypeOf[A]
    val tpeB = weakTypeOf[B]

    if (tpeA =:= tpeB) {
      return c.Expr[zio.blocks.schema.Into[A, B]](q"zio.blocks.schema.Into.identity")
    }

    if (!isProduct(tpeA) || !isProduct(tpeB)) {
      c.abort(c.enclosingPosition, s"Derivation only supported for case classes. Cannot derive ${tpeA} => ${tpeB}")
    }

    val fieldsA = getFields(tpeA)
    val fieldsB = getFields(tpeB)

    val mappingCode = fieldsB.map { fieldB =>
      val nameB = fieldB.name
      val typeB = fieldB.typeSignature

      // 1. Exact Match
      val exact = fieldsA.find(f => f.name == nameB && f.typeSignature =:= typeB)

      // 2. Name Match
      lazy val nameMatch = fieldsA.find(f => f.name == nameB)

      // 3. Unique Type
      lazy val uniqueType = {
        val candidatesA = fieldsA.filter(_.typeSignature =:= typeB)
        val candidatesB = fieldsB.filter(_.typeSignature =:= typeB)
        if (candidatesA.size == 1 && candidatesB.size == 1) Some(candidatesA.head) else None
      }

      val matchFound = exact.orElse(nameMatch).orElse(uniqueType)

      matchFound match {
        case Some(fieldA) =>
          val access     = q"input.${fieldA.name}"
          val conversion = convertField(access, fieldA.typeSignature, typeB)

          q"""
            val ${TermName(s"res_${nameB}")} = $conversion
          """

        case None if isOption(typeB) =>
          q"""
            val ${TermName(s"res_${nameB}")} = Right(None)
           """

        case None =>
          c.abort(c.enclosingPosition, s"Cannot find mapping for field '${nameB}' of type ${typeB} in ${tpeA}")
      }
    }

    val errorCollection = fieldsB.map { f =>
      val name = f.name.toString
      val term = TermName(s"res_${f.name}")
      q"""
         $term match {
           case Left(err) => Some(err.atPath($name))
           case _ => None
         }
       """
    }

    val reconstruction = fieldsB.map { f =>
      val term = TermName(s"res_${f.name}")
      q"$term.right.get.asInstanceOf[${f.typeSignature}]"
    }

    val constructor = if (tpeB.companion != NoSymbol) {
      q"${tpeB.typeSymbol.companion}.apply(..$reconstruction)"
    } else {
      q"new $tpeB(..$reconstruction)"
    }

    val result = q"""
      new zio.blocks.schema.Into[$tpeA, $tpeB] {
        def into(input: $tpeA): Either[zio.blocks.schema.SchemaError, $tpeB] = {
          ..$mappingCode
          
          val errors = List(..$errorCollection).flatten
          
          if (errors.nonEmpty) {
             Left(zio.blocks.schema.SchemaError.accumulateErrors(Nil).left.getOrElse(errors.head).asInstanceOf[zio.blocks.schema.SchemaError])
          } else {
             Right($constructor)
          }
        }
      }
    """

    c.Expr[zio.blocks.schema.Into[A, B]](result)
  }

  private def convertField(access: Tree, from: Type, to: Type): Tree =
    if (from =:= to) {
      q"Right($access)"
    } else {
      val primitiveConversion = (from.toString, to.toString) match {
        case ("Int", "Long")     => Some(q"Right($access.toLong)")
        case ("Int", "Double")   => Some(q"Right($access.toDouble)")
        case ("Float", "Double") => Some(q"Right($access.toDouble)")
        case ("Byte", "Short")   => Some(q"Right($access.toShort)")
        case ("Short", "Int")    => Some(q"Right($access.toInt)")
        case _                   => None
      }

      primitiveConversion.getOrElse {
        val intoType         = appliedType(typeOf[zio.blocks.schema.Into[_, _]], from, to)
        val implicitInstance = c.inferImplicitValue(intoType)

        if (implicitInstance != EmptyTree) {
          q"$implicitInstance.into($access)"
        } else if (isProduct(from) && isProduct(to)) {
          q"zio.blocks.schema.Into.derive[$from, $to].into($access)"
        } else {
          c.abort(c.enclosingPosition, s"No Into instance found for $from => $to")
        }
      }
    }

  private def getFields(tpe: Type): List[TermSymbol] =
    tpe.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => m.asTerm
    }.toList

  private def isProduct(tpe: Type): Boolean =
    tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isCaseClass

  private def isOption(tpe: Type): Boolean =
    tpe.typeSymbol.fullName == "scala.Option"
}
