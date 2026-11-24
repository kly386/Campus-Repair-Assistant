# One-click import for RAG knowledge seed data.
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\seed-knowledge.ps1
# Optional: .\scripts\seed-knowledge.ps1 -Container mysql-master -Database campus_repair
param(
    [string]$Container = 'mysql-master',
    [string]$Database = 'campus_repair'
)

$ErrorActionPreference = 'Stop'

if ($Container -notmatch '^[A-Za-z0-9_.-]+$') {
    throw "Invalid container name: $Container"
}
if ($Database -notmatch '^[A-Za-z0-9_]+$') {
    throw "Invalid database name: $Database"
}

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
$sqlFile = Join-Path $root 'docs/sql/knowledge-seed.sql'

if (-not (Test-Path $sqlFile)) {
    throw "Seed SQL not found: $sqlFile"
}

function Get-DotEnvValue([string]$key) {
    if (-not (Test-Path $envFile)) {
        return $null
    }
    foreach ($line in Get-Content $envFile) {
        if ($line -match "^\s*$key\s*=\s*(.*?)\s*$") {
            return $Matches[1]
        }
    }
    return $null
}

$dbPassword = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { Get-DotEnvValue 'DB_PASSWORD' }
if (-not $dbPassword) {
    throw 'DB_PASSWORD not found. Set it in the repository .env file or as an environment variable.'
}

$remotePath = '/tmp/campus-knowledge-seed.sql'
Write-Host "[seed] Copying seed SQL into container $Container ..."
& docker cp $sqlFile "${Container}:${remotePath}"
if ($LASTEXITCODE -ne 0) {
    throw "docker cp failed. Make sure Docker is running and container '$Container' exists."
}

try {
    Write-Host "[seed] Importing into $Database.knowledge_base (utf8mb4, upsert) ..."
    $mysqlCommand = 'mysql -uroot --default-character-set=utf8mb4 ' + $Database + ' < ' + $remotePath
    $mysqlArgs = @(
        'exec',
        '-e', "MYSQL_PWD=$dbPassword",
        $Container,
        'sh', '-c',
        $mysqlCommand
    )
    & docker @mysqlArgs
    if ($LASTEXITCODE -ne 0) {
        throw 'MySQL import failed. Check container status, root password, and docs/sql/setup-all.sql.'
    }

    Write-Host '[seed] Approved knowledge count:'
    $sql = "SELECT COUNT(*) FROM knowledge_base WHERE review_status='APPROVED' AND is_deleted=0;"
    $checkArgs = @(
        'exec',
        '-e', "MYSQL_PWD=$dbPassword",
        $Container,
        'mysql', '-uroot', '--default-character-set=utf8mb4',
        '-D', $Database, '-N', '-B', '-e',
        $sql
    )
    & docker @checkArgs
    if ($LASTEXITCODE -ne 0) {
        throw 'Verification query failed.'
    }
}
finally {
    & docker exec $Container rm -f $remotePath | Out-Null
}

Write-Host '[seed] Done. Teammates can run the same script after pulling this SQL file.'
