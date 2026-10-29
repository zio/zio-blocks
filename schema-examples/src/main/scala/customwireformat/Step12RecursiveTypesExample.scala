//> using scala 3.9.0
//> using options -experimental
//> using dep dev.zio::zio-blocks-schema:0.0.56
//> using file SensorModel.scala PackedFormat.scala
// Run from this folder: scala-cli run Step12RecursiveTypesExample.scala
// Or from schema-examples: sbt "runMain customwireformat.Step12RecursiveTypesExample"

package customwireformat

/**
 * How to Write a Codec for a New Wire Format — Encoding Recursive Types
 *
 * Derives the packed codec of the recursive Zone tree and round-trips a site
 * with nested zones.
 */
object Step12RecursiveTypesExample {
  def main(args: Array[String]): Unit = {
    val site = Zone(
      "north-site",
      Vector(DeviceId("gw-north")),
      List(
        Zone(
          "floor-1",
          Vector(DeviceId("t-17"), DeviceId("h-02")),
          List(Zone("cold-room", Vector(DeviceId("t-18")), Nil))
        ),
        Zone("floor-2", Vector(DeviceId("d-01")), Nil)
      )
    )
    val zoneCodec: PackedCodec[Zone] = Zone.schema.derive(PackedFormat)

    println(zoneCodec.decode(zoneCodec.encodeToBuffer(site)))
  }
}
