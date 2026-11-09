@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM ----------------------------------------------------------------------------

@ECHO OFF
@SETLOCAL

SET ERROR_CODE=0

@SETLOCAL

IF NOT "%JAVA_HOME%"=="" GOTO OkJHome
FOR %%i IN (java.exe) DO SET JAVA_EXE=%%~$PATH:i
IF "%JAVA_EXE%"=="" (
  IF EXIST "C:\Program Files\Common Files\Oracle\Java\javapath\java.exe" (
    SET "JAVACMD=C:\Program Files\Common Files\Oracle\Java\javapath\java.exe"
    GOTO checkJCmd
  )
  ECHO Error: JAVA_HOME is not defined and cannot be found in your PATH. >&2
  GOTO error
)
SET "JAVACMD=%JAVA_EXE%"
GOTO checkJCmd

:OkJHome
SET "JAVACMD=%JAVA_HOME%\bin\java.exe"

:checkJCmd
IF EXIST "%JAVACMD%" GOTO chkMHome
ECHO Error: JAVA_HOME is set to an invalid directory: "%JAVA_HOME%" >&2
GOTO error

:chkMHome
SET "EXEC_DIR=%~dp0"
SET "MAVEN_PROJECTBASEDIR=%EXEC_DIR%"
IF "%MAVEN_PROJECTBASEDIR:~-1%"=="\" SET "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

SET "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
SET "WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"

IF EXIST "%WRAPPER_JAR%" GOTO runJar

powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar', '%WRAPPER_JAR%')}"
IF ERRORLEVEL 1 GOTO error

:runJar
"%JAVACMD%" %MAVEN_OPTS% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" -classpath "%WRAPPER_JAR%" %WRAPPER_LAUNCHER% %*
IF ERRORLEVEL 1 GOTO error
GOTO end

:error
SET ERROR_CODE=1

:end
@ENDLOCAL
EXIT /B %ERROR_CODE%
