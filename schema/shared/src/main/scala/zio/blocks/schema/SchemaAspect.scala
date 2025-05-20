package zio.blocks.schema

trait SchemaAspect[-Min, +Max] {

  def apply[A >: Max <: Min](reflect: Reflect.Bound[A]): Reflect.Bound[A]
}

object SchemaAspect {

  val identity: SchemaAspect[Any, Nothing] = new SchemaAspect[Any, Nothing] {
    def apply[A](reflect: Reflect.Bound[A]): Reflect.Bound[A] = reflect
  }

  def doc(doc: String): SchemaAspect[Any, Nothing] = new SchemaAspect[Any, Nothing] {
    def apply[A](reflect: Reflect.Bound[A]): Reflect.Bound[A] = reflect.doc(doc)
  }

  def examples[A0](value: A0, values: A0*): SchemaAspect[A0, A0] = new SchemaAspect[A0, A0] {

    def apply[A >: A0 <: A0](reflect: Reflect.Bound[A]): Reflect.Bound[A] =
      reflect.examples(value, values: _*)
  }

}
