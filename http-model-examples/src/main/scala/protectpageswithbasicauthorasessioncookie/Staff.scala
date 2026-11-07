package protectpageswithbasicauthorasessioncookie

import java.security.{MessageDigest, SecureRandom}
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

final case class PasswordHash(salt: Array[Byte], hash: Array[Byte])

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Checking a
 * Password
 *
 * PBKDF2 password hashing and the staff accounts, shared by every file that
 * checks a password.
 */
object PasswordHash {
  val iterations: Int = 600000

  private val random = new SecureRandom()

  def derive(password: String, salt: Array[Byte]): Array[Byte] = {
    val spec = new PBEKeySpec(password.toCharArray, salt, iterations, 256)
    try SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded
    finally spec.clearPassword()
  }

  def create(password: String): PasswordHash = {
    val salt = new Array[Byte](16)
    random.nextBytes(salt)
    PasswordHash(salt, derive(password, salt))
  }

  // MessageDigest.isEqual compares every byte, however early the first difference is.
  def matches(password: String, stored: PasswordHash): Boolean =
    MessageDigest.isEqual(derive(password, stored.salt), stored.hash)
}

object Staff {
  // Sample data only: a real service loads the salts and hashes from its user store and never sees the passwords.
  private val accounts: Map[String, PasswordHash] = Map(
    "ada"   -> PasswordHash.create("correct horse battery staple"),
    "grace" -> PasswordHash.create("tea and biscuits at four")
  )

  private val unknownUser: PasswordHash = PasswordHash.create("no such user")

  def authenticate(username: String, password: String): Option[String] = {
    val passwordMatches = PasswordHash.matches(password, accounts.getOrElse(username, unknownUser))
    if (passwordMatches && accounts.contains(username)) Some(username) else None
  }
}
