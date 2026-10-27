# 本地一键启动后端：自动加载仓库根目录 .env 里的环境变量
# 用法: powershell -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            Set-Item -Path "env:$($Matches[1])" -Value $Matches[2]
        }
    }
    Write-Host "[run-backend] 已加载 .env（OSS 密钥已注入，不打印内容）"
} else {
    Write-Warning "[run-backend] 未找到 .env，OSS 相关接口将提示未配置"
}

Push-Location (Join-Path $root "backend")
try {
    mvn spring-boot:run
} finally {
    Pop-Location
}
