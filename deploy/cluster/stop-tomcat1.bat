@echo off
REM ============================================================
REM  Campus Repair Assistant - Tomcat ʵ�� 1��8080��ֹͣ
REM  stop 20 -force�����ȴ� 20 �룬��ʱǿ�ƽ���
REM ============================================================
setlocal
set "SCRIPT_DIR=%~dp0"
if not exist "%SCRIPT_DIR%\cluster.local.bat" (
    echo [ERROR] ȱ�� cluster.local.bat���״�ʹ���������� setup-cluster.ps1
    pause
    exit /b 1
)
call "%SCRIPT_DIR%\cluster.local.bat"
set "CATALINA_BASE=%CAMPUS_ROOT%\instance1"
echo [stop] instance1  CATALINA_BASE=%CATALINA_BASE%
call "%CATALINA_HOME%\bin\catalina.bat" stop
REM �?30 秒后仍在监听，可手工 taskkill /PID <pid> /F 强制结束
endlocal
