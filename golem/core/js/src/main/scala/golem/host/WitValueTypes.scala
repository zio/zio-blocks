package golem.host

import scala.scalajs.js

/**
 * Scala types for `golem:rpc/types@0.2.2` value serialization types.
 *
 * These model the WIT `wit-value`, `wit-node`, `wit-type`, `wit-type-node`, and
 * `value-and-type` types used by the durability and oplog APIs.
 */
object WitValueTypes {

  type NodeIndex = Int

  // --- WIT: wit-node variant (22 cases) ---

  sealed trait WitNode extends Product with Serializable
  object WitNode {
    final case class RecordValue(fields: List[NodeIndex])                       extends WitNode
    final case class VariantValue(caseIndex: Int, value: Option[NodeIndex])     extends WitNode
    final case class EnumValue(caseIndex: Int)                                  extends WitNode
    final case class FlagsValue(flags: List[Boolean])                           extends WitNode
    final case class TupleValue(elements: List[NodeIndex])                      extends WitNode
    final case class ListValue(elements: List[NodeIndex])                       extends WitNode
    final case class OptionValue(value: Option[NodeIndex])                      extends WitNode
    final case class ResultValue(ok: Option[NodeIndex], err: Option[NodeIndex]) extends WitNode
    final case class PrimU8(value: Short)                                       extends WitNode
    final case class PrimU16(value: Int)                                        extends WitNode
    final case class PrimU32(value: Long)                                       extends WitNode
    final case class PrimU64(value: BigInt)                                     extends WitNode
    final case class PrimS8(value: Byte)                                        extends WitNode
    final case class PrimS16(value: Short)                                      extends WitNode
    final case class PrimS32(value: Int)                                        extends WitNode
    final case class PrimS64(value: Long)                                       extends WitNode
    final case class PrimFloat32(value: Float)                                  extends WitNode
    final case class PrimFloat64(value: Double)                                 extends WitNode
    final case class PrimChar(value: Char)                                      extends WitNode
    final case class PrimBool(value: Boolean)                                   extends WitNode
    final case class PrimString(value: String)                                  extends WitNode
    final case class Handle(uri: String, resourceId: BigInt)                    extends WitNode

    def fromDynamic(raw: js.Dynamic): WitNode = {
      val tag = raw.tag.asInstanceOf[String]
      tag match {
        case "record-value" =>
          val arr = raw.selectDynamic("val").asInstanceOf[js.Array[Int]]
          RecordValue(arr.toList)
        case "variant-value" =>
          val tup      = raw.selectDynamic("val").asInstanceOf[js.Tuple2[js.Any, js.Any]]
          val caseIdx  = tup._1.asInstanceOf[Int]
          val valueOpt = if (js.isUndefined(tup._2) || tup._2 == null) None else Some(tup._2.asInstanceOf[Int])
          VariantValue(caseIdx, valueOpt)
        case "enum-value"  => EnumValue(raw.selectDynamic("val").asInstanceOf[Int])
        case "flags-value" =>
          val arr = raw.selectDynamic("val").asInstanceOf[js.Array[Boolean]]
          FlagsValue(arr.toList)
        case "tuple-value" =>
          val arr = raw.selectDynamic("val").asInstanceOf[js.Array[Int]]
          TupleValue(arr.toList)
        case "list-value" =>
          val arr = raw.selectDynamic("val").asInstanceOf[js.Array[Int]]
          ListValue(arr.toList)
        case "option-value" =>
          val v = raw.selectDynamic("val")
          if (js.isUndefined(v) || v == null) OptionValue(None)
          else OptionValue(Some(v.asInstanceOf[Int]))
        case "result-value" =>
          val v     = raw.selectDynamic("val").asInstanceOf[js.Dynamic]
          val okTag = v.tag.asInstanceOf[String]
          if (okTag == "ok") {
            val okVal = v.selectDynamic("val")
            ResultValue(
              ok = if (js.isUndefined(okVal) || okVal == null) None else Some(okVal.asInstanceOf[Int]),
              err = None
            )
          } else {
            val errVal = v.selectDynamic("val")
            ResultValue(
              ok = None,
              err = if (js.isUndefined(errVal) || errVal == null) None else Some(errVal.asInstanceOf[Int])
            )
          }
        case "prim-u8"      => PrimU8(raw.selectDynamic("val").asInstanceOf[Short])
        case "prim-u16"     => PrimU16(raw.selectDynamic("val").asInstanceOf[Int])
        case "prim-u32"     => PrimU32(raw.selectDynamic("val").asInstanceOf[Double].toLong)
        case "prim-u64"     => PrimU64(BigInt(raw.selectDynamic("val").toString))
        case "prim-s8"      => PrimS8(raw.selectDynamic("val").asInstanceOf[Byte])
        case "prim-s16"     => PrimS16(raw.selectDynamic("val").asInstanceOf[Short])
        case "prim-s32"     => PrimS32(raw.selectDynamic("val").asInstanceOf[Int])
        case "prim-s64"     => PrimS64(raw.selectDynamic("val").asInstanceOf[Double].toLong)
        case "prim-float32" => PrimFloat32(raw.selectDynamic("val").asInstanceOf[Float])
        case "prim-float64" => PrimFloat64(raw.selectDynamic("val").asInstanceOf[Double])
        case "prim-char"    =>
          val v = raw.selectDynamic("val")
          PrimChar(v.toString.charAt(0))
        case "prim-bool"   => PrimBool(raw.selectDynamic("val").asInstanceOf[Boolean])
        case "prim-string" => PrimString(raw.selectDynamic("val").asInstanceOf[String])
        case "handle"      =>
          val tup = raw.selectDynamic("val").asInstanceOf[js.Tuple2[js.Dynamic, js.Any]]
          Handle(tup._1.value.asInstanceOf[String], BigInt(tup._2.toString))
        case other => throw new IllegalArgumentException(s"Unknown WitNode tag: $other")
      }
    }

