package zio.blocks.typeid

import zio.test.*

object OwnerSpec extends ZIOSpecDefault {

  def spec = suite("Owner")(
    suite("construction")(
      test("should create root owner") {
        assertTrue(
          Owner.Root.segments.isEmpty,
          Owner.Root.asString == "",
          Owner.Root.isEmpty
        )
      },
      
      test("should create owner with single package") {
        val owner = Owner(List(Owner.Segment.Package("scala")))
        assertTrue(
          owner.asString == "scala",
          !owner.isEmpty
        )
      },
      
      test("should create owner with nested packages") {
        val owner = Owner(List(
          Owner.Segment.Package("scala"),
          Owner.Segment.Package("collection")
        ))
        assertTrue(
          owner.asString == "scala.collection"
        )
      },
      
      test("should append segments using /") {
        val owner = Owner.Root / Owner.Segment.Package("scala") / Owner.Segment.Package("collection")
        assertTrue(
          owner.asString == "scala.collection",
          owner.segments.length == 2
        )
      }
    ),
    
    suite("predefined owners")(
      test("scala owner should be correct") {
        assertTrue(
          Owner.scala.asString == "scala"
        )
      },
      
      test("javaLang owner should be correct") {
        assertTrue(
          Owner.javaLang.asString == "java.lang"
        )
      },
      
      test("javaUtil owner should be correct") {
        assertTrue(
          Owner.javaUtil.asString == "java.util"
        )
      },
      
      test("scalaCollection owner should be correct") {
        assertTrue(
          Owner.scalaCollection.asString == "scala.collection"
        )
      },
      
      test("scalaCollectionImmutable owner should be correct") {
        assertTrue(
          Owner.scalaCollectionImmutable.asString == "scala.collection.immutable"
        )
      }
    ),
    
    suite("segments")(
      test("Package segment should have correct name") {
        val seg = Owner.Segment.Package("test")
        assertTrue(seg.name == "test")
      },
      
      test("Term segment should have correct name") {
        val seg = Owner.Segment.Term("MyObject")
        assertTrue(seg.name == "MyObject")
      },
      
      test("Type segment should have correct name") {
        val seg = Owner.Segment.Type("MyClass")
        assertTrue(seg.name == "MyClass")
      }
    )
  )
}
