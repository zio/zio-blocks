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

package zio.blocks.rpc.fixtures

import zio._
import zio.blocks.schema.Schema
import zio.blocks.rpc.{MetaAnnotation, ErrorAnnotation}

// Test-only stub: this schema is only used to satisfy implicit resolution for
// Task[_] error types during macro derivation. It is never used for actual
// serialization/deserialization. A proper Schema[Throwable] would require
// mapping to/from a concrete error representation.
implicit val throwableSchema: Schema[Throwable] = Schema.string.asInstanceOf[Schema[Throwable]]
// === Test Data Types (all with Schema derivation) ===

case class Todo(id: Int, title: String) derives Schema

case class SearchResult(items: List[String], total: Int) derives Schema

case class ServiceError(code: Int, message: String) derives Schema

case class BasicError(msg: String) derives Schema

sealed trait Status derives Schema
case object Healthy  extends Status
case object Degraded extends Status

// === Test Annotations ===

class failsWith[E] extends ErrorAnnotation[E]

class Idempotent extends MetaAnnotation

class Streaming extends MetaAnnotation

class Deprecated(reason: String) extends MetaAnnotation

// === Service Trait Fixtures ===

// Simple single-method service
trait GreeterService {
  def greet(name: String): UIO[String]
}

// Multi-method service
trait TodoService {
  def getTodo(id: Int): Task[Todo]

  def createTodo(title: String, description: String): Task[Todo]

  def listTodos(): Task[List[Todo]]
}

// Empty service (edge case)
trait EmptyService

// Zero-param method
trait HealthService {
  def health(): UIO[Status]
}

// Multi-param method
trait SearchService {
  def search(query: String, limit: Int, offset: Int): Task[SearchResult]
}

// Service with error type annotation
@failsWith[ServiceError]
trait ErrorService {
  def riskyOp(input: String): IO[ServiceError, String]
}

// Service with method-level annotations
@failsWith[BasicError]
trait AnnotatedService {
  @Idempotent
  def lookup(id: Long): Task[String]

  @Streaming
  def subscribe(topic: String): Task[Unit]
}
