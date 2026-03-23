package golem

sealed trait Principal extends Product with Serializable

object Principal {
  final case class Oidc(
    sub: String,
    issuer: String,
    claims: String,
    email: Option[String] = None,
    name: Option[String] = None,
    emailVerified: Option[Boolean] = None,
    givenName: Option[String] = None,
    familyName: Option[String] = None,
    picture: Option[String] = None,
    preferredUsername: Option[String] = None
  ) extends Principal

  final case class Agent(
    componentId: Uuid,
    agentId: String
  ) extends Principal

  final case class GolemUser(
    accountId: Uuid
  ) extends Principal

  case object Anonymous extends Principal
}
