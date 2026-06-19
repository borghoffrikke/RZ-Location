@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d D:\Work\Projects\Location
echo Building normal...
call gradlew.bat assembleNormalDebug --no-daemon
echo.
echo Building senior...
call gradlew.bat assembleSeniorDebug --no-daemon
echo.
echo Done.
echo Normal APK: app\build\outputs\apk\normal\debug\app-normal-debug.apk
echo Senior APK: app\build\outputs\apk\senior\debug\app-senior-debug.apk
exit /b %ERRORLEVEL%
