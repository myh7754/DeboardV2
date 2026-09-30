<#
시드는 compose가 알아서 한다. `docker compose up -d` 만으로 1000만 건까지 채워진다.
이 스크립트는 편의용 — 시더가 끝나길 기다렸다가 k6를 돌린다.

  .\run.ps1                     # up -d + 시드 완료 대기 + keyset 999페이지 측정
  .\run.ps1 -Mode offset        # 대조군
  .\run.ps1 -Mode keyset -Page 9999
  .\run.ps1 -Status             # 지금 상태만 보고 종료
  .\run.ps1 -SeedOnly           # 시드 완료까지만 기다리고 종료
  .\run.ps1 -Rebuild            # 코드 고친 뒤 app.jar 다시 굽기 (JDK 21 필요)
#>
param(
    [ValidateSet('keyset','offset')]
    [string]$Mode = 'keyset',
    [int]$Page = 999,
    [int]$Vus = 500,
    [long]$TargetRows = 10000000,
    [switch]$SeedOnly,
    [switch]$Status,
    [switch]$Rebuild
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function ScalarSql([string]$q) {
    $out = docker compose exec -T mysql mysql -uroot -proot --default-character-set=utf8mb4 -N -B deboard -e $q 2>$null
    if ($LASTEXITCODE -ne 0) { return -1 }
    $line = ($out | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1)
    if ($null -eq $line) { return -1 }
    return [int64]$line
}

function Show-Status {
    $c = ScalarSql "SELECT COUNT(*) FROM post"
    if ($c -lt 0) {
        Write-Host "    post 테이블 없음 (DB가 안 떴거나 백엔드가 스키마 생성 전)" -ForegroundColor Yellow
        return $c
    }
    $d = ScalarSql "SELECT COUNT(DISTINCT created_at) FROM post"
    Write-Host "    post = $c 건, created_at 고유값 = $d"
    if ($c -gt 0 -and $d -eq 1) {
        Write-Host "    !! created_at이 전부 동일 — seed.sql 펼치기가 아직 안 돌았습니다" -ForegroundColor Yellow
    }
    return $c
}

if ($Rebuild) {
    Write-Host "==> gradle bootJar" -ForegroundColor Cyan
    Push-Location ..
    try {
        ./gradlew.bat bootJar -x test --console=plain
        if ($LASTEXITCODE -ne 0) { throw "gradle 빌드 실패" }
    } finally { Pop-Location }
    Copy-Item ../build/libs/DeboardV2-*.jar app.jar -Force
    Write-Host "    app.jar 갱신: $((Get-Item app.jar).Length) bytes"
}

# app.jar가 없는 채로 up 하면 Docker가 app.jar라는 '디렉터리'를 만들고 백엔드가 조용히 죽는다.
if (-not (Test-Path -PathType Leaf 'app.jar')) {
    throw "bench/app.jar 가 없습니다. 저장소 루트에서 ./gradlew.bat bootJar -x test 로 만든 뒤 build/libs/*.jar 를 bench/app.jar 로 복사하세요."
}

New-Item -ItemType Directory -Force -Path k6/out | Out-Null

Write-Host "==> docker compose up -d (mysql / redis / backend / seeder)" -ForegroundColor Cyan
docker compose up -d
if ($LASTEXITCODE -ne 0) { throw "docker compose up 실패" }

Write-Host "==> 현재 상태" -ForegroundColor Cyan
$count = Show-Status
if ($Status) { exit 0 }

# 시더가 목표치를 채울 때까지 대기. 실제 진행 로그는 seeder 컨테이너가 찍는다.
if ($count -lt $TargetRows) {
    Write-Host "==> 시더 대기 ($TargetRows 건까지). 진행: docker compose logs -f seeder" -ForegroundColor Cyan
    $stall = 0
    $last = [int64]::MinValue
    while ($true) {
        $c = ScalarSql "SELECT COUNT(*) FROM post"
        if ($c -ge $TargetRows) { Write-Host "    post $c — 시드 완료" -ForegroundColor Green; break }

        # 시더가 죽었으면 무한정 기다리지 않는다
        $running = (docker compose ps -q seeder 2>$null)
        if (-not $running) {
            throw "seeder 컨테이너가 없습니다. docker compose logs seeder 를 확인하세요. (현재 $c 건)"
        }

        Write-Host "    post $c / $TargetRows"
        if ($c -eq $last) { $stall++ } else { $stall = 0 }
        $last = $c
        if ($stall -ge 120) { throw "20분간 진행이 없습니다. docker compose logs seeder 를 확인하세요." }
        Start-Sleep -Seconds 10
    }
    $count = Show-Status

    # 증식 직후엔 버퍼풀이 쓰기 캐시로 더럽혀져 있다. 읽기 측정 전에 한 번 정리.
    Write-Host "==> 재기동 (버퍼풀 워밍 상태 초기화)" -ForegroundColor Cyan
    docker compose restart mysql backend
    Start-Sleep -Seconds 30
}

if ($SeedOnly) { Write-Host "시드 완료. 종료합니다." -ForegroundColor Green; exit 0 }

$tag = "$Mode-page$Page-vus$Vus"
$name = "k6-$tag-$(Get-Date -Format 'MMdd-HHmm')"

Write-Host "==> k6 실행 — MODE=$Mode PAGE=$Page VUS=$Vus (약 10분)" -ForegroundColor Cyan
Write-Host "    컨테이너 이름: $name"
# --rm 을 쓰지 않는다. 끝난 뒤에도 Docker Desktop에서 로그를 볼 수 있어야 한다.
# 지울 때: docker container prune
docker compose run --name $name -e MODE=$Mode -e PAGE=$Page -e VUS=$Vus k6 run /scripts/load-test.js

Write-Host ""
Write-Host "결과 ─────────────────────────────" -ForegroundColor Green
Write-Host "  k6/out/summary-$tag.txt     요약 (표에 옮길 값)"
Write-Host "  k6/out/summary-$tag.json    전체 지표"
Write-Host "  k6/out/report.html          그래프"
Write-Host "  docker logs $name           콘솔 로그 다시 보기"
Write-Host ""
Write-Host "다음 측정: .\run.ps1 -Mode offset -Page $Page" -ForegroundColor Green
