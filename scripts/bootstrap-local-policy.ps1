$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$environmentNames = @("SPRING_PROFILES_ACTIVE", "SPRING_MAIN_WEB_APPLICATION_TYPE")
$previousEnvironment = @{}
foreach ($name in $environmentNames) {
    $previousEnvironment[$name] = [pscustomobject]@{
        exists = Test-Path "Env:$name"
        value = [Environment]::GetEnvironmentVariable($name, "Process")
    }
}

$originalLocation = Get-Location
$repoRoot = Split-Path -Parent $PSScriptRoot
$mavenExitCode = 0

try {
    Set-Location $repoRoot
    $mavenVersion = (& mvn -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        throw "Local Policy Bootstrap requires Java 21.`nMaven could not start. Configure JAVA_HOME and retry."
    }
    if ($mavenVersion -notmatch '(?m)^Java version:\s+(\d+)' -or [int]$Matches[1] -ne 21) {
        throw "Local Policy Bootstrap requires Maven to use Java 21. Configure JAVA_HOME and retry."
    }

    $env:SPRING_PROFILES_ACTIVE = "mysql,policy,local-policy-bootstrap"
    $env:SPRING_MAIN_WEB_APPLICATION_TYPE = "none"

    Write-Host "LoanOps Local Policy Bootstrap"
    Write-Host "Corpus: synthetic demo policy (not a real bank or regulatory policy)"
    Write-Host "Canonical store: configured local MySQL"
    Write-Host "Derived index: configured LoanOps Policy Qdrant collection"

    & mvn spring-boot:run
    $mavenExitCode = $LASTEXITCODE
} finally {
    foreach ($name in $environmentNames) {
        $previous = $previousEnvironment[$name]
        if ($previous.exists) {
            [Environment]::SetEnvironmentVariable($name, $previous.value, "Process")
        } else {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        }
    }
    Set-Location $originalLocation
}

if ($mavenExitCode -ne 0) {
    Write-Error "Local Policy Bootstrap failed. Review the application error for the MySQL, corpus, Ollama/bge-m3, or Qdrant layer."
    exit $mavenExitCode
}
