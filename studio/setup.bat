@echo off
setlocal enabledelayedexpansion
echo ============================================================
echo  RuOS Android Studio -- Windows Setup
echo ============================================================
echo.

set STUDIO_DIR=%~dp0
cd /d "%STUDIO_DIR%"

@rem ── 1. gradle-wrapper.jar ───────────────────────────────────
set WRAPPER_JAR=%STUDIO_DIR%gradle\wrapper\gradle-wrapper.jar
if not exist "%WRAPPER_JAR%" (
    echo [1/4] Downloading gradle-wrapper.jar ...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^
        (New-Object Net.WebClient).DownloadFile( ^
            'https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar', ^
            '%WRAPPER_JAR%')"
    if not exist "%WRAPPER_JAR%" (
        echo ERROR: Download failed. Check your internet connection.
        goto :fail
    )
    echo       OK
) else (
    echo [1/4] gradle-wrapper.jar already present.
)

@rem ── 2. local.properties (Android SDK path) ──────────────────
set LOCAL_PROPS=%STUDIO_DIR%local.properties
if not exist "%LOCAL_PROPS%" (
    echo [2/4] Creating local.properties ...
    set SDK_PATH=
    @rem Try common default locations
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        set "SDK_PATH=%LOCALAPPDATA%\Android\Sdk"
    ) else if exist "%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe" (
        set "SDK_PATH=%USERPROFILE%\AppData\Local\Android\Sdk"
    )

    if "!SDK_PATH!"=="" (
        echo       Android SDK not found at default location.
        set /p "SDK_PATH=Enter your Android SDK path (e.g. C:\Users\mstar\AppData\Local\Android\Sdk): "
    )

    @rem Escape backslashes for Java properties format
    set "SDK_ESCAPED=!SDK_PATH:\=\\!"
    echo sdk.dir=!SDK_ESCAPED!> "%LOCAL_PROPS%"
    echo       Written: sdk.dir=!SDK_ESCAPED!
) else (
    echo [2/4] local.properties already exists.
)

@rem ── 3. JDK check ────────────────────────────────────────────
echo [3/4] Checking Java ...
java -version >NUL 2>&1
if %ERRORLEVEL% neq 0 (
    echo       WARNING: 'java' not found in PATH.
    echo       Gradle will auto-provision JDK 17 via toolchain on first build.
    echo       This requires internet access and may take a few minutes.
) else (
    java -version 2>&1 | findstr /C:"version \"17" >NUL
    if %ERRORLEVEL% equ 0 (
        echo       JDK 17 found.
    ) else (
        echo       WARNING: Java found but not JDK 17.
        echo       Gradle will auto-provision JDK 17 via toolchain.
        for /f "tokens=*" %%v in ('java -version 2^>^&1') do echo       Current: %%v & goto :jdk_done
        :jdk_done
    )
)

@rem ── 4. Build launcher APK ───────────────────────────────────
echo [4/4] Building launcher APK ...
echo       Running: gradlew.bat :launcher:assembleDebug
echo.
call "%STUDIO_DIR%gradlew.bat" :launcher:assembleDebug
if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: Build failed. Check the output above for details.
    goto :fail
)

echo.
echo ============================================================
echo  BUILD SUCCESSFUL
echo.
echo  APK: %STUDIO_DIR%launcher\build\outputs\apk\debug\launcher-debug.apk
echo ============================================================
goto :end

:fail
echo.
echo Setup failed. See errors above.
exit /b 1

:end
endlocal
