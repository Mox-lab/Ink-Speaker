@echo off
REM ============================================================
REM MoYu - JVM 启动脚本(Windows 版,第 7 阶段)
REM ============================================================
REM 用法:
REM   bin\start.bat              REM 默认 dev profile
REM   bin\start.bat prod         REM 指定 prod profile
REM
REM 所有 JVM 参数通过环境变量覆盖,无硬编码默认值参见 .env.example
REM ============================================================
setlocal enabledelayedexpansion

REM ------------------------------------------------------------
REM 1. 确定 Profile
REM ------------------------------------------------------------
if "%~1"=="" (
    if "%SPRING_PROFILES_ACTIVE%"=="" (
        set PROFILE=dev
    ) else (
        set PROFILE=%SPRING_PROFILES_ACTIVE%
    )
) else (
    set PROFILE=%~1
)
set SPRING_PROFILES_ACTIVE=%PROFILE%

REM ------------------------------------------------------------
REM 2. 应用主 jar 定位
REM ------------------------------------------------------------
set APP_DIR=%~dp0..
set JAR_FILE=%APP_DIR%\target\moyu-1.0.0-SNAPSHOT.jar

if not exist "%JAR_FILE%" (
    echo [ERROR] jar 不存在: %JAR_FILE% 1>&2
    echo         请先执行 mvn package -DskipTests 1>&2
    exit /b 1
)

REM ------------------------------------------------------------
REM 3. JVM 参数配置化(全部可由环境变量覆盖)
REM ------------------------------------------------------------
if "%JAVA_HEAP_INIT%"=="" set JAVA_HEAP_INIT=-XX:InitialRAMPercentage=25
if "%JAVA_HEAP_MAX%"=="" set JAVA_HEAP_MAX=-XX:MaxRAMPercentage=75
if "%JAVA_GC%"=="" set JAVA_GC=-XX:+UseG1GC
if "%JAVA_GC_OPTS%"=="" set JAVA_GC_OPTS=-XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=4m -XX:InitiatingHeapOccupancyPercent=45
if "%JAVA_METASPACE%"=="" set JAVA_METASPACE=-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m
if "%JAVA_GC_LOG%"=="" set JAVA_GC_LOG=-Xlog:gc*:file=logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20m
if "%JAVA_DIAGNOSTICS%"=="" set JAVA_DIAGNOSTICS=-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=logs/heapdump.hprof -XX:+ExitOnOutOfMemoryError
if "%JAVA_LOCALE%"=="" set JAVA_LOCALE=-Duser.timezone=GMT+8 -Dfile.encoding=UTF-8

set JVM_OPTS=%JAVA_HEAP_INIT% %JAVA_HEAP_MAX% %JAVA_GC% %JAVA_GC_OPTS% %JAVA_METASPACE% %JAVA_GC_LOG% %JAVA_DIAGNOSTICS% %JAVA_LOCALE% %EXTRA_JVM_OPTS%

echo [start] profile=%PROFILE%
echo [start] jvm=%JVM_OPTS%
echo [start] jar=%JAR_FILE%

REM ------------------------------------------------------------
REM 4. 启动
REM ------------------------------------------------------------
if not exist "%APP_DIR%\logs" mkdir "%APP_DIR%\logs"

java %JVM_OPTS% -jar "%JAR_FILE%" --spring.profiles.active="%PROFILE%"
