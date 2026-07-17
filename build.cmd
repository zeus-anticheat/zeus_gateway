@echo off

set "JAVA_HOME=C:\Program Files\Java\jdk-25"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "PYTHONDONTWRITEBYTECODE=1"

echo ============================================
echo  Zeus Plugins Build Script
echo  Supports: ZeusProtocolJava, ZeusGateway, ZeusFabric
echo ============================================
echo.

echo [1/3] Checking Java version and release contracts...
call java -version
call py -3 -m unittest scripts\test_support_profiles.py
if %ERRORLEVEL% NEQ 0 exit /b 1
echo.

echo ============================================
echo  Building Maven modules (ZeusProtocolJava + ZeusGatewayLegacy + ZeusGateway)
echo ============================================
echo.

call mvn -v
echo.
call mvn clean install -pl ZeusProtocolJava -am
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] ZeusProtocolJava build failed!
    pause
    exit /b 1
)

echo.
echo [OK] ZeusProtocolJava built successfully.
echo.

call mvn clean package -pl ZeusGateway -am
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] ZeusGateway build failed!
    pause
    exit /b 1
)

echo.
echo [OK] ZeusGateway unified artifact built successfully.
echo.

echo ============================================
echo  Building Gradle module (ZeusFabric)
echo ============================================
echo.

if exist "ZeusFabric\gradlew.bat" (
    echo Using Gradle wrapper...
    cd ZeusFabric
    for /f "usebackq delims=" %%T in (`py -3 ..\scripts\list_fabric_build_targets.py`) do (
        echo Building ZeusFabric target %%T...
        call gradlew.bat build -PmcTarget=%%T
        if errorlevel 1 (
            cd ..
            echo.
            echo [ERROR] ZeusFabric target %%T build failed!
            pause
            exit /b 1
        )
    )
    cd ..
) else if exist "ZeusFabric\build.gradle" (
    echo Gradle wrapper not found, attempting system Gradle...
    cd ZeusFabric
    for /f "usebackq delims=" %%T in (`py -3 ..\scripts\list_fabric_build_targets.py`) do (
        echo Building ZeusFabric target %%T...
        call gradle build -PmcTarget=%%T
        if errorlevel 1 (
            cd ..
            echo.
            echo [ERROR] ZeusFabric target %%T build failed!
            pause
            exit /b 1
        )
    )
    cd ..
) else (
    echo [SKIP] ZeusFabric build.gradle not found or Gradle not configured.
    echo        To build ZeusFabric, run 'gradle build' inside the ZeusFabric directory.
    echo        Make sure to install ZeusProtocolJava to local Maven repo first:
    echo          mvn install -pl ZeusProtocolJava
)

echo.
echo [gate] Certifying newly built artifacts...
call py -3 scripts\write_release_evidence.py
if %ERRORLEVEL% NEQ 0 exit /b 1
call py -3 scripts\render_support_matrix.py --write
if %ERRORLEVEL% NEQ 0 exit /b 1
call py -3 scripts\render_support_readiness.py --write
if %ERRORLEVEL% NEQ 0 exit /b 1
call py -3 scripts\verify_support_matrix.py --require-artifacts
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Newly built artifacts or support claims failed verification.
    pause
    exit /b 1
)
echo [OK] Newly built artifacts and support claims verified.

echo.
echo ============================================
echo  Build Summary
echo ============================================
echo.

if exist "ZeusProtocolJava\target\ZeusProtocolJava-1.0-SNAPSHOT.jar" (
    echo [OK] ZeusProtocolJava : ZeusProtocolJava\target\ZeusProtocolJava-1.0-SNAPSHOT.jar
) else (
    echo [--] ZeusProtocolJava : not found
)

if exist "ZeusGateway\target\ZeusGateway-1.0-SNAPSHOT.jar" (
    echo [OK] ZeusGateway: ZeusGateway\target\ZeusGateway-1.0-SNAPSHOT.jar
) else (
    echo [--] ZeusGateway: not found
)

for /f "usebackq delims=" %%T in (`py -3 scripts\list_fabric_build_targets.py`) do (
    if exist "ZeusFabric\build\libs\ZeusFabric-%%T-1.0-SNAPSHOT.jar" (
        echo [OK] ZeusFabric-%%T: ZeusFabric\build\libs\ZeusFabric-%%T-1.0-SNAPSHOT.jar
    ) else (
        echo [--] ZeusFabric-%%T: not built (requires Gradle)
    )
)

echo.
echo ============================================
echo  Verification Surface:
echo    ZeusGateway -> one Java 8 Bukkit-family artifact; consult support-matrix.json
for /f "usebackq delims=" %%T in (`py -3 scripts\list_fabric_build_targets.py`) do (
    echo    ZeusFabric-%%T -> exact target build; consult support-matrix.json
)
echo ============================================
echo.

pause
