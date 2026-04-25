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

package zio.blocks.telemetry.otel

import zio.blocks.telemetry._
import java.time.Duration

final case class ExporterConfig(
  endpoint: String = "http://localhost:4318",
  headers: Map[String, String] = Map.empty,
  timeout: Duration = Duration.ofSeconds(30),
  maxQueueSize: Int = 2048,
  maxBatchSize: Int = 512,
  flushIntervalMillis: Long = 5000
)
