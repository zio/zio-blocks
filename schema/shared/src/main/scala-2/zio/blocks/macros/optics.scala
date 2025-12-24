package zio.blocks.macros

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

@compileTimeOnly("enable macro paradise to expand macro annotations")
class optics(underscore: Boolean = false) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro OpticsMacro.impl
}

object OpticsMacro {
  def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    val prefixUnderscore = c.prefix.tree match {
      case q"new optics(underscore = true)" => true
      case q"new optics(true)" => true
      case _ => false
    }

    def makeLens(className: TypeName, field: ValDef): Tree = {
      val fieldName = field.name
      val lensName = TermName((if (prefixUnderscore) "_" else "") + fieldName.toString)
      val fieldType = field.tpt
      
      q"""
        val $lensName: zio.optics.Lens[$className, $fieldType] = 
          zio.optics.Lens(
            model => scala.util.Right(model.$fieldName),
            newVal => model => scala.util.Right(model.copy($fieldName = newVal))
          )
      """
    }

    val result = annottees.map(_.tree).toList match {
      case (cls @ q"$mods class $tpname[..$tparams] $ctorMods(...$paramss) extends { ..$earlydefns } with ..$parents { $self => ..$stats }") :: rest =>
        val companionOpt = rest.collectFirst { case obj: ModuleDef => obj }
        val lensDefs = paramss.flatten.map(p => makeLens(tpname, p))
        
        val newCompanion = companionOpt match {
          case Some(q"$objMods object $objName extends { ..$objEarly } with ..$objParents { $objSelf => ..$objStats }") =>
            q"""$objMods object $objName extends { ..$objEarly } with ..$objParents { $objSelf => ..$objStats; ..$lensDefs }"""
          case None =>
            val objName = tpname.toTermName
            q"""object $objName { ..$lensDefs }"""
        }
        q"$cls; $newCompanion"
      case _ => c.abort(c.enclosingPosition, "@optics only supports case classes")
    }
    c.Expr[Any](result)
  }
}