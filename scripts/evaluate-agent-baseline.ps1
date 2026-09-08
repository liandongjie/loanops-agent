param(
    [int]$Port = 18091,
    [string]$ManifestPath = "evaluation/agent-baseline-cases.json",
    [string]$ProxyHost = "",
    [int]$ProxyPort = 0,
    [switch]$ValidateOnly,
    [switch]$SkipBuild,
    [switch]$UseExistingApp
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$AllowedCaseTypes = @("single-turn", "read-only-guard", "stateful-two-turn")
$AllowedReadOnlyTools = @("getCurrentRepayment", "getOverdueDiagnosis", "getSettlementStatus")

function Resolve-RepoPath([string]$Path) {
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path (Get-Location) $Path
}

function Read-Manifest([string]$Path) {
    $resolved = Resolve-RepoPath $Path
    if (-not (Test-Path $resolved)) {
        throw "Evaluation manifest not found: $resolved"
    }
    return Get-Content -Encoding utf8 $resolved -Raw | ConvertFrom-Json
}

function Validate-Manifest($Manifest) {
    if ([int]$Manifest.schemaVersion -ne 1) {
        throw "Unsupported evaluation manifest schemaVersion: $($Manifest.schemaVersion)"
    }
    if ([string]::IsNullOrWhiteSpace([string]$Manifest.businessDate)) {
        throw "Manifest businessDate is required"
    }
    if ([string]::IsNullOrWhiteSpace([string]$Manifest.businessZone)) {
        throw "Manifest businessZone is required"
    }
    if ($null -eq $Manifest.cases -or $Manifest.cases.Count -lt 1) {
        throw "Manifest must contain at least one case"
    }

    $seen = @{}
    foreach ($case in $Manifest.cases) {
        if ([string]::IsNullOrWhiteSpace([string]$case.id)) {
            throw "Every evaluation case requires an id"
        }
        if ($seen.ContainsKey([string]$case.id)) {
            throw "Duplicate evaluation case id: $($case.id)"
        }
        $seen[[string]$case.id] = $true
        if ($AllowedCaseTypes -notcontains [string]$case.type) {
            throw "Unsupported case type '$($case.type)' for $($case.id)"
        }
        if ([string]::IsNullOrWhiteSpace([string]$case.category)) {
            throw "Case $($case.id) requires a category"
        }
        switch ([string]$case.type) {
            "single-turn" {
                if ([string]::IsNullOrWhiteSpace([string]$case.message)) { throw "Case $($case.id) requires message" }
                if ($null -eq $case.expected -or [string]::IsNullOrWhiteSpace([string]$case.expected.toolName) -or [string]::IsNullOrWhiteSpace([string]$case.expected.loanNo)) {
                    throw "Case $($case.id) requires expected.toolName and expected.loanNo"
                }
            }
            "read-only-guard" {
                if ([string]::IsNullOrWhiteSpace([string]$case.message) -or [string]::IsNullOrWhiteSpace([string]$case.loanNo)) {
                    throw "Case $($case.id) requires message and loanNo"
                }
            }
            "stateful-two-turn" {
                if ([string]::IsNullOrWhiteSpace([string]$case.turn1) -or [string]::IsNullOrWhiteSpace([string]$case.turn2)) {
                    throw "Case $($case.id) requires turn1 and turn2"
                }
                if ($null -eq $case.expected -or [string]::IsNullOrWhiteSpace([string]$case.expected.turn1ToolName) -or [string]::IsNullOrWhiteSpace([string]$case.expected.turn2ToolName) -or [string]::IsNullOrWhiteSpace([string]$case.expected.loanNo)) {
                    throw "Case $($case.id) requires expected turn Tool names and loanNo"
                }
            }
        }
    }
}
function Assert-Java21 {
    $javaVersion = (& java -version 2>&1 | Out-String)
    if ($javaVersion -notmatch 'version "21[\.]') {
        throw "JDK 21 is required. Current java -version output:
$javaVersion"
    }
}

function Wait-ForService([string]$Uri, $Process, [int]$MaxAttempts = 90) {
    for ($i = 0; $i -lt $MaxAttempts; $i++) {
        if ($null -ne $Process -and $Process.HasExited) {
            throw "Application exited before becoming ready (exitCode=$($Process.ExitCode))"
        }
        try {
            Invoke-RestMethod -Method Get -Uri $Uri -TimeoutSec 2 | Out-Null
            return
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    throw "Application did not become ready: $Uri"
}

function Invoke-Agent([string]$Message, [string]$ConversationId = "") {
    $payload = @{ message = $Message }
    if (-not [string]::IsNullOrWhiteSpace($ConversationId)) {
        $payload.conversationId = $ConversationId
    }
    $json = $payload | ConvertTo-Json -Compress
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds(90)
    $content = New-Object System.Net.Http.StringContent($json, [Text.Encoding]::UTF8, "application/json")
    try {
        $response = $client.PostAsync("http://127.0.0.1:$Port/api/agent/chat", $content).GetAwaiter().GetResult()
        $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $responseJson = [Text.Encoding]::UTF8.GetString($bytes)
        if (-not $response.IsSuccessStatusCode) {
            throw "Agent request failed with HTTP $([int]$response.StatusCode): $responseJson"
        }
        return $responseJson | ConvertFrom-Json
    } finally {
        $content.Dispose()
        $client.Dispose()
    }
}

function Get-AgentAudit([string]$RequestId) {
    return Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/api/agent/audits/$RequestId" -TimeoutSec 10
}

function Get-LoanState([string]$LoanNo) {
    return Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$Port/api/loans/$LoanNo/status" -TimeoutSec 10
}

function New-Checks {
    Write-Output -NoEnumerate ([System.Collections.Generic.List[object]]::new())
}

function Add-Check($Checks, [string]$Name, [bool]$Passed, [string]$Detail) {
    [void]$Checks.Add([pscustomobject]@{
        name = $Name
        passed = $Passed
        detail = $Detail
    })
}

function Test-Regex([string]$Value, [string]$Pattern) {
    if ([string]::IsNullOrWhiteSpace($Pattern)) { return $true }
    if ($null -eq $Value) { return $false }
    return $Value -match $Pattern
}

function Find-Tool($Audit, [string]$ToolName, [string]$LoanNo) {
    $matches = @($Audit.tools | Where-Object {
        [string]$_.toolName -eq $ToolName -and
        [string]$_.loanNo -eq $LoanNo -and
        [string]$_.status -eq "SUCCESS"
    })
    Write-Output -NoEnumerate $matches
}

function Finish-Case([string]$Id, [string]$Category, $Checks, [hashtable]$Evidence) {
    $passed = @($Checks | Where-Object { -not $_.passed }).Count -eq 0
    $caseStatus = if ($passed) { "PASS" } else { "FAIL" }
    $result = @{
        id = $Id
        category = $Category
        status = $caseStatus
        checks = $Checks.ToArray()
    }
    foreach ($key in $Evidence.Keys) {
        $result[$key] = $Evidence[$key]
    }
    return [pscustomobject]$result
}

function Run-SingleTurnCase($Case) {
    $checks = New-Checks
    try {
        $response = Invoke-Agent ([string]$Case.message)
        $audit = Get-AgentAudit ([string]$response.requestId)
        Add-Check $checks "audit-success" ([string]$audit.status -eq "SUCCESS") "audit status=$($audit.status)"
        Add-Check $checks "conversation-correlation" ([string]$audit.conversationId -eq [string]$response.conversationId) "response=$($response.conversationId) audit=$($audit.conversationId)"
        $tools = Find-Tool $audit ([string]$Case.expected.toolName) ([string]$Case.expected.loanNo)
        Add-Check $checks "expected-tool" ($tools.Count -ge 1) "expected=$($Case.expected.toolName)/$($Case.expected.loanNo)"
        Add-Check $checks "required-fact-in-answer" (Test-Regex ([string]$response.answer) ([string]$Case.expected.answerRegex)) "pattern=$($Case.expected.answerRegex)"
        if ($Case.expected.PSObject.Properties.Name -contains "secondaryAnswerRegex") {
            Add-Check $checks "secondary-fact-in-answer" (Test-Regex ([string]$response.answer) ([string]$Case.expected.secondaryAnswerRegex)) "pattern=$($Case.expected.secondaryAnswerRegex)"
        }
        return Finish-Case ([string]$Case.id) ([string]$Case.category) $checks @{
            requestId = [string]$response.requestId
            conversationId = [string]$response.conversationId
            provider = [string]$audit.provider
            model = [string]$audit.model
            durationMs = [long]$audit.durationMs
            systemPromptHash = [string]$audit.systemPromptHash
            observedTools = @($audit.tools | ForEach-Object { "$($_.toolName):$($_.loanNo):$($_.status)" })
        }
    } catch {
        Add-Check $checks "execution" $false $_.Exception.Message
        return Finish-Case ([string]$Case.id) ([string]$Case.category) $checks @{}
    }
}

function Run-ReadOnlyGuardCase($Case) {
    $checks = New-Checks
    try {
        $before = Get-LoanState ([string]$Case.loanNo)
        $beforeJson = $before | ConvertTo-Json -Depth 20 -Compress
        $response = Invoke-Agent ([string]$Case.message)
        $audit = Get-AgentAudit ([string]$response.requestId)
        $after = Get-LoanState ([string]$Case.loanNo)
        $afterJson = $after | ConvertTo-Json -Depth 20 -Compress

        Add-Check $checks "audit-success" ([string]$audit.status -eq "SUCCESS") "audit status=$($audit.status)"
        Add-Check $checks "refusal-language" (Test-Regex ([string]$response.answer) ([string]$Case.expected.answerRegex)) "pattern=$($Case.expected.answerRegex)"
        Add-Check $checks "loan-state-unchanged" ($beforeJson -eq $afterJson) "deterministic loan state must be identical before and after"
        $auditTools = @($audit.tools)
        $unexpected = @($auditTools | Where-Object { $AllowedReadOnlyTools -notcontains [string]$_.toolName })
        $observedToolNames = @($auditTools | ForEach-Object { [string]$_.toolName })
        Add-Check $checks "read-only-tools-only" ($unexpected.Count -eq 0) "observed tools=$($observedToolNames -join ',')"

        return Finish-Case ([string]$Case.id) ([string]$Case.category) $checks @{
            requestId = [string]$response.requestId
            conversationId = [string]$response.conversationId
            provider = [string]$audit.provider
            model = [string]$audit.model
            durationMs = [long]$audit.durationMs
            systemPromptHash = [string]$audit.systemPromptHash
            observedTools = @($audit.tools | ForEach-Object { "$($_.toolName):$($_.loanNo):$($_.status)" })
        }
    } catch {
        Add-Check $checks "execution" $false $_.Exception.Message
        return Finish-Case ([string]$Case.id) ([string]$Case.category) $checks @{}
    }
}

function Run-StatefulCase($Case) {
    $checks = New-Checks
    try {
        $turn1 = Invoke-Agent ([string]$Case.turn1)
        $audit1 = Get-AgentAudit ([string]$turn1.requestId)
        $turn2 = Invoke-Agent ([string]$Case.turn2) ([string]$turn1.conversationId)
        $audit2 = Get-AgentAudit ([string]$turn2.requestId)

        Add-Check $checks "same-conversation" ([string]$turn1.conversationId -eq [string]$turn2.conversationId) "turn1=$($turn1.conversationId) turn2=$($turn2.conversationId)"
        Add-Check $checks "new-request-id" ([string]$turn1.requestId -ne [string]$turn2.requestId) "turn1=$($turn1.requestId) turn2=$($turn2.requestId)"
        Add-Check $checks "turn1-audit-success" ([string]$audit1.status -eq "SUCCESS") "status=$($audit1.status)"
        Add-Check $checks "turn2-audit-success" ([string]$audit2.status -eq "SUCCESS") "status=$($audit2.status)"
        Add-Check $checks "turn1-tool" ((Find-Tool $audit1 ([string]$Case.expected.turn1ToolName) ([string]$Case.expected.loanNo)).Count -ge 1) "expected=$($Case.expected.turn1ToolName)/$($Case.expected.loanNo)"
        Add-Check $checks "turn2-fresh-tool" ((Find-Tool $audit2 ([string]$Case.expected.turn2ToolName) ([string]$Case.expected.loanNo)).Count -ge 1) "expected=$($Case.expected.turn2ToolName)/$($Case.expected.loanNo)"
        Add-Check $checks "turn2-history-from" ([int]$audit2.historyFromSequence -eq [int]$Case.expected.historyFromSequence) "actual=$($audit2.historyFromSequence)"
        Add-Check $checks "turn2-history-to" ([int]$audit2.historyToSequence -eq [int]$Case.expected.historyToSequence) "actual=$($audit2.historyToSequence)"
        Add-Check $checks "turn2-current-fact" (Test-Regex ([string]$turn2.answer) ([string]$Case.expected.turn2AnswerRegex)) "pattern=$($Case.expected.turn2AnswerRegex)"

        return Finish-Case ([string]$Case.id) ([string]$Case.category) $checks @{
            requestId = [string]$turn2.requestId
            turn1RequestId = [string]$turn1.requestId
            conversationId = [string]$turn2.conversationId
            provider = [string]$audit2.provider
            model = [string]$audit2.model
            durationMs = [long]$audit2.durationMs
            systemPromptHash = [string]$audit2.systemPromptHash
            historyHash = [string]$audit2.historyHash
            observedTools = @($audit2.tools | ForEach-Object { "$($_.toolName):$($_.loanNo):$($_.status)" })
        }
    } catch {
        Add-Check $checks "execution" $false $_.Exception.Message
        return Finish-Case ([string]$Case.id) ([string]$Case.category) $checks @{}
    }
}

function Write-Reports($Report, [string]$OutputDirectory, [string]$Stamp) {
    New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
    $json = $Report | ConvertTo-Json -Depth 30
    $jsonPath = Join-Path $OutputDirectory "agent-baseline-$Stamp.json"
    $latestJson = Join-Path $OutputDirectory "agent-baseline-latest.json"
    $json | Set-Content -Encoding utf8 $jsonPath
    $json | Set-Content -Encoding utf8 $latestJson

    $md = [System.Collections.Generic.List[string]]::new()
    [void]$md.Add("# LoanOps Agent Evaluation Baseline")
    [void]$md.Add("")
    [void]$md.Add("- Outcome: **$($Report.outcome)**")
    [void]$md.Add("- Repository HEAD: ``$($Report.repositoryHead)``")
    [void]$md.Add("- Provider / model: ``$($Report.provider)`` / ``$($Report.model)``")
    [void]$md.Add("- Business date: ``$($Report.businessDate)``")
    [void]$md.Add("- System prompt hash: ``$($Report.systemPromptHash)``")
    [void]$md.Add("- Cases: **$($Report.passed)/$($Report.caseCount) PASS**, $($Report.failed) FAIL")
    [void]$md.Add("")
    [void]$md.Add("| Case | Category | Status | Duration ms | Failed checks |")
    [void]$md.Add("|---|---|---:|---:|---|")
    foreach ($case in @($Report.cases)) {
        $failedChecks = @($case.checks | Where-Object { -not $_.passed } | ForEach-Object { $_.name }) -join ", "
        $duration = if ($case.PSObject.Properties.Name -contains "durationMs") { [string]$case.durationMs } else { "" }
        [void]$md.Add("| $($case.id) | $($case.category) | $($case.status) | $duration | $failedChecks |")
    }
    [void]$md.Add("")
    [void]$md.Add("> This report is an Agent regression baseline. It is not a source of financial truth; deterministic Java domain services remain authoritative.")

    $mdPath = Join-Path $OutputDirectory "agent-baseline-$Stamp.md"
    $latestMd = Join-Path $OutputDirectory "agent-baseline-latest.md"
    $md | Set-Content -Encoding utf8 $mdPath
    $md | Set-Content -Encoding utf8 $latestMd
    return [pscustomobject]@{ json = $jsonPath; markdown = $mdPath }
}

$manifest = Read-Manifest $ManifestPath
Validate-Manifest $manifest

if ($ValidateOnly) {
    Write-Host "PASS: evaluation manifest is valid ($($manifest.cases.Count) cases)."
    exit 0
}

$stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd-HHmmss")
$outputDirectory = Join-Path (Get-Location) "target/evaluation"
$head = (git rev-parse HEAD).Trim()
$provider = "deepseek"
$model = if ($env:LOANOPS_CHAT_MODEL) { $env:LOANOPS_CHAT_MODEL } else { "deepseek-chat" }

if (-not $UseExistingApp -and [string]::IsNullOrWhiteSpace($env:DEEPSEEK_API_KEY)) {
    $blocked = [pscustomobject]@{
        schemaVersion = 1
        generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        outcome = "ENV_BLOCKED"
        reason = "DEEPSEEK_API_KEY is not available in this process environment"
        repositoryHead = $head
        provider = $provider
        model = $model
        businessDate = [string]$manifest.businessDate
        businessZone = [string]$manifest.businessZone
        systemPromptHash = $null
        caseCount = $manifest.cases.Count
        passed = 0
        failed = 0
        cases = @()
    }
    $paths = Write-Reports $blocked $outputDirectory $stamp
    Write-Host "ENV_BLOCKED: DEEPSEEK_API_KEY is not available. Reports: $($paths.json), $($paths.markdown)"
    exit 2
}

$process = $null
try {
if (-not $UseExistingApp) {
Assert-Java21

if (-not $SkipBuild) {
    Write-Host "[1/4] Packaging application (tests remain a separate verification gate)..."
    & mvn package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "mvn package -DskipTests failed" }
}

$jar = Get-ChildItem (Join-Path (Get-Location) "target") -Filter "loanops-agent-*.jar" -File |
    Where-Object { $_.Name -notmatch '^original-' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if ($null -eq $jar) { throw "Packaged application jar was not found under target/" }

New-Item -ItemType Directory -Force $outputDirectory | Out-Null
$outLog = Join-Path $outputDirectory "agent-baseline-app.out.log"
$errLog = Join-Path $outputDirectory "agent-baseline-app.err.log"
Remove-Item $outLog, $errLog -ErrorAction SilentlyContinue

$javaArgs = @()
if (-not [string]::IsNullOrWhiteSpace($ProxyHost)) {
    if ($ProxyPort -le 0) { throw "ProxyPort must be greater than zero when ProxyHost is set" }
    $javaArgs += "-Dhttp.proxyHost=$ProxyHost"
    $javaArgs += "-Dhttp.proxyPort=$ProxyPort"
    $javaArgs += "-Dhttps.proxyHost=$ProxyHost"
    $javaArgs += "-Dhttps.proxyPort=$ProxyPort"
}
$javaArgs += "-jar"
$javaArgs += $jar.FullName
$javaArgs += "--server.port=$Port"
$javaArgs += "--spring.profiles.active=ai"
$javaArgs += "--loanops.business-date=$($manifest.businessDate)"
$javaArgs += "--loanops.business-zone=$($manifest.businessZone)"

Write-Host "[2/4] Starting temporary AI application on port $Port using H2 + fixed demo data..."
$process = Start-Process -FilePath "java" -ArgumentList $javaArgs -PassThru -RedirectStandardOutput $outLog -RedirectStandardError $errLog
} else {
    Write-Host "[1/4] Reusing existing Agent application on port $Port; no API key is read by this runner."
}

    Wait-ForService "http://127.0.0.1:$Port/api/loans/LN-10002/status" $process
    Write-Host "[3/4] Running $($manifest.cases.Count) live Agent baseline cases..."

    $results = [System.Collections.Generic.List[object]]::new()
    foreach ($case in $manifest.cases) {
        Write-Host "  - $($case.id)"
        $result = switch ([string]$case.type) {
            "single-turn" { Run-SingleTurnCase $case }
            "read-only-guard" { Run-ReadOnlyGuardCase $case }
            "stateful-two-turn" { Run-StatefulCase $case }
            default { throw "Unsupported case type: $($case.type)" }
        }
        [void]$results.Add($result)
        if ($result.PSObject.Properties.Name -contains "provider") {
            $provider = [string]$result.provider
            $model = [string]$result.model
        }
    }

    $passed = @($results | Where-Object { $_.status -eq "PASS" }).Count
    $failed = $results.Count - $passed
    $firstPromptHash = @($results | Where-Object { $_.PSObject.Properties.Name -contains "systemPromptHash" -and -not [string]::IsNullOrWhiteSpace([string]$_.systemPromptHash) } | Select-Object -First 1)
    $promptHash = if ($firstPromptHash.Count -gt 0) { [string]$firstPromptHash[0].systemPromptHash } else { $null }

    $overallOutcome = if ($failed -eq 0) { "PASS" } else { "FAIL" }
    $report = [pscustomobject]@{
        schemaVersion = 1
        generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        outcome = $overallOutcome
        repositoryHead = $head
        provider = $provider
        model = $model
        businessDate = [string]$manifest.businessDate
        businessZone = [string]$manifest.businessZone
        systemPromptHash = $promptHash
        caseCount = $results.Count
        passed = $passed
        failed = $failed
        cases = $results.ToArray()
    }

    $paths = Write-Reports $report $outputDirectory $stamp
    Write-Host "[4/4] Evaluation complete: $passed/$($results.Count) PASS."
    Write-Host "JSON: $($paths.json)"
    Write-Host "Markdown: $($paths.markdown)"
    if ($failed -ne 0) { exit 1 }
} catch {
    $errorReport = [pscustomobject]@{
        schemaVersion = 1
        generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        outcome = "ERROR"
        reason = $_.Exception.Message
        repositoryHead = $head
        provider = $provider
        model = $model
        businessDate = [string]$manifest.businessDate
        businessZone = [string]$manifest.businessZone
        systemPromptHash = $null
        caseCount = $manifest.cases.Count
        passed = 0
        failed = 0
        cases = @()
    }
    $paths = Write-Reports $errorReport $outputDirectory $stamp
    Write-Host "ERROR: $($errorReport.reason). Reports: $($paths.json), $($paths.markdown)"
    exit 1
} finally {
    if ($null -ne $process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        Wait-Process -Id $process.Id -ErrorAction SilentlyContinue
    }
}
