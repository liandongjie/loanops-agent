param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("deepseek", "ollama", "glm")]
    [string]$Provider,
    [switch]$WithPolicy
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$identity = switch ($Provider) {
    "deepseek" { [pscustomobject]@{ adapter = "deepseek"; model = "deepseek-chat" } }
    "ollama" { [pscustomobject]@{ adapter = "ollama"; model = "qwen3:4b" } }
    "glm" { [pscustomobject]@{ adapter = "zhipuai"; model = "glm-5.2" } }
}
$profiles = if ($WithPolicy) { "mysql,policy,ai" } else { "ai" }
$policyEnabled = if ($WithPolicy) { "true" } else { "false" }
$environmentNames = @(
    "SPRING_PROFILES_ACTIVE",
    "LOANOPS_CHAT_PROVIDER",
    "LOANOPS_CHAT_ADAPTER",
    "LOANOPS_CHAT_MODEL",
    "POLICY_RETRIEVAL_ENABLED"
)
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
        throw "LoanOps Agent requires Java 21.`nMaven could not start. Configure JAVA_HOME to a JDK 21 installation and retry."
    }
    if ($mavenVersion -notmatch '(?m)^Java version:\s+(\d+)') {
        throw "LoanOps Agent requires Java 21.`nMaven's Java version could not be determined."
    }
    $javaMajor = [int]$Matches[1]
    if ($javaMajor -ne 21) {
        throw "LoanOps Agent requires Java 21.`nMaven is currently using Java $javaMajor.`nConfigure JAVA_HOME to a JDK 21 installation and retry."
    }

    $env:SPRING_PROFILES_ACTIVE = $profiles
    $env:LOANOPS_CHAT_PROVIDER = $Provider
    $env:LOANOPS_CHAT_ADAPTER = $identity.adapter
    $env:LOANOPS_CHAT_MODEL = $identity.model
    $env:POLICY_RETRIEVAL_ENABLED = $policyEnabled

    Write-Host "LoanOps Agent Local Launcher"
    Write-Host "Provider: $Provider"
    Write-Host "Adapter: $($identity.adapter)"
    Write-Host "Model: $($identity.model)"
    Write-Host "Profiles: $profiles"
    Write-Host "Policy RAG: $(if ($WithPolicy) { 'enabled' } else { 'disabled' })"
    Write-Host "Server: Spring Boot default"

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
    exit $mavenExitCode
}
