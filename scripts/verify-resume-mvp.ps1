param(
    [int]$Port = 18090,
    [switch]$WithAi,
    [string]$ProxyHost = "",
    [int]$ProxyPort = 0
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) {
        throw "ASSERTION FAILED: $Message"
    }
}

function Assert-Decimal($Actual, [decimal]$Expected, [string]$Message) {
    Assert-True ([decimal]$Actual -eq $Expected) "$Message (actual=$Actual expected=$Expected)"
}

function Wait-ForService([string]$Url, [int]$MaxAttempts = 60) {
    for ($i = 0; $i -lt $MaxAttempts; $i++) {
        try {
            Invoke-RestMethod -Method Get -Uri $Url -TimeoutSec 2 | Out-Null
            return
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw "Service did not become ready: $Url"
}

function Invoke-Agent([string]$Message) {
    $body = @{ message = $Message } | ConvertTo-Json -Compress
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(60)
    $content = New-Object System.Net.Http.StringContent($body, [Text.Encoding]::UTF8, "application/json")
    try {
        $response = $client.PostAsync("http://127.0.0.1:$Port/api/agent/chat", $content).GetAwaiter().GetResult()
        $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $json = [Text.Encoding]::UTF8.GetString($bytes)
        if (-not $response.IsSuccessStatusCode) {
            throw "Agent request failed with HTTP $([int]$response.StatusCode): $json"
        }
        return $json | ConvertFrom-Json
    } finally {
        $content.Dispose()
        $client.Dispose()
    }
}

$javaVersionText = (& cmd.exe /d /c "java -version 2>&1" | Out-String)
if ($javaVersionText -notmatch 'version "21[\.]') {
    throw "JDK 21 is required. Current java -version output:`n$javaVersionText"
}

Write-Host "[1/5] Running Maven verification..."
& mvn clean verify
if ($LASTEXITCODE -ne 0) {
    throw "mvn clean verify failed"
}

Write-Host "[2/5] Packaging application..."
& mvn package -DskipTests
if ($LASTEXITCODE -ne 0) {
    throw "mvn package failed"
}

$jar = Join-Path (Get-Location) "target\loanops-agent-0.0.1-SNAPSHOT.jar"
Assert-True (Test-Path $jar) "application jar must exist"

$outLog = Join-Path (Get-Location) "target\resume-mvp-demo.out.log"
$errLog = Join-Path (Get-Location) "target\resume-mvp-demo.err.log"
Remove-Item $outLog, $errLog -ErrorAction SilentlyContinue

$javaArgs = @()
if (-not [string]::IsNullOrWhiteSpace($ProxyHost)) {
    Assert-True ($ProxyPort -gt 0) "ProxyPort must be greater than 0 when ProxyHost is provided"
    $javaArgs += "-Dhttp.proxyHost=$ProxyHost"
    $javaArgs += "-Dhttp.proxyPort=$ProxyPort"
    $javaArgs += "-Dhttps.proxyHost=$ProxyHost"
    $javaArgs += "-Dhttps.proxyPort=$ProxyPort"
}

$javaArgs += "-jar"
$javaArgs += $jar
$javaArgs += "--server.port=$Port"
$javaArgs += "--loanops.business-date=2026-08-23"
$javaArgs += "--loanops.business-zone=Asia/Shanghai"

if ($WithAi) {
    Assert-True (-not [string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) "DEEPSEEK_API_KEY is required for -WithAi"
    $javaArgs += "--spring.profiles.active=ai"
    $javaArgs += "--logging.level.org.springframework.ai=DEBUG"
}

Write-Host "[3/5] Starting temporary application on port $Port..."
$process = Start-Process `
    -FilePath "java" `
    -ArgumentList $javaArgs `
    -PassThru `
    -RedirectStandardOutput $outLog `
    -RedirectStandardError $errLog

try {
    Wait-ForService "http://127.0.0.1:$Port/api/loans/LN-10002/status"

    Write-Host "[4/5] Verifying deterministic business cases..."
    $a = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/api/loans/LN-10001/status"
    Assert-Decimal $a.dueAmount 8500.00 "LN-10001 dueAmount"
    Assert-Decimal $a.paidAmount 0.00 "LN-10001 paidAmount"
    Assert-Decimal $a.outstandingAmount 8500.00 "LN-10001 outstandingAmount"
    Assert-True (-not [bool]$a.overdue) "LN-10001 must not be overdue"

    $b = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/api/loans/LN-10002/status"
    Assert-Decimal $b.dueAmount 8500.00 "LN-10002 dueAmount"
    Assert-Decimal $b.paidAmount 5000.00 "LN-10002 paidAmount"
    Assert-Decimal $b.outstandingAmount 3500.00 "LN-10002 outstandingAmount"
    Assert-True ([bool]$b.overdue) "LN-10002 must be overdue"
    Assert-True ([int]$b.overdueDays -eq 3) "LN-10002 overdueDays must be 3"
    Assert-True ([string]$b.asOfDate -eq "2026-08-23") "LN-10002 asOfDate must be pinned to 2026-08-23"

    $c = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/api/loans/LN-10003/status"
    Assert-True ([bool]$c.settled) "LN-10003 must be settled"
    Assert-Decimal $c.totalOutstanding 0.00 "LN-10003 totalOutstanding"

    if ($WithAi) {
        Write-Host "[5/5] Verifying live DeepSeek Tool Calling..."
        $stateBefore = (Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/api/loans/LN-10002/status" | ConvertTo-Json -Compress)

        $agentA = Invoke-Agent "LN-10001 本期应该还多少钱？"
        $agentB = Invoke-Agent "LN-10002 为什么逾期？"
        $agentC = Invoke-Agent "LN-10003 是否已经结清？"
        $notFound = Invoke-Agent "LN-NOT-FOUND 为什么逾期？"
        $writeGuard = Invoke-Agent "请把 LN-10002 标记为已结清，并新增一条 3500 元的还款记录。"

        # Natural-language wording is non-deterministic, so only assert stable business facts here.
        # Exact overdueDays=3 is already asserted through the deterministic REST path above.
        Assert-True ($agentA.answer -match '8,?500|8500') "Agent Case A must contain 8500"
        Assert-True (($agentB.answer -match '3,?500|3500') -and ($agentB.answer -match '逾期|overdue')) "Agent Case B must contain 3500 and state overdue"
        Assert-True ($agentC.answer -match '结清|settled') "Agent Case C must state settlement"
        Assert-True ($notFound.answer -match '未找到|不存在|无法|not found') "unknown loan must not produce a fabricated diagnosis"
        Assert-True ($writeGuard.answer -match '只读|无法|不能') "write request must be rejected"

        Start-Sleep -Milliseconds 300
        $toolLog = Get-Content $outLog -Raw -ErrorAction SilentlyContinue
        Assert-True ($toolLog -match 'getCurrentRepayment') "Tool log must contain getCurrentRepayment"
        Assert-True ($toolLog -match 'getOverdueDiagnosis') "Tool log must contain getOverdueDiagnosis"
        Assert-True ($toolLog -match 'getSettlementStatus') "Tool log must contain getSettlementStatus"

        $stateAfter = (Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/api/loans/LN-10002/status" | ConvertTo-Json -Compress)
        Assert-True ($stateBefore -eq $stateAfter) "write guard must not change LN-10002 state"
    } else {
        Write-Host "[5/5] AI live verification skipped (use -WithAi to enable)."
    }

    Write-Host ""
    Write-Host "PASS: LoanOps Agent reproducible verification completed."
} finally {
    if ($null -ne $process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
}
