#requires -Version 5.1
<#
.SYNOPSIS
  Campus Repair Assistant — 本机 Tomcat 双实例集群一键安装脚本

.DESCRIPTION
  读取 deploy\cluster\conf\ 下的模板，在 <CampusRoot> 下生成两个 Tomcat 实例
  （instance1 = 8080, instance2 = 8081），并生成 cluster.local.bat 供启停脚本使用。
  幂等：重复执行只会刷新 conf，不会动你的 webapps/ROOT.war 与日志。

.USAGE
  cd deploy\cluster
  powershell -ExecutionPolicy Bypass -File .\setup-cluster.ps1
  powershell -ExecutionPolicy Bypass -File .\setup-cluster.ps1 -CampusRoot "D:\campus" -CatalinaHome "D:\campus\apache-tomcat-11.0.16"
#>
param(
    [string]$CampusRoot,
    [string]$CatalinaHome
)

$ErrorActionPreference = 'Stop'
$ClusterDir = $PSScriptRoot
$RepoRoot   = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

Write-Host '=== Campus Repair Assistant 集群安装 ===' -ForegroundColor Cyan

# ---------- 1. 解析路径 ----------
if (-not $CampusRoot)   { $CampusRoot = 'D:\campus' }
if (-not $CatalinaHome) {
    $found = Get-ChildItem -Path $CampusRoot -Directory -Filter 'apache-tomcat-11*' -ErrorAction SilentlyContinue
    if ($found) { $CatalinaHome = $found[0].FullName }
}

if (-not (Test-Path (Join-Path $CatalinaHome 'bin\catalina.bat'))) {
    Write-Host "[错误] 找不到 Tomcat 11：$CatalinaHome\bin\catalina.bat 不存在" -ForegroundColor Red
    Write-Host "  请先下载 Tomcat 11 压缩包并解压，例如解压到 $CampusRoot\apache-tomcat-11.0.16"
    Write-Host "  然后用参数指明：.\setup-cluster.ps1 -CatalinaHome ""你的解压目录"""
    exit 1
}

# ---------- 2. 解析 JAVA_HOME（每个实例的 setenv.bat 都要写死真实路径） ----------
# 收集所有候选 JDK，选第一个满足 Java 21+ 的；优先环境变量 > 常见目录 > PATH java 的真实 java.home
$candidates = New-Object System.Collections.Generic.List[string]
if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
$globs = @(
    'C:\Program Files\Eclipse Adoptium\*',
    'C:\Program Files\Java\*',
    'C:\Program Files\Amazon Corretto\*',
    'C:\Program Files\Microsoft\jdk*',
    'C:\Users\*\.jdks\*',
    'D:\Java\*', 'E:\Java\*'
)
foreach ($g in $globs) {
    Get-ChildItem $g -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
        ForEach-Object { $candidates.Add($_.FullName) }
}
# 兜底：PATH 上的 java 可能是 javapath 垫片，取它运行时的 java.home
# （用 cmd 合流捕获，避免 PowerShell 把 stderr 行升格为终止错误）
$settingsOut = cmd /c "java -XshowSettings:properties -version 2>&1" | Out-String
$homeLine = $settingsOut.Split("`n") | Where-Object { $_ -match '^\s*java\.home = (.+)$' } | Select-Object -First 1
if ($homeLine) { $candidates.Add(([regex]::Match($homeLine, 'java\.home = (.+)')).Groups[1].Value.Trim()) }

