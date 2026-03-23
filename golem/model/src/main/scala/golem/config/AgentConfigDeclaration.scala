package golem.config

import golem.data.ElementSchema

sealed trait AgentConfigSource extends Product with Serializable

object AgentConfigSource {
  case object Local  extends AgentConfigSource
  case object Secret extends AgentConfigSource
}

final case class AgentConfigDeclaration(
  source: AgentConfigSource,
  path: List[String],
  valueType: ElementSchema
)
