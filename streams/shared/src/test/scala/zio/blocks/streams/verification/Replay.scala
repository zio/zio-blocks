/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.streams.verification

final case class Replay(
  seed: Long,
  ordinal: Long,
  shrinkPath: Vector[Int],
  scenarioId: String,
  faultTokens: Vector[String],
  schedule: Vector[String]
) extends Serializable

object Replay {
  def encode(value: Replay): String =
    Vector(
      value.seed.toString,
      value.ordinal.toString,
      value.shrinkPath.mkString(","),
      escape(value.scenarioId),
      value.faultTokens.map(escape).mkString(","),
      value.schedule.map(escape).mkString(",")
    ).mkString("|")

  def decode(text: String): Either[String, Replay] = text.split("\\|", -1).toVector match {
    case Vector(seed, ordinal, path, id, faults, schedule) =>
      try Right(Replay(seed.toLong, ordinal.toLong, ints(path), unescape(id), strings(faults), strings(schedule)))
      catch { case error: NumberFormatException => Left(error.getMessage) }
    case _ => Left("replay must contain six fields")
  }

  private def ints(value: String): Vector[Int] =
    if (value.isEmpty) Vector.empty else value.split(",").toVector.map(_.toInt)
  private def strings(value: String): Vector[String] =
    if (value.isEmpty) Vector.empty else value.split(",", -1).toVector.map(unescape)
  private def escape(value: String): String   = value.replace("%", "%25").replace("|", "%7C").replace(",", "%2C")
  private def unescape(value: String): String = value.replace("%2C", ",").replace("%7C", "|").replace("%25", "%")
}
