package zio.blocks.schema

import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit

/**
 * Basic performance benchmarks for Into conversions.
 * 
 * Run with: sbt "project benchmarks" "Jmh/run"
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class IntoBenchmarks {
  
  // Test data
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long)
  
  case class LargeV1(
    f1: Int, f2: String, f3: Boolean, f4: Long, f5: Double,
    f6: Int, f7: String, f8: Boolean, f9: Long, f10: Double
  )
  case class LargeV2(
    f1: Long, f2: String, f3: Boolean, f4: Long, f5: Double,
    f6: Long, f7: String, f8: Boolean, f9: Long, f10: Double
  )
  
  val personV1 = PersonV1("Alice", 30)
  val largeV1 = LargeV1(1, "a", true, 2L, 3.0, 4, "b", false, 5L, 6.0)
  
  val personInto = Into.derived[PersonV1, PersonV2]
  val largeInto = Into.derived[LargeV1, LargeV2]
  
  @Benchmark
  def numericWidening(): Long = {
    val into = Into.derived[Int, Long]
    into.intoOrThrow(42)
  }
  
  @Benchmark
  def numericNarrowing(): Int = {
    val into = Into.derived[Long, Int]
    into.intoOrThrow(42L)
  }
  
  @Benchmark
  def simpleProductConversion(): PersonV2 = {
    personInto.intoOrThrow(personV1)
  }
  
  @Benchmark
  def largeProductConversion(): LargeV2 = {
    largeInto.intoOrThrow(largeV1)
  }
  
  @Benchmark
  def collectionElementCoercion(): List[Long] = {
    val into = Into.derived[List[Int], List[Long]]
    into.intoOrThrow(List(1, 2, 3, 4, 5))
  }
  
  @Benchmark
  def collectionTypeConversion(): Vector[String] = {
    val into = Into.derived[List[String], Vector[String]]
    into.intoOrThrow(List("a", "b", "c", "d", "e"))
  }
  
  @Benchmark
  def nestedProductConversion(): PersonV2 = {
    case class AddressV1(street: String, number: Int)
    case class PersonWithAddressV1(name: String, age: Int, address: AddressV1)
    
    case class AddressV2(street: String, number: Long)
    case class PersonWithAddressV2(name: String, age: Long, address: AddressV2)
    
    val into = Into.derived[PersonWithAddressV1, PersonWithAddressV2]
    val person = PersonWithAddressV1("Alice", 30, AddressV1("Main St", 123))
    into.intoOrThrow(person)
  }
  
  @Benchmark
  def coproductConversion(): Any = {
    sealed trait EventV1
    object EventV1 {
      case class Created(name: String, id: Int) extends EventV1
      case object Deleted extends EventV1
    }
    
    sealed trait EventV2
    object EventV2 {
      case class Created(name: String, id: Long) extends EventV2
      case object Deleted extends EventV2
    }
    
    val into = Into.derived[EventV1, EventV2]
    into.intoOrThrow(EventV1.Created("test", 42))
  }
  
  @Benchmark
  def mapConversion(): Map[String, Long] = {
    val into = Into.derived[Map[String, Int], Map[String, Long]]
    val map = Map("a" -> 1, "b" -> 2, "c" -> 3)
    into.intoOrThrow(map)
  }
  
  @Benchmark
  def eitherConversion(): Either[Long, Long] = {
    val into = Into.derived[Either[Int, Int], Either[Long, Long]]
    into.intoOrThrow(Right(42))
  }
  
  @Benchmark
  def largeCollectionConversion(): Vector[Long] = {
    val into = Into.derived[List[Int], Vector[Long]]
    val list = (1 to 100).toList
    into.intoOrThrow(list)
  }
  
  @Benchmark
  def deeplyNestedConversion(): Any = {
    case class Level1(v: Int, next: Option[Level2])
    case class Level2(v: Int, next: Option[Level3])
    case class Level3(v: Int, next: Option[Level4])
    case class Level4(v: Int)
    
    case class Level1V2(v: Long, next: Option[Level2V2])
    case class Level2V2(v: Long, next: Option[Level3V2])
    case class Level3V2(v: Long, next: Option[Level4V2])
    case class Level4V2(v: Long)
    
    val into = Into.derived[Level1, Level1V2]
    val nested = Level1(1, Some(Level2(2, Some(Level3(3, Some(Level4(4)))))))
    into.intoOrThrow(nested)
  }
  
  @Benchmark
  def largeProductConversion(): LargeV2 = {
    largeInto.intoOrThrow(largeV1)
  }
  
  @Benchmark
  def optionConversion(): Option[Long] = {
    val into = Into.derived[Option[Int], Option[Long]]
    into.intoOrThrow(Some(42))
  }
  
  @Benchmark
  def tupleConversion(): (Long, String) = {
    val into = Into.derived[(Int, String), (Long, String)]
    into.intoOrThrow((42, "test"))
  }
  
  @Benchmark
  def nestedCollectionConversion(): Vector[List[Long]] = {
    val into = Into.derived[List[Vector[Int]], Vector[List[Long]]]
    val nested = List(Vector(1, 2), Vector(3, 4), Vector(5, 6))
    into.intoOrThrow(nested)
  }
  
  @Benchmark
  def veryLargeCollectionConversion(): Vector[Long] = {
    val into = Into.derived[List[Int], Vector[Long]]
    val list = (1 to 10000).toList
    into.intoOrThrow(list)
  }
  
  @Benchmark
  def complexNestedStructure(): Any = {
    case class Level1(v: Int, items: List[Level2])
    case class Level2(v: Int, items: List[Level3])
    case class Level3(v: Int, items: List[Level4])
    case class Level4(v: Int)
    
    case class Level1V2(v: Long, items: Vector[Level2V2])
    case class Level2V2(v: Long, items: Vector[Level3V2])
    case class Level3V2(v: Long, items: Vector[Level4V2])
    case class Level4V2(v: Long)
    
    val into = Into.derived[Level1, Level1V2]
    val nested = Level1(1, List(
      Level2(2, List(Level3(3, List(Level4(4), Level4(5))))),
      Level2(6, List(Level3(7, List(Level4(8))))
    ))
    into.intoOrThrow(nested)
  }
  
  @Benchmark
  def repeatedConversion(): PersonV2 = {
    // Simulate repeated conversions (common in real-world scenarios)
    var result = personV1
    for (_ <- 1 to 100) {
      result = personInto.intoOrThrow(result.asInstanceOf[PersonV1])
    }
    result.asInstanceOf[PersonV2]
  }
  
  @Benchmark
  def batchConversion(): List[PersonV2] = {
    val into = Into.derived[PersonV1, PersonV2]
    val batch = (1 to 1000).map(i => PersonV1(s"Person$i", 20 + i)).toList
    batch.map(p => into.intoOrThrow(p))
  }
}

