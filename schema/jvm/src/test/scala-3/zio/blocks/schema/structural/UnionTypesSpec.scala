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

package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/**
 * Tests for sealed trait to structural union type conversion (Scala 3 only).
 */
object UnionTypesSpec extends SchemaBaseSpec {

  sealed trait Result
  object Result {
    case class Success(value: Int)    extends Result
    case class Failure(error: String) extends Result
  }

  sealed trait Status
  object Status {
    case object Active   extends Status
    case object Inactive extends Status
  }

  def spec: Spec[Any, Nothing] = suite("UnionTypesSpec")(
    test("sealed trait with case classes converts to structural") {
      val schema     = Schema.derived[Result]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("sealed trait with case objects converts to structural") {
      val schema     = Schema.derived[Status]
      val structural = schema.structural
      assertTrue(structural != null)
    },
    test("Result sealed trait converts to union structural type") {
      typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.structural.UnionTypesSpec._
        val schema: Schema[Result] = Schema.derived[Result]
        val structural: Schema[{def Failure: {def error: String}} | {def Success: {def value: Int}}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    },
    test("Status sealed trait converts to union structural type") {
      typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.structural.UnionTypesSpec._
        val schema: Schema[Status] = Schema.derived[Status]
        val structural: Schema[{def Active: {}} | {def Inactive: {}}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    }
  )
}
