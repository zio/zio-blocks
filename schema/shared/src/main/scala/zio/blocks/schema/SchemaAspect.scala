package zio.blocks.schema

trait SchemaAspect { // [-Min, +Max] {

  def apply[A](reflect: Reflect.Bound[A]): Reflect.Bound[A]
}

object SchemaAspect {

  val identity: SchemaAspect = new SchemaAspect {
    def apply[A](reflect: Reflect.Bound[A]): Reflect.Bound[A] = reflect
  }

  def doc(doc: String): SchemaAspect = new SchemaAspect {
    def apply[A](reflect: Reflect.Bound[A]): Reflect.Bound[A] = reflect.doc(doc)
  }

//   def examples[A](value: A, values: A): SchemaAspect = new SchemaAspect {

//     def apply[A](reflect: Reflect.Bound[A]): Reflect.Bound[A] =
//       reflect.examples(value.asInstanceOf[A], values.asInstanceOf[Seq[A]]: _*)
//   }

}
