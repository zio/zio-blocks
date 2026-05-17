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

package zio.blocks.jwt

sealed trait Algorithm extends Product with Serializable {
  def name: String
}

object Algorithm {
  case object HS256 extends Algorithm {
    val name: String = "HS256"
  }

  case object HS384 extends Algorithm {
    val name: String = "HS384"
  }

  case object HS512 extends Algorithm {
    val name: String = "HS512"
  }

  case object RS256 extends Algorithm {
    val name: String = "RS256"
  }

  case object RS384 extends Algorithm {
    val name: String = "RS384"
  }

  case object RS512 extends Algorithm {
    val name: String = "RS512"
  }

  case object PS256 extends Algorithm {
    val name: String = "PS256"
  }

  case object PS384 extends Algorithm {
    val name: String = "PS384"
  }

  case object PS512 extends Algorithm {
    val name: String = "PS512"
  }

  case object ES256 extends Algorithm {
    val name: String = "ES256"
  }

  case object ES384 extends Algorithm {
    val name: String = "ES384"
  }

  case object ES512 extends Algorithm {
    val name: String = "ES512"
  }

  case object EdDSA extends Algorithm {
    val name: String = "EdDSA"
  }

  val all: List[Algorithm] = List(HS256, HS384, HS512, RS256, RS384, RS512, PS256, PS384, PS512, ES256, ES384, ES512, EdDSA)

  private[this] val byName: Map[String, Algorithm] = all.map(alg => alg.name -> alg).toMap

  def fromString(s: String): Option[Algorithm] = byName.get(s)
}
