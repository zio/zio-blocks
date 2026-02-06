package zio.blocks.scope

import scala.annotation.implicitNotFound

/**
 * Prototype to verify the CanAccess typeclass approach works in Scala 2.
 */
object CanAccessPrototype {

  // Minimal scope hierarchy for testing
  // Key insight: child.Tag <: parent.Tag (child is SUBTYPE, not supertype)
  // This matches the main codebase's `type Tag <: tail.Tag`
  sealed trait MiniScope {
    type Tag <: AnyRef
  }

  object MiniScope {
    // Base trait for all tags - allows subtyping chain
    trait BaseTag

    final class Global extends MiniScope {
      type Tag = BaseTag // Global has the widest tag
    }

    // For child scopes, we create a fresh tag type that extends the parent's tag
    // Since we can't do `trait Tag extends tail.Tag`, we use a type member with upper bound
    final class ::[+H, +T <: MiniScope](val tail: T) extends MiniScope {
      // Fresh nested trait - each instance has its own unique Tag type
      trait MyTag extends BaseTag
      type Tag = MyTag
    }
  }

  // Tagged type - NON-TRANSPARENT to prevent S from being erased/re-inferred
  type @@[A, S] = A with @@.Tagged[S]

  object @@ {
    trait Tagged[S]
    def tag[A, S](a: A): A @@ S   = a.asInstanceOf[A @@ S]
    def untag[A, S](a: A @@ S): A = a.asInstanceOf[A]
  }

  // The typeclass: evidence that tag S can be accessed within scope Sc
  // Rule: In scope Sc, you can access a value tagged with S if Sc#Tag <: S
  @implicitNotFound("Scoped value with tag ${S} cannot be accessed in scope ${Sc}")
  trait CanAccess[S, Sc <: MiniScope]

  object CanAccess {
    // A scope can access values tagged with S if the scope's Tag is a subtype of S
    implicit def canAccess[S, Sc <: MiniScope](implicit ev: Sc#Tag <:< S): CanAccess[S, Sc] = null
  }

  // Operations on tagged values - require CanAccess evidence
  implicit class TaggedOps[A, S](private val value: A @@ S) extends AnyVal {
    def map[B, Sc <: MiniScope](f: A => B)(implicit @annotation.unused scope: Sc, ev: CanAccess[S, Sc]): B @@ S =
      @@.tag(f(@@.untag(value)))

    def get[Sc <: MiniScope](implicit @annotation.unused scope: Sc, ev: CanAccess[S, Sc]): A =
      @@.untag(value)
  }

  // Helper to create scoped values
  def tagged[A, Sc <: MiniScope](a: A)(implicit scope: Sc): A @@ scope.Tag =
    @@.tag[A, scope.Tag](a)
}

import zio.test._

object CanAccessPrototypeSpec extends ZIOSpecDefault {
  import CanAccessPrototype._
  import MiniScope._

  def spec = suite("CanAccess Prototype")(
    test("same scope: can access value") {
      val global                      = new Global
      implicit val scope: global.type = global
      val value: Int @@ global.Tag    = tagged(42)
      val result: Int                 = value.get
      assertTrue(result == 42)
    },
    test("child scope: can access parent-tagged value") {
      val parent                         = new Global
      val child                          = new ::[String, parent.type](parent)
      implicit val scope: child.type     = child
      val parentValue: Int @@ parent.Tag = @@.tag(42)
      // Child scope should be able to access parent-tagged value
      val result: Int = parentValue.get
      assertTrue(result == 42)
    },
    test("grandchild scope: can access grandparent-tagged value") {
      val grandparent                              = new Global
      val parent                                   = new ::[String, grandparent.type](grandparent)
      val child                                    = new ::[Int, parent.type](parent)
      implicit val scope: child.type               = child
      val grandparentValue: Int @@ grandparent.Tag = @@.tag(42)
      // Grandchild should be able to access grandparent-tagged value
      val result: Int = grandparentValue.get
      assertTrue(result == 42)
    },
    test("map works with correct scope") {
      val global                      = new Global
      implicit val scope: global.type = global
      val value: Int @@ global.Tag    = tagged(42)
      val mapped: Int @@ global.Tag   = value.map(_ * 2)
      val result: Int                 = mapped.get
      assertTrue(result == 84)
    },
    test("nested scopes: innermost implicit works") {
      val parent = new Global
      val child  = new ::[String, parent.type](parent)

      // Only child is implicit - should work without ambiguity
      implicit val scope: child.type     = child
      val parentValue: Int @@ parent.Tag = @@.tag(42)
      val result: Int                    = parentValue.get
      assertTrue(result == 42)
    },
    suite("Compile-time rejection tests")(
      test("cannot access child-tagged value from parent scope") {
        // A value tagged with child.Tag should NOT be accessible from parent scope
        typeCheck {
          """
          import zio.blocks.scope.CanAccessPrototype._
          import zio.blocks.scope.CanAccessPrototype.MiniScope._
          val parent = new Global
          val child = new ::[String, parent.type](parent)
          implicit val scope: parent.type = parent
          val childValue: Int @@ child.Tag = @@.tag(42)
          childValue.get  // Should fail: parent cannot access child-tagged value
          """
        }.map(result => assertTrue(result.isLeft))
      },
      test("cannot access sibling scope's tagged value") {
        // A value tagged with sibling1.Tag should NOT be accessible from sibling2
        typeCheck {
          """
          import zio.blocks.scope.CanAccessPrototype._
          import zio.blocks.scope.CanAccessPrototype.MiniScope._
          val parent = new Global
          val sibling1 = new ::[String, parent.type](parent)
          val sibling2 = new ::[Int, parent.type](parent)
          implicit val scope: sibling2.type = sibling2
          val sib1Value: Int @@ sibling1.Tag = @@.tag(42)
          sib1Value.get  // Should fail: sibling2 cannot access sibling1-tagged value
          """
        }.map(result => assertTrue(result.isLeft))
      },
      test("cannot access value without any scope") {
        // Without an implicit scope, should not compile
        typeCheck {
          """
          import zio.blocks.scope.CanAccessPrototype._
          import zio.blocks.scope.CanAccessPrototype.MiniScope._
          val global = new Global
          val value: Int @@ global.Tag = @@.tag(42)
          value.get  // Should fail: no implicit scope
          """
        }.map(result => assertTrue(result.isLeft))
      },
      test("cannot use map without scope") {
        typeCheck {
          """
          import zio.blocks.scope.CanAccessPrototype._
          import zio.blocks.scope.CanAccessPrototype.MiniScope._
          val global = new Global
          val value: Int @@ global.Tag = @@.tag(42)
          value.map(_ * 2)  // Should fail: no implicit scope
          """
        }.map(result => assertTrue(result.isLeft))
      }
    )
  )
}
