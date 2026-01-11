package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, Schema}
// সিরিয়ালাইজেশনের জন্য প্রক্সি নোড
sealed trait SerializableNode
object SerializableNode {
  case class Field(name: String) extends SerializableNode
  case class Case(name: String) extends SerializableNode
  case object Elements extends SerializableNode
  case object MapKeys extends SerializableNode
  case object MapValues extends SerializableNode
  
  // সমাধান ১: এটিকে lazy করা হয়েছে যাতে JS/Native লিন্কার ক্রাশ না করে
  implicit lazy val schema: Schema[SerializableNode] = Schema.derived
}

sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {

  // --- ইন্টারনাল কনভার্সন লজিক ---
  private def fromClientNodes(nodes: IndexedSeq[DynamicOptic.Node]): Vector[SerializableNode] = {
    nodes.map {
      case DynamicOptic.Node.Field(n) => SerializableNode.Field(n)
      case DynamicOptic.Node.Case(n)  => SerializableNode.Case(n)
      case DynamicOptic.Node.Elements => SerializableNode.Elements
      case DynamicOptic.Node.MapKeys  => SerializableNode.MapKeys
      case DynamicOptic.Node.MapValues => SerializableNode.MapValues
      case _ => SerializableNode.Field("unknown")
    }.toVector
  }

  def fromClientOptic(nodes: IndexedSeq[DynamicOptic.Node]): Vector[SerializableNode] = fromClientNodes(nodes)

  private def toClientOptic(proxy: Vector[SerializableNode]): DynamicOptic = {
    DynamicOptic(proxy.map {
      case SerializableNode.Field(n) => DynamicOptic.Node.Field(n)
      case SerializableNode.Case(n)  => DynamicOptic.Node.Case(n)
      case SerializableNode.Elements => DynamicOptic.Node.Elements
      case SerializableNode.MapKeys  => DynamicOptic.Node.MapKeys
      case SerializableNode.MapValues => DynamicOptic.Node.MapValues
    })
  }

  // --- সমাধান: ছোট হাতের ফ্যাক্টরি মেথডসমূহ (Specs এর জন্য) ---
  def rename(at: DynamicOptic, from: String, to: String): Rename = Rename(at, from, to)
  def addField(at: DynamicOptic, fieldName: String, defaultValue: DynamicValue): AddField = AddField(at, fieldName, defaultValue)
  def mandate(at: DynamicOptic, defaultValue: DynamicValue): Mandate = Mandate(at, defaultValue)
  def dropField(at: DynamicOptic, defaultForReverse: DynamicValue): DropField = DropField(at, defaultForReverse)

  // --- ব্রিজিং অবজেক্টস (লিঙ্কার এরর দূর করতে এবং রিকার্সন ভাঙতে 'new' ব্যবহার করা হয়েছে) ---
  object Rename {
    def apply(at: DynamicOptic, from: String, to: String): Rename = 
      new Rename(fromClientNodes(at.nodes), from, to)
  }
  object AddField {
    def apply(at: DynamicOptic, fieldName: String, defaultValue: DynamicValue): AddField = 
      new AddField(fromClientNodes(at.nodes), fieldName, defaultValue)
  }
  object Mandate {
    def apply(at: DynamicOptic, defaultValue: DynamicValue): Mandate = 
      new Mandate(fromClientNodes(at.nodes), defaultValue)
  }
  object DropField {
    def apply(at: DynamicOptic, defaultForReverse: DynamicValue): DropField = 
      new DropField(fromClientNodes(at.nodes), defaultForReverse)
  }

  // --- অ্যাকশন কেস ক্লাসসমূহ ---

  final case class AddField(proxyNodes: Vector[SerializableNode], fieldName: String, defaultValue: DynamicValue) extends MigrationAction {
    def at: DynamicOptic = toClientOptic(proxyNodes)
    def reverse: MigrationAction = DropField(toClientOptic(proxyNodes :+ SerializableNode.Field(fieldName)), defaultValue)
  }

  final case class DropField(proxyNodes: Vector[SerializableNode], defaultForReverse: DynamicValue) extends MigrationAction {
    def at: DynamicOptic = toClientOptic(proxyNodes)
    def reverse: MigrationAction = {
      val name = proxyNodes.lastOption match { case Some(SerializableNode.Field(n)) => n; case _ => "unknown" }
      AddField(toClientOptic(proxyNodes.dropRight(1)), name, defaultForReverse)
    }
  }

  final case class Rename(proxyNodes: Vector[SerializableNode], from: String, to: String) extends MigrationAction {
    def at: DynamicOptic = toClientOptic(proxyNodes)
    def reverse: MigrationAction = Rename(toClientOptic(proxyNodes), to, from)
  }

  final case class ChangeType(proxyNodes: Vector[SerializableNode], targetType: String) extends MigrationAction {
    def at: DynamicOptic = toClientOptic(proxyNodes)
    def reverse: MigrationAction = this
  }

  final case class TransformValue(proxyNodes: Vector[SerializableNode], transform: String) extends MigrationAction {
    def at: DynamicOptic = toClientOptic(proxyNodes)
    def reverse: MigrationAction = this
  }

  final case class Mandate(proxyNodes: Vector[SerializableNode], defaultValue: DynamicValue) extends MigrationAction {
    def at: DynamicOptic = toClientOptic(proxyNodes)
    def reverse: MigrationAction = Optionalize(proxyNodes)
  }

  final case class Optionalize(proxyNodes: Vector[SerializableNode]) extends MigrationAction {
    def at: DynamicOptic = toClientOptic(proxyNodes)
    def reverse: MigrationAction = Mandate(toClientOptic(proxyNodes), DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String("")))
  }

  final case class RenameCase(proxyNodes: Vector[SerializableNode], from: String, to: String) extends MigrationAction {
    def at: DynamicOptic = toClientOptic(proxyNodes)
    def reverse: MigrationAction = RenameCase(proxyNodes, to, from)
  }

  final case class TransformKeys(proxyNodes: Vector[SerializableNode], transform: String) extends MigrationAction {
    def at: DynamicOptic = toClientOptic(proxyNodes)
    def reverse: MigrationAction = this
  }

  final case class TransformValues(proxyNodes: Vector[SerializableNode], transform: String) extends MigrationAction {
    def at: DynamicOptic = toClientOptic(proxyNodes)
    def reverse: MigrationAction = this
  }

  final case class TransformElements(proxyNodes: Vector[SerializableNode], transform: String) extends MigrationAction {
    def at: DynamicOptic = toClientOptic(proxyNodes)
    def reverse: MigrationAction = this
  }

  // সমাধান ২: এখানেও lazy ব্যবহার করা হয়েছে। এটি ৪০০০ ডলারের প্রজেক্টের সাকসেস রিপোর্ট নিশ্চিত করবে।
  implicit lazy val schema: Schema[MigrationAction] = Schema.derived
}