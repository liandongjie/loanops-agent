param(
    [int]$Port = 18082,
    [switch]$KeepDatabase
)

$ErrorActionPreference = "Stop"

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw "ASSERTION FAILED: $Message"
    }
}

function Get-ComposeMySqlContainerId {
    $containerId = (docker compose ps -q mysql).Trim()
    if (-not $containerId) {
        throw "MySQL compose container was not found"
    }
    return $containerId
}

function Wait-MySqlHealthy {
    $deadline = (Get-Date).AddMinutes(3)
    do {
        try {
            $containerId = Get-ComposeMySqlContainerId
            $status = docker inspect --format '{{.State.Health.Status}}' $containerId 2>$null
            if ($status -eq "healthy") {
                return
            }
        } catch {
            # Container may still be starting; retry until the deadline.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    docker compose logs mysql
    throw "MySQL did not become healthy"
}

function Wait-Http([string]$Uri) {
    $deadline = (Get-Date).AddSeconds(45)
    do {
        try {
            Invoke-RestMethod -Method Get -Uri $Uri -TimeoutSec 2 | Out-Null
            return
        } catch {
            Start-Sleep -Seconds 1
        }
    } while ((Get-Date) -lt $deadline)

    throw "Application did not become ready: $Uri"
}

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME is required and must point to JDK 21"
}

$javaVersion = (& cmd.exe /d /c '"%JAVA_HOME%\bin\java.exe" -version 2>&1' | Out-String)
Assert-True ($javaVersion -match 'version "21[\.]') "JDK 21 is required"

$dbHost = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { "127.0.0.1" }
$dbPort = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { "3307" }
$dbName = if ($env:MYSQL_DATABASE) { $env:MYSQL_DATABASE } else { "loanops" }
$dbUser = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "loanops" }
$dbPassword = if ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { "loanops_dev" }

Write-Host "[1/6] Starting MySQL 8.0 on port $dbPort..."
docker compose up -d mysql
Wait-MySqlHealthy

$oldProfile = $env:SPRING_PROFILES_ACTIVE
$oldHost = $env:MYSQL_HOST
$oldPort = $env:MYSQL_PORT
$oldDatabase = $env:MYSQL_DATABASE
$oldUser = $env:MYSQL_USER
$oldPassword = $env:MYSQL_PASSWORD

$env:SPRING_PROFILES_ACTIVE = "mysql"
$env:MYSQL_HOST = $dbHost
$env:MYSQL_PORT = $dbPort
$env:MYSQL_DATABASE = $dbName
$env:MYSQL_USER = $dbUser
$env:MYSQL_PASSWORD = $dbPassword

$process = $null
try {
    Write-Host "[2/6] Running the full test suite against MySQL..."
    mvn clean verify
    if ($LASTEXITCODE -ne 0) { throw "mvn clean verify failed" }

    Write-Host "[3/6] Packaging application..."
    mvn package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }

    Write-Host "[4/6] Starting application with mysql profile..."
    $jar = Join-Path (Get-Location) "target\loanops-agent-0.0.1-SNAPSHOT.jar"
    $stdout = Join-Path (Get-Location) "target\mysql-smoke.out.log"
    $stderr = Join-Path (Get-Location) "target\mysql-smoke.err.log"
    $process = Start-Process `
        -FilePath "$env:JAVA_HOME\bin\java.exe" `
        -ArgumentList @("-jar", $jar, "--server.port=$Port", "--loanops.business-date=2026-08-23", "--loanops.business-zone=Asia/Shanghai") `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru

    $statusUri = "http://127.0.0.1:$Port/api/loans/LN-10002/status"
    Wait-Http $statusUri

    Write-Host "[5/6] Verifying deterministic REST result..."
    $status = Invoke-RestMethod -Method Get -Uri $statusUri
    Assert-True ($status.loanNo -eq "LN-10002") "loanNo must be LN-10002"
    Assert-True ([decimal]$status.dueAmount -eq 8500) "dueAmount must be 8500"
    Assert-True ([decimal]$status.paidAmount -eq 5000) "paidAmount must be 5000"
    Assert-True ([decimal]$status.outstandingAmount -eq 3500) "outstandingAmount must be 3500"
    Assert-True ($status.overdue -eq $true) "LN-10002 must be overdue"
    Assert-True ([int]$status.overdueDays -eq 3) "overdueDays must be 3 at the fixed business date"

    Write-Host "[6/6] Verifying Flyway history in MySQL..."
    $history = docker compose exec -T -e MYSQL_PWD=$dbPassword mysql `
        mysql -N "--user=$dbUser" "--database=$dbName" `
        -e "SELECT CONCAT(version, ':', description, ':', success) FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank;"
    $historyText = $history -join "`n"
    Assert-True ($historyText -match '1:create loan schema:1') "Flyway V1 must be applied"
    Assert-True ($historyText -match '2:seed demo data:1') "Flyway V2 must be applied"
    Assert-True ($historyText -match '3:create agent audit tables:1') "Flyway V3 must be applied"

    $auditTables = docker compose exec -T -e MYSQL_PWD=$dbPassword mysql `
        mysql -N "--user=$dbUser" "--database=$dbName" `
        -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('agent_audit_log', 'agent_tool_audit_log');"
    Assert-True ([int]($auditTables -join '').Trim() -eq 2) "V3 audit tables must exist"

    Write-Host "PASS: MySQL + Flyway verification completed."
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        Wait-Process -Id $process.Id -ErrorAction SilentlyContinue
    }

    $env:SPRING_PROFILES_ACTIVE = $oldProfile
    $env:MYSQL_HOST = $oldHost
    $env:MYSQL_PORT = $oldPort
    $env:MYSQL_DATABASE = $oldDatabase
    $env:MYSQL_USER = $oldUser
    $env:MYSQL_PASSWORD = $oldPassword

    if (-not $KeepDatabase) {
        docker compose down | Out-Null
    }
}
