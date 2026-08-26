/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package zio.blocks.sql.query

sealed trait JoinKind
object JoinKind {
  case object Inner extends JoinKind
  case object Left  extends JoinKind
}
