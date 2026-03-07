package zio.blocks.typeid

import zio.blocks.chunk.Chunk
import zio.test._

// ---------------------------------------------------------------------------
// Test fixtures — defined at top level so macros can see them
// ---------------------------------------------------------------------------

case class NominalCaseClass(value: Int)

trait NominalTrait

class NominalClass(val x: Int)

case class AppliedWrapper[A](value: A)

case object NominalCaseObject

object NominalObject

// ---------------------------------------------------------------------------
// Spec
// ---------------------------------------------------------------------------

object IsNominalTypeSpec extends ZIOSpecDefault {

  def spec = suite("IsNominalType and IsNominalIntersection")(
    suite("IsNominalType — successful derivation")(
      test("derives for a concrete case class") {
        val ev = implicitly[IsNominalType[NominalCaseClass]]
        assertTrue(ev.typeId.fullName.contains("NominalCaseClass"))
      },
      test("derives for a trait") {
        val ev = implicitly[IsNominalType[NominalTrait]]
        assertTrue(ev.typeId.fullName.contains("NominalTrait"))
      },
      test("derives for a concrete class") {
        val ev = implicitly[IsNominalType[NominalClass]]
        assertTrue(ev.typeId.fullName.contains("NominalClass"))
      },
      test("derives for a case object") {
        val ev = implicitly[IsNominalType[NominalCaseObject.type]]
        assertTrue(ev.typeId.fullName.contains("NominalCaseObject"))
      },
      test("derives for a plain object") {
        val ev = implicitly[IsNominalType[NominalObject.type]]
        assertTrue(ev.typeId.fullName.contains("NominalObject"))
      },
      test("derives for an applied type (List[Int])") {
        val ev = implicitly[IsNominalType[List[Int]]]
        assertTrue(ev.typeId.fullName.contains("List"))
      },
      test("derives for an applied type (Option[String])") {
        val ev = implicitly[IsNominalType[Option[String]]]
        assertTrue(ev.typeId.fullName.contains("Option"))
      },
      test("derives for a primitive type") {
        val ev = implicitly[IsNominalType[Int]]
        assertTrue(ev.typeId.fullName.contains("Int"))
      },
      test("derives for String") {
        val ev = implicitly[IsNominalType[String]]
        assertTrue(ev.typeId.fullName.contains("String"))
      },
      test("typeIdErased is consistent with typeId.erased") {
        val ev = implicitly[IsNominalType[NominalCaseClass]]
        assertTrue(ev.typeIdErased == ev.typeId.erased)
      },
      test("apply summoner returns an instance") {
        val ev = IsNominalType[NominalCaseClass]
        assertTrue(ev.typeId.fullName.contains("NominalCaseClass"))
      },
      test("two derivations for the same type have the same fullName") {
        val ev1 = implicitly[IsNominalType[NominalCaseClass]]
        val ev2 = implicitly[IsNominalType[NominalCaseClass]]
        assertTrue(ev1.typeId.fullName == ev2.typeId.fullName)
      }
    ),
    suite("IsNominalType — compile-time failures")(
      test("does not compile for an intersection type (A with B)") {
        typeCheck("implicitly[zio.blocks.typeid.IsNominalType[NominalTrait with NominalClass]]").map(result =>
          assertTrue(result.isLeft)
        )
      },
      test("does not compile for a structural refinement") {
        typeCheck("implicitly[zio.blocks.typeid.IsNominalType[NominalTrait { def foo: Int }]]").map(result =>
          assertTrue(result.isLeft)
        )
      },
      test("error message mentions the type name") {
        typeCheck("implicitly[zio.blocks.typeid.IsNominalType[NominalTrait with NominalClass]]").map(result =>
          assertTrue(
            result.isLeft &&
              (result.left.exists(_.contains("NominalTrait")) ||
                result.left.exists(_.contains("non-nominal")) ||
                result.left.exists(_.contains("IsNominalType")))
          )
        )
      }
    ),
    suite("IsNominalIntersection — successful derivation")(
      test("derives for a single concrete type") {
        val ev = implicitly[IsNominalIntersection[NominalCaseClass]]
        assertTrue(ev.typeIdsErased.length == 1)
      },
      test("single type typeIdsErased contains the correct erased id") {
        val ev   = implicitly[IsNominalIntersection[NominalCaseClass]]
        val ntEv = implicitly[IsNominalType[NominalCaseClass]]
        assertTrue(ev.typeIdsErased.contains(ntEv.typeIdErased))
      },
      test("derives for a two-member intersection") {
        val ev = implicitly[IsNominalIntersection[NominalTrait with NominalClass]]
        assertTrue(ev.typeIdsErased.length == 2)
      },
      test("two-member intersection contains both erased ids") {
        val ev  = implicitly[IsNominalIntersection[NominalTrait with NominalClass]]
        val evT = implicitly[IsNominalType[NominalTrait]]
        val evC = implicitly[IsNominalType[NominalClass]]
        val ids = ev.typeIdsErased
        assertTrue(ids.contains(evT.typeIdErased) && ids.contains(evC.typeIdErased))
      },
      test("derives for a three-member intersection") {
        val ev = implicitly[IsNominalIntersection[NominalTrait with NominalClass with NominalCaseClass]]
        assertTrue(ev.typeIdsErased.length == 3)
      },
      test("apply summoner returns an instance") {
        val ev = IsNominalIntersection[NominalCaseClass]
        assertTrue(ev.typeIdsErased.length == 1)
      },
      test("derives for Chunk[TypeId.Erased] return type") {
        val ev = implicitly[IsNominalIntersection[NominalCaseClass]]
        assertTrue(ev.typeIdsErased.isInstanceOf[Chunk[_]])
      }
    ),
    suite("IsNominalType — DSL constraint pattern (issue #1172)")(
      // This suite proves the use-case from the Github issue: a DSL method that
      // requires its type parameter A to be a concrete nominal type, so the
      // compiler rejects calls where A is still abstract.
      //
      // Pattern:
      //   def contains[A: IsNominalType](a: A)(implicit ev: Allows[To, ...]): Expr
      //
      // Without IsNominalType[A], nothing would prevent calling contains[T] inside
      // a generic method where T is unresolved — yielding a useless constraint.
      // IsNominalType[A] closes that gap at compile time.
      test("a method constrained by IsNominalType accepts a concrete type at call site") {
        def requiresNominal[A: IsNominalType](a: A): String =
          s"${a.getClass.getSimpleName}:${IsNominalType[A].typeId.fullName}"
        assertTrue(
          requiresNominal(42).contains("Int"),
          requiresNominal("hello").contains("String"),
          requiresNominal(List(1, 2, 3)).contains("List")
        )
      },
      test("a constrained method propagates IsNominalType requirement to its caller") {
        // Proves the constraint is not a no-op: calling requiresNominal from a
        // caller that also declares [A: IsNominalType] compiles and works correctly.
        // Conversely, an unconstrained generic caller cannot forward a bare type
        // parameter B — this is enforced by scalac at the concrete call site
        // (verified in IsNominalTypeNegativeSpec in scala-3/ via typeChecks).
        def requiresNominal[A: IsNominalType](a: A): String =
          s"${a.getClass.getSimpleName}:${IsNominalType[A].typeId.fullName}"
        def alsoConstrained[A: IsNominalType](a: A): String =
          requiresNominal(a) // propagates — compiles because A: IsNominalType
        assertTrue(
          alsoConstrained(42).contains("Int"),
          alsoConstrained("hello").contains("String")
        )
      }
    ),
    suite("IsNominalIntersection — compile-time failures")(
      test("does not compile when a member has a structural refinement") {
        typeCheck(
          "implicitly[zio.blocks.typeid.IsNominalIntersection[NominalTrait with NominalClass { def foo: Int }]]"
        ).map(result => assertTrue(result.isLeft))
      },
      test("error message mentions the member type") {
        typeCheck(
          "implicitly[zio.blocks.typeid.IsNominalIntersection[NominalTrait with NominalClass { def foo: Int }]]"
        ).map(result =>
          assertTrue(
            result.isLeft &&
              (result.left.exists(_.contains("not a nominal type")) ||
                result.left.exists(_.contains("IsNominalIntersection")) ||
                result.left.exists(_.contains("NominalClass")))
          )
        )
      }
    )
  )
}
