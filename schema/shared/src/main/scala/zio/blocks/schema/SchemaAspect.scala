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

  def examples[A](value: A, values: A*): SchemaAspect[A, A] = new SchemaAspect[A, A] {

    def apply[A](reflect: Reflect.Bound[A]): Reflect.Bound[A] =
      reflect.examples(value.asInstanceOf[A], values.asInstanceOf[Seq[A]]: _*)
  }

}
