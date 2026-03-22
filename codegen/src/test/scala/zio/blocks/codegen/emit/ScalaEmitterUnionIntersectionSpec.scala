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

package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterUnionIntersectionSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter union and intersection types")(
      test("union of two types") {
        val typeRef = TypeRef.union(TypeRef.String, TypeRef.Int)
        val result  = ScalaEmitter.emitTypeRef(typeRef)
        assertTrue(result == "String | Int")
      },
      test("union of three types") {
        val typeRef = TypeRef.union(TypeRef.String, TypeRef.Int, TypeRef.Boolean)
        val result  = ScalaEmitter.emitTypeRef(typeRef)
        assertTrue(result == "String | Int | Boolean")
      },
      test("intersection of two types") {
        val typeRef = TypeRef.intersection(TypeRef("HasName"), TypeRef("HasId"))
        val result  = ScalaEmitter.emitTypeRef(typeRef)
        assertTrue(result == "HasName & HasId")
      },
      test("nested union with Option") {
        val typeRef = TypeRef.union(TypeRef.String, TypeRef.optional(TypeRef.Int))
        val result  = ScalaEmitter.emitTypeRef(typeRef)
        assertTrue(result == "String | Option[Int]")
      },
      test("intersection used as field type") {
        val field  = Field("value", TypeRef.intersection(TypeRef("Readable"), TypeRef("Writable")))
        val result = ScalaEmitter.emitField(field, EmitterConfig.default)
        assertTrue(result == "value: Readable & Writable")
      }
    )
}
