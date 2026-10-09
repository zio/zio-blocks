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

# Generate the docs with mdoc into website/docs
sbt docs/mdoc

# Build the website (the catalog generator runs first, then Docusaurus)
cd website
yarn install --frozen-lockfile
yarn build
