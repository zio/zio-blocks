//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using file Model.scala
// Run from this folder:      scala-cli run Step5OpticsExample.scala
// Or from schema-examples:   sbt "runMain namedtuples.Step5OpticsExample"

package namedtuples

import zio.blocks.schema._

/**
 * How to Use Scala 3 Named Tuples with Schema — Read and Update a Field with
 * Optics
 *
 * Builds lenses and a traversal over named-tuple fields with `CompanionOptics`,
 * and uses them to read and update a reading and a station.
 */
object Step5OpticsExample {

  object Readings extends CompanionOptics[Reading] {
    val city: Lens[Reading, String]  = $(_.city)
    val tempC: Lens[Reading, Double] = $(_.tempC)
  }

  object Stations extends CompanionOptics[Station] {
    val tempC: Lens[Station, Double]         = $(_.reading.tempC)
    val humidity: Lens[Station, Option[Int]] = $(_.humidity)
    val tags: Traversal[Station, String]     = $(_.tags.each)
  }

  def main(args: Array[String]): Unit = {
    val oslo: Reading        = (city = "Oslo", tempC = 3.8)
    val osloStation: Station = (id = "OSL-1", reading = oslo, humidity = Some(81), tags = List("coast"))

    println(s"get: ${Readings.tempC.get(oslo)}")
    println(s"replace: ${Readings.tempC.replace(oslo, 4.1)}")
    println(s"modify: ${Readings.tempC.modify(oslo, _ + 0.5)}")

    println(s"nested: ${Stations.tempC.modify(osloStation, _ - 0.3)}")
    println(s"option: ${Stations.humidity.modify(osloStation, _.map(_ + 2))}")
    println(s"each: ${Stations.tags.modify(osloStation, _.toUpperCase)}")
  }
}
