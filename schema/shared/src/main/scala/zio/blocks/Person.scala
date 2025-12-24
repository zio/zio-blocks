package zio.blocks

import zio.blocks.macros.GenerateOptics
import scala.annotation.experimental

@experimental
@GenerateOptics
final case class Person(firstName: String, age: Int)