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

package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Constructor-level structural spec for the [[MigrationError]] ADT. Pins the
 * contract that every constructor carries `path: DynamicOptic` as its first
 * field, and directly constructs every variant to pin its public shape.
 *
 * [[MigrationError.SchemaMismatch]] is used by the interpreter's
 * Rename-non-field, Rename-non-record, AddField-non-record, and
 * TransformValue/ChangeType non-addressable-path arms.
 */
object MigrationErrorSpec extends SchemaBaseSpec {

  def spec: Spec[Any, Any] = suite("MigrationErrorSpec")(
    test("ActionFailed exposes path and message containing actionName and path.toScalaString") {
      val p = DynamicOptic.root.field("x")
      val e = MigrationError.ActionFailed(p, "AddField")
      assertTrue(e.path == p) &&
      assertTrue(e.message.contains("AddField")) &&
      assertTrue(e.message.contains(p.toScalaString))
    },
    test("MissingField exposes path and fieldName, message includes both") {
      val p = DynamicOptic.root.field("user")
      val e = MigrationError.MissingField(p, "age")
      assertTrue(e.path == p) &&
      assertTrue(e.message.contains("age")) &&
      assertTrue(e.message.contains(p.toScalaString))
    },
    test("SchemaMismatch exposes path, expected, actual (emitted by interpreter per  fix)") {
      val p = DynamicOptic.root.field("age")
      val e = MigrationError.SchemaMismatch(p, "Int", "String")
      assertTrue(e.path == p) &&
      assertTrue(e.message.contains("Int")) &&
      assertTrue(e.message.contains("String")) &&
      assertTrue(e.message.contains(p.toScalaString))
    },
    test("KeyCollision exposes path and key, message includes both (/)") {
      val p   = DynamicOptic.root.field("m").mapKeys
      val key = DynamicValue.Primitive(PrimitiveValue.String("offending"))
      val e   = MigrationError.KeyCollision(p, key)
      assertTrue(e.path == p) &&
      assertTrue(e.message.contains(p.toScalaString)) &&
      assertTrue(e.message.contains("offending"))
    },
    test("Irreversible exposes path and cause; message includes base string and optional cause") {
      // No-cause form
      val p      = DynamicOptic.root.field("fullName")
      val eBare  = MigrationError.Irreversible(p)
      assertTrue(eBare.path == p) &&
      assertTrue(eBare.cause.isEmpty) &&
      assertTrue(eBare.message == s"Irreversible action at ${p.toScalaString}") &&
      // Cause-ful form
      {
        val eFull = MigrationError.Irreversible(p, Some("split result shape mismatch"))
        assertTrue(eFull.cause.contains("split result shape mismatch")) &&
        assertTrue(eFull.message == s"Irreversible action at ${p.toScalaString}: split result shape mismatch")
      }
    },
    test("MigrationError.toScalaString renders `<ConstructorName>: <message>` for every constructor") {
      //  golden-string: render a path like .addresses.each.streetNumber
      val deepPath = DynamicOptic.root.field("addresses").elements.field("streetNumber")
      // Verify the renderer format for each constructor using the deep-path.
      val af  = MigrationError.ActionFailed(deepPath, "TransformValue", Some("some cause"))
      val mf  = MigrationError.MissingField(deepPath, "streetNumber")
      val sm  = MigrationError.SchemaMismatch(deepPath, "Int", "String")
      val kc  = MigrationError.KeyCollision(deepPath, DynamicValue.Primitive(PrimitiveValue.String("k")))
      val ir  = MigrationError.Irreversible(deepPath, Some("cannot invert"))

      // Golden-string assertions (exact `==` per , not `.contains`).
      assertTrue(af.toScalaString == s"ActionFailed: Action TransformValue failed at ${deepPath.toScalaString}: some cause") &&
      assertTrue(mf.toScalaString == s"MissingField: Missing field 'streetNumber' at ${deepPath.toScalaString}") &&
      assertTrue(sm.toScalaString == s"SchemaMismatch: Schema mismatch at ${deepPath.toScalaString}: expected Int, got String") &&
      assertTrue(kc.toScalaString == s"KeyCollision: Key collision at ${deepPath.toScalaString}: key ${DynamicValue.Primitive(PrimitiveValue.String("k"))} collides with an existing entry") &&
      assertTrue(ir.toScalaString == s"Irreversible: Irreversible action at ${deepPath.toScalaString}: cannot invert")
    },
    test("every MigrationError constructor accepts path as first parameter (compile-time proof)") {
      val p = DynamicOptic.root
      val _: MigrationError = MigrationError.ActionFailed(p, "X")
      val _: MigrationError = MigrationError.MissingField(p, "f")
      val _: MigrationError = MigrationError.SchemaMismatch(p, "a", "b")
      val _: MigrationError = MigrationError.KeyCollision(p, DynamicValue.Primitive(PrimitiveValue.String("k")))
      val _: MigrationError = MigrationError.Irreversible(p)                                                        // cause-less
      val _: MigrationError = MigrationError.Irreversible(p, Some("reason"))                                        // cause-ful
      assertCompletes
    }
  )
}
