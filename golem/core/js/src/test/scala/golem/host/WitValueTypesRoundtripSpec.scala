package golem.host

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

class WitValueTypesRoundtripSpec extends AnyFunSuite with Matchers {
  import WitValueTypes._

  private def roundtripNode(node: WitNode, expectedTag: String): Unit = {
    val dyn = WitNode.toDynamic(node)
    dyn.tag.asInstanceOf[String] shouldBe expectedTag
    val parsed = WitNode.fromDynamic(dyn)
    (node, parsed) match {
      case (WitNode.Handle(u1, r1), WitNode.Handle(u2, r2)) =>
        u1 shouldBe u2; r1 shouldBe r2
      case _ => parsed shouldBe node
    }
  }

  test("RecordValue round-trip") {
    roundtripNode(WitNode.RecordValue(List(0, 1, 2)), "record-value")
  }

  test("VariantValue with Some round-trip") {
    roundtripNode(WitNode.VariantValue(0, Some(1)), "variant-value")
  }

  test("VariantValue with None round-trip") {
    roundtripNode(WitNode.VariantValue(1, None), "variant-value")
  }

  test("EnumValue round-trip") {
    roundtripNode(WitNode.EnumValue(3), "enum-value")
  }

  test("FlagsValue round-trip") {
    roundtripNode(WitNode.FlagsValue(List(true, false, true)), "flags-value")
  }

  test("TupleValue round-trip") {
    roundtripNode(WitNode.TupleValue(List(0, 1)), "tuple-value")
  }

  test("ListValue round-trip") {
    roundtripNode(WitNode.ListValue(List(0, 1, 2)), "list-value")
  }

  test("OptionValue Some round-trip") {
    roundtripNode(WitNode.OptionValue(Some(0)), "option-value")
  }

  test("OptionValue None round-trip") {
    roundtripNode(WitNode.OptionValue(None), "option-value")
  }

  test("ResultValue ok round-trip") {
    roundtripNode(WitNode.ResultValue(Some(0), None), "result-value")
  }

  test("ResultValue err round-trip") {
    roundtripNode(WitNode.ResultValue(None, Some(1)), "result-value")
  }

  test("ResultValue both-none round-trip") {
    roundtripNode(WitNode.ResultValue(None, None), "result-value")
  }

  test("PrimU8 round-trip") {
    roundtripNode(WitNode.PrimU8(42), "prim-u8")
  }

  test("PrimU16 round-trip") {
    roundtripNode(WitNode.PrimU16(1000), "prim-u16")
  }

  test("PrimU32 round-trip") {
    val node = WitNode.PrimU32(100000L)
    val dyn  = WitNode.toDynamic(node)
    dyn.tag.asInstanceOf[String] shouldBe "prim-u32"
    val parsed = WitNode.fromDynamic(dyn)
    parsed shouldBe a[WitNode.PrimU32]
    parsed.asInstanceOf[WitNode.PrimU32].value shouldBe 100000L
  }

  test("PrimU64 round-trip") {
    val node = WitNode.PrimU64(BigInt("18446744073709551615"))
    val dyn  = WitNode.toDynamic(node)
    dyn.tag.asInstanceOf[String] shouldBe "prim-u64"
    val parsed = WitNode.fromDynamic(dyn)
    parsed shouldBe a[WitNode.PrimU64]
    parsed.asInstanceOf[WitNode.PrimU64].value shouldBe BigInt("18446744073709551615")
  }

  test("PrimS8 round-trip") {
    roundtripNode(WitNode.PrimS8((-1).toByte), "prim-s8")
  }

  test("PrimS16 round-trip") {
    roundtripNode(WitNode.PrimS16((-100).toShort), "prim-s16")
  }

  test("PrimS32 round-trip") {
    roundtripNode(WitNode.PrimS32(-1000), "prim-s32")
  }

  test("PrimS64 round-trip") {
    val node = WitNode.PrimS64(-100000L)
    val dyn  = WitNode.toDynamic(node)
    dyn.tag.asInstanceOf[String] shouldBe "prim-s64"
    val parsed = WitNode.fromDynamic(dyn)
    parsed shouldBe a[WitNode.PrimS64]
    parsed.asInstanceOf[WitNode.PrimS64].value shouldBe -100000L
  }

  test("PrimFloat32 round-trip") {
    val node   = WitNode.PrimFloat32(3.14f)
    val dyn    = WitNode.toDynamic(node)
    val parsed = WitNode.fromDynamic(dyn)
    parsed shouldBe a[WitNode.PrimFloat32]
    parsed.asInstanceOf[WitNode.PrimFloat32].value shouldBe 3.14f +- 0.001f
  }

  test("PrimFloat64 round-trip") {
    roundtripNode(WitNode.PrimFloat64(2.718281828), "prim-float64")
  }

  test("PrimChar round-trip") {
    roundtripNode(WitNode.PrimChar('A'), "prim-char")
  }

  test("PrimBool round-trip") {
    roundtripNode(WitNode.PrimBool(true), "prim-bool")
    roundtripNode(WitNode.PrimBool(false), "prim-bool")
  }

  test("PrimString round-trip") {
    roundtripNode(WitNode.PrimString("hello"), "prim-string")
  }

  test("Handle round-trip") {
    val node = WitNode.Handle("urn:example:resource", BigInt(42))
    val dyn  = WitNode.toDynamic(node)
    dyn.tag.asInstanceOf[String] shouldBe "handle"
    val parsed = WitNode.fromDynamic(dyn)
    parsed shouldBe a[WitNode.Handle]
    val h = parsed.asInstanceOf[WitNode.Handle]
    h.uri shouldBe "urn:example:resource"
    h.resourceId shouldBe BigInt(42)
  }

  test("unknown WitNode tag throws") {
    val raw = js.Dynamic.literal(tag = "unknown-tag", `val` = 0)
    an[IllegalArgumentException] should be thrownBy WitNode.fromDynamic(raw)
  }

  // --- WitTypeNode round-trips ---

  private def roundtripTypeNode(node: WitTypeNode, expectedTag: String): Unit = {
    val dyn = WitTypeNode.toDynamic(node)
    dyn.tag.asInstanceOf[String] shouldBe expectedTag
    val parsed = WitTypeNode.fromDynamic(dyn)
    (node, parsed) match {
      case (WitTypeNode.HandleType(r1, m1), WitTypeNode.HandleType(r2, m2)) =>
        r1 shouldBe r2; m1 shouldBe m2
      case _ => parsed shouldBe node
    }
  }

  test("RecordType round-trip") {
    roundtripTypeNode(WitTypeNode.RecordType(List(("name", 0), ("age", 1))), "record-type")
  }

  test("VariantType round-trip") {
    roundtripTypeNode(WitTypeNode.VariantType(List(("ok", Some(0)), ("none", None))), "variant-type")
  }

  test("EnumType round-trip") {
    roundtripTypeNode(WitTypeNode.EnumType(List("red", "green", "blue")), "enum-type")
  }

  test("FlagsType round-trip") {
    roundtripTypeNode(WitTypeNode.FlagsType(List("read", "write")), "flags-type")
  }

  test("TupleType round-trip") {
    roundtripTypeNode(WitTypeNode.TupleType(List(0, 1)), "tuple-type")
  }

  test("ListType round-trip") {
    roundtripTypeNode(WitTypeNode.ListType(0), "list-type")
  }

  test("OptionType round-trip") {
    roundtripTypeNode(WitTypeNode.OptionType(0), "option-type")
  }

  test("ResultType round-trip") {
    roundtripTypeNode(WitTypeNode.ResultType(Some(0), Some(1)), "result-type")
    roundtripTypeNode(WitTypeNode.ResultType(None, None), "result-type")
  }

  test("all primitive type nodes round-trip") {
    val prims = List(
      (WitTypeNode.PrimU8Type, "prim-u8-type"),
      (WitTypeNode.PrimU16Type, "prim-u16-type"),
      (WitTypeNode.PrimU32Type, "prim-u32-type"),
      (WitTypeNode.PrimU64Type, "prim-u64-type"),
      (WitTypeNode.PrimS8Type, "prim-s8-type"),
      (WitTypeNode.PrimS16Type, "prim-s16-type"),
      (WitTypeNode.PrimS32Type, "prim-s32-type"),
      (WitTypeNode.PrimS64Type, "prim-s64-type"),
      (WitTypeNode.PrimF32Type, "prim-f32-type"),
      (WitTypeNode.PrimF64Type, "prim-f64-type"),
      (WitTypeNode.PrimCharType, "prim-char-type"),
      (WitTypeNode.PrimBoolType, "prim-bool-type"),
      (WitTypeNode.PrimStringType, "prim-string-type")
    )
    prims.foreach { case (node, tag) => roundtripTypeNode(node, tag) }
  }

