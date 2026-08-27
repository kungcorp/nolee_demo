@echo off
setlocal
cd /d "%~dp0"

rem Requires JDK 17+. Set JAVA_HOME if the build cannot find one.
if not defined JAVA_HOME (
    where java >nul 2>&1 || (
        echo ERROR: No JDK found. Install JDK 17 or newer and set JAVA_HOME.
        exit /b 1
    )
)

rem Some Windows images leave AF_UNIX sockets unusable, and Gradle then dies with
rem "Unable to establish loopback connection" — which reads like a firewall problem and is not one.
rem Pointing the JDK at an intentionally absent directory makes it fall back to TCP loopback.
rem This is process-local and changes nothing about the computer's settings.
set "JAVA_TOOL_OPTIONS=%JAVA_TOOL_OPTIONS% -Djdk.net.unixdomain.tmpdir=%SystemDrive%\NoleeDisabledUnixSockets\Unavailable"

call .\gradlew.bat :app:assembleDebug
if errorlevel 1 exit /b 1

echo.
echo Built app\build\outputs\apk\debug\app-debug.apk
echo Install with: adb install -r app\build\outputs\apk\debug\app-debug.apk
endlocal
