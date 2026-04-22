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

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

/**
 * JS-safe shared spec placeholder. The actual public-signature source-scan
 * assertions live in `schema/jvm/src/test/scala/.../PublicApiJvmSpec.scala`
 * because they rely on `scala.io.Source.fromFile` / `java.io.File`, which
 * cannot link on Scala.js. This shared object exists so the
 * cross-platform sbt preflight can reference the name on both platforms
 * without pulling JVM-only file APIs through the shared compile unit.
 */
object PublicApiSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("PublicApiSpec")()
}
