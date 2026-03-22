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

package zio.blocks.schema

import zio.test.Assertion._
import zio.test._

object SchemaVersionSpecificSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("SchemaVersionSpecificSpec")(
    test("compiles when explicit type annotation is used with schema.derive(Format)") {
      typeCheck {
        """
        import zio.blocks.schema._
        import zio.blocks.schema.json.{JsonCodec, JsonFormat}

        case class Person(name: String, age: Int)

        object Person {
          implicit val schema: Schema[Person] = Schema.derived[Person]
          implicit val jsonCodec: JsonCodec[Person] = schema.derive(JsonFormat)
        }
        """
      }.map(result => assert(result)(isRight))
    },
    test("compiles when explicit type annotation is used with schema.derive(deriver)") {
      typeCheck {
        """
        import zio.blocks.schema._
        import zio.blocks.schema.json.{JsonCodec, JsonCodecDeriver}

        case class Person(name: String, age: Int)

        object Person {
          implicit val schema: Schema[Person] = Schema.derived[Person]
          implicit val jsonCodec: JsonCodec[Person] = schema.derive(JsonCodecDeriver)
        }
        """
      }.map(result => assert(result)(isRight))
    },
    test("compiles without explicit type annotation using schema.derive(Format)") {
      typeCheck {
        """
        import zio.blocks.schema._
        import zio.blocks.schema.json.JsonFormat

        case class Person(name: String, age: Int)

        object Person {
          implicit val schema: Schema[Person] = Schema.derived[Person]
          val jsonCodec = schema.derive(JsonFormat)
        }
        """
      }.map(result => assert(result)(isRight))
    },
    test("doesn't generate schema for unsupported classes") {
      typeCheck {
        "Schema.derived[scala.concurrent.duration.Duration]"
      }.map(assert(_)(isLeft(containsString("Cannot find a primary constructor for 'Infinite.this.<local child>'"))))
    },
    test("doesn't generate schema for unsupported collections") {
      typeCheck {
        "Schema.derived[scala.collection.mutable.CollisionProofHashMap[String, Int]]"
      }.map(
        assert(_)(
          isLeft(
            containsString("Cannot derive schema for 'scala.collection.mutable.CollisionProofHashMap[String,Int]'.")
          )
        )
      )
    }
  )
}
