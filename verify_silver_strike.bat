@echo off
cd /d "%~dp0"
echo ========================================================
echo  SILVER STRIKE VERIFICATION PROTOCOL
echo ========================================================
echo.
echo Target: ZIO Blocks (V2) - Algebraic Patch & Diff
echo Action: Running Unit Tests (PatchSpec, JsonBinaryCodecDeriverSpec)
echo Config: Using Local JDK 17
echo.

set "JAVA_HOME=%~dp0..\jdk17\jdk-17.0.17+10"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo JAVA_HOME set to: %JAVA_HOME%
java -version
javac -version
echo.

java -Xss64m -Xmx4g -jar ..\sbt-launch.jar "project schemaJVM" clean test

echo.
echo ========================================================
echo  VERIFICATION COMPLETE
echo ========================================================
echo DONE