    def toDynamic(node: WitNode): js.Dynamic = node match {
      case RecordValue(fields) => js.Dynamic.literal(tag = "record-value", `val` = js.Array(fields: _*))
      case VariantValue(ci, v) =>
        val optIdx: js.Any = v.map(_.asInstanceOf[js.Any]).getOrElse(js.undefined)
        js.Dynamic.literal(tag = "variant-value", `val` = js.Tuple2(ci, optIdx))
      case EnumValue(ci)     => js.Dynamic.literal(tag = "enum-value", `val` = ci)
      case FlagsValue(flags) => js.Dynamic.literal(tag = "flags-value", `val` = js.Array(flags: _*))
      case TupleValue(elems) => js.Dynamic.literal(tag = "tuple-value", `val` = js.Array(elems: _*))
      case ListValue(elems)  => js.Dynamic.literal(tag = "list-value", `val` = js.Array(elems: _*))
      case OptionValue(v)    =>
        val optIdx: js.Any = v.map(_.asInstanceOf[js.Any]).getOrElse(js.undefined)
        js.Dynamic.literal(tag = "option-value", `val` = optIdx)
      case ResultValue(ok, err) =>
        val inner = if (ok.isDefined || err.isEmpty) {
          val okVal: js.Any = ok.map(_.asInstanceOf[js.Any]).getOrElse(js.undefined)
          js.Dynamic.literal(tag = "ok", `val` = okVal)
        } else {
          val errVal: js.Any = err.map(_.asInstanceOf[js.Any]).getOrElse(js.undefined)
          js.Dynamic.literal(tag = "err", `val` = errVal)
        }
        js.Dynamic.literal(tag = "result-value", `val` = inner)
      case PrimU8(v)        => js.Dynamic.literal(tag = "prim-u8", `val` = v)
      case PrimU16(v)       => js.Dynamic.literal(tag = "prim-u16", `val` = v)
      case PrimU32(v)       => js.Dynamic.literal(tag = "prim-u32", `val` = v.toDouble)
      case PrimU64(v)       => js.Dynamic.literal(tag = "prim-u64", `val` = js.BigInt(v.toString))
      case PrimS8(v)        => js.Dynamic.literal(tag = "prim-s8", `val` = v)
      case PrimS16(v)       => js.Dynamic.literal(tag = "prim-s16", `val` = v)
      case PrimS32(v)       => js.Dynamic.literal(tag = "prim-s32", `val` = v)
      case PrimS64(v)       => js.Dynamic.literal(tag = "prim-s64", `val` = v.toDouble)
      case PrimFloat32(v)   => js.Dynamic.literal(tag = "prim-float32", `val` = v)
      case PrimFloat64(v)   => js.Dynamic.literal(tag = "prim-float64", `val` = v)
      case PrimChar(v)      => js.Dynamic.literal(tag = "prim-char", `val` = v.toString)
      case PrimBool(v)      => js.Dynamic.literal(tag = "prim-bool", `val` = v)
      case PrimString(v)    => js.Dynamic.literal(tag = "prim-string", `val` = v)
      case Handle(uri, rid) =>
        js.Dynamic.literal(tag = "handle", `val` = js.Tuple2(js.Dynamic.literal(value = uri), js.BigInt(rid.toString)))
    }
  }

