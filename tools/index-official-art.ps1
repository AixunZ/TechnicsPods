param(
    [Parameter(Mandatory)][string[]]$Apk,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '../.diagnostics/artwork-index'),
    [string]$Selection,
    [string]$SdkPath = (Join-Path $PSScriptRoot '../.android-sdk')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing
$aapt = Join-Path $SdkPath 'build-tools/36.1.0/aapt2.exe'
$signer = Join-Path $SdkPath 'build-tools/36.1.0/apksigner.bat'
if (!(Test-Path -LiteralPath $aapt) -or !(Test-Path -LiteralPath $signer)) {
    throw 'Android build tools 36.1.0 are required'
}
$out = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $out -Force | Out-Null
$officialPackage = 'com.panasonic.technicsaudioconnect'
$files = @($Apk | ForEach-Object { (Resolve-Path -LiteralPath $_).Path })
if (@($files | Select-Object -Unique).Count -ne $files.Count) { throw 'Duplicate APK input' }
$index = [System.Xml.XmlDocument]::new()
$root = $index.CreateElement('official-apk-index')
$root.SetAttribute('schema', '1')
$root.SetAttribute('verified', 'false')
$root.SetAttribute('note', 'Resource identity must be reviewed; names and dimensions do not prove AZ100 or color')
$index.AppendChild($root) | Out-Null
$candidates = @()
$identities = @()
$apkNumber = 0
foreach ($path in $files) {
    $apkNumber++
    $badging = (& $aapt dump badging $path 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Unable to inspect APK: $path" }
    $identity = [regex]::Match($badging, "package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'")
    if (!$identity.Success) { throw "Missing package identity: $path" }
    $certs = (& $signer verify --print-certs $path 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $path" }
    $certificate = [regex]::Match($certs, 'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]{64})')
    if (!$certificate.Success -or $certs -match 'Signer #2 certificate') {
        throw 'Only a verified single current APK signer is supported'
    }
    $info = [pscustomobject]@{
        Path = $path; Number = $apkNumber; Name = [IO.Path]::GetFileName($path)
        Package = $identity.Groups[1].Value; VersionCode = $identity.Groups[2].Value
        Version = $identity.Groups[3].Value; Signature = $certificate.Groups[1].Value.ToLowerInvariant()
    }
    $identities += $info
    $element = $index.CreateElement('apk')
    $element.SetAttribute('index', [string]$apkNumber)
    $element.SetAttribute('file', $info.Name)
    $element.SetAttribute('package', $info.Package)
    $element.SetAttribute('versionCode', $info.VersionCode)
    $element.SetAttribute('version', $info.Version)
    $element.SetAttribute('signatureSha256', $info.Signature)
    $element.SetAttribute('sha256', (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant())
    $root.AppendChild($element) | Out-Null
    $dump = @(& $aapt dump resources $path 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Resource table inspection failed: $path" }
    [IO.File]::WriteAllLines((Join-Path $out "resources-$apkNumber.txt"), [string[]]$dump)
    $zip = [IO.Compression.ZipFile]::OpenRead($path)
    try {
        $currentName = ''; $currentId = ''
        foreach ($line in $dump) {
            $resource = [regex]::Match([string]$line, '^\s*resource (0x[0-9a-fA-F]+) ((?:drawable|mipmap)/[A-Za-z0-9_]+)\s*$')
            if ($resource.Success) {
                $currentId = $resource.Groups[1].Value
                $currentName = $resource.Groups[2].Value
                continue
            }
            if ([string]$line -match '^\s*(?:resource|type) ') { $currentName = ''; continue }
            if (!$currentName) { continue }
            $value = [regex]::Match([string]$line, '^\s*\(([^)]*)\) \(file\) (\S+)(?: type=(\S+))?\s*$')
            if (!$value.Success) {
                $reference = [regex]::Match([string]$line, '^\s*\(([^)]*)\) (@\S+)\s*$')
                if ($reference.Success) {
                    $alias = $index.CreateElement('reference')
                    $alias.SetAttribute('resource', $currentName)
                    $alias.SetAttribute('id', $currentId)
                    $alias.SetAttribute('configuration', $reference.Groups[1].Value)
                    $alias.SetAttribute('target', $reference.Groups[2].Value)
                    $element.AppendChild($alias) | Out-Null
                }
                continue
            }
            $entryPath = $value.Groups[2].Value
            $entry = $zip.GetEntry($entryPath)
            if ($null -eq $entry -or $entry.Length -gt 8MB) { continue }
            $stream = $entry.Open()
            try {
                $algorithm = [Security.Cryptography.SHA256]::Create()
                try { $hash = [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
                finally { $algorithm.Dispose() }
            } finally { $stream.Dispose() }
            $item = $index.CreateElement('candidate')
            $item.SetAttribute('resource', $currentName)
            $item.SetAttribute('id', $currentId)
            $item.SetAttribute('configuration', $value.Groups[1].Value)
            $item.SetAttribute('entry', $entryPath)
            $item.SetAttribute('format', $value.Groups[3].Value)
            $item.SetAttribute('bytes', [string]$entry.Length)
            $item.SetAttribute('sha256', $hash)
            $width = 0; $height = 0
            if ($entryPath -match '\.(?:png|jpe?g|gif|bmp)$') {
                $stream = $entry.Open()
                try {
                    try {
                        $image = [Drawing.Image]::FromStream($stream, $false, $false)
                        try { $width = $image.Width; $height = $image.Height }
                        finally { $image.Dispose() }
                    } catch { }
                } finally { $stream.Dispose() }
            }
            $item.SetAttribute('widthPixels', [string]$width)
            $item.SetAttribute('heightPixels', [string]$height)
            if ($entryPath.EndsWith('.xml')) {
                $xmlTree = @(& $aapt dump xmltree --file $entryPath $path 2>&1)
                if ($LASTEXITCODE -eq 0) {
                    $detailFile = 'xml-' + $apkNumber + '-' + $currentId.Substring(2) + '-' + $candidates.Count + '.txt'
                    [IO.File]::WriteAllLines((Join-Path $out $detailFile), [string[]]$xmlTree)
                    $item.SetAttribute('xmlTree', $detailFile)
                }
            }
            $element.AppendChild($item) | Out-Null
            $candidates += [pscustomobject]@{
                Apk = $info; Resource = $currentName; Entry = $entryPath
                Configuration = $value.Groups[1].Value; Hash = $hash; Format = $value.Groups[3].Value
            }
        }
        # Non-resource and nested archive assets remain indexed, without being guessed as artwork.
        foreach ($entry in $zip.Entries) {
            if (!$entry.FullName.StartsWith('assets/') -or $entry.Name.Length -eq 0) { continue }
            $asset = $index.CreateElement('asset')
            $asset.SetAttribute('entry', $entry.FullName)
            $asset.SetAttribute('bytes', [string]$entry.Length)
            $element.AppendChild($asset) | Out-Null
        }
    } finally { $zip.Dispose() }
}
$index.Save((Join-Path $out 'index.xml'))
if (!$Selection) {
    Write-Output "Indexed $($files.Count) APKs and $($candidates.Count) drawable variants. Review index.xml before mapping model/color slots."
    exit 0
}
if (@($identities | Where-Object { $_.Package -ne $officialPackage }).Count -gt 0 -or
        @($identities.Signature | Select-Object -Unique).Count -ne 1 -or
        @($identities.VersionCode | Select-Object -Unique).Count -ne 1) {
    throw 'Every base/split APK must have the official package, matching version code and signer'
}
$selectionDocument = [System.Xml.XmlDocument]::new()
$selectionDocument.XmlResolver = $null
$xmlSettings = [System.Xml.XmlReaderSettings]::new()
$xmlSettings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
$xmlSettings.XmlResolver = $null
$xmlSettings.MaxCharactersInDocument = 65536
$selectionReader = [System.Xml.XmlReader]::Create((Resolve-Path -LiteralPath $Selection).Path, $xmlSettings)
try { $selectionDocument.Load($selectionReader) }
finally { $selectionReader.Dispose() }
$selectionRoot = $selectionDocument.DocumentElement
if ($selectionRoot.Name -ne 'az100-selection' -or $selectionRoot.GetAttribute('model') -ne 'EAH-AZ100' -or
        $selectionRoot.GetAttribute('reviewed') -ne 'true' -or
        $selectionRoot.GetAttribute('color') -notmatch '^[A-Za-z0-9_-]{1,64}$') {
    throw 'Selection requires explicit reviewed=true, model=EAH-AZ100 and exact color'
}
$manifest = [System.Xml.XmlDocument]::new()
$manifestRoot = $manifest.CreateElement('az100-art')
$manifest.AppendChild($manifestRoot) | Out-Null
foreach ($entry in @{
    schema = '1'; model = 'EAH-AZ100'; source = 'official'; verified = 'true'
    package = $officialPackage; color = $selectionRoot.GetAttribute('color')
    sourceVersion = $identities[0].Version; sourceVersionCode = $identities[0].VersionCode
    signatureSha256 = $identities[0].Signature
}.GetEnumerator()) { $manifestRoot.SetAttribute($entry.Key, [string]$entry.Value) }
$seen = @{}
foreach ($slot in $selectionRoot.ChildNodes) {
    if ($slot -isnot [Xml.XmlElement]) { continue }
    $name = $slot.GetAttribute('name')
    # PowerShell exposes an XmlElement's `Name` attribute through ETS when the
    # element also has a name="..." attribute. Use the DOM LocalName so the
    # selection parser remains correct on Windows PowerShell 5 and 7.
    if ($slot.LocalName -ne 'slot' -or $name -notin @('main', 'left', 'right', 'case') -or $seen.ContainsKey($name)) {
        throw 'Only unique known artwork slots are accepted'
    }
    $seen[$name] = $true
    $matches = @($candidates | Where-Object {
        $_.Apk.Number -eq [int]$slot.GetAttribute('apkIndex') -and $_.Resource -eq $slot.GetAttribute('resource') -and
        $_.Entry -eq $slot.GetAttribute('entry') -and $_.Configuration -eq $slot.GetAttribute('configuration')
    })
    if ($matches.Count -ne 1) { throw "Selection must match exactly one indexed resource: $name" }
    $candidate = $matches[0]
    $item = $manifest.CreateElement('slot')
    $item.SetAttribute('name', $name)
    $item.SetAttribute('resource', $candidate.Resource)
    $item.SetAttribute('resourceSha256', $candidate.Hash)
    $manifestRoot.AppendChild($item) | Out-Null
    $raw = Join-Path $out 'raw'
    New-Item -ItemType Directory -Path $raw -Force | Out-Null
    $extension = [IO.Path]::GetExtension($candidate.Entry)
    if ($extension -notmatch '^\.[A-Za-z0-9]{1,8}$') { $extension = '.bin' }
    $zip = [IO.Compression.ZipFile]::OpenRead($candidate.Apk.Path)
    try {
        [IO.Compression.ZipFileExtensions]::ExtractToFile($zip.GetEntry($candidate.Entry),
                (Join-Path $raw ($name + $extension)), $true)
    } finally { $zip.Dispose() }
}
if ($seen.Count -eq 0) { throw 'Selection has no resource slots' }
$manifest.Save((Join-Path $out 'official-mapping.xml'))
Write-Output 'Reviewed mapping and raw resources exported. XML/vector references require installed-app Android Resources rendering; raw assets are not a ready bundled image set.'
