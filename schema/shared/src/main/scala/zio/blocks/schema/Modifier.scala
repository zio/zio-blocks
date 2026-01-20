/*
 * Copyright 2023 ZIO Blocks Maintainers
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

import scala.annotation.meta.field
import scala.annotation.StaticAnnotation

/**
 * A sealed trait that represents a modifier used to annotate terms or reflect
 * values. Modifiers are used to provide metadata or additional configuration
 * associated with terms or reflect values.
 */
sealed trait Modifier extends StaticAnnotation

object Modifier {

  /**
   * `Term` represents a sealed trait for modifiers that annotate terms: record
   * fields or variant cases.
   *
   * The following are the known subtypes of `Term`:
   *   - `transient`: Used to indicate that a field should not be persisted or
   *     serialized.
   *   - `rename`: Used to specify a new name for a term, typically useful in
   *     serialization scenarios.
   *   - `alias`: Provides an alternative name (alias) for a term.
   *   - `config`: Represents a key-value pair for attaching additional
   *     configuration metadata to terms.
   */
  sealed trait Term extends Modifier

  /**
   * A modifier that marks a term (such as a field) as transient.
   */
  @field case class transient() extends Term

  /**
   * A modifier used to specify a new name for a term.
   *
   * @param name
   *   The new name to apply to the term.
   */
  @field case class rename(name: String) extends Term

  /**
   * A modifier representing an alias for a term.
   *
   * @param name
   *   The alias name for the term.
   */
  @field case class alias(name: String) extends Term

  /**
   * Represents a sealed trait for modifiers that annotate reflect values.
   */
  sealed trait Reflect extends Modifier

  /**
   * A configuration key-value pair, which can be attached to any type of
   * reflect values. The convention for keys is `<format>.<property>`. For
   * example, `protobuf.field-id` is a key that specifies the field id for a
   * protobuf format.
   */
  @field case class config(key: String, value: String) extends Term with Reflect
}