  // --- WIT: wit-value record ---

  final case class WitValue(nodes: List[WitNode])

  object WitValue {
    def fromDynamic(raw: js.Dynamic): WitValue = {
      val arr = raw.nodes.asInstanceOf[js.Array[js.Dynamic]]
      WitValue(arr.toList.map(WitNode.fromDynamic))
    }

    def toDynamic(wv: WitValue): js.Dynamic = {
      val arr = js.Array[js.Dynamic]()
      wv.nodes.foreach(n => arr.push(WitNode.toDynamic(n)))
      js.Dynamic.literal(nodes = arr)
    }
  }

  // --- WIT: resource-mode enum ---

  sealed trait ResourceMode extends Product with Serializable
  object ResourceMode {
    case object Owned    extends ResourceMode
    case object Borrowed extends ResourceMode

    def fromString(s: String): ResourceMode = s match {
      case "owned"    => Owned
      case "borrowed" => Borrowed
      case _          => Owned
    }
  }

  // --- WIT: wit-type-node variant (21 cases) ---

  sealed trait WitTypeNode extends Product with Serializable
  object WitTypeNode {
    final case class RecordType(fields: List[(String, NodeIndex)])             extends WitTypeNode
    final case class VariantType(cases: List[(String, Option[NodeIndex])])     extends WitTypeNode
    final case class EnumType(cases: List[String])                             extends WitTypeNode
    final case class FlagsType(flags: List[String])                            extends WitTypeNode
    final case class TupleType(elements: List[NodeIndex])                      extends WitTypeNode
    final case class ListType(element: NodeIndex)                              extends WitTypeNode
    final case class OptionType(element: NodeIndex)                            extends WitTypeNode
    final case class ResultType(ok: Option[NodeIndex], err: Option[NodeIndex]) extends WitTypeNode
    case object PrimU8Type                                                     extends WitTypeNode
    case object PrimU16Type                                                    extends WitTypeNode
    case object PrimU32Type                                                    extends WitTypeNode
    case object PrimU64Type                                                    extends WitTypeNode
    case object PrimS8Type                                                     extends WitTypeNode
    case object PrimS16Type                                                    extends WitTypeNode
    case object PrimS32Type                                                    extends WitTypeNode
    case object PrimS64Type                                                    extends WitTypeNode
    case object PrimF32Type                                                    extends WitTypeNode
    case object PrimF64Type                                                    extends WitTypeNode
    case object PrimCharType                                                   extends WitTypeNode
    case object PrimBoolType                                                   extends WitTypeNode
    case object PrimStringType                                                 extends WitTypeNode
    final case class HandleType(resourceId: BigInt, mode: ResourceMode)        extends WitTypeNode

