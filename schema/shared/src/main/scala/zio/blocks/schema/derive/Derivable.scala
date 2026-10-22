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

package zio.blocks.schema.derive

/**
 * Evidence that a value of type `D` provides a `Deriver[TC]`. It lets
 * `Schema#derive` take either a deriver or a format such as `JsonFormat`, whose
 * default deriver is then used: every `Deriver[TC]` has one
 * (`deriverDerivable`), and every `BinaryFormat` and `TextFormat` brings one as
 * its `derivable` member.
 */
trait Derivable[-D, TC[_]] {

  /** Returns the deriver that `d` provides. */
  def deriver(d: D): Deriver[TC]
}

/** The [[Derivable]] instance of every [[Deriver]]. */
object Derivable {

  /** Makes every `Deriver[TC]` derivable as itself. */
  implicit def deriverDerivable[TC[_]]: Derivable[Deriver[TC], TC] =
    new Derivable[Deriver[TC], TC] {
      def deriver(d: Deriver[TC]): Deriver[TC] = d
    }
}
