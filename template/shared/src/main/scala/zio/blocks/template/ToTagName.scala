package zio.blocks.template

trait ToTagName[-A] {
  def toTagName(a: A): String
}

object ToTagName {

  def apply[A](implicit ev: ToTagName[A]): ToTagName[A] = ev

  implicit val safeTagNameToTagName: ToTagName[SafeTagName] = new ToTagName[SafeTagName] {
    def toTagName(a: SafeTagName): String = a.value
  }
}
