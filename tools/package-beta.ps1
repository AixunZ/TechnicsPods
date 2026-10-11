param(
    [Alias('Public')]
    [switch]$PublicRelease,
    [Alias('ReleaseApk')]
    [string]$ReleaseApkPath = $env:TECHNICSPODS_RELEASE_APK,
    [Alias('MaintainerCertificateSha256', 'ReleaseCertificateSha256')]
    [string]$ExpectedCertificateSha256 = $env:TECHNICSPODS_RELEASE_CERT_SHA256,
    [string]$ExpectedReleaseApkSha256 = $env:TECHNICSPODS_RELEASE_SHA256,
    [string]$ExpectedApplicationId = 'dev.technics.pods',
    [switch]$SkipSource,
    [switch]$WorkingTreeSource,
    [string]$SdkPath = (Join-Path $PSScriptRoot '../.android-sdk')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$metadataPath = if ($PublicRelease) { 'app/build/outputs/apk/release/output-metadata.json' } else { 'app/build/outputs/apk/debug/output-metadata.json' }
$metadata = Get-Content (Join-Path $projectRoot $metadataPath) -Raw | ConvertFrom-Json
$version = [string]$metadata.elements[0].versionName
$versionCode = [int64]$metadata.elements[0].versionCode
if ($version -notmatch '^[0-9A-Za-z.-]+$') { throw 'Invalid artifact version' }
if ([string]$metadata.applicationId -ne $ExpectedApplicationId) {
    throw "Build package '$($metadata.applicationId)' does not match expected '$ExpectedApplicationId'"
}
if ($PublicRelease -and $SkipSource) { throw 'Public release packaging requires the source archive; do not use -SkipSource' }
$delivery = Join-Path $projectRoot "dist/$version"
$prefix = "TechnicsPods-$version"
New-Item -ItemType Directory -Path $delivery -Force | Out-Null
if ($PublicRelease) {
    Get-ChildItem -LiteralPath $delivery -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -eq 'delivery-manifest.json' -or $_.Name -eq 'SHA256SUMS.txt' -or $_.Name.StartsWith("$prefix-") } |
        Remove-Item -Force
}
$signer = Join-Path $SdkPath 'build-tools/36.1.0/apksigner.bat'
$aapt2 = Join-Path $SdkPath 'build-tools/36.1.0/aapt2.exe'
$testKey = Join-Path $projectRoot '.android-user-home/debug.keystore'
if (!(Test-Path -LiteralPath $signer) -or ($PublicRelease -and !(Test-Path -LiteralPath $aapt2))) {
    throw 'Set SdkPath to the build SDK with apksigner and aapt2 before packaging'
}
if (!$PublicRelease -and !(Test-Path -LiteralPath $testKey)) {
    throw 'Retain the local debug key before local test packaging'
}

function Copy-Artifact {
    param([string]$Source, [string]$Destination)
    $sourceFull = [IO.Path]::GetFullPath($Source)
    $destinationFull = [IO.Path]::GetFullPath($Destination)
    if (!(Test-Path -LiteralPath $sourceFull -PathType Leaf)) { throw "Artifact was not found: $sourceFull" }
    if ($sourceFull -ine $destinationFull) { Copy-Item -LiteralPath $sourceFull -Destination $destinationFull -Force }
    return $destinationFull
}

