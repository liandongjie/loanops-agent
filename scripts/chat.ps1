param(
    [string]$BaseUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Net.Http

$server = $BaseUrl.TrimEnd('/')
$chatUri = "$server/api/agent/chat"
$conversationId = $null
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(120)

Write-Host "LoanOps Terminal Chat"
Write-Host "Server: $server"
Write-Host "Commands: /new  /id  /exit"

try {
    while ($true) {
        Write-Host -NoNewline "You: "
        $inputLine = [Console]::ReadLine()
        if ($null -eq $inputLine) {
            break
        }
        if ([string]::IsNullOrWhiteSpace($inputLine)) {
            continue
        }

        $message = $inputLine.Trim()
        if ($message -eq "/new") {
            $conversationId = $null
            Write-Host "New conversation started."
            continue
        }
        if ($message -eq "/id") {
            $displayId = if ($null -eq $conversationId) { "<none>" } else { $conversationId }
            Write-Host "conversationId=$displayId"
            continue
        }
        if ($message -eq "/exit") {
            break
        }

        if ($message.StartsWith('/')) {
            Write-Host "Unknown command"
            continue
        }

        $payload = @{ message = $message }
        if ($null -ne $conversationId) {
            $payload.conversationId = $conversationId
        }

        $content = [System.Net.Http.StringContent]::new(
            ($payload | ConvertTo-Json -Compress),
            [System.Text.Encoding]::UTF8,
            "application/json")
        $response = $null

        try {
            $response = $client.PostAsync($chatUri, $content).GetAwaiter().GetResult()
            $responseBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()

            if (-not $response.IsSuccessStatusCode) {
                try {
                    $apiError = $responseBody | ConvertFrom-Json -ErrorAction Stop
                    if ([string]::IsNullOrWhiteSpace([string]$apiError.code) -or
                            [string]::IsNullOrWhiteSpace([string]$apiError.message)) {
                        throw "Invalid API error"
                    }
                    Write-Host "ERROR [$($apiError.code)] $($apiError.message)"
                } catch {
                    $shortResponse = ($responseBody -replace '\s+', ' ').Trim()
                    if ($shortResponse.Length -gt 300) {
                        $shortResponse = $shortResponse.Substring(0, 300) + "..."
                    }
                    Write-Host "HTTP $([int]$response.StatusCode): $shortResponse"
                }
                continue
            }

            try {
                $result = $responseBody | ConvertFrom-Json -ErrorAction Stop
                if ([string]::IsNullOrWhiteSpace([string]$result.conversationId) -or
                        [string]::IsNullOrWhiteSpace([string]$result.requestId) -or
                        $null -eq $result.answer) {
                    throw "Required response fields are missing"
                }
            } catch {
                Write-Host "ERROR Invalid response from LoanOps Agent"
                continue
            }

            Write-Host ""
            Write-Host ([string]$result.answer)
            Write-Host "requestId=$($result.requestId)"
            $conversationId = [string]$result.conversationId
        } catch [System.Net.Http.HttpRequestException] {
            Write-Host "Cannot reach LoanOps Agent at $server. Start the application first."
        } catch [System.Threading.Tasks.TaskCanceledException] {
            Write-Host "Request to LoanOps Agent timed out."
        } catch {
            Write-Host "ERROR $($_.Exception.Message)"
        } finally {
            $content.Dispose()
            if ($null -ne $response) {
                $response.Dispose()
            }
        }
    }
} finally {
    $client.Dispose()
}
