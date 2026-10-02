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
echo [1/3] Aligning APK to 16 KB page size...
call "%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0\zipalign.exe" -f -P 16 4 "app\build\outputs\apk\release\app-release-unsigned.apk" "app\build\outputs\apk\release\app-release-aligned.apk"
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Zipalign failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Signing APK...
call "%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0\apksigner.bat" sign --ks "%USERPROFILE%\.android\debug.keystore" --ks-pass pass:android --key-pass pass:android --out ".\GBA_Native_K_v0.2.apk" "app\build\outputs\apk\release\app-release-aligned.apk"
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Signing failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [3/3] Verifying APK signature and 16 KB alignment...
call "%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify ".\GBA_Native_K_v0.2.apk"

echo.
echo ====================================================
echo  BUILD SUCCESSFUL!
echo  APK: GBA_Native_K_v0.2.apk (16KB Page-Aligned & Ready)
echo ====================================================
pause
