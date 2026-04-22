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

object SchemaExprDefaultValueSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("SchemaExprDefaultValueSpec")(
    suite("construction")(
      test("constructs DefaultValue with fieldPath and targetSchemaRepr") {
        val path = DynamicOptic.root.field("age")
        val repr = SchemaRepr.Primitive("int")
        val dv   = SchemaExpr.DefaultValue[Any](path, repr)
        assert(dv.fieldPath)(equalTo(path)) &&
        assert(dv.targetSchemaRepr)(equalTo(repr))
      }
    ),
    suite("equality")(
      test("same fields are equal") {
        val path = DynamicOptic.root.field("age")
        val repr = SchemaRepr.Primitive("int")
        assert(SchemaExpr.DefaultValue[Any](path, repr))(equalTo(SchemaExpr.DefaultValue[Any](path, repr)))
      },
      test("different fieldPath is not equal") {
        val repr  = SchemaRepr.Primitive("int")
        val path1 = DynamicOptic.root.field("age")
        val path2 = DynamicOptic.root.field("name")
        val a     = SchemaExpr.DefaultValue[Any](path1, repr)
        val b     = SchemaExpr.DefaultValue[Any](path2, repr)
        assert(a == b)(equalTo(false))
      },
      test("different targetSchemaRepr is not equal") {
        val path  = DynamicOptic.root.field("age")
        val repr1 = SchemaRepr.Primitive("int")
        val repr2 = SchemaRepr.Primitive("string")
        val a     = SchemaExpr.DefaultValue[Any](path, repr1)
        val b     = SchemaExpr.DefaultValue[Any](path, repr2)
        assert(a == b)(equalTo(false))
      }
    ),
    suite("eval")(
      test("eval returns isLeft for any input") {
        val dv = SchemaExpr.DefaultValue[String](DynamicOptic.root.field("age"), SchemaRepr.Primitive("int"))
        assert(dv.eval("anything"))(isLeft)
      },
      test("evalDynamic returns isLeft for any input") {
        val dv = SchemaExpr.DefaultValue[String](DynamicOptic.root.field("age"), SchemaRepr.Primitive("int"))
        assert(dv.evalDynamic("anything"))(isLeft)
      }
    )
  )
}
