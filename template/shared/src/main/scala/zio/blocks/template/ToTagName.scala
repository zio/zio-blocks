package zio.blocks.template

trait ToTagName[-A] {
  def toTagName(a: A): String
}

object ToTagName {

  def apply[A](implicit ev: ToTagName[A]): ToTagName[A] = ev

  implicit val safeTagNameToTagName: ToTagName[SafeTagName] = new ToTagName[SafeTagName] {
    def toTagName(a: SafeTagName): String = a.value
  }

  implicit val elementToTagName: ToTagName[Dom.Element] = new ToTagName[Dom.Element] {
    def toTagName(a: Dom.Element): String = a.tag
  }

  implicit val tagToTagName: ToTagName[Tag] = new ToTagName[Tag] {
    def toTagName(a: Tag): String = a.name
  }
}
