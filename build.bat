@echo off
echo ====================================================
echo  BUILDING GBA NATIVE FOR ANDROID (NDK + mGBA CORE)
echo ====================================================

call gradlew.bat assembleRelease
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Signing APK...
call "%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0\apksigner.bat" sign --ks "%USERPROFILE%\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --out ".\GBA_Native_K_v0.1.apk" "app\build\outputs\apk\release\app-release-unsigned.apk"

echo.
echo ====================================================
echo  BUILD SUCCESSFUL!
echo  APK: GBA_Native_K_v0.1.apk (Ready to install)
echo ====================================================
pause
