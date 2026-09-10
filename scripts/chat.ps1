param(
    [string]$BaseUrl = "http://127.0.0.1:8080"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.Net.Http

$server = $BaseUrl.TrimEnd('/')
$chatUri = "$server/api/agent/chat/stream"
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

        $request = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::Post, $chatUri)
        $request.Headers.Accept.ParseAdd("text/event-stream, application/json")
        $request.Content = [System.Net.Http.StringContent]::new(
            ($payload | ConvertTo-Json -Compress),
            [System.Text.Encoding]::UTF8,
            "application/json")
        $response = $null
        $stream = $null
        $reader = $null
        $printedDelta = $false

        try {
            $response = $client.SendAsync(
                $request,
                [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
            ).GetAwaiter().GetResult()

            if (-not $response.IsSuccessStatusCode) {
                $responseBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
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

            $candidateConversationId = $null
            $streamRequestId = $null
            $sawTerminalEvent = $false
            $eventName = $null
            $dataLines = [System.Collections.Generic.List[string]]::new()

            $handleEvent = {
                if ([string]::IsNullOrWhiteSpace($eventName)) {
                    $dataLines.Clear()
                    return
                }
                $eventData = [string]::Join("`n", $dataLines)
                $dataLines.Clear()
                try {
                    $eventObject = $eventData | ConvertFrom-Json -ErrorAction Stop
                } catch {
                    throw "Invalid SSE event data"
                }

                switch ($eventName) {
                    "start" {
                        if ([string]::IsNullOrWhiteSpace([string]$eventObject.requestId) -or
                                [string]::IsNullOrWhiteSpace([string]$eventObject.conversationId)) {
                            throw "Invalid start event"
                        }
                        $streamRequestId = [string]$eventObject.requestId
                        $candidateConversationId = [string]$eventObject.conversationId
                    }
                    "delta" {
                        if ($null -eq $eventObject.text) {
                            throw "Invalid delta event"
                        }
                        Write-Host -NoNewline ([string]$eventObject.text)
                        $printedDelta = $true
                    }
                    "done" {
                        if ($eventObject.committed -ne $true -or
                                [string]::IsNullOrWhiteSpace($candidateConversationId)) {
                            throw "Invalid done event"
                        }
                        if ($printedDelta) {
                            Write-Host ""
                        }
                        $conversationId = $candidateConversationId
                        $doneRequestId = [string]$eventObject.requestId
                        if ([string]::IsNullOrWhiteSpace($doneRequestId)) {
                            $doneRequestId = $streamRequestId
                        }
                        Write-Host "requestId=$doneRequestId"
                        $sawTerminalEvent = $true
                    }
                    "error" {
                        if ($printedDelta) {
                            Write-Host ""
                        }
                        $code = if ([string]::IsNullOrWhiteSpace([string]$eventObject.code)) {
                            "AGENT_STREAM_FAILED"
                        } else {
                            [string]$eventObject.code
                        }
                        $errorMessage = if ([string]::IsNullOrWhiteSpace([string]$eventObject.message)) {
                            "Agent stream failed"
                        } else {
                            [string]$eventObject.message
                        }
                        Write-Host "ERROR [$code] $errorMessage"
                        Write-Host "Response was not committed."
                        $sawTerminalEvent = $true
                    }
                }
                $eventName = $null
            }

            $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
            $reader = [System.IO.StreamReader]::new(
                $stream,
                [System.Text.Encoding]::UTF8,
                $true,
                1024,
                $true)

            while ($null -ne ($line = $reader.ReadLineAsync().GetAwaiter().GetResult())) {
                if ($line.Length -eq 0) {
                    . $handleEvent
                    if ($sawTerminalEvent) {
                        break
                    }
                    continue
                }
                if ($line.StartsWith("event:")) {
                    $eventName = $line.Substring(6).TrimStart()
                } elseif ($line.StartsWith("data:")) {
                    $dataLines.Add($line.Substring(5).TrimStart())
                }
            }

            if (-not $sawTerminalEvent) {
                if ($printedDelta) {
                    Write-Host ""
                }
                Write-Host "ERROR Stream ended before commit confirmation."
                Write-Host "Server commit status is unknown."
            }
        } catch [System.Net.Http.HttpRequestException] {
            Write-Host "Cannot reach LoanOps Agent at $server. Start the application first."
        } catch [System.Threading.Tasks.TaskCanceledException] {
            Write-Host "Request to LoanOps Agent timed out."
        } catch {
            if ($printedDelta) {
                Write-Host ""
            }
            Write-Host "ERROR Invalid streaming response from LoanOps Agent."
            Write-Host "Server commit status is unknown."
        } finally {
            if ($null -ne $reader) {
                $reader.Dispose()
            }
            if ($null -ne $stream) {
                $stream.Dispose()
            }
            if ($null -ne $response) {
                $response.Dispose()
            }
            $request.Dispose()
        }
    }
} finally {
    $client.Dispose()
}
