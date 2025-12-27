package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

trait DerivedSchema[A] {
  implicit def schema: Schema[A] = macro DerivedSchemaMacro.impl[A]
}

object DerivedSchemaMacro {
  def impl[A: c.WeakTypeTag](c: whitebox.Context): c.Tree = {
    import c.universe._
    val tpe = weakTypeOf[A]
    q"_root_.zio.blocks.schema.Schema.derived[$tpe]"
  }
}
