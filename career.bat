@echo off
rem CareerPilot launcher (Windows). No arguments opens the GUI; anything else runs a CLI command.
set JAR=%~dp0target\careerpilot.jar
if not exist "%JAR%" (
  echo Build first: mvn package
  exit /b 1
)
java -jar "%JAR%" %*
