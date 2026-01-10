package zio.blocks.schema.into.coproduct

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for case matching by constructor signature in coproduct conversions.
 *
 * Focuses on matching when names differ but constructor signatures match.
 *
 * Covers:
 *   - Signature matching with different case names
 *   - Signature matching with type coercion
 *   - Signature matching with field reordering
 */
object SignatureMatchingSpec extends ZIOSpecDefault {

  // === Test Data Types ===

  // Different names, same signatures
  sealed trait EventV1
  object EventV1 {
    case class Created(id: String, ts: Long)               extends EventV1
    case class Deleted(id: String)                         extends EventV1
    case class Updated(id: String, ts: Long, data: String) extends EventV1
  }

  sealed trait EventV2
  object EventV2 {
    case class Spawned(id: String, ts: Long)                extends EventV2
    case class Removed(id: String)                          extends EventV2
    case class Modified(id: String, ts: Long, data: String) extends EventV2
  }

  // Signature matching with type coercion
  sealed trait MessageV1
  object MessageV1 {
    case class Text(content: String, priority: Int) extends MessageV1
    case class Binary(data: Array[Byte])            extends MessageV1
  }

  sealed trait MessageV2
  object MessageV2 {
    case class TextMessage(content: String, priority: Long) extends MessageV2
    case class BinaryMessage(data: Array[Byte])             extends MessageV2
  }

  // Multiple cases with unique signatures
  sealed trait ShapeV1
  object ShapeV1 {
    case class Circle(radius: Double)                    extends ShapeV1
    case class Rectangle(width: Double, height: Double)  extends ShapeV1
    case class Triangle(a: Double, b: Double, c: Double) extends ShapeV1
  }

  sealed trait ShapeV2
  object ShapeV2 {
    case class Round(radius: Double)                extends ShapeV2
    case class Box(width: Double, height: Double)   extends ShapeV2
    case class Tri(a: Double, b: Double, c: Double) extends ShapeV2
  }

  // Signature matching with nested structures
  sealed trait RequestV1
  object RequestV1 {
    case class Get(path: String, headers: Map[String, String])                extends RequestV1
    case class Post(path: String, body: String, headers: Map[String, String]) extends RequestV1
  }

  sealed trait RequestV2
  object RequestV2 {
    case class Fetch(path: String, headers: Map[String, String])                extends RequestV2
    case class Submit(path: String, body: String, headers: Map[String, String]) extends RequestV2
  }

  // For "All Cases Match by Signature" test
  sealed trait SourceADTMulti
  object SourceADTMulti {
    case class A(x: Int)            extends SourceADTMulti
    case class B(y: String)         extends SourceADTMulti
    case class C(z: Boolean)        extends SourceADTMulti
    case class D(a: Int, b: String) extends SourceADTMulti
  }

  sealed trait TargetADTMulti
  object TargetADTMulti {
    case class First(x: Long)             extends TargetADTMulti
    case class Second(y: String)          extends TargetADTMulti
    case class Third(z: Boolean)          extends TargetADTMulti
    case class Fourth(a: Long, b: String) extends TargetADTMulti
  }

  // For "Signature Uniqueness" test
  sealed trait SourceUnique
  object SourceUnique {
    case class TypeA(x: Int)     extends SourceUnique
    case class TypeB(y: String)  extends SourceUnique
    case class TypeC(z: Boolean) extends SourceUnique
  }

  sealed trait TargetUnique
  object TargetUnique {
    case class Different1(x: Int)     extends TargetUnique
    case class Different2(y: String)  extends TargetUnique
    case class Different3(z: Boolean) extends TargetUnique
  }

  // For "Error Handling" tests
  sealed trait SourceErr
  object SourceErr {
    case class Data(value: Long) extends SourceErr
  }

  sealed trait TargetErr
  object TargetErr {
    case class Info(value: Int) extends TargetErr
  }

  sealed trait SourceOk
  object SourceOk {
    case class Data(value: Long) extends SourceOk
  }

  sealed trait TargetOk
  object TargetOk {
    case class Info(value: Int) extends TargetOk
  }

