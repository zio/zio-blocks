package zio.blocks.macros

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

@compileTimeOnly("enable macro paradise to expand macro annotations")
class GenerateOptics(underscore: Boolean = false) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro GenerateOpticsMacro.impl
}

object GenerateOpticsMacro {
  def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    val underscore = c.prefix.tree match {
      case q"new GenerateOptics(underscore = true)" => true
      case q"new GenerateOptics(true)" => true
      case _ => false
    }

    def generateLens(clsName: TypeName, field: ValDef): Tree = {
      val fieldName = field.name
      val lensName = TermName(if (underscore) "_" + fieldName.toString else fieldName.toString)
      val fieldType = field.tpt

      q"""
        val $lensName: zio.optics.Lens[$clsName, $fieldType] = 
          zio.optics.Lens(
            model => scala.util.Right(model.$fieldName),
            v => model => scala.util.Right(model.copy($fieldName = v))
          )
      """
    }

    val result = annottees.map(_.tree).toList match {
      case (cls @ q"$mods class $tpname[..$tparams] $ctorMods(...$paramss) extends { ..$earlydefns } with ..$parents { $self => ..$stats }") :: rest =>
        
        val lensDefs = paramss.flatten.map(f => generateLens(tpname, f))

        val companion = rest.collectFirst { case obj: ModuleDef => obj } match {
          case Some(q"$mods object $tname extends { ..$early } with ..$parents { $self => ..$body }") =>
            q"$mods object $tname extends { ..$early } with ..$parents { $self => ..$body; ..$lensDefs }"
          case None =>
            val objName = tpname.toTermName
            q"object $objName { ..$lensDefs }"
        }

        q"$cls; $companion"

      case _ => c.abort(c.enclosingPosition, "@GenerateOptics only works on case classes")
    }

    c.Expr[Any](result)
  }
}