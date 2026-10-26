@echo off
REM ============================================================
REM  Campus Repair Assistant - Tomcat 实例 2（8081）启动
REM  首次使用请先运行 setup-cluster.ps1 生成本机路径配置
REM ============================================================
setlocal
set "SCRIPT_DIR=%~dp0"
if not exist "%SCRIPT_DIR%\cluster.local.bat" (
    echo [ERROR] 缺少 cluster.local.bat，首次使用请先运行 setup-cluster.ps1
    pause
    exit /b 1
)
call "%SCRIPT_DIR%\cluster.local.bat"
set "CATALINA_BASE=%CAMPUS_ROOT%\instance2"
echo [start] instance2  CATALINA_BASE=%CATALINA_BASE%
call "%CATALINA_HOME%\bin\catalina.bat" start
endlocal
