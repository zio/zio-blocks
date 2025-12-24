package zio.blocks.macros

import zio.test.*
import zio.optics.*
import zio.ZIO
import zio.blocks.Person // Import the annotated class
import scala.annotation.experimental

@experimental
object OpticsSpec extends ZIOSpecDefault {
  def spec = suite("Optics Macro")(
    test("generates lenses for Person") {
      val p = Person("Alice", 30)
      
      // These should now be visible
      val nameLens = Person.firstName
      val ageLens  = Person.age
      
      for {
        name <- ZIO.fromEither(nameLens.get(p))
        p2   <- ZIO.fromEither(ageLens.set(p, 31))
      } yield assertTrue(name == "Alice", p2.age == 31)
    }
  )
}