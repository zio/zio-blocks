// Demo Script for Into[A, B] and As[A, B] Type Classes
// Run this in sbt console: sbt "project schemaJVM" console

import zio.blocks.schema._

object DemoScript {
  
  def main(args: Array[String]): Unit = {
    println("=== Into[A, B] and As[A, B] Demo ===\n")
    
    // 1. Basic Into conversions
    println("1. Basic Into Conversions")
    println("-" * 50)
    basicIntoDemo()
    
    // 2. Schema Evolution
    println("\n2. Schema Evolution Patterns")
    println("-" * 50)
    schemaEvolutionDemo()
    
    // 3. As round-trip
    println("\n3. As Bidirectional Conversions")
    println("-" * 50)
    asRoundTripDemo()
    
    // 4. Error handling
    println("\n4. Error Handling and Validation")
    println("-" * 50)
    errorHandlingDemo()
    
    // 5. Collection conversions
    println("\n5. Collection Type Conversions")
    println("-" * 50)
    collectionDemo()
    
    println("\n=== Demo Complete ===")
  }
  
  def basicIntoDemo(): Unit = {
    case class Person(name: String, age: Int)
    case class User(name: String, age: Int)
    
    val into = Into.derived[Person, User]
    val person = Person("Alice", 30)
    val result = into.into(person)
    
    println(s"Person: $person")
    println(s"Converted to User: $result")
    println(s"Success: ${result.isRight}")
  }
  
  def schemaEvolutionDemo(): Unit = {
    case class UserV1(name: String, age: Int)
    case class UserV2(name: String, age: Int, email: Option[String])
    
    val into = Into.derived[UserV1, UserV2]
    val userV1 = UserV1("Bob", 25)
    val result = into.into(userV1)
    
    println(s"UserV1: $userV1")
    println(s"Converted to UserV2: $result")
    println(s"Email field added as None: ${result.map(_.email)}")
    
    // Reverse conversion
    val as = As.derived[UserV1, UserV2]
    val userV2 = UserV2("Charlie", 35, Some("charlie@example.com"))
    val back = as.from(userV2)
    println(s"\nUserV2: $userV2")
    println(s"Converted back to UserV1: $back")
  }
  
  def asRoundTripDemo(): Unit = {
    case class Point(x: Int, y: Int)
    case class Coord(x: Long, y: Long)
    
    val as = As.derived[Point, Coord]
    val point = Point(10, 20)
    
    println(s"Original Point: $point")
    val coord = as.into(point)
    println(s"Converted to Coord: $coord")
    
    val roundTrip = coord.flatMap(c => as.from(c))
    println(s"Round-trip back to Point: $roundTrip")
    println(s"Round-trip successful: ${roundTrip.map(_ == point).getOrElse(false)}")
  }
  
  def errorHandlingDemo(): Unit = {
    case class Config(timeout: Long)
    case class ConfigV2(timeout: Int)
    
    val into = Into.derived[Config, ConfigV2]
    
    // Valid conversion
    val valid = Config(42L)
    val result1 = into.into(valid)
    println(s"Valid conversion: $valid -> $result1")
    
    // Invalid conversion (overflow)
    val invalid = Config(3000000000L) // Exceeds Int.MaxValue
    val result2 = into.into(invalid)
    println(s"Invalid conversion (overflow): $invalid -> $result2")
    println(s"Error message: ${result2.left.map(_.message).left.getOrElse("No error")}")
  }
  
  def collectionDemo(): Unit = {
    val into1 = Into.derived[List[Int], Vector[Long]]
    val list = List(1, 2, 3)
    val vector = into1.into(list)
    println(s"List[Int]: $list")
    println(s"Converted to Vector[Long]: $vector")
    
    val into2 = Into.derived[List[Int], Set[Int]]
    val listWithDups = List(1, 2, 2, 3)
    val set = into2.into(listWithDups)
    println(s"\nList[Int] with duplicates: $listWithDups")
    println(s"Converted to Set[Int] (duplicates removed): $set")
  }
}

