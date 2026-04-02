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

import zio.blocks.schema.Schema
import zio.blocks.rpc.MetaAnnotation

// === Test Data Types (all with Schema derivation) ===

case class Todo(id: Int, title: String) derives Schema

case class SearchResult(items: List[String], total: Int) derives Schema

case class ServiceError(code: Int, message: String) derives Schema

case class BasicError(msg: String) derives Schema

sealed trait Status derives Schema
case object Healthy  extends Status
case object Degraded extends Status

// === Test Annotations ===

class Idempotent extends MetaAnnotation


class Deprecated(reason: String) extends MetaAnnotation

// === Service Trait Fixtures ===

// Simple single-method service — plain return type (no error)
trait GreeterService {
  def greet(name: String): String
}

// Multi-method service — Either return types
trait TodoService {
  def getTodo(id: Int): Either[ServiceError, Todo]
  def createTodo(title: String, description: String): Either[ServiceError, Todo]
  def listTodos(): Either[ServiceError, List[Todo]]
}

// Empty service (edge case)
trait EmptyService

// Zero-param method — plain return type
trait HealthService {
  def health(): Status
}

// Multi-param method
trait SearchService {
  def search(query: String, limit: Int, offset: Int): Either[ServiceError, SearchResult]
}

// Service with error types from Either
trait ErrorService {
  def riskyOp(input: String): Either[ServiceError, String]
}

// Service with method-level annotations
trait AnnotatedService {
  @Idempotent
  def lookup(id: Long): Either[BasicError, String]
  def subscribe(topic: String): Either[BasicError, Unit]
}

// Parent service trait for inheritance tests
trait ParentService {
  def parentMethod(x: String): String
}

// Child inherits parent
trait InheritedService extends ParentService {
  def childMethod(y: Int): Either[BasicError, String]
}

// Multiple annotations on one method
trait MultiAnnotatedService {
  @Idempotent @Deprecated("old")
  def dualAnnotated(id: Long): String
}
