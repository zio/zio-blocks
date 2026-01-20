@echo off
echo ============================================
echo ZIO-Blocks Development Server
echo ============================================
echo.

REM Kill any existing sbt processes that might be holding locks
echo Cleaning up any stale sbt processes...
taskkill /F /IM java.exe /FI "WINDOWTITLE eq sbt*" 2>nul

REM Clean sbt server locks
echo Cleaning sbt server locks...
if exist "project\target\.sbt-server" rmdir /s /q "project\target\.sbt-server" 2>nul
if exist "target\sbt-server" rmdir /s /q "target\sbt-server" 2>nul

echo.
echo Starting sbt...
echo.
echo Commands you can run:
echo   compile      - Compile the project
echo   test         - Run all tests  
echo   testOnly *JsonSpec* - Run only Json tests
echo   console      - Start Scala REPL
echo   exit         - Exit sbt
echo.
echo ============================================

sbt

pause
