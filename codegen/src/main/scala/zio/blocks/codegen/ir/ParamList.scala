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

package zio.blocks.codegen.ir

/**
 * Modifier for a parameter list, indicating whether it is normal, implicit, or
 * using.
 */
sealed trait ParamListModifier

object ParamListModifier {
  case object Normal   extends ParamListModifier
  case object Implicit extends ParamListModifier
  case object Using    extends ParamListModifier
}

/**
 * Wraps a list of method parameters with an optional modifier (implicit/using).
 *
 * @param params
 *   The parameters in this parameter list
 * @param modifier
 *   The modifier for this parameter list (defaults to Normal)
 */
final case class ParamList(
  params: List[MethodParam],
  modifier: ParamListModifier = ParamListModifier.Normal
)