    def fromDynamic(raw: js.Dynamic): WitTypeNode = {
      val tag = raw.tag.asInstanceOf[String]
      tag match {
        case "record-type" =>
          val arr = raw.selectDynamic("val").asInstanceOf[js.Array[js.Tuple2[String, Int]]]
          RecordType(arr.toList.map(t => (t._1, t._2)))
        case "variant-type" =>
          val arr = raw.selectDynamic("val").asInstanceOf[js.Array[js.Tuple2[String, js.Any]]]
          VariantType(arr.toList.map { t =>
            val opt = if (js.isUndefined(t._2) || t._2 == null) None else Some(t._2.asInstanceOf[Int])
            (t._1, opt)
          })
        case "enum-type" =>
          val arr = raw.selectDynamic("val").asInstanceOf[js.Array[String]]
          EnumType(arr.toList)
        case "flags-type" =>
          val arr = raw.selectDynamic("val").asInstanceOf[js.Array[String]]
          FlagsType(arr.toList)
        case "tuple-type" =>
          val arr = raw.selectDynamic("val").asInstanceOf[js.Array[Int]]
          TupleType(arr.toList)
        case "list-type"   => ListType(raw.selectDynamic("val").asInstanceOf[Int])
        case "option-type" => OptionType(raw.selectDynamic("val").asInstanceOf[Int])
        case "result-type" =>
          val tup = raw.selectDynamic("val").asInstanceOf[js.Tuple2[js.Any, js.Any]]
          val ok  = if (js.isUndefined(tup._1) || tup._1 == null) None else Some(tup._1.asInstanceOf[Int])
          val err = if (js.isUndefined(tup._2) || tup._2 == null) None else Some(tup._2.asInstanceOf[Int])
          ResultType(ok, err)
        case "prim-u8-type"     => PrimU8Type
        case "prim-u16-type"    => PrimU16Type
        case "prim-u32-type"    => PrimU32Type
        case "prim-u64-type"    => PrimU64Type
        case "prim-s8-type"     => PrimS8Type
        case "prim-s16-type"    => PrimS16Type
        case "prim-s32-type"    => PrimS32Type
        case "prim-s64-type"    => PrimS64Type
        case "prim-f32-type"    => PrimF32Type
        case "prim-f64-type"    => PrimF64Type
        case "prim-char-type"   => PrimCharType
        case "prim-bool-type"   => PrimBoolType
        case "prim-string-type" => PrimStringType
        case "handle-type"      =>
          val tup  = raw.selectDynamic("val").asInstanceOf[js.Tuple2[js.Any, js.Dynamic]]
          val rid  = BigInt(tup._1.toString)
          val mode = ResourceMode.fromString(tup._2.tag.asInstanceOf[String])
          HandleType(rid, mode)
        case other => throw new IllegalArgumentException(s"Unknown WitTypeNode tag: $other")
      }
    }

    def toDynamic(node: WitTypeNode): js.Dynamic = node match {
      case RecordType(fields) =>
        val arr = js.Array[js.Any]()
        fields.foreach { case (name, idx) => arr.push(js.Tuple2(name, idx)) }
        js.Dynamic.literal(tag = "record-type", `val` = arr)
      case VariantType(cases) =>
        val arr = js.Array[js.Any]()
        cases.foreach { case (name, opt) =>
          val v: js.Any = opt.map(_.asInstanceOf[js.Any]).getOrElse(js.undefined)
          arr.push(js.Tuple2(name, v))
        }
        js.Dynamic.literal(tag = "variant-type", `val` = arr)
      case EnumType(cases)     => js.Dynamic.literal(tag = "enum-type", `val` = js.Array(cases: _*))
      case FlagsType(flags)    => js.Dynamic.literal(tag = "flags-type", `val` = js.Array(flags: _*))
      case TupleType(elems)    => js.Dynamic.literal(tag = "tuple-type", `val` = js.Array(elems: _*))
      case ListType(elem)      => js.Dynamic.literal(tag = "list-type", `val` = elem)
      case OptionType(elem)    => js.Dynamic.literal(tag = "option-type", `val` = elem)
      case ResultType(ok, err) =>
        val okVal: js.Any  = ok.map(_.asInstanceOf[js.Any]).getOrElse(js.undefined)
        val errVal: js.Any = err.map(_.asInstanceOf[js.Any]).getOrElse(js.undefined)
        js.Dynamic.literal(tag = "result-type", `val` = js.Tuple2(okVal, errVal))
      case PrimU8Type            => js.Dynamic.literal(tag = "prim-u8-type")
      case PrimU16Type           => js.Dynamic.literal(tag = "prim-u16-type")
      case PrimU32Type           => js.Dynamic.literal(tag = "prim-u32-type")
      case PrimU64Type           => js.Dynamic.literal(tag = "prim-u64-type")
      case PrimS8Type            => js.Dynamic.literal(tag = "prim-s8-type")
      case PrimS16Type           => js.Dynamic.literal(tag = "prim-s16-type")
      case PrimS32Type           => js.Dynamic.literal(tag = "prim-s32-type")
      case PrimS64Type           => js.Dynamic.literal(tag = "prim-s64-type")
      case PrimF32Type           => js.Dynamic.literal(tag = "prim-f32-type")
      case PrimF64Type           => js.Dynamic.literal(tag = "prim-f64-type")
      case PrimCharType          => js.Dynamic.literal(tag = "prim-char-type")
      case PrimBoolType          => js.Dynamic.literal(tag = "prim-bool-type")
      case PrimStringType        => js.Dynamic.literal(tag = "prim-string-type")
      case HandleType(rid, mode) =>
        val modeTag = mode match {
          case ResourceMode.Owned    => "owned"
          case ResourceMode.Borrowed => "borrowed"
        }
        js.Dynamic.literal(
          tag = "handle-type",
          `val` = js.Tuple2(js.BigInt(rid.toString), js.Dynamic.literal(tag = modeTag))
        )
    }
  }

