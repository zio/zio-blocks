#!/bin/bash
# Netlify build for the website (see netlify.toml): the docs need `sbt docs/mdoc`, so install Java and sbt first.
# Same approach as zio/zio-http.
set -euo pipefail

# Install SDKMAN
curl -s "https://get.sdkman.io" | bash
set +u
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install and use Java (CI builds with JDK 25)
sdk install java 25.0.4-tem
sdk use java 25.0.4-tem

# Install and use sbt (the launcher then fetches the sbt version pinned in project/build.properties)
sdk install sbt 1.11.7
sdk use sbt 1.11.7
set -u

# A Netlify build gets 8 GiB of memory (Free and Starter plans), but .jvmopts lets sbt's heap grow to 12 GiB for the
# GitHub runners. A JVM that outgrows the container is killed by the kernel (exit code 137) instead of failing with an
# OutOfMemoryError. Cap the heap well below the container: SBT_OPTS comes after .jvmopts on the java command line, so
# its -Xmx wins.
export SBT_OPTS="-Xmx5g"

# Generate the docs with mdoc into website/docs, then the Scaladoc into website/static/api (served at /api). Two sbt
# runs, so the Scaladoc of every module does not have to share a heap with what mdoc left behind.
sbt docs/mdoc
sbt docs/generateApiDocs

# Build the website (the catalog generator runs first, then Docusaurus)
cd website
yarn install --frozen-lockfile
yarn build
