USAEJHWm12$
`schemaThrift`
git commit --allow-empty -m "chore: fix error"
git commit -m "feat: fix error"
$env:JDK_JAVA_OPTIONS = "-Xmx6G -Xss32M -XX:+UseG1GC"
sbt ++2.13.18! testJVM

sbt +test:compile



sbt "++2.13; check; ++3.7; check"
sbt ++2.13.x testJVM docJVM
sbt ++3.3.x coverage testJVM coverageReport docJVM
sbt ++3.7.x coverage testJVM coverageReport docJVM
sbt ++3.3.x coverage testJVM coverageReport
sbt ++3.7.x coverage testJVM coverageReport
sbt ++2.13.x testJS docJS
sbt ++3.3.x testJS docJS









sbt +compile


sbt validate

sbt +test:compile
sbt +test
sbt +compile






sbt clean
sbt reload
sbt +test
