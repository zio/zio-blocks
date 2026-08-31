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

package zio.blocks.sql

/**
 * Transaction isolation level.
 *
 * Each value maps to the standard JDBC isolation level via [[jdbcLevel]].
 * Levels are defined as local `Int` constants matching the JDBC specification
 * (1, 2, 4, 8) to avoid a `java.sql` dependency in the shared (JS) build. This
 * keeps `sql/shared` free of `java.sql` — the JVM side interprets [[jdbcLevel]]
 * via `Connection.setTransactionIsolation` — mirroring the convention in
 * `DbCodec.scala:101` where shared sources keep `java.sql` constants local.
 *
 * SQLite semantics: SQLite natively supports only `SERIALIZABLE` (its default).
 * Other levels are accepted by the driver and documented here but may be
 * treated as `SERIALIZABLE` by the SQLite engine — the implementation still
 * applies the requested level via `Connection.setTransactionIsolation` so
 * behaviour is uniform across dialects, with the caveat that on SQLite the
 * isolation guarantee remains serializable regardless of the requested level.
 */
enum TransactionIsolation(val jdbcLevel: Int) {
  case ReadUncommitted extends TransactionIsolation(1) // JDBC TRANSACTION_READ_UNCOMMITTED = 1
  case ReadCommitted   extends TransactionIsolation(2) // JDBC TRANSACTION_READ_COMMITTED = 2
  case RepeatableRead  extends TransactionIsolation(4) // JDBC TRANSACTION_REPEATABLE_READ = 4
  case Serializable    extends TransactionIsolation(8) // JDBC TRANSACTION_SERIALIZABLE = 8
}