function Get-ApkCertificateDigest {
    param([string]$Apk)
    $verification = (& $signer verify --verbose --print-certs $Apk 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "Signature verification failed: $Apk`n$verification" }
    $digest = ([regex]::Match($verification, 'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F: ]+)').Groups[1].Value -replace '[^0-9a-fA-F]', '').ToLowerInvariant()
    if ($digest.Length -ne 64) { throw "APK certificate SHA-256 digest was not reported: $Apk" }
    return $digest
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

$artifactPaths = @()
$certificate = $null
$releaseCertificate = $null
$releaseApkSha256 = $null
if ($PublicRelease) {
    $releaseApk = $ReleaseApkPath
    if ([string]::IsNullOrWhiteSpace($releaseApk)) {
        $releaseApk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
    } elseif (![IO.Path]::IsPathRooted($releaseApk)) {
        $releaseApk = Join-Path $projectRoot $releaseApk
    }
    if (!(Test-Path -LiteralPath $releaseApk -PathType Leaf)) {
        throw "Signed app-release.apk was not found: $releaseApk"
    }
    $badging = (& $aapt2 dump badging $releaseApk 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "Could not inspect APK manifest: $releaseApk`n$badging" }
    $apkMatch = [regex]::Match($badging,
        "package:\s+name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']*)'")
    if (!$apkMatch.Success) { throw 'Release APK manifest metadata was not found' }
    if ($apkMatch.Groups[1].Value -ne $ExpectedApplicationId) {
        throw "Release APK package '$($apkMatch.Groups[1].Value)' does not match expected '$ExpectedApplicationId'"
    }
    if ($apkMatch.Groups[2].Value -ne [string]$versionCode -or $apkMatch.Groups[3].Value -ne $version) {
        throw "Release APK version $($apkMatch.Groups[3].Value)/$($apkMatch.Groups[2].Value) does not match build metadata $version/$versionCode"
    }
    $verification = (& $signer verify --verbose --print-certs $releaseApk 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) { throw "Release APK signature verification failed`n$verification" }
    $releaseCertificate = ([regex]::Match($verification,
        'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F: ]+)').Groups[1].Value -replace '[^0-9a-fA-F]', '').ToLowerInvariant()
    if ($releaseCertificate.Length -ne 64) { throw 'Release APK certificate SHA-256 digest was not reported' }
    $expectedCertificate = ($ExpectedCertificateSha256 -replace '[^0-9a-fA-F]', '').ToLowerInvariant()
    if ($expectedCertificate) {
        if ($expectedCertificate.Length -ne 64) { throw 'TECHNICSPODS_RELEASE_CERT_SHA256 must be a SHA-256 certificate digest' }
        if ($releaseCertificate -ne $expectedCertificate) { throw 'Release APK certificate does not match expected maintainer certificate' }
    }
    $releaseDestination = Join-Path $delivery "$prefix-release.apk"
    $sourceReleaseApkSha256 = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([IO.Path]::GetFullPath($releaseApk) -ine [IO.Path]::GetFullPath($releaseDestination)) {
        Copy-Item -LiteralPath $releaseApk -Destination $releaseDestination -Force
    }
    $artifactPaths += $releaseDestination
    $releaseApkSha256 = (Get-FileHash -LiteralPath $releaseDestination -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($sourceReleaseApkSha256 -ne $releaseApkSha256) { throw 'Release APK SHA-256 changed while staging the artifact' }
    $expectedReleaseApkSha256 = ($ExpectedReleaseApkSha256 -replace '[^0-9a-fA-F]', '').ToLowerInvariant()
    if ($expectedReleaseApkSha256) {
        if ($expectedReleaseApkSha256.Length -ne 64) { throw 'TECHNICSPODS_RELEASE_SHA256 must be a SHA-256 APK digest' }
        if ($releaseApkSha256 -ne $expectedReleaseApkSha256) { throw 'Release APK does not match expected SHA-256' }
    }
    $mappingSource = Join-Path $projectRoot 'app/build/outputs/mapping/release/mapping.txt'
    if (!(Test-Path -LiteralPath $mappingSource -PathType Leaf)) { throw 'Release mapping.txt was not found' }
    $mappingDestination = Join-Path $delivery "$prefix-mapping.txt"
    Copy-Item -LiteralPath $mappingSource -Destination $mappingDestination -Force
    $artifactPaths += $mappingDestination
} else {
    $inputs = [ordered]@{
        'debug.apk' = 'app/build/outputs/apk/debug/app-debug.apk'
        'release-unsigned.apk' = 'app/build/outputs/apk/release/app-release-unsigned.apk'
        'ipc-tests.apk' = 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
        'mapping.txt' = 'app/build/outputs/mapping/release/mapping.txt'
    }
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
    foreach ($name in @('debug.apk', 'release-test-signed.apk', 'ipc-tests.apk')) {
        $verification = (& $signer verify --verbose --print-certs (Join-Path $delivery "$prefix-$name") | Out-String)
        if ($LASTEXITCODE -ne 0) { throw "Signature verification failed: $name" }
        $digest = ([regex]::Match($verification, 'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F: ]+)').Groups[1].Value -replace '[^0-9a-fA-F]', '').ToLowerInvariant()
        if (!$digest -or ($certificate -and $certificate -ne $digest)) { throw 'Test certificates do not match' }
        $certificate = $digest
        Write-Output "$name signature verified"
    }
}

$revision = $null
$sourceDirty = $false
if (!$SkipSource) {
    Push-Location $projectRoot
    try {
        $gitRoot = $projectRoot.Replace('\', '/')
        $status = (& git -c "safe.directory=$gitRoot" status --porcelain | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect source status' }
        $sourceDirty = [bool]$status
        if ($sourceDirty -and !$WorkingTreeSource) { throw 'Use -WorkingTreeSource to archive reviewed uncommitted source, or commit the baseline' }
        $revision = (& git -c "safe.directory=$gitRoot" rev-parse HEAD | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'No committed source baseline' }
        $sourcePaths = if ($WorkingTreeSource) {
            @(& git -c "safe.directory=$gitRoot" -c core.quotepath=false ls-files --cached --others --exclude-standard)
        } else { @(& git -c "safe.directory=$gitRoot" -c core.quotepath=false ls-tree -r --name-only HEAD) }
        if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect source baseline' }
        $excluded = '(^|/)(\.diagnostics|\.toolchains|\.gradle[^/]*|\.android[^/]*|build|dist|captures)(/|$)|\.(jks|keystore|apk|aab|jsonl)$|(^|/)local\.properties$'
        if (@($sourcePaths | Where-Object { $_ -match $excluded }).Count -ne 0) { throw 'Source baseline includes local-only files' }
        $sourceZip = Join-Path $delivery "$prefix-source.zip"
        if ($WorkingTreeSource) {
            Add-Type -AssemblyName System.IO.Compression
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            $sourceStream = [System.IO.File]::Open($sourceZip, [System.IO.FileMode]::Create)
            $archive = [System.IO.Compression.ZipArchive]::new($sourceStream, [System.IO.Compression.ZipArchiveMode]::Create)
            try {
                foreach ($relative in @($sourcePaths | Sort-Object -Unique)) {
                    $absolute = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $relative))
                    if (!$absolute.StartsWith($projectRoot + [System.IO.Path]::DirectorySeparatorChar,
                            [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Source path escaped workspace' }
                    if (!(Test-Path -LiteralPath $absolute -PathType Leaf)) { continue }
                    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $absolute,
                            "$prefix/$($relative.Replace('\', '/'))") | Out-Null
                }
            } finally { $archive.Dispose() }
        } else {
            & git -c "safe.directory=$gitRoot" archive --format=zip "--prefix=$prefix/" "--output=$sourceZip" HEAD
            if ($LASTEXITCODE -ne 0) { throw 'Source archive failed' }
        }
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
    mode = if ($PublicRelease) { 'public-release' } else { 'local-test' }
    version = $version
    versionCode = $versionCode
    applicationId = [string]$metadata.applicationId
    sourceRevision = $revision
    sourceIncludesWorkingTree = [bool]$WorkingTreeSource
    sourceDirty = $sourceDirty
    createdAt = [DateTimeOffset]::UtcNow.ToString('o')
    unitTests = [ordered]@{ tests = $tests; failures = $failures; errors = $errors; skipped = $skipped }
    lint = [ordered]@{ errors = 0; warnings = [int]$lintResult.Groups[2].Value }
    instrumentationExecuted = $false
    physicalAcceptanceExecuted = $false
    artifacts = $artifacts
}
if ($PublicRelease) {
    $manifest.releaseCertificateSha256 = $releaseCertificate
    $manifest.releaseApkSha256 = $releaseApkSha256
    $manifest.releaseSignatureVerified = $true
    $manifest.signing = 'Maintainer release key; certificate verified by apksigner'
} else {
    $manifest.testCertificateSha256 = $certificate
    $manifest.signing = 'Local Android debug/test key; maintainer release signing and upload pending'
}
$utf8 = [System.Text.UTF8Encoding]::new($false)
$manifestPath = Join-Path $delivery 'delivery-manifest.json'
[System.IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 6) + "`n", $utf8)
$checksums = @($artifacts | ForEach-Object { "$($_.sha256)  $($_.file)" })
$checksums += "$((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant())  delivery-manifest.json"
[System.IO.File]::WriteAllText((Join-Path $delivery 'SHA256SUMS.txt'), ($checksums -join "`n") + "`n", $utf8)
Write-Output ($manifest | ConvertTo-Json -Depth 6)
