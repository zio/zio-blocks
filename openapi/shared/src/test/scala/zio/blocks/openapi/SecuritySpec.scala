package zio.blocks.openapi

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.docs.{Doc, Inline, Paragraph}
import zio.blocks.schema._
import zio.blocks.schema.json.Json
import zio.test._

object SecuritySpec extends SchemaBaseSpec {
  private def doc(s: String): Doc      = Doc(Chunk.single(Paragraph(Chunk.single(Inline.Text(s)))))
  def spec: Spec[TestEnvironment, Any] = suite("Security Types")(
    suite("APIKeyLocation")(
      test("Query location can be constructed") {
        val location = APIKeyLocation.Query

        assertTrue(location == APIKeyLocation.Query)
      },
      test("Header location can be constructed") {
        val location = APIKeyLocation.Header

        assertTrue(location == APIKeyLocation.Header)
      },
      test("Cookie location can be constructed") {
        val location = APIKeyLocation.Cookie

        assertTrue(location == APIKeyLocation.Cookie)
      },
      test("Schema[APIKeyLocation] can be derived") {
        val schema = Schema[APIKeyLocation]

        assertTrue(schema != null)
      },
      test("APIKeyLocation round-trips through DynamicValue") {
        val location = APIKeyLocation.Header

        val dv     = Schema[APIKeyLocation].toDynamicValue(location)
        val result = Schema[APIKeyLocation].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.contains(APIKeyLocation.Header)
        )
      }
    ),
    suite("SecurityScheme.APIKey")(
      test("can be constructed with required fields") {
        val apiKey = SecurityScheme.APIKey(
          name = "api_key",
          in = APIKeyLocation.Header
        )

        assertTrue(
          apiKey.name == "api_key",
          apiKey.in == APIKeyLocation.Header,
          apiKey.description.isEmpty,
          apiKey.extensions.isEmpty
        )
      },
      test("can be constructed with all fields") {
        val extensions = ChunkMap("x-custom" -> Json.String("value"))
        val apiKey     = SecurityScheme.APIKey(
          name = "X-API-Key",
          in = APIKeyLocation.Header,
          description = Some(doc("API key authentication")),
          extensions = extensions
        )

        assertTrue(
          apiKey.name == "X-API-Key",
          apiKey.in == APIKeyLocation.Header,
          apiKey.description.contains(doc("API key authentication")),
          apiKey.extensions.size == 1
        )
      },
      test("supports query location") {
        val apiKey = SecurityScheme.APIKey(
          name = "api_key",
          in = APIKeyLocation.Query
        )

        assertTrue(apiKey.in == APIKeyLocation.Query)
      },
      test("supports cookie location") {
        val apiKey = SecurityScheme.APIKey(
          name = "session",
          in = APIKeyLocation.Cookie
        )

        assertTrue(apiKey.in == APIKeyLocation.Cookie)
      },
      test("preserves extensions") {
        val extensions = ChunkMap(
          "x-internal"   -> Json.Boolean(true),
          "x-rate-limit" -> Json.Number(1000)
        )
        val apiKey = SecurityScheme.APIKey(
          name = "key",
          in = APIKeyLocation.Header,
          extensions = extensions
        )

        assertTrue(
          apiKey.extensions.size == 2,
          apiKey.extensions.get("x-internal").contains(Json.Boolean(true)),
          apiKey.extensions.get("x-rate-limit").contains(Json.Number(1000))
        )
      },
      test("APIKey round-trips through DynamicValue") {
        val apiKey = SecurityScheme.APIKey(
          name = "api_key",
          in = APIKeyLocation.Header,
          description = Some(doc("Test key")),
          extensions = ChunkMap("x-test" -> Json.String("value"))
        )

        val dv     = Schema[SecurityScheme].toDynamicValue(apiKey)
        val result = Schema[SecurityScheme].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists {
            case SecurityScheme.APIKey(name, in, desc, ext) =>
              name == "api_key" &&
              in == APIKeyLocation.Header &&
              desc.contains(doc("Test key")) &&
              ext.nonEmpty
            case _ => false
          }
        )
      }
    ),
    suite("SecurityScheme.HTTP")(
      test("can be constructed with required scheme field") {
        val http = SecurityScheme.HTTP(scheme = "bearer")

        assertTrue(
          http.scheme == "bearer",
          http.bearerFormat.isEmpty,
          http.description.isEmpty,
          http.extensions.isEmpty
        )
      },
      test("can be constructed with all fields") {
        val extensions = ChunkMap("x-custom" -> Json.String("value"))
        val http       = SecurityScheme.HTTP(
          scheme = "bearer",
          bearerFormat = Some("JWT"),
          description = Some(doc("Bearer authentication using JWT")),
          extensions = extensions
        )

        assertTrue(
          http.scheme == "bearer",
          http.bearerFormat.contains("JWT"),
          http.description.contains(doc("Bearer authentication using JWT")),
          http.extensions.size == 1
        )
      },
      test("supports basic auth scheme") {
        val http = SecurityScheme.HTTP(scheme = "basic")

        assertTrue(http.scheme == "basic")
      },
      test("supports custom auth schemes") {
        val http = SecurityScheme.HTTP(scheme = "digest")

        assertTrue(http.scheme == "digest")
      },
      test("preserves extensions") {
        val extensions = ChunkMap(
          "x-token-type" -> Json.String("opaque"),
          "x-version"    -> Json.Number(2)
        )
        val http = SecurityScheme.HTTP(
          scheme = "bearer",
          extensions = extensions
        )

        assertTrue(
          http.extensions.size == 2,
          http.extensions.get("x-token-type").contains(Json.String("opaque")),
          http.extensions.get("x-version").contains(Json.Number(2))
        )
      },
      test("HTTP round-trips through DynamicValue") {
        val http = SecurityScheme.HTTP(
          scheme = "bearer",
          bearerFormat = Some("JWT"),
          description = Some(doc("Test auth")),
          extensions = ChunkMap("x-test" -> Json.Boolean(true))
        )

        val dv     = Schema[SecurityScheme].toDynamicValue(http)
        val result = Schema[SecurityScheme].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists {
            case SecurityScheme.HTTP(scheme, bearerFormat, desc, ext) =>
              scheme == "bearer" &&
              bearerFormat.contains("JWT") &&
              desc.contains(doc("Test auth")) &&
              ext.nonEmpty
            case _ => false
          }
        )
      }
    ),
    suite("OAuthFlow")(
      test("can be constructed with empty scopes") {
        val flow = OAuthFlow()

        assertTrue(
          flow.authorizationUrl.isEmpty,
          flow.tokenUrl.isEmpty,
          flow.refreshUrl.isEmpty,
          flow.scopes.isEmpty,
          flow.extensions.isEmpty
        )
      },
      test("can be constructed with all fields") {
        val scopes = ChunkMap(
          "read:users"  -> "Read user information",
          "write:users" -> "Modify user information"
        )
        val extensions = ChunkMap("x-custom" -> Json.String("value"))

        val flow = OAuthFlow(
          authorizationUrl = Some("https://example.com/oauth/authorize"),
          tokenUrl = Some("https://example.com/oauth/token"),
          refreshUrl = Some("https://example.com/oauth/refresh"),
          scopes = scopes,
          extensions = extensions
        )

        assertTrue(
          flow.authorizationUrl.contains("https://example.com/oauth/authorize"),
          flow.tokenUrl.contains("https://example.com/oauth/token"),
          flow.refreshUrl.contains("https://example.com/oauth/refresh"),
          flow.scopes.size == 2,
          flow.extensions.size == 1
        )
      },
      test("supports implicit flow (authorizationUrl only)") {
        val flow = OAuthFlow(
          authorizationUrl = Some("https://example.com/oauth/authorize"),
          scopes = ChunkMap("read" -> "Read access")
        )

        assertTrue(
          flow.authorizationUrl.isDefined,
          flow.tokenUrl.isEmpty
        )
      },
      test("supports password flow (tokenUrl only)") {
        val flow = OAuthFlow(
          tokenUrl = Some("https://example.com/oauth/token"),
          scopes = ChunkMap("read" -> "Read access")
        )

        assertTrue(
          flow.authorizationUrl.isEmpty,
          flow.tokenUrl.isDefined
        )
      },
      test("supports authorization code flow (both URLs)") {
        val flow = OAuthFlow(
          authorizationUrl = Some("https://example.com/oauth/authorize"),
          tokenUrl = Some("https://example.com/oauth/token"),
          scopes = ChunkMap("read" -> "Read access", "write" -> "Write access")
        )

        assertTrue(
          flow.authorizationUrl.isDefined,
          flow.tokenUrl.isDefined
        )
      },
      test("preserves multiple scopes") {
        val scopes = ChunkMap(
          "read:users"   -> "Read user data",
          "write:users"  -> "Modify user data",
          "delete:users" -> "Delete users",
          "admin"        -> "Full admin access"
        )
        val flow = OAuthFlow(scopes = scopes)

        assertTrue(
          flow.scopes.size == 4,
          flow.scopes.contains("read:users"),
          flow.scopes.contains("write:users"),
          flow.scopes.contains("delete:users"),
          flow.scopes.contains("admin")
        )
      },
      test("preserves extensions") {
        val extensions = ChunkMap(
          "x-pkce-required" -> Json.Boolean(true),
          "x-timeout"       -> Json.Number(3600)
        )
        val flow = OAuthFlow(extensions = extensions)

        assertTrue(
          flow.extensions.size == 2,
          flow.extensions.get("x-pkce-required").contains(Json.Boolean(true)),
          flow.extensions.get("x-timeout").contains(Json.Number(3600))
        )
      },
      test("Schema[OAuthFlow] can be derived") {
        val schema = Schema[OAuthFlow]

        assertTrue(schema != null)
      },
      test("OAuthFlow round-trips through DynamicValue") {
        val flow = OAuthFlow(
          authorizationUrl = Some("https://example.com/auth"),
          tokenUrl = Some("https://example.com/token"),
          refreshUrl = Some("https://example.com/refresh"),
          scopes = ChunkMap("read" -> "Read access"),
          extensions = ChunkMap("x-test" -> Json.String("value"))
        )

        val dv     = Schema[OAuthFlow].toDynamicValue(flow)
        val result = Schema[OAuthFlow].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.authorizationUrl.isDefined),
          result.exists(_.tokenUrl.isDefined),
          result.exists(_.refreshUrl.isDefined),
          result.exists(_.scopes.nonEmpty),
          result.exists(_.extensions.nonEmpty)
        )
      }
    ),
    suite("OAuthFlows")(
      test("can be constructed with no flows") {
        val flows = OAuthFlows()

        assertTrue(
          flows.`implicit`.isEmpty,
          flows.password.isEmpty,
          flows.clientCredentials.isEmpty,
          flows.authorizationCode.isEmpty,
          flows.extensions.isEmpty
        )
      },
      test("can be constructed with all flow types") {
        val implicitFlow = OAuthFlow(
          authorizationUrl = Some("https://example.com/oauth/authorize"),
          scopes = ChunkMap("read" -> "Read access")
        )
        val passwordFlow = OAuthFlow(
          tokenUrl = Some("https://example.com/oauth/token"),
          scopes = ChunkMap("read" -> "Read access", "write" -> "Write access")
        )
        val clientCredentialsFlow = OAuthFlow(
          tokenUrl = Some("https://example.com/oauth/token"),
          scopes = ChunkMap("admin" -> "Admin access")
        )
        val authorizationCodeFlow = OAuthFlow(
          authorizationUrl = Some("https://example.com/oauth/authorize"),
          tokenUrl = Some("https://example.com/oauth/token"),
          scopes = ChunkMap("read" -> "Read access", "write" -> "Write access")
        )
        val extensions = ChunkMap("x-custom" -> Json.String("value"))

        val flows = OAuthFlows(
          `implicit` = Some(implicitFlow),
          password = Some(passwordFlow),
          clientCredentials = Some(clientCredentialsFlow),
          authorizationCode = Some(authorizationCodeFlow),
          extensions = extensions
        )

        assertTrue(
          flows.`implicit`.isDefined,
          flows.password.isDefined,
          flows.clientCredentials.isDefined,
          flows.authorizationCode.isDefined,
          flows.extensions.size == 1
        )
      },
      test("supports only implicit flow") {
        val flow = OAuthFlow(
          authorizationUrl = Some("https://example.com/oauth/authorize"),
          scopes = ChunkMap("read" -> "Read")
        )
        val flows = OAuthFlows(
          `implicit` = Some(flow)
        )

        assertTrue(
          flows.`implicit`.isDefined,
          flows.password.isEmpty,
          flows.clientCredentials.isEmpty,
          flows.authorizationCode.isEmpty
        )
      },
      test("supports only password flow") {
        val flow = OAuthFlow(
          tokenUrl = Some("https://example.com/oauth/token"),
          scopes = ChunkMap("read" -> "Read")
        )
        val flows = OAuthFlows(password = Some(flow))

        assertTrue(
          flows.`implicit`.isEmpty,
          flows.password.isDefined,
          flows.clientCredentials.isEmpty,
          flows.authorizationCode.isEmpty
        )
      },
      test("supports only client credentials flow") {
        val flow = OAuthFlow(
          tokenUrl = Some("https://example.com/oauth/token"),
          scopes = ChunkMap("service" -> "Service access")
        )
        val flows = OAuthFlows(clientCredentials = Some(flow))

        assertTrue(
          flows.`implicit`.isEmpty,
          flows.password.isEmpty,
          flows.clientCredentials.isDefined,
          flows.authorizationCode.isEmpty
        )
      },
      test("supports only authorization code flow") {
        val flow = OAuthFlow(
          authorizationUrl = Some("https://example.com/oauth/authorize"),
          tokenUrl = Some("https://example.com/oauth/token"),
          scopes = ChunkMap("read" -> "Read", "write" -> "Write")
        )
        val flows = OAuthFlows(authorizationCode = Some(flow))

        assertTrue(
          flows.`implicit`.isEmpty,
          flows.password.isEmpty,
          flows.clientCredentials.isEmpty,
          flows.authorizationCode.isDefined
        )
      },
      test("preserves extensions") {
        val extensions = ChunkMap(
          "x-oauth-version" -> Json.String("2.0"),
          "x-support-pkce"  -> Json.Boolean(true)
        )
        val flows = OAuthFlows(extensions = extensions)

        assertTrue(
          flows.extensions.size == 2,
          flows.extensions.get("x-oauth-version").contains(Json.String("2.0")),
          flows.extensions.get("x-support-pkce").contains(Json.Boolean(true))
        )
      },
      test("Schema[OAuthFlows] can be derived") {
        val schema = Schema[OAuthFlows]

        assertTrue(schema != null)
      },
      test("OAuthFlows round-trips through DynamicValue") {
        val flow = OAuthFlow(
          authorizationUrl = Some("https://example.com/auth"),
          tokenUrl = Some("https://example.com/token"),
          scopes = ChunkMap("read" -> "Read")
        )
        val flows = OAuthFlows(
          authorizationCode = Some(flow),
          extensions = ChunkMap("x-test" -> Json.Boolean(true))
        )

        val dv     = Schema[OAuthFlows].toDynamicValue(flows)
        val result = Schema[OAuthFlows].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists(_.authorizationCode.isDefined),
          result.exists(_.extensions.nonEmpty)
        )
      }
    ),
    suite("SecurityScheme.OAuth2")(
      test("can be constructed with required flows field") {
        val flows = OAuthFlows(
          authorizationCode = Some(
            OAuthFlow(
              authorizationUrl = Some("https://example.com/oauth/authorize"),
              tokenUrl = Some("https://example.com/oauth/token"),
              scopes = ChunkMap("read" -> "Read access")
            )
          )
        )
        val oauth2 = SecurityScheme.OAuth2(flows = flows)

        assertTrue(
          oauth2.flows.authorizationCode.isDefined,
          oauth2.description.isEmpty,
          oauth2.extensions.isEmpty
        )
      },
      test("can be constructed with all fields") {
        val flows = OAuthFlows(
          `implicit` = Some(
            OAuthFlow(
              authorizationUrl = Some("https://example.com/oauth/authorize"),
              scopes = ChunkMap("read" -> "Read access")
            )
          )
        )
        val extensions = ChunkMap("x-custom" -> Json.String("value"))

        val oauth2 = SecurityScheme.OAuth2(
          flows = flows,
          description = Some(doc("OAuth2 authentication")),
          extensions = extensions
        )

        assertTrue(
          oauth2.flows.`implicit`.isDefined,
          oauth2.description.contains(doc("OAuth2 authentication")),
          oauth2.extensions.size == 1
        )
      },
      test("preserves extensions") {
        val flows      = OAuthFlows()
        val extensions = ChunkMap(
          "x-oauth-provider" -> Json.String("Auth0"),
          "x-version"        -> Json.Number(2)
        )
        val oauth2 = SecurityScheme.OAuth2(
          flows = flows,
          extensions = extensions
        )

        assertTrue(
          oauth2.extensions.size == 2,
          oauth2.extensions.get("x-oauth-provider").contains(Json.String("Auth0")),
          oauth2.extensions.get("x-version").contains(Json.Number(2))
        )
      },
      test("OAuth2 round-trips through DynamicValue") {
        val flows = OAuthFlows(
          password = Some(
            OAuthFlow(
              tokenUrl = Some("https://example.com/token"),
              scopes = ChunkMap("read" -> "Read")
            )
          )
        )
        val oauth2 = SecurityScheme.OAuth2(
          flows = flows,
          description = Some(doc("Test OAuth2")),
          extensions = ChunkMap("x-test" -> Json.String("value"))
        )

        val dv     = Schema[SecurityScheme].toDynamicValue(oauth2)
        val result = Schema[SecurityScheme].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists {
            case SecurityScheme.OAuth2(flows, desc, ext) =>
              flows.password.isDefined &&
              desc.contains(doc("Test OAuth2")) &&
              ext.nonEmpty
            case _ => false
          }
        )
      }
    ),
    suite("SecurityScheme.OpenIdConnect")(
      test("can be constructed with required openIdConnectUrl field") {
        val oidc = SecurityScheme.OpenIdConnect(
          openIdConnectUrl = "https://example.com/.well-known/openid-configuration"
        )

        assertTrue(
          oidc.openIdConnectUrl == "https://example.com/.well-known/openid-configuration",
          oidc.description.isEmpty,
          oidc.extensions.isEmpty
        )
      },
      test("can be constructed with all fields") {
        val extensions = ChunkMap("x-custom" -> Json.String("value"))
        val oidc       = SecurityScheme.OpenIdConnect(
          openIdConnectUrl = "https://example.com/.well-known/openid-configuration",
          description = Some(doc("OpenID Connect authentication")),
          extensions = extensions
        )

        assertTrue(
          oidc.openIdConnectUrl == "https://example.com/.well-known/openid-configuration",
          oidc.description.contains(doc("OpenID Connect authentication")),
          oidc.extensions.size == 1
        )
      },
      test("preserves extensions") {
        val extensions = ChunkMap(
          "x-provider" -> Json.String("Okta"),
          "x-version"  -> Json.Number(1)
        )
        val oidc = SecurityScheme.OpenIdConnect(
          openIdConnectUrl = "https://example.com/.well-known/openid-configuration",
          extensions = extensions
        )

        assertTrue(
          oidc.extensions.size == 2,
          oidc.extensions.get("x-provider").contains(Json.String("Okta")),
          oidc.extensions.get("x-version").contains(Json.Number(1))
        )
      },
      test("OpenIdConnect round-trips through DynamicValue") {
        val oidc = SecurityScheme.OpenIdConnect(
          openIdConnectUrl = "https://example.com/.well-known/openid-configuration",
          description = Some(doc("Test OIDC")),
          extensions = ChunkMap("x-test" -> Json.Boolean(true))
        )

        val dv     = Schema[SecurityScheme].toDynamicValue(oidc)
        val result = Schema[SecurityScheme].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists {
            case SecurityScheme.OpenIdConnect(url, desc, ext) =>
              url == "https://example.com/.well-known/openid-configuration" &&
              desc.contains(doc("Test OIDC")) &&
              ext.nonEmpty
            case _ => false
          }
        )
      }
    ),
    suite("SecurityScheme.MutualTLS")(
      test("can be constructed with no fields") {
        val mtls = SecurityScheme.MutualTLS()

        assertTrue(
          mtls.description.isEmpty,
          mtls.extensions.isEmpty
        )
      },
      test("can be constructed with all fields") {
        val extensions = ChunkMap("x-custom" -> Json.String("value"))
        val mtls       = SecurityScheme.MutualTLS(
          description = Some(doc("Mutual TLS authentication")),
          extensions = extensions
        )

        assertTrue(
          mtls.description.contains(doc("Mutual TLS authentication")),
          mtls.extensions.size == 1
        )
      },
      test("preserves extensions") {
        val extensions = ChunkMap(
          "x-cert-required" -> Json.Boolean(true),
          "x-ca-bundle"     -> Json.String("/path/to/ca.pem")
        )
        val mtls = SecurityScheme.MutualTLS(extensions = extensions)

        assertTrue(
          mtls.extensions.size == 2,
          mtls.extensions.get("x-cert-required").contains(Json.Boolean(true)),
          mtls.extensions.get("x-ca-bundle").contains(Json.String("/path/to/ca.pem"))
        )
      },
      test("MutualTLS round-trips through DynamicValue") {
        val mtls = SecurityScheme.MutualTLS(
          description = Some(doc("Test mTLS")),
          extensions = ChunkMap("x-test" -> Json.Number(1))
        )

        val dv     = Schema[SecurityScheme].toDynamicValue(mtls)
        val result = Schema[SecurityScheme].fromDynamicValue(dv)

        assertTrue(
          result.isRight,
          result.exists {
            case SecurityScheme.MutualTLS(desc, ext) =>
              desc.contains(doc("Test mTLS")) && ext.nonEmpty
            case _ => false
          }
        )
      }
    ),
    suite("SecurityScheme")(
      test("Schema[SecurityScheme] can be derived") {
        val schema = Schema[SecurityScheme]

        assertTrue(schema != null)
      },
      test("all SecurityScheme variants round-trip through DynamicValue") {
        val apiKey = SecurityScheme.APIKey("api_key", APIKeyLocation.Header)
        val http   = SecurityScheme.HTTP("bearer")
        val oauth2 = SecurityScheme.OAuth2(OAuthFlows())
        val oidc   = SecurityScheme.OpenIdConnect("https://example.com/.well-known/openid-configuration")
        val mtls   = SecurityScheme.MutualTLS()

        val schemes = Chunk(apiKey, http, oauth2, oidc, mtls)
        val results = schemes.map { scheme =>
          val dv = Schema[SecurityScheme].toDynamicValue(scheme)
          Schema[SecurityScheme].fromDynamicValue(dv)
        }

        assertTrue(
          results.forall(_.isRight),
          results.size == 5
        )
      }
    )
  )
}
