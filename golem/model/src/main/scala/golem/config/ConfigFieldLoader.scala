package golem.config

import golem.data.ElementSchema
import zio.blocks.schema.Schema

trait ConfigFieldLoader {
  def loadLocal[A](path: List[String], elementSchema: ElementSchema)(implicit schema: Schema[A]): A
  def loadSecret[A](path: List[String], elementSchema: ElementSchema)(implicit schema: Schema[A]): Secret[A]
}
