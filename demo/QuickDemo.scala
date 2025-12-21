// Quick Demo - Copia e incolla nel REPL
// sbt "project schemaJVM" console

import zio.blocks.schema._

// 1. Basic Into
println("=== 1. Basic Into ===")
case class Person(name: String, age: Int)
case class User(name: String, age: Int)
val into = Into.derived[Person, User]
println(into.into(Person("Alice", 30)))

// 2. Schema Evolution
println("\n=== 2. Schema Evolution ===")
case class UserV1(name: String, age: Int)
case class UserV2(name: String, age: Int, email: Option[String])
val into2 = Into.derived[UserV1, UserV2]
println(into2.into(UserV1("Bob", 25)))

// 3. As Round-trip
println("\n=== 3. As Round-trip ===")
case class Point(x: Int, y: Int)
case class Coord(x: Long, y: Long)
val as = As.derived[Point, Coord]
val point = Point(10, 20)
val coord = as.into(point)
println(s"Point -> Coord: $coord")
val back = coord.flatMap(c => as.from(c))
println(s"Coord -> Point: $back")

// 4. Error Handling
println("\n=== 4. Error Handling ===")
case class Config(timeout: Long)
case class ConfigV2(timeout: Int)
val into3 = Into.derived[Config, ConfigV2]
println(s"Valid: ${into3.into(Config(42L))}")
println(s"Invalid: ${into3.into(Config(3000000000L))}")

// 5. Collections
println("\n=== 5. Collections ===")
val into4 = Into.derived[List[Int], Vector[Long]]
println(into4.into(List(1, 2, 3)))

val into5 = Into.derived[List[Int], Set[Int]]
println(into5.into(List(1, 2, 2, 3)))

println("\n=== Demo Complete ===")

