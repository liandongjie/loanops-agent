param(
    [ValidateSet("e0", "e1")]
    [string]$Label = "e0",
    [double]$Threshold = 0.60,
    [switch]$ManualReview
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$database = "loanops_policy_rag_eval"
$collection = "loanops_policy_rag_eval"
$mysqlUser = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "loanops" }
$mysqlPassword = if ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { "loanops_dev" }
$rootPassword = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "loanops_root_dev" }

if ($database -ne "loanops_policy_rag_eval") { throw "Unsafe evaluation database name" }

docker compose up -d mysql qdrant | Out-Host
$mysqlContainer = (docker compose ps -q mysql).Trim()
if ([string]::IsNullOrWhiteSpace($mysqlContainer)) { throw "MySQL compose container is unavailable" }

try {
    docker exec -e "MYSQL_PWD=$rootPassword" $mysqlContainer mysql -uroot -e "
        DROP DATABASE IF EXISTS $database;
        CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
        GRANT ALL PRIVILEGES ON $database.* TO '$mysqlUser'@'%';
        FLUSH PRIVILEGES;
    "
    if ($LASTEXITCODE -ne 0) { throw "Could not create isolated evaluation database" }

    $env:MYSQL_DATABASE = $database
    $env:MYSQL_USER = $mysqlUser
    $env:MYSQL_PASSWORD = $mysqlPassword
    $env:POLICY_RAG_EVAL = if ($ManualReview) { "false" } else { "true" }
    $env:POLICY_RAG_MANUAL_REVIEW = if ($ManualReview) { "true" } else { "false" }
    $env:POLICY_RAG_EVAL_LABEL = $Label
    $env:POLICY_EVAL_GIT_HEAD = (git rev-parse HEAD).Trim()
    $env:POLICY_SCORE_THRESHOLD = $Threshold.ToString([System.Globalization.CultureInfo]::InvariantCulture)

    $testClass = if ($ManualReview) { "PolicyRagManualReviewIntegrationTest" } else { "PolicyRagEvaluationIntegrationTest" }
    mvn "-Dtest=$testClass" test
    if ($LASTEXITCODE -ne 0) { throw "Policy RAG evaluation failed: $testClass" }
} finally {
    try {
        Invoke-RestMethod -Method Delete -Uri "http://localhost:6333/collections/$collection" | Out-Null
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -ne 404) { Write-Warning $_.Exception.Message }
    }
    docker exec -e "MYSQL_PWD=$rootPassword" $mysqlContainer mysql -uroot -e "DROP DATABASE IF EXISTS $database;"
}
