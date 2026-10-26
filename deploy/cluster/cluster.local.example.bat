@echo off
REM ============================================================
REM  本机路径配置（此文件含个人路径，不入库，勿提交）
REM  repo 中的模板为 cluster.local.example.bat；
REM  setup-cluster.ps1 会按本机实际情况生成本文件。
REM ============================================================

REM 集群根目录：实例目录、上传目录、前端产物都放这里
set "CAMPUS_ROOT=D:\campus"

REM Tomcat 11 安装目录（共享 bin/lib 的 CATALINA_HOME）
set "CATALINA_HOME=D:\campus\apache-tomcat-11.0.16"
