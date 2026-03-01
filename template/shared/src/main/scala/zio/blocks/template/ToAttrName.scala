package zio.blocks.template

trait ToAttrName[-A] {
  def toAttrName(a: A): String
}

object ToAttrName {

  def apply[A](implicit ev: ToAttrName[A]): ToAttrName[A] = ev

  implicit val safeAttrNameToAttrName: ToAttrName[SafeAttrName] = new ToAttrName[SafeAttrName] {
    def toAttrName(a: SafeAttrName): String = a.value
  }

  implicit val eventAttrNameToAttrName: ToAttrName[EventAttrName] = new ToAttrName[EventAttrName] {
    def toAttrName(a: EventAttrName): String = a.value
  }
}