  def spec: Spec[TestEnvironment, Any] = suite("SignatureMatchingSpec")(
    suite("Basic Signature Matching")(
      test("matches Created(String, Long) to Spawned(String, Long) by signature") {
        val event: EventV1 = EventV1.Created("abc", 123L)
        val result         = Into.derived[EventV1, EventV2].into(event)

        assert(result)(isRight(equalTo(EventV2.Spawned("abc", 123L): EventV2)))
      },
      test("matches Deleted(String) to Removed(String) by signature") {
        val event: EventV1 = EventV1.Deleted("xyz")
        val result         = Into.derived[EventV1, EventV2].into(event)

        assert(result)(isRight(equalTo(EventV2.Removed("xyz"): EventV2)))
      },
      test("matches Updated(String, Long, String) to Modified by signature") {
        val event: EventV1 = EventV1.Updated("id123", 456L, "new data")
        val result         = Into.derived[EventV1, EventV2].into(event)

        assert(result)(isRight(equalTo(EventV2.Modified("id123", 456L, "new data"): EventV2)))
      }
    ),
    suite("Signature Matching with Type Coercion")(
      test("matches Text to TextMessage with Int to Long coercion") {
        val msg: MessageV1 = MessageV1.Text("hello", 5)
        val result         = Into.derived[MessageV1, MessageV2].into(msg)

        assert(result)(isRight(equalTo(MessageV2.TextMessage("hello", 5L): MessageV2)))
      },
      test("matches Binary to BinaryMessage by signature") {
        val msg: MessageV1 = MessageV1.Binary(Array[Byte](1, 2, 3))
        val result         = Into.derived[MessageV1, MessageV2].into(msg)

        assert(result.map(_.isInstanceOf[MessageV2.BinaryMessage]))(isRight(isTrue))
      }
    ),
    suite("Multiple Unique Signatures")(
      test("matches Circle(Double) to Round(Double) by signature") {
        val shape: ShapeV1 = ShapeV1.Circle(5.0)
        val result         = Into.derived[ShapeV1, ShapeV2].into(shape)

        assert(result)(isRight(equalTo(ShapeV2.Round(5.0): ShapeV2)))
      },
      test("matches Rectangle(Double, Double) to Box by signature") {
        val shape: ShapeV1 = ShapeV1.Rectangle(4.0, 3.0)
        val result         = Into.derived[ShapeV1, ShapeV2].into(shape)

        assert(result)(isRight(equalTo(ShapeV2.Box(4.0, 3.0): ShapeV2)))
      },
      test("matches Triangle(Double, Double, Double) to Tri by signature") {
        val shape: ShapeV1 = ShapeV1.Triangle(3.0, 4.0, 5.0)
        val result         = Into.derived[ShapeV1, ShapeV2].into(shape)

        assert(result)(isRight(equalTo(ShapeV2.Tri(3.0, 4.0, 5.0): ShapeV2)))
      }
    ),
    suite("Signature Matching with Complex Types")(
      test("matches Get to Fetch with Map[String, String] parameter") {
        val req: RequestV1 = RequestV1.Get("/api", Map("Auth" -> "Bearer token"))
        val result         = Into.derived[RequestV1, RequestV2].into(req)

        assert(result)(isRight(equalTo(RequestV2.Fetch("/api", Map("Auth" -> "Bearer token")): RequestV2)))
      },
      test("matches Post to Submit with multiple parameters") {
        val req: RequestV1 = RequestV1.Post("/api", """{"data":"test"}""", Map("Content-Type" -> "application/json"))
        val result         = Into.derived[RequestV1, RequestV2].into(req)

        assert(result)(
          isRight(
            equalTo(
              RequestV2.Submit("/api", """{"data":"test"}""", Map("Content-Type" -> "application/json")): RequestV2
            )
          )
        )
      }
    ),
    suite("All Cases Match by Signature")(
      test("converts all cases when signatures match") {
        val cases: List[SourceADTMulti] = List(
          SourceADTMulti.A(42),
          SourceADTMulti.B("test"),
          SourceADTMulti.C(true),
          SourceADTMulti.D(100, "data")
        )

        val results = cases.map(Into.derived[SourceADTMulti, TargetADTMulti].into)

        assertTrue(
          results == List(
            Right(TargetADTMulti.First(42L): TargetADTMulti),
            Right(TargetADTMulti.Second("test"): TargetADTMulti),
            Right(TargetADTMulti.Third(true): TargetADTMulti),
            Right(TargetADTMulti.Fourth(100L, "data"): TargetADTMulti)
          )
        )
      }
    ),
    suite("Signature Uniqueness")(
      test("matches when each signature appears exactly once") {
        val s1: SourceUnique = SourceUnique.TypeA(1)
        val s2: SourceUnique = SourceUnique.TypeB("two")
        val s3: SourceUnique = SourceUnique.TypeC(true)

        assert(Into.derived[SourceUnique, TargetUnique].into(s1))(
          isRight(equalTo(TargetUnique.Different1(1): TargetUnique))
        ) &&
        assert(Into.derived[SourceUnique, TargetUnique].into(s2))(
          isRight(equalTo(TargetUnique.Different2("two"): TargetUnique))
        ) &&
        assert(Into.derived[SourceUnique, TargetUnique].into(s3))(
          isRight(equalTo(TargetUnique.Different3(true): TargetUnique))
        )
      }
    ),
    suite("Error Handling")(
      test("fails when field conversion fails despite signature match") {
        val source: SourceErr = SourceErr.Data(Long.MaxValue)
        val result            = Into.derived[SourceErr, TargetErr].into(source)

        assert(result)(isLeft)
      },
      test("succeeds when field conversion is valid") {
        val source: SourceOk = SourceOk.Data(42L)
        val result           = Into.derived[SourceOk, TargetOk].into(source)

        assert(result)(isRight(equalTo(TargetOk.Info(42): TargetOk)))
      }
    )
  )
}
