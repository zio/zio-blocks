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

package zio.blocks.config

private[config] trait SecretPackageBase {
  type Secret[+A]

  protected def secretApply[A](value: A): Secret[A]
  protected def secretUnwrap[A](secret: Secret[A]): A

  implicit def secretDisplayable[A]: Displayable[Secret[A]] = Displayable.instance(_ => "<secret>")

  object Secret {
    def apply[A](value: A): Secret[A] = secretApply(value)

    def unwrap[A](secret: Secret[A]): A = secretUnwrap(secret)

    def displayable[A]: Displayable[Secret[A]] = secretDisplayable[A]
  }
}
