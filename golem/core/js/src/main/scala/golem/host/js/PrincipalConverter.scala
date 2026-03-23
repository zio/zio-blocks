package golem.host.js

import golem.{Principal, Uuid}
import scala.scalajs.js

object PrincipalConverter {

  def fromJs(dynamic: js.Dynamic): Principal = {
    val tag = dynamic.tag
    if (js.isUndefined(tag) || tag == null) Principal.Anonymous
    else
      tag.asInstanceOf[String] match {
        case "oidc" =>
          val p = dynamic.asInstanceOf[JsPrincipalOidc].value
          Principal.Oidc(
            sub = p.sub,
            issuer = p.issuer,
            claims = p.claims,
            email = p.email.toOption,
            name = p.name.toOption,
            emailVerified = p.emailVerified.toOption,
            givenName = p.givenName.toOption,
            familyName = p.familyName.toOption,
            picture = p.picture.toOption,
            preferredUsername = p.preferredUsername.toOption
          )
        case "agent" =>
          val p         = dynamic.asInstanceOf[JsPrincipalAgent].value
          val jsUuid    = p.agentId.componentId.uuid
          val componentId = Uuid(
            highBits = BigInt(jsUuid.highBits.toString),
            lowBits = BigInt(jsUuid.lowBits.toString)
          )
          Principal.Agent(
            componentId = componentId,
            agentId = p.agentId.agentId
          )
        case "golem-user" =>
          val p      = dynamic.asInstanceOf[JsPrincipalGolemUser].value
          val jsUuid = p.accountId.uuid
          val accountId = Uuid(
            highBits = BigInt(jsUuid.highBits.toString),
            lowBits = BigInt(jsUuid.lowBits.toString)
          )
          Principal.GolemUser(accountId = accountId)
        case "anonymous" =>
          Principal.Anonymous
        case _ =>
          Principal.Anonymous
      }
  }
}
