param(
    [switch]$SkipSource,
    [string]$SdkPath = (Join-Path $PSScriptRoot '../.android-sdk')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$metadata = Get-Content (Join-Path $projectRoot 'app/build/outputs/apk/debug/output-metadata.json') -Raw | ConvertFrom-Json
$version = $metadata.elements[0].versionName
if ($version -notmatch '^[0-9A-Za-z.-]+$') { throw 'Invalid artifact version' }
$delivery = Join-Path $projectRoot "dist/$version"
$prefix = "TechnicsPods-$version"
New-Item -ItemType Directory -Path $delivery -Force | Out-Null
$signer = Join-Path $SdkPath 'build-tools/36.1.0/apksigner.bat'
$testKey = Join-Path $projectRoot '.android-user-home/debug.keystore'
if (!(Test-Path -LiteralPath $signer) -or !(Test-Path -LiteralPath $testKey)) {
    throw 'Set SdkPath to the build SDK and retain the local debug key before packaging'
}

$unitReports = @(Get-ChildItem (Join-Path $projectRoot 'app/build/test-results/testDebugUnitTest') -Filter 'TEST-*.xml')
if ($unitReports.Count -eq 0) { throw 'Run testDebugUnitTest before packaging' }
$tests = 0
$failures = 0
$errors = 0
$skipped = 0
foreach ($file in $unitReports) {
    $report = [xml](Get-Content -LiteralPath $file.FullName -Raw)
    $tests += [int]$report.testsuite.tests
    $failures += [int]$report.testsuite.failures
    $errors += [int]$report.testsuite.errors
    $skipped += [int]$report.testsuite.skipped
}
if ($failures -ne 0 -or $errors -ne 0) { throw 'Unit tests are not passing' }
$lint = Get-Content (Join-Path $projectRoot 'app/build/reports/lint-results-debug.txt') -Raw
$lintResult = [regex]::Match($lint, '(\d+) errors, (\d+) warnings')
if (!$lintResult.Success -or $lintResult.Groups[1].Value -ne '0') { throw 'Lint is not passing' }

$inputs = [ordered]@{
    'debug.apk' = 'app/build/outputs/apk/debug/app-debug.apk'
    'release-unsigned.apk' = 'app/build/outputs/apk/release/app-release-unsigned.apk'
    'ipc-tests.apk' = 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    'mapping.txt' = 'app/build/outputs/mapping/release/mapping.txt'
}
$artifactPaths = @()
foreach ($item in $inputs.GetEnumerator()) {
    $destination = Join-Path $delivery "$prefix-$($item.Key)"
    Copy-Item -LiteralPath (Join-Path $projectRoot $item.Value) -Destination $destination -Force
    $artifactPaths += $destination
}
$signedRelease = Join-Path $delivery "$prefix-release-test-signed.apk"
& $signer sign --ks $testKey --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android `
    --out $signedRelease (Join-Path $delivery "$prefix-release-unsigned.apk")
if ($LASTEXITCODE -ne 0) { throw 'Test signing failed' }
$artifactPaths += $signedRelease
if (Test-Path -LiteralPath "$signedRelease.idsig") { $artifactPaths += "$signedRelease.idsig" }
$certificate = $null
foreach ($name in @('debug.apk', 'release-test-signed.apk', 'ipc-tests.apk')) {
    $verification = (& $signer verify --verbose --print-certs (Join-Path $delivery "$prefix-$name") | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "Signature verification failed: $name" }
    $digest = [regex]::Match($verification, 'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)').Groups[1].Value
    if (!$digest -or ($certificate -and $certificate -ne $digest)) { throw 'Test certificates do not match' }
    $certificate = $digest
    Write-Output "$name signature verified"
}

$revision = $null
if (!$SkipSource) {
    Push-Location $projectRoot
    try {
        $gitRoot = $projectRoot.Replace('\', '/')
        $status = (& git -c "safe.directory=$gitRoot" status --porcelain | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $status) { throw 'Commit the reviewed source baseline before source packaging' }
        $revision = (& git -c "safe.directory=$gitRoot" rev-parse HEAD | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'No committed source baseline' }
        $sourcePaths = @(& git -c "safe.directory=$gitRoot" ls-tree -r --name-only HEAD)
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect source baseline' }
        $excluded = '(^|/)(\.diagnostics|\.toolchains|\.gradle[^/]*|\.android[^/]*|build|dist|captures)(/|$)|\.(jks|keystore|apk|aab|jsonl)$|(^|/)local\.properties$'
        if (@($sourcePaths | Where-Object { $_ -match $excluded }).Count -ne 0) { throw 'Source baseline includes local-only files' }
        $sourceZip = Join-Path $delivery "$prefix-source.zip"
        & git -c "safe.directory=$gitRoot" archive --format=zip "--prefix=$prefix/" "--output=$sourceZip" HEAD
        if ($LASTEXITCODE -ne 0) { throw 'Source archive failed' }
        $artifactPaths += $sourceZip
    } finally { Pop-Location }
}

$artifacts = @($artifactPaths | Sort-Object | ForEach-Object {
    [ordered]@{
        file = Split-Path $_ -Leaf
        bytes = (Get-Item -LiteralPath $_).Length
        sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()
    }
})
$manifest = [ordered]@{
    version = $version
    versionCode = $metadata.elements[0].versionCode
    sourceRevision = $revision
    createdAt = [DateTimeOffset]::UtcNow.ToString('o')
    unitTests = [ordered]@{ tests = $tests; failures = $failures; errors = $errors; skipped = $skipped }
    lint = [ordered]@{ errors = 0; warnings = [int]$lintResult.Groups[2].Value }
    testCertificateSha256 = $certificate
    instrumentationExecuted = $false
    physicalAcceptanceExecuted = $false
    signing = 'Local Android debug/test key; maintainer release signing and upload pending'
    artifacts = $artifacts
}
$utf8 = [System.Text.UTF8Encoding]::new($false)
$manifestPath = Join-Path $delivery 'delivery-manifest.json'
[System.IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 6) + "`n", $utf8)
$checksums = @($artifacts | ForEach-Object { "$($_.sha256)  $($_.file)" })
$checksums += "$((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant())  delivery-manifest.json"
[System.IO.File]::WriteAllText((Join-Path $delivery 'SHA256SUMS.txt'), ($checksums -join "`n") + "`n", $utf8)
Write-Output ($manifest | ConvertTo-Json -Depth 6)
