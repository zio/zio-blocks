package zio.blocks.schema.migration.macros

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration.ToDynamicOptic
import scala.quoted.*

object AccessorMacros {
  
  // Scala 3-এ ম্যাক্রো এন্ট্রি পয়েন্ট হিসেবে 'inline def' ব্যবহার করা সবচেয়ে নিরাপদ এবং স্ট্যান্ডার্ড
  inline def derive[S, A](inline selector: S => A): ToDynamicOptic[S, A] = 
    ${ deriveImpl[S, A]('selector) }

  def deriveImpl[S: Type, A: Type](selector: Expr[S => A])(using Quotes): Expr[ToDynamicOptic[S, A]] = {
    import quotes.reflect.*

    def extractPath(tree: Tree): List[String] = tree match {
      case Inlined(_, _, target) => extractPath(target)
      case Lambda(_, body) => extractPath(body)
      case Select(obj, name) => extractPath(obj) :+ name
      case Ident(_) => Nil 
      case _ => report.errorAndAbort(s"Unsupported selector: ${tree.show}. Use _.field style.")
    }

    val paths = extractPath(selector.asTerm)
    
    '{
      new ToDynamicOptic[S, A] {
        def apply(): DynamicOptic = {
          val nodes = ${ Expr(paths) }.map(name => DynamicOptic.Node.Field(name))
          DynamicOptic(nodes.toIndexedSeq)
        }
      }
    }
  }
}