package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for deeply nested structure conversions.
 *
 * Covers:
 *   - 3+ levels of nesting
 *   - Nested products within products
 *   - Nested collections within products
 *   - Mixed nesting with type coercion at various levels
 */
object DeepNestingSpec extends ZIOSpecDefault {

  // === Level 1 (innermost) ===
  case class AddressA(street: String, city: String, zip: Int)
  case class AddressB(street: String, city: String, zip: Long)

  // === Level 2 ===
  case class ContactA(name: String, address: AddressA)
  case class ContactB(name: String, address: AddressB)

  // === Level 3 ===
  case class EmployeeA(id: Int, contact: ContactA)
  case class EmployeeB(id: Long, contact: ContactB)

  // === Level 4 ===
  case class DepartmentA(name: String, manager: EmployeeA)
  case class DepartmentB(name: String, manager: EmployeeB)

  // === Level 5 (outermost) ===
  case class CompanyA(name: String, hq: DepartmentA)
  case class CompanyB(name: String, hq: DepartmentB)

  // === Deep nesting with collections ===
  case class InnerItem(value: Int)
  case class InnerItemAlt(value: Long)

  case class MiddleLayer(items: List[InnerItem])
  case class MiddleLayerAlt(items: List[InnerItemAlt])

  case class OuterWrapper(middle: MiddleLayer, tag: String)
  case class OuterWrapperAlt(middle: MiddleLayerAlt, tag: String)

  // === Deep nesting with Options ===
  case class DeepOpt1(value: Int)
  case class DeepOpt1Alt(value: Long)

  case class DeepOpt2(inner: Option[DeepOpt1])
  case class DeepOpt2Alt(inner: Option[DeepOpt1Alt])

  case class DeepOpt3(middle: DeepOpt2)
  case class DeepOpt3Alt(middle: DeepOpt2Alt)

  // === Deep nesting with Either ===
  case class Left1(value: String)
  case class Left1Alt(value: String)
  case class Right1(value: Int)
  case class Right1Alt(value: Long)

  case class EitherWrapper(result: Either[Left1, Right1])
  case class EitherWrapperAlt(result: Either[Left1Alt, Right1Alt])

  def spec: Spec[TestEnvironment, Any] = suite("DeepNestingSpec")(
    suite("3 Levels Deep")(
      test("converts 3-level nested structure with coercion at innermost") {
        implicit val addressInto: Into[AddressA, AddressB] = Into.derived[AddressA, AddressB]
        implicit val contactInto: Into[ContactA, ContactB] = Into.derived[ContactA, ContactB]

        val source = EmployeeA(1, ContactA("Alice", AddressA("123 Main", "NYC", 10001)))
        val result = Into.derived[EmployeeA, EmployeeB].into(source)

        assert(result)(
          isRight(
            equalTo(
              EmployeeB(1L, ContactB("Alice", AddressB("123 Main", "NYC", 10001L)))
            )
          )
        )
      }
    ),
    suite("4 Levels Deep")(
      test("converts 4-level nested structure") {
        implicit val addressInto: Into[AddressA, AddressB]    = Into.derived[AddressA, AddressB]
        implicit val contactInto: Into[ContactA, ContactB]    = Into.derived[ContactA, ContactB]
        implicit val employeeInto: Into[EmployeeA, EmployeeB] = Into.derived[EmployeeA, EmployeeB]

        val source = DepartmentA("Engineering", EmployeeA(1, ContactA("Bob", AddressA("456 Tech", "SF", 94102))))
        val result = Into.derived[DepartmentA, DepartmentB].into(source)

        assert(result)(
          isRight(
            equalTo(
              DepartmentB("Engineering", EmployeeB(1L, ContactB("Bob", AddressB("456 Tech", "SF", 94102L))))
            )
          )
        )
      }
    ),
    suite("5 Levels Deep")(
      test("converts 5-level nested structure") {
        implicit val addressInto: Into[AddressA, AddressB]    = Into.derived[AddressA, AddressB]
        implicit val contactInto: Into[ContactA, ContactB]    = Into.derived[ContactA, ContactB]
        implicit val employeeInto: Into[EmployeeA, EmployeeB] = Into.derived[EmployeeA, EmployeeB]
        implicit val deptInto: Into[DepartmentA, DepartmentB] = Into.derived[DepartmentA, DepartmentB]

        val source =
          CompanyA("TechCorp", DepartmentA("HQ", EmployeeA(1, ContactA("CEO", AddressA("1 HQ", "NYC", 10001)))))
        val result = Into.derived[CompanyA, CompanyB].into(source)

        assert(result)(
          isRight(
            equalTo(
              CompanyB("TechCorp", DepartmentB("HQ", EmployeeB(1L, ContactB("CEO", AddressB("1 HQ", "NYC", 10001L)))))
            )
          )
        )
      }
    ),
    suite("Deep Nesting with Collections")(
      test("converts nested structure containing List") {
        implicit val innerInto: Into[InnerItem, InnerItemAlt]      = Into.derived[InnerItem, InnerItemAlt]
        implicit val middleInto: Into[MiddleLayer, MiddleLayerAlt] = Into.derived[MiddleLayer, MiddleLayerAlt]

        val source = OuterWrapper(MiddleLayer(List(InnerItem(1), InnerItem(2), InnerItem(3))), "test")
        val result = Into.derived[OuterWrapper, OuterWrapperAlt].into(source)

        assert(result)(
          isRight(
            equalTo(
              OuterWrapperAlt(MiddleLayerAlt(List(InnerItemAlt(1L), InnerItemAlt(2L), InnerItemAlt(3L))), "test")
            )
          )
        )
      },
      test("converts nested structure with empty List") {
        implicit val innerInto: Into[InnerItem, InnerItemAlt]      = Into.derived[InnerItem, InnerItemAlt]
        implicit val middleInto: Into[MiddleLayer, MiddleLayerAlt] = Into.derived[MiddleLayer, MiddleLayerAlt]

        val source = OuterWrapper(MiddleLayer(Nil), "empty")
        val result = Into.derived[OuterWrapper, OuterWrapperAlt].into(source)

        assert(result)(
          isRight(
            equalTo(
              OuterWrapperAlt(MiddleLayerAlt(Nil), "empty")
            )
          )
        )
      }
    ),
    suite("Deep Nesting with Options")(
      test("converts nested Options with Some values") {
        implicit val deep1Into: Into[DeepOpt1, DeepOpt1Alt] = Into.derived[DeepOpt1, DeepOpt1Alt]
        implicit val deep2Into: Into[DeepOpt2, DeepOpt2Alt] = Into.derived[DeepOpt2, DeepOpt2Alt]

        val source = DeepOpt3(DeepOpt2(Some(DeepOpt1(42))))
        val result = Into.derived[DeepOpt3, DeepOpt3Alt].into(source)

        assert(result)(
          isRight(
            equalTo(
              DeepOpt3Alt(DeepOpt2Alt(Some(DeepOpt1Alt(42L))))
            )
          )
        )
      },
      test("converts nested Options with None") {
        implicit val deep1Into: Into[DeepOpt1, DeepOpt1Alt] = Into.derived[DeepOpt1, DeepOpt1Alt]
        implicit val deep2Into: Into[DeepOpt2, DeepOpt2Alt] = Into.derived[DeepOpt2, DeepOpt2Alt]

        val source = DeepOpt3(DeepOpt2(None))
        val result = Into.derived[DeepOpt3, DeepOpt3Alt].into(source)

        assert(result)(
          isRight(
            equalTo(
              DeepOpt3Alt(DeepOpt2Alt(None))
            )
          )
        )
      }
    ),
    suite("Deep Nesting with Either")(
      test("converts nested Either - Left case") {
        implicit val leftInto: Into[Left1, Left1Alt]    = Into.derived[Left1, Left1Alt]
        implicit val rightInto: Into[Right1, Right1Alt] = Into.derived[Right1, Right1Alt]

        val source = EitherWrapper(Left(Left1("error")))
        val result = Into.derived[EitherWrapper, EitherWrapperAlt].into(source)

        assert(result)(
          isRight(
            equalTo(
              EitherWrapperAlt(Left(Left1Alt("error")))
            )
          )
        )
      },
      test("converts nested Either - Right case") {
        implicit val leftInto: Into[Left1, Left1Alt]    = Into.derived[Left1, Left1Alt]
        implicit val rightInto: Into[Right1, Right1Alt] = Into.derived[Right1, Right1Alt]

        val source = EitherWrapper(Right(Right1(100)))
        val result = Into.derived[EitherWrapper, EitherWrapperAlt].into(source)

        assert(result)(
          isRight(
            equalTo(
              EitherWrapperAlt(Right(Right1Alt(100L)))
            )
          )
        )
      }
    ),
    suite("Error Propagation in Deep Nesting")(
      test("error at innermost level propagates up") {
        case class DeepInner(value: Long)
        case class DeepInnerNarrow(value: Int)
        case class DeepMiddle(inner: DeepInner)
        case class DeepMiddleNarrow(inner: DeepInnerNarrow)
        case class DeepOuter(middle: DeepMiddle)
        case class DeepOuterNarrow(middle: DeepMiddleNarrow)

        implicit val innerInto: Into[DeepInner, DeepInnerNarrow]    = Into.derived[DeepInner, DeepInnerNarrow]
        implicit val middleInto: Into[DeepMiddle, DeepMiddleNarrow] = Into.derived[DeepMiddle, DeepMiddleNarrow]

        val source = DeepOuter(DeepMiddle(DeepInner(Long.MaxValue)))
        val result = Into.derived[DeepOuter, DeepOuterNarrow].into(source)

        assert(result)(isLeft)
      },
      test("error at middle level propagates up") {
        case class DeepInner(value: Int)
        case class DeepInnerAlt(value: Long)
        case class DeepMiddle(inner: DeepInner, extra: Long)
        case class DeepMiddleNarrow(inner: DeepInnerAlt, extra: Int)
        case class DeepOuter(middle: DeepMiddle)
        case class DeepOuterAlt(middle: DeepMiddleNarrow)

        implicit val innerInto: Into[DeepInner, DeepInnerAlt]       = Into.derived[DeepInner, DeepInnerAlt]
        implicit val middleInto: Into[DeepMiddle, DeepMiddleNarrow] = Into.derived[DeepMiddle, DeepMiddleNarrow]

        val source = DeepOuter(DeepMiddle(DeepInner(1), Long.MaxValue))
        val result = Into.derived[DeepOuter, DeepOuterAlt].into(source)

        assert(result)(isLeft)
      }
    )
  )
}
