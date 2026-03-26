package example.integrationtests

import golem.Principal
import golem.runtime.annotations.agentImplementation

import scala.concurrent.Future

@agentImplementation()
final class PrincipalAgentImpl(input: String, principal: Principal) extends PrincipalAgent {
  private val creatorInfo: String = principal match {
    case Principal.Oidc(sub, issuer, _, email, _, _, _, _, _, _) =>
      s"OIDC user: sub=$sub, issuer=$issuer, email=${email.getOrElse("N/A")}"
    case Principal.Agent(componentId, agentId) =>
      s"Agent: componentId=$componentId, agentId=$agentId"
    case Principal.GolemUser(accountId) =>
      s"Golem user: accountId=$accountId"
    case Principal.Anonymous =>
      "Anonymous"
  }

  override def whoCreated(): Future[String] =
    Future.successful(s"Agent '$input' was created by: $creatorInfo")

  override def currentCaller(caller: Principal): Future[String] = {
    val callerInfo = caller match {
      case Principal.Anonymous                            => "anonymous"
      case Principal.Oidc(sub, _, _, _, _, _, _, _, _, _) => s"OIDC:$sub"
      case Principal.Agent(_, agentId)                    => s"agent:$agentId"
      case Principal.GolemUser(accountId)                 => s"user:$accountId"
    }
    Future.successful(s"Current caller: $callerInfo")
  }
}