  test("HandleType round-trip") {
    roundtripTypeNode(WitTypeNode.HandleType(BigInt(1), ResourceMode.Owned), "handle-type")
    roundtripTypeNode(WitTypeNode.HandleType(BigInt(2), ResourceMode.Borrowed), "handle-type")
  }

  test("unknown WitTypeNode tag throws") {
    val raw = js.Dynamic.literal(tag = "unknown-type-tag", `val` = 0)
    an[IllegalArgumentException] should be thrownBy WitTypeNode.fromDynamic(raw)
  }

  // --- ResourceMode ---

  test("ResourceMode.fromString") {
    ResourceMode.fromString("owned") shouldBe ResourceMode.Owned
    ResourceMode.fromString("borrowed") shouldBe ResourceMode.Borrowed
    ResourceMode.fromString("other") shouldBe ResourceMode.Owned
  }

  // --- NamedWitTypeNode round-trip ---

  test("NamedWitTypeNode with name and owner round-trip") {
    val node   = NamedWitTypeNode(Some("field"), Some("owner"), WitTypeNode.PrimStringType)
    val dyn    = NamedWitTypeNode.toDynamic(node)
    val parsed = NamedWitTypeNode.fromDynamic(dyn)
    parsed.name shouldBe Some("field")
    parsed.owner shouldBe Some("owner")
    parsed.typeNode shouldBe WitTypeNode.PrimStringType
  }

  test("NamedWitTypeNode with None name and owner round-trip") {
    val node   = NamedWitTypeNode(None, None, WitTypeNode.PrimS32Type)
    val dyn    = NamedWitTypeNode.toDynamic(node)
    val parsed = NamedWitTypeNode.fromDynamic(dyn)
    parsed.name shouldBe None
    parsed.owner shouldBe None
    parsed.typeNode shouldBe WitTypeNode.PrimS32Type
  }

  // --- WitValue round-trip ---

  test("WitValue round-trip with multiple nodes") {
    val value = WitValue(
      List(
        WitNode.PrimString("hello"),
        WitNode.PrimS32(42),
        WitNode.PrimBool(true)
      )
    )
    val dyn    = WitValue.toDynamic(value)
    val parsed = WitValue.fromDynamic(dyn)
    parsed.nodes.size shouldBe 3
    parsed.nodes(0) shouldBe WitNode.PrimString("hello")
    parsed.nodes(1) shouldBe WitNode.PrimS32(42)
    parsed.nodes(2) shouldBe WitNode.PrimBool(true)
  }

  // --- WitType round-trip ---

  test("WitType round-trip with multiple nodes") {
    val wt = WitType(
      List(
        NamedWitTypeNode(Some("name"), None, WitTypeNode.PrimStringType),
        NamedWitTypeNode(None, None, WitTypeNode.PrimS32Type)
      )
    )
    val dyn    = WitType.toDynamic(wt)
    val parsed = WitType.fromDynamic(dyn)
    parsed.nodes.size shouldBe 2
    parsed.nodes(0).name shouldBe Some("name")
    parsed.nodes(0).typeNode shouldBe WitTypeNode.PrimStringType
    parsed.nodes(1).name shouldBe None
    parsed.nodes(1).typeNode shouldBe WitTypeNode.PrimS32Type
  }

  // --- ValueAndType round-trip ---

  test("ValueAndType round-trip") {
    val vat = ValueAndType(
      WitValue(List(WitNode.PrimString("test"), WitNode.PrimS32(99))),
      WitType(
        List(
          NamedWitTypeNode(Some("s"), None, WitTypeNode.PrimStringType),
          NamedWitTypeNode(Some("n"), None, WitTypeNode.PrimS32Type)
        )
      )
    )
    val dyn    = ValueAndType.toDynamic(vat)
    val parsed = ValueAndType.fromDynamic(dyn)
    parsed.value.nodes.size shouldBe 2
    parsed.typ.nodes.size shouldBe 2
    parsed.value.nodes(0) shouldBe WitNode.PrimString("test")
    parsed.typ.nodes(0).name shouldBe Some("s")
  }
}
