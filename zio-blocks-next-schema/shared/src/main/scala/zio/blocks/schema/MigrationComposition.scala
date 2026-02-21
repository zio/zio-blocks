```scala
package zio.blocks.schema

import scala.collection.immutable.List

sealed trait Migration {
  def apply(version: Int): Option[Int]
}

object Migration {
  case object V1ToV2 extends Migration {
    override def apply(version: Int): Option[Int] = version match {
      case 1 => Some(2)
      case _ => None
    }
  }

  // Add more migrations as needed
}

trait MigrationComposition {
  def compose(migrations: List[Migration]): Migration = new Migration {
    override def apply(version: Int): Option[Int] = {
      migrations.foldLeft(Some(version)) { (acc, migration) =>
        acc.flatMap(migration.apply)
      }
    }
  }
}
```
