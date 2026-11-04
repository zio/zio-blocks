# JdkHttp adapter: answer 500 on fatal handler errors

## Problem

The JdkHttp adapter (copied into several guides and examples) catches only
`NonFatal` throwables. A `LinkageError` (`ExceptionInInitializerError`,
`NoClassDefFoundError`, `UnsupportedClassVersionError`) escapes the handler,
`finally exchange.close()` drops the connection, and nothing is logged. The
client sees "HTTP/1.1 header parser received no bytes".

## Goal

A `LinkageError` in the handler (or while writing the response) is logged and
answered with 500, exactly like an exception. `VirtualMachineError` (including
`StackOverflowError`, `OutOfMemoryError`) and `InterruptedException` still
propagate: the adapter must not swallow them.

## Catch shape

Replace `case NonFatal(error) =>` (both places) with

```scala
case error @ (NonFatal(_) | _: LinkageError) =>
```

Same log line, same 500 path. 500 only when no headers went out
(`exchange.getResponseCode == -1`) stays as is.

## Steps

1. Canonical copy `http-model-examples/.../servehttpmodelrequests/JdkHttp.scala` and
   `docs/guides/serve-http-model-requests.md` (inline block + prose). Prove with the
   reproducer from `docs/dev/docs-issues.md` before and after.
2. Other copies:
   - http-model-examples: `validatequeryparametersandheaders/JdkHttp.scala`,
     `protectpageswithbasicauthorasessioncookie/JdkHttp.scala`,
     `sendserversentevents/StreamingJdkHttp.scala`
   - jwt-examples `protectendpointswithjwt/JdkHttp.scala`
   - capstone-examples `smalljsonbackendfromblocks/JdkHttp.scala`
   - zio-blocks-htmx-examples `addinteractivitywithhtmx/`, `returnhtmxfragments/`
   - datastar-examples `buildalivedashboardwithdatastar/JdkHttp.scala`, `StreamingJdkHttp.scala`
   - guides with inline copies: validate-query-parameters-and-headers,
     protect-endpoints-with-jwt, small-json-backend-from-blocks,
     protect-pages-with-basic-auth-or-a-session-cookie
3. Verify: compile each examples build, run one main per build, mdoc every
   affected guide, `scala-cli fmt --check` on changed packages.
4. Opus fresh-context review, apply valid findings, open PR, CI green.
