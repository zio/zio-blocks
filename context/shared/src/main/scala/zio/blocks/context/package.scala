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

package zio.blocks.context

/**
 * Evidence that `A` is a nominal type (a class, trait, object or applied type),
 * which `Context` needs to use `A` as a key. An alias of
 * `zio.blocks.typeid.IsNominalType`; the compiler derives it.
 */
type IsNominalType[A] = zio.blocks.typeid.IsNominalType[A]

/** The companion of `IsNominalType`, re-exported from `zio.blocks.typeid`. */
val IsNominalType = zio.blocks.typeid.IsNominalType

/**
 * Evidence that `A` is a nominal type or an intersection of nominal types,
 * which `Context#prune` needs to know the types to keep. An alias of
 * `zio.blocks.typeid.IsNominalIntersection`; the compiler derives it.
 */
type IsNominalIntersection[A] = zio.blocks.typeid.IsNominalIntersection[A]

/**
 * The companion of `IsNominalIntersection`, re-exported from
 * `zio.blocks.typeid`.
 */
val IsNominalIntersection = zio.blocks.typeid.IsNominalIntersection
