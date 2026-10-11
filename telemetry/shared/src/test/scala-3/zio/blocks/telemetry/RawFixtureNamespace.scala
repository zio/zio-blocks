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

package zio.blocks.telemetry

/**
 * Raw gating namespace of `LogSpec.StableCodeFixture` on Scala 3, where the
 * macro reports enclosing modules with JVM `$` markers. This prefix is not a
 * prefix of the normalized emitted name, which is what makes the level-override
 * regression discriminating on this compiler version.
 */
private[telemetry] object RawFixtureNamespace {
  final val value: String = "zio.blocks.telemetry.LogSpec$"
}
