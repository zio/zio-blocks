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

package zio.blocks.schema.derive

import zio.blocks.schema._

sealed trait InstanceOverride

case class InstanceOverrideByOptic[TC[_], A](optic: DynamicOptic, instance: Lazy[TC[A]]) extends InstanceOverride

case class InstanceOverrideByType[TC[_], A](name: TypeName[A], instance: Lazy[TC[A]]) extends InstanceOverride
