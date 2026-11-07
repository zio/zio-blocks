package protectpageswithbasicauthorasessioncookie

import java.security.SecureRandom
import java.util.Base64
import scala.collection.concurrent.TrieMap
import zio.http._

final case class Session(user: String, expiresAtSeconds: Long)

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Issuing a Session
 * Cookie
 *
 * The in-memory session store and the session cookie, shared by every file that
 * signs people in.
 */
object Sessions {
  val lifetimeSeconds: Long = 30 * 60

  private val random = new SecureRandom()
  private val active = TrieMap.empty[String, Session]

  private def nowSeconds: Long = System.currentTimeMillis() / 1000

  def start(user: String): String = {
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    val id = Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
    active.put(id, Session(user, nowSeconds + lifetimeSeconds))
    id
  }

  def find(id: String): Option[Session] =
    active.get(id) match {
      case Some(session) if session.expiresAtSeconds > nowSeconds => Some(session)
      case Some(_)                                                =>
        active.remove(id)
        None
      case None => None
    }

  def end(id: String): Unit = {
    active.remove(id)
    ()
  }
}

object SessionCookie {
  val sessionCookieName = "roastery_session"

  def sessionCookie(id: String): ResponseCookie =
    ResponseCookie(
      sessionCookieName,
      id,
      path = Some(Path.root),
      maxAge = Some(Sessions.lifetimeSeconds),
      isSecure = true,
      isHttpOnly = true,
      sameSite = Some(SameSite.Lax)
    )
}