  // --- WIT: named-wit-type-node record ---

  final case class NamedWitTypeNode(
    name: Option[String],
    owner: Option[String],
    typeNode: WitTypeNode
  )

  object NamedWitTypeNode {
    def fromDynamic(raw: js.Dynamic): NamedWitTypeNode = {
      val name  = { val n = raw.name; if (js.isUndefined(n) || n == null) None else Some(n.asInstanceOf[String]) }
      val owner = { val o = raw.owner; if (js.isUndefined(o) || o == null) None else Some(o.asInstanceOf[String]) }
      val tn    = WitTypeNode.fromDynamic(raw.selectDynamic("type").asInstanceOf[js.Dynamic])
      NamedWitTypeNode(name, owner, tn)
    }

    def toDynamic(n: NamedWitTypeNode): js.Dynamic = {
      val nameVal: js.Any  = n.name.map(_.asInstanceOf[js.Any]).getOrElse(js.undefined)
      val ownerVal: js.Any = n.owner.map(_.asInstanceOf[js.Any]).getOrElse(js.undefined)
      js.Dynamic.literal(name = nameVal, owner = ownerVal, `type` = WitTypeNode.toDynamic(n.typeNode))
    }
  }

  // --- WIT: wit-type record ---

  final case class WitType(nodes: List[NamedWitTypeNode])

  object WitType {
    def fromDynamic(raw: js.Dynamic): WitType = {
      val arr = raw.nodes.asInstanceOf[js.Array[js.Dynamic]]
      WitType(arr.toList.map(NamedWitTypeNode.fromDynamic))
    }

    def toDynamic(wt: WitType): js.Dynamic = {
      val arr = js.Array[js.Dynamic]()
      wt.nodes.foreach(n => arr.push(NamedWitTypeNode.toDynamic(n)))
      js.Dynamic.literal(nodes = arr)
    }
  }

  // --- WIT: value-and-type record ---

  final case class ValueAndType(value: WitValue, typ: WitType)

  object ValueAndType {
    def fromDynamic(raw: js.Dynamic): ValueAndType = {
      val v = WitValue.fromDynamic(raw.value.asInstanceOf[js.Dynamic])
      val t = WitType.fromDynamic(raw.typ.asInstanceOf[js.Dynamic])
      ValueAndType(v, t)
    }

    def toDynamic(vat: ValueAndType): js.Dynamic = {
      val v = WitValue.toDynamic(vat.value)
      val t = WitType.toDynamic(vat.typ)
      js.Dynamic.literal(value = v, typ = t)
    }
  }
}
