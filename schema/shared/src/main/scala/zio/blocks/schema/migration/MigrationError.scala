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

/**
 * Captures a migration failure together with the [[DynamicOptic]] path at which
 * it occurred, enabling diagnostics like "Rename failed at
 * .payment.when[CreditCard]".
 */
final case class MigrationError(optic: DynamicOptic, message: String) {
  override def toString: String = s"$message at ${MigrationError.renderOptic(optic)}"
}

object MigrationError {

  def renderOptic(optic: DynamicOptic): String = optic match {
    case DynamicOptic.Field(name, None)    => s".$name"
    case DynamicOptic.Field(name, Some(n)) => s".$name${renderOptic(n)}"
    case DynamicOptic.Case(name, None)     => s".when[$name]"
    case DynamicOptic.Case(name, Some(n))  => s".when[$name]${renderOptic(n)}"
    case DynamicOptic.Element(None)        => ".each"
    case DynamicOptic.Element(Some(n))     => s".each${renderOptic(n)}"
    case DynamicOptic.Key(None)            => ".key"
    case DynamicOptic.Key(Some(n))         => s".key${renderOptic(n)}"
    case DynamicOptic.Value(None)          => ".value"
    case DynamicOptic.Value(Some(n))       => s".value${renderOptic(n)}"
  }
}
