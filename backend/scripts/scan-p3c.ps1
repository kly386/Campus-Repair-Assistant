<#
Backend Alibaba Java Coding Guidelines scan (p3c-pmd) for VSCode / CLI.

Usage:
    powershell -ExecutionPolicy Bypass -File scripts\scan-p3c.ps1
Custom dir:
    powershell -ExecutionPolicy Bypass -File scripts\scan-p3c.ps1 -P3cHome D:\p3c

Same detection engine (p3c-pmd, 54 rules) as the IDEA Alibaba plugin.
Dependencies (all on D: drive, nothing on C:):
    D:\p3c\p3c-pmd-2.1.1.jar
    D:\p3c\kotlin-stdlib-1.4.32.jar
    D:\p3c\kotlin-stdlib-common-1.4.32.jar
    D:\p3c\annotations-13.0.jar
    D:\p3c\pmd-bin-6.55.0\lib\*
    JDK 1.8+ (this project uses D:\Java_Practical_Training_Team_2\Java)

Exit code: 0 = clean / 4 = violations found (PMD) / 1 = error
#>
param(
    [string]$P3cHome = $(if ($env:P3C_HOME) { $env:P3C_HOME } else { "D:\p3c" }),
    [string]$SourceDir = $(Join-Path $PSScriptRoot "..\src")
)

if (-not (Test-Path $SourceDir)) {
    Write-Error ("Source dir not found: " + $SourceDir)
    exit 1
}

$deps = @("p3c-pmd-2.1.1.jar", "kotlin-stdlib-1.4.32.jar", "kotlin-stdlib-common-1.4.32.jar", "annotations-13.0.jar")
foreach ($d in $deps) {
    if (-not (Test-Path (Join-Path $P3cHome $d))) {
        Write-Error ("Missing dependency: " + $P3cHome + "\" + $d + " (see docs/development-guide for setup)")
        exit 1
    }
}

$javaExe = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
}
elseif (Test-Path "D:\Java_Practical_Training_Team_2\Java\bin\java.exe") {
    $javaExe = "D:\Java_Practical_Training_Team_2\Java\bin\java.exe"
}
elseif (Get-Command java -ErrorAction SilentlyContinue) {
    $javaExe = (Get-Command java).Source
}
else {
    Write-Error "JDK java.exe not found, please set JAVA_HOME"
    exit 1
}

$ruleNames = @("ali-comment.xml", "ali-concurrent.xml", "ali-constant.xml", "ali-exception.xml",
    "ali-flowcontrol.xml", "ali-naming.xml", "ali-oop.xml", "ali-orm.xml", "ali-other.xml", "ali-set.xml")
$rules = ($ruleNames | ForEach-Object { "rulesets/java/$_" }) -join ","

$p3cJar = Join-Path $P3cHome "p3c-pmd-2.1.1.jar"
$kotlinStd = Join-Path $P3cHome "kotlin-stdlib-1.4.32.jar"
$kotlinStdCommon = Join-Path $P3cHome "kotlin-stdlib-common-1.4.32.jar"
$annotations = Join-Path $P3cHome "annotations-13.0.jar"
$pmdLib = Join-Path $P3cHome "pmd-bin-6.55.0\lib\*"

Write-Host "== Alibaba code guidelines scan (p3c-pmd) ==" -ForegroundColor Cyan
Write-Host "    JDK    : $javaExe"
Write-Host "    P3C    : $P3cHome"
Write-Host "    Source : $SourceDir"
Write-Host ""

& $javaExe -cp "$p3cJar;$kotlinStd;$kotlinStdCommon;$annotations;$pmdLib" net.sourceforge.pmd.PMD -d $SourceDir -R $rules -f text -language java -no-cache 2>&1

exit $LASTEXITCODE