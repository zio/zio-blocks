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

package zio.http.htmx

/**
 * Synchronization strategies supported by the `hx-sync` attribute.
 */
sealed trait HxSyncStrategy extends Product with Serializable {
  def render: String
}

object HxSyncStrategy {
  case object Abort   extends HxSyncStrategy { def render: String = "abort"   }
  case object Replace extends HxSyncStrategy { def render: String = "replace" }
  case object Drop    extends HxSyncStrategy { def render: String = "drop"    }
  case object Queue   extends HxSyncStrategy { def render: String = "queue"   }

  def parse(value: String): Either[String, HxSyncStrategy] = value.trim match {
    case "abort"   => Right(Abort)
    case "replace" => Right(Replace)
    case "drop"    => Right(Drop)
    case "queue"   => Right(Queue)
    case other     => Left("Unknown HTMX sync strategy: " + other)
  }
}

/**
 * Typed `hx-sync` value pairing a target selector with a synchronization
 * strategy.
 */
final case class HxSync(target: HxTarget, strategy: HxSyncStrategy) {
  def render: String = target.render + ":" + strategy.render
}

object HxSync {
  def parse(value: String): Either[String, HxSync] = {
    val trimmed = value.trim
    strategies.find { case (_, suffix) => trimmed.endsWith(suffix) } match {
      case Some((strategy, suffix)) if trimmed.length > suffix.length =>
        HxTarget.parse(trimmed.substring(0, trimmed.length - suffix.length)).map(HxSync(_, strategy))
      case _ => Left("Invalid HTMX sync value: " + value)
    }
  }

  private val strategies = Seq(
    HxSyncStrategy.Abort   -> ":abort",
    HxSyncStrategy.Replace -> ":replace",
    HxSyncStrategy.Drop    -> ":drop",
    HxSyncStrategy.Queue   -> ":queue"
  )

  implicit val toHtmxValue: ToHtmxValue[HxSync] = new ToHtmxValue[HxSync] {
    def toHtmxValue(value: HxSync): String = value.render
  }
}
