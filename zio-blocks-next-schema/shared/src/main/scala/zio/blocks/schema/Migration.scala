```scala
package zio.blocks.schema

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

  case object V2ToV3 extends Migration {
    override def apply(version: Int): Option[Int] = version match {
      case 2 => Some(3)
      case _ => None
    }
  }

  case object RemoveFieldMigration extends Migration {
    override def apply(version: Int): Option[Int] = version match {
      case v if v >= 3 => Some(v - 1) // Adjust the logic as needed
      case _ => None
    }
  }

  case object RenameFieldMigration extends Migration {
    override def apply(version: Int): Option[Int] = version match {
      case v if v >= 4 => Some(v + 1) // Adjust the logic as needed
      case _ => None
    }
  }

  case object ChangeTypeMigration extends Migration {
    override def apply(version: Int): Option[Int] = version match {
      case v if v >= 5 => Some(v + 2) // Adjust the logic as needed
      case _ => None
    }
  }
}
// Add more migrations as needed