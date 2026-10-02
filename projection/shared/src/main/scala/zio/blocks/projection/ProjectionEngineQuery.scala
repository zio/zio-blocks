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

package zio.blocks.projection

import zio.*

/**
 * Query concern of [[ProjectionEngine]]: point reads over primary and sharded
 * stores.
 *
 * Split out of the engine god-file so lifecycle (fibers), rebuild (schema
 * drift), and query (reads) can be reasoned about independently. No behavior
 * change — [[ProjectionEngine]] delegates to these helpers.
 */
private[projection] object EngineQuery {

  def primaryStoreFor[A](engine: ProjectionEngine, spec: Projection[A]): ProjectionStore[A] =
    engine.primaryStores(spec.name).asInstanceOf[ProjectionStore[A]]

  def query[A](engine: ProjectionEngine, spec: Projection[A], entityId: String): Task[Option[A]] =
    EngineRebuild.ensureRebuiltIfPending(engine, spec) *>
      primaryStoreFor(engine, spec).findById(entityId).flatMap {
        case some @ Some(_) => ZIO.succeed(some)
        case None           =>
          spec.scope match {
            case ProjectionScope.CrossEntity(_) =>
              engine.shardsRef.get.flatMap { outer =>
                outer.get(spec.name) match {
                  case None        => ZIO.succeed(None)
                  case Some(inner) =>
                    ZIO
                      .foreach(inner.values.toList)(
                        _.asInstanceOf[ProjectionStore[A]].findById(entityId).catchAll(_ => ZIO.succeed(None))
                      )
                      .map(_.collectFirst { case Some(v) => v })
                }
              }
            case _ => ZIO.succeed(None)
          }
      }

  /** Query by spec name + id (untyped helper) */
  def queryByName[A](engine: ProjectionEngine, specName: String, entityId: String): Task[Option[A]] =
    engine.primaryStores.get(specName) match {
      case Some(store) => store.asInstanceOf[ProjectionStore[A]].findById(entityId)
      case None        => ZIO.succeed(None)
    }

  def getLastProcessedSeq[A](engine: ProjectionEngine, spec: Projection[A]): Task[Long] =
    primaryStoreFor(engine, spec).getLastProcessedSeq
}
