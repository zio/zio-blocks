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

package zio.blocks.schema.avro

import neotype._
import zio.blocks.schema.{Schema, SchemaBaseSpec, SchemaError}
import zio.blocks.schema.binding.Binding
import zio.blocks.schema.avro.AvroTestUtils._
import zio.blocks.typeid.TypeId
import zio.test._

object NeotypeSupportSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("NeotypeSupportSpec")(
    test("derive schemas for cases classes with subtype and newtype fields") {
      val value = new Planet(Name("Earth"), Kilogram(5.97e24), Meter(6378000.0), Some(Meter(1.5e15)))
      avroSchema[Planet](
        "{\"type\":\"record\",\"name\":\"Planet\",\"namespace\":\"zio.blocks.schema.avro.NeotypeSupportSpec\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"mass\",\"type\":\"double\"},{\"name\":\"radius\",\"type\":\"double\"},{\"name\":\"distanceFromSun\",\"type\":[{\"type\":\"record\",\"name\":\"None\",\"namespace\":\"scala\",\"fields\":[]},{\"type\":\"record\",\"name\":\"Some\",\"namespace\":\"scala\",\"fields\":[{\"name\":\"value\",\"type\":\"double\"}]}]}]}"
      ) &&
      roundTrip[Planet](value, 31)
    }
  )

  inline given newTypeSchema[A, B](using
    newType: Newtype.WithType[A, B],
    schema: Schema[A],
    typeId: TypeId[B]
  ): Schema[B] =
    Schema[A].transform(
      a => newType.make(a).fold(msg => throw SchemaError.validationFailed(msg), identity),
      newType.unwrap
    )

  type Name = Name.Type

  object Name extends Newtype[String] {
    override inline def validate(string: String): Boolean = string.length > 0
  }

  type Kilogram = Kilogram.Type

  object Kilogram extends Subtype[Double]

  type Meter = Meter.Type

  object Meter extends Newtype[Double] {
    override inline def validate(value: Double): Boolean = value >= 0.0
  }

  case class Planet(name: Name, mass: Kilogram, radius: Meter, distanceFromSun: Option[Meter])

  object Planet {
    implicit val schema: Schema[Planet] = Schema.derived
  }
}