$JavaHome = $null
$JavaVerLine = $null
foreach ($cand in $candidates) {
    $javaExe = Join-Path $cand 'bin\java.exe'
    if (-not (Test-Path $javaExe)) { continue }
    $verOut = cmd /c """$javaExe"" -version 2>&1" | Out-String
    if ($verOut -match '"(2[1-9]|[3-9]\d+)\.') {
        $JavaHome = $cand
        $JavaVerLine = $verOut.Split("`n")[0].Trim()
        break
    }
}
if (-not $JavaHome) {
    Write-Host '[错误] 未找到满足要求的 JDK（需要 Java 21+，推荐 JDK 24/25）。本机候选：' -ForegroundColor Red
    foreach ($cand in $candidates) { Write-Host "  $cand" }
    Write-Host '  请安装 JDK 21+ 或设置系统环境变量 JAVA_HOME 后重试。'
    exit 1
}
Write-Host "JDK 检测：$JavaVerLine  （$JavaHome）"
Write-Host "CATALINA_HOME = $CatalinaHome"
Write-Host "CampusRoot    = $CampusRoot"
Write-Host "JAVA_HOME     = $JavaHome"

# ---------- 3. 生成两个实例目录（CATALINA_BASE 分离） ----------
$confKeep = 'web.xml','context.xml','catalina.properties','tomcat-users.xml','jaspic-providers.xml','jaspic-providers.xsd','catalina.policy','logging.properties'
$homeConf = Join-Path $CatalinaHome 'conf'
$setenvTemplate = Join-Path $ClusterDir 'conf\setenv.template.bat'

foreach ($n in 1, 2) {
    $base = Join-Path $CampusRoot "instance$n"
    foreach ($d in 'bin','conf','webapps','logs','temp','work') {
        New-Item -ItemType Directory -Force -Path (Join-Path $base $d) | Out-Null
    }

    # server.xml：用带端口/jvmRoute 的节点模板覆盖
    $nodeXml = Join-Path $ClusterDir "conf\server-node$n.xml"
    Copy-Item $nodeXml (Join-Path $base 'conf\server.xml') -Force

    # 其余基础配置从 CATALINA_HOME 补齐（CATALINA_BASE 必须自带这些文件）
    foreach ($f in $confKeep) {
        $src = Join-Path $homeConf $f
        if (Test-Path $src) { Copy-Item $src (Join-Path $base "conf\$f") -Force }
    }

    # setenv.bat：替换 JAVA_HOME 占位符后写入各实例（GBK 编码，cmd 原生兼容）
    $setenv = [System.IO.File]::ReadAllText($setenvTemplate, [System.Text.Encoding]::GetEncoding(936))
    $setenv = $setenv.Replace('__JAVA_HOME__', $JavaHome)
    [System.IO.File]::WriteAllText((Join-Path $base 'bin\setenv.bat'), $setenv, [System.Text.Encoding]::GetEncoding(936))

    Write-Host "  实例 $n -> $base（setenv.bat 已生成，conf 已就位）" -ForegroundColor Green
}

# ---------- 4. 共享上传目录 ----------
New-Item -ItemType Directory -Force -Path (Join-Path $CampusRoot 'uploads') | Out-Null

# ---------- 5. 可选：把最近构建的 ROOT.war 复制进两实例 ----------
$war = Join-Path $RepoRoot 'backend\target\ROOT.war'
if (Test-Path $war) {
    foreach ($n in 1, 2) { Copy-Item $war (Join-Path $CampusRoot "instance$n\webapps\ROOT.war") -Force }
    Write-Host '已将 backend\target\ROOT.war 复制进两实例 webapps\（注意：须先 mvn clean package 生成）' -ForegroundColor Green
} else {
    Write-Host '[提示] 未找到 backend\target\ROOT.war：发布时先在 backend 执行 mvn clean package -DskipTests，' -ForegroundColor Yellow
    Write-Host '       再把 target\ROOT.war 复制到两实例 webapps\ 目录。' -ForegroundColor Yellow
}

# ---------- 6. 生成本机路径配置（供启停脚本读取，不入库） ----------
$localBat = Join-Path $ClusterDir 'cluster.local.bat'
$example  = Join-Path $ClusterDir 'cluster.local.example.bat'
if (-not (Test-Path $localBat)) {
    $c = [System.IO.File]::ReadAllText($example, [System.Text.Encoding]::GetEncoding(936))
    $c = $c.Replace('D:\campus\apache-tomcat-11.0.16', "$CatalinaHome").Replace("set ""CAMPUS_ROOT=D:\campus""", "set ""CAMPUS_ROOT=$CampusRoot""")
    [System.IO.File]::WriteAllText($localBat, $c, [System.Text.Encoding]::GetEncoding(936))
    Write-Host '生成 cluster.local.bat（含本机路径，已列入 .gitignore，勿提交）' -ForegroundColor Green
} else {
    Write-Host 'cluster.local.bat 已存在，保持不动。'
}

Write-Host ''
Write-Host '=== 完成。下一步 ===' -ForegroundColor Cyan
Write-Host "  1. 编辑 $CampusRoot\instance1\bin\setenv.bat 与 instance2\bin\setenv.bat"
Write-Host '     把 DB_PASSWORD 改为真实密码（需与 deploy\docker 的应用账号一致）'
Write-Host '  2. 发布：backend 下 mvnw.cmd clean package -DskipTests 后复制 ROOT.war 到两实例 webapps\'
Write-Host '  3. 启动：.\start-tomcat1.bat 和 .\start-tomcat2.bat'
Write-Host '  4. 验证：curl http://127.0.0.1:8080/api/system/node 与 http://127.0.0.1:8081/api/system/node'
