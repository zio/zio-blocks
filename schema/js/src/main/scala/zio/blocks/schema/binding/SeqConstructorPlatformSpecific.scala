package zio.blocks.schema.binding

trait SeqConstructorPlatformSpecific {
  def newArray[A](fqcn: String, length: Int): Array[A] =
    new scala.scalajs.js.Array[AnyRef](length).asInstanceOf[Array[A]]
}
