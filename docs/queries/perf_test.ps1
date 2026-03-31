# /api/posts 성능 테스트 스크립트
# 사용법: powershell -ExecutionPolicy Bypass -File perf_test.ps1

param(
    [string]$Url = "http://localhost:8080/api/posts",
    [int]$Requests = 30,
    [int]$Concurrent = 5,
    [string]$Label = "테스트"
)

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  $Label" -ForegroundColor Cyan
Write-Host "  URL: $Url" -ForegroundColor Cyan
Write-Host "  요청 수: $Requests  동시: $Concurrent" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$times = [System.Collections.Concurrent.ConcurrentBag[double]]::new()
$errors = [System.Collections.Concurrent.ConcurrentBag[string]]::new()

# 워밍업 1회 (JVM warm-up)
Write-Host "`n[워밍업] 첫 요청 JVM 워밍업 중..." -ForegroundColor Yellow
try {
    $null = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 30
} catch {}

Write-Host "[시작] $Requests 건 요청 ($Concurrent 동시)...`n"

$batches = [Math]::Ceiling($Requests / $Concurrent)

for ($b = 0; $b -lt $batches; $b++) {
    $batchStart = $b * $Concurrent
    $batchEnd   = [Math]::Min($batchStart + $Concurrent, $Requests)
    $batchCount = $batchEnd - $batchStart

    $jobs = @()
    for ($i = 0; $i -lt $batchCount; $i++) {
        $reqNum = $batchStart + $i + 1
        $jobs += Start-Job -ScriptBlock {
            param($u, $n)
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                $null = Invoke-WebRequest -Uri $u -UseBasicParsing -TimeoutSec 30
                $sw.Stop()
                return @{ ok = $true; ms = $sw.Elapsed.TotalMilliseconds; n = $n }
            } catch {
                $sw.Stop()
                return @{ ok = $false; ms = $sw.Elapsed.TotalMilliseconds; n = $n; err = $_.Exception.Message }
            }
        } -ArgumentList $Url, $reqNum
    }

    foreach ($job in $jobs) {
        $result = Receive-Job -Job $job -Wait
        Remove-Job -Job $job -Force
        if ($result.ok) {
            $times.Add($result.ms)
            Write-Host ("  #{0,3}  {1,8:F0} ms  OK" -f $result.n, $result.ms) -ForegroundColor Green
        } else {
            $errors.Add($result.err)
            Write-Host ("  #{0,3}  {1,8:F0} ms  ERROR: {2}" -f $result.n, $result.ms, $result.err) -ForegroundColor Red
        }
    }
}

# 결과 집계
$allTimes = $times.ToArray() | Sort-Object
$count    = $allTimes.Count

if ($count -gt 0) {
    $avg  = ($allTimes | Measure-Object -Average).Average
    $min  = $allTimes[0]
    $max  = $allTimes[$count - 1]
    $p50  = $allTimes[[Math]::Floor($count * 0.50)]
    $p95  = $allTimes[[Math]::Floor($count * 0.95)]
    $p99  = $allTimes[[Math]::Min([Math]::Floor($count * 0.99), $count - 1)]

    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  결과: $Label" -ForegroundColor Cyan
    Write-Host "----------------------------------------" -ForegroundColor Cyan
    Write-Host ("  성공: {0}건  실패: {1}건" -f $count, $errors.Count)
    Write-Host ("  평균:  {0,8:F0} ms" -f $avg)
    Write-Host ("  최소:  {0,8:F0} ms" -f $min)
    Write-Host ("  최대:  {0,8:F0} ms" -f $max)
    Write-Host ("  P50:   {0,8:F0} ms" -f $p50)
    Write-Host ("  P95:   {0,8:F0} ms" -f $p95)
    Write-Host ("  P99:   {0,8:F0} ms" -f $p99)
    Write-Host "========================================" -ForegroundColor Cyan
} else {
    Write-Host "`n모든 요청 실패" -ForegroundColor Red
}
