package zio.blocks.schema.toon.benchmark

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit
import zio.blocks.schema.Schema
import zio.blocks.schema.toon.{ToonFormat, ToonBinaryCodec}

/**
 * JMH benchmarks comparing TOON codec performance.
 *
 * Run with: `sbt "schema-toon/Jmh/run -i 5 -wi 3 -f 1 -t 1"`
 *
 * Quick run:
 * `sbt "schema-toon/Jmh/run -i 1 -wi 1 -f 1 -t 1 ToonEncodingBenchmark"`
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class ToonEncodingBenchmark {

  // Test data classes
  case class SimplePerson(name: String, age: Int, email: String)

  case class Address(street: String, city: String, zip: String)
  case class PersonWithAddress(name: String, age: Int, address: Address)

  case class LargeRecord(
    id: Int,
    name: String,
    email: String,
    phone: String,
    address: String,
    city: String,
    state: String,
    zip: String,
    country: String,
    notes: String
  )

  // Schemas
  object Schemas {
    implicit val simplePersonSchema: Schema[SimplePerson]           = Schema.derived
    implicit val addressSchema: Schema[Address]                     = Schema.derived
    implicit val personWithAddressSchema: Schema[PersonWithAddress] = Schema.derived
    implicit val largeRecordSchema: Schema[LargeRecord]             = Schema.derived
  }

  import Schemas._

  // Codecs - derived once
  val simplePersonCodec: ToonBinaryCodec[SimplePerson] =
    simplePersonSchema.derive(ToonFormat.deriver)

  val personWithAddressCodec: ToonBinaryCodec[PersonWithAddress] =
    personWithAddressSchema.derive(ToonFormat.deriver)

  val largeRecordCodec: ToonBinaryCodec[LargeRecord] =
    largeRecordSchema.derive(ToonFormat.deriver)

  val listIntCodec: ToonBinaryCodec[List[Int]] =
    Schema[List[Int]].derive(ToonFormat.deriver)

  val listStringCodec: ToonBinaryCodec[List[String]] =
    Schema[List[String]].derive(ToonFormat.deriver)

  // Test data
  val simplePerson = SimplePerson("Alice Smith", 30, "alice@example.com")

  val personWithAddress = PersonWithAddress(
    "Bob Jones",
    25,
    Address("123 Main St", "Springfield", "12345")
  )

  val largeRecord = LargeRecord(
    id = 12345,
    name = "Charlie Brown",
    email = "charlie@example.com",
    phone = "555-123-4567",
    address = "456 Oak Avenue",
    city = "Metropolis",
    state = "NY",
    zip = "10001",
    country = "USA",
    notes = "A longer text field with some content to encode"
  )

  val smallIntList  = List(1, 2, 3, 4, 5)
  val mediumIntList = (1 to 100).toList
  val largeIntList  = (1 to 1000).toList

  val smallStringList  = List("apple", "banana", "cherry")
  val mediumStringList = (1 to 100).map(i => s"item_$i").toList

  // Primitive codecs for direct comparison
  val stringCodec = ToonBinaryCodec.stringCodec
  val intCodec    = ToonBinaryCodec.intCodec

  // --- Benchmarks ---

  @Benchmark
  def encodeSimplePerson(): String =
    simplePersonCodec.encodeToString(simplePerson)

  @Benchmark
  def encodeNestedPerson(): String =
    personWithAddressCodec.encodeToString(personWithAddress)

  @Benchmark
  def encodeLargeRecord(): String =
    largeRecordCodec.encodeToString(largeRecord)

  @Benchmark
  def encodeSmallIntList(): String =
    listIntCodec.encodeToString(smallIntList)

  @Benchmark
  def encodeMediumIntList(): String =
    listIntCodec.encodeToString(mediumIntList)

  @Benchmark
  def encodeLargeIntList(): String =
    listIntCodec.encodeToString(largeIntList)

  @Benchmark
  def encodeSmallStringList(): String =
    listStringCodec.encodeToString(smallStringList)

  @Benchmark
  def encodeMediumStringList(): String =
    listStringCodec.encodeToString(mediumStringList)

  // Primitive benchmarks
  @Benchmark
  def encodePrimitiveString(): Array[Byte] =
    stringCodec.encodeToBytes("Hello World")

  @Benchmark
  def encodePrimitiveStringWithQuotes(): Array[Byte] =
    stringCodec.encodeToBytes("Hello, World!")

  @Benchmark
  def encodePrimitiveInt(): Array[Byte] =
    intCodec.encodeToBytes(12345678)
}

/**
 * Memory allocation benchmark - measures GC pressure.
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, batchSize = 1000)
@Measurement(iterations = 10, batchSize = 1000)
@Fork(1)
class ToonAllocationBenchmark {

  case class DataRecord(id: Int, name: String, value: Double)

  object DataRecord {
    implicit val schema: Schema[DataRecord] = Schema.derived
  }

  val codec: ToonBinaryCodec[DataRecord] =
    DataRecord.schema.derive(ToonFormat.deriver)

  val record = DataRecord(42, "test", 3.14159)

  @Benchmark
  def measureAllocations(): String =
    codec.encodeToString(record)
}
