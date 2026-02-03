package zio.blocks.schema.migration

import zio.blocks.schema._

case class PersonV1(name: String)
object PersonV1 {
  implicit val schema: Schema[PersonV1] = Schema.derived
}

case class PersonV2(name: String, age: Int)
object PersonV2 {
  implicit val schema: Schema[PersonV2] = Schema.derived
}

case class PersonV3(name: String, age: Int, active: Boolean)
object PersonV3 {
  implicit val schema: Schema[PersonV3] = Schema.derived
}

case class PersonRenamed(fullName: String)
object PersonRenamed {
  implicit val schema: Schema[PersonRenamed] = Schema.derived
}

case class PersonWithIntId(name: String, id: Int)
object PersonWithIntId {
  implicit val schema: Schema[PersonWithIntId] = Schema.derived
}

case class PersonWithLongId(name: String, id: Long)
object PersonWithLongId {
  implicit val schema: Schema[PersonWithLongId] = Schema.derived
}

case class Contact(email: String)
object Contact {
  implicit val schema: Schema[Contact] = Schema.derived
}

case class ContactV2(email: String, phone: String)
object ContactV2 {
  implicit val schema: Schema[ContactV2] = Schema.derived
}

case class PersonWithContact(name: String, contact: Contact)
object PersonWithContact {
  implicit val schema: Schema[PersonWithContact] = Schema.derived
}

case class PersonWithContactV2(name: String, contact: ContactV2)
object PersonWithContactV2 {
  implicit val schema: Schema[PersonWithContactV2] = Schema.derived
}

case class NestedV1(person: PersonWithContact)
object NestedV1 {
  implicit val schema: Schema[NestedV1] = Schema.derived
}

case class NestedV2(person: PersonWithContactV2)
object NestedV2 {
  implicit val schema: Schema[NestedV2] = Schema.derived
}

sealed trait StatusV1
object StatusV1 {
  case object Active   extends StatusV1
  case object Inactive extends StatusV1
  implicit val schema: Schema[StatusV1] = Schema.derived
}

sealed trait StatusV2
object StatusV2 {
  case object Enabled  extends StatusV2
  case object Inactive extends StatusV2
  implicit val schema: Schema[StatusV2] = Schema.derived
}

sealed trait StatusV3
object StatusV3 {
  case object Active   extends StatusV3
  case object Inactive extends StatusV3
  case object Pending  extends StatusV3
  implicit val schema: Schema[StatusV3] = Schema.derived
}

case class PersonWithFullName(fullName: String, age: Int)
object PersonWithFullName {
  implicit val schema: Schema[PersonWithFullName] = Schema.derived
}

case class PersonWithSplitName(firstName: String, lastName: String, age: Int)
object PersonWithSplitName {
  implicit val schema: Schema[PersonWithSplitName] = Schema.derived
}

case class ItemV1(name: String)
object ItemV1 {
  implicit val schema: Schema[ItemV1] = Schema.derived
}

case class ItemV2(name: String, count: Int)
object ItemV2 {
  implicit val schema: Schema[ItemV2] = Schema.derived
}

case class ContainerWithItemsV1(items: Seq[ItemV1])
object ContainerWithItemsV1 {
  implicit val schema: Schema[ContainerWithItemsV1] = Schema.derived
}

case class ContainerWithItemsV2(items: Seq[ItemV2])
object ContainerWithItemsV2 {
  implicit val schema: Schema[ContainerWithItemsV2] = Schema.derived
}

case class MapValueV1(value: String)
object MapValueV1 {
  implicit val schema: Schema[MapValueV1] = Schema.derived
}

case class MapValueV2(value: String, version: Int)
object MapValueV2 {
  implicit val schema: Schema[MapValueV2] = Schema.derived
}

case class ContainerWithMapV1(data: scala.collection.immutable.Map[String, MapValueV1])
object ContainerWithMapV1 {
  implicit val schema: Schema[ContainerWithMapV1] = Schema.derived
}

case class ContainerWithMapV2(data: scala.collection.immutable.Map[String, MapValueV2])
object ContainerWithMapV2 {
  implicit val schema: Schema[ContainerWithMapV2] = Schema.derived
}
