Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = $PSScriptRoot
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")
$Gradlew = Join-Path $RepoRoot "gradlew.bat"
$GradleProperties = Join-Path $RepoRoot "gradle.properties"
$ResultsDir = Join-Path $RepoRoot "testResults"
$ClasspathSeparator = [IO.Path]::PathSeparator
$LastLaunchExitCode = 0

$AllLabels = @(
  "fabric-1.20.1-1.20.2",
  "fabric-1.20.4",
  "fabric-1.20.3-1.20.6",
  "fabric-1.21.1",
  "fabric-1.21.4",
  "fabric-1.21.9",
  "fabric-1.21-1.21.11",
  "fabric-26.1",
  "forge-1.20.1-1.20.2",
  "forge-1.20.4",
  "forge-1.20.3-1.20.6",
  "forge-1.21.1",
  "neoforge-1.20.1-1.20.2",
  "neoforge-1.20.4",
  "neoforge-1.20.6",
  "neoforge-1.21.1",
  "neoforge-1.21.4",
  "neoforge-1.21-1.21.11",
  "neoforge-26.1"
)

function Show-Usage {
  @"
Usage:
  .\scripts\windows\test-built-clients.ps1 [--skip <label[,label...]|loader>] <label[,label...]|all|loader>
  .\scripts\windows\test-built-clients.ps1 [--skip <label[,label...]|loader>] <label> <label> ...

Examples:
  .\scripts\windows\test-built-clients.ps1 fabric-1.21-1.21.11
  .\scripts\windows\test-built-clients.ps1 fabric-1.21-1.21.11,neoforge-1.21-1.21.11
  .\scripts\windows\test-built-clients.ps1 forge-1.20.4 neoforge-1.20.6
  .\scripts\windows\test-built-clients.ps1 forge
  .\scripts\windows\test-built-clients.ps1 all:fabric
  .\scripts\windows\test-built-clients.ps1 all --skip forge
  .\scripts\windows\test-built-clients.ps1 forge --skip forge-1.20.4,forge-1.21.1
  .\scripts\windows\test-built-clients.ps1 all
"@ | Write-Host
}

function Read-GradleProperty([string] $key) {
  foreach ($line in Get-Content $GradleProperties) {
    if ($line -match "^$([regex]::Escape($key))=(.*)$") {
      return $Matches[1]
    }
  }
  throw "Missing $key in $GradleProperties"
}

$ModId = Read-GradleProperty "modId"
$ProjectVersion = Read-GradleProperty "projectVersion"

function Strip-TrailingLabelPunctuation([string] $value) {
  return ($value.Trim() -replace '[\.,;:]+$', '')
}

function Normalize-RequestedLabel([string] $label) {
  $label = Strip-TrailingLabelPunctuation $label
  switch ($label) {
    { $_ -in @("fabric-1.20.1", "fabric-1.20.2") } { return "fabric-1.20.1-1.20.2" }
    { $_ -in @("fabric-1.20.3", "fabric-1.20.4") } { return "fabric-1.20.4" }
    { $_ -in @("fabric-1.20.5", "fabric-1.20.6") } { return "fabric-1.20.3-1.20.6" }
    { $_ -in @("fabric-1.21", "fabric-1.21.1") } { return "fabric-1.21.1" }
    { $_ -in @("fabric-1.21.2", "fabric-1.21.3", "fabric-1.21.4") } { return "fabric-1.21.4" }
    { $_ -in @("fabric-1.21.5", "fabric-1.21.6", "fabric-1.21.7", "fabric-1.21.8", "fabric-1.21.9") } { return "fabric-1.21.9" }
    { $_ -in @("fabric-1.21.10", "fabric-1.21.11") } { return "fabric-1.21-1.21.11" }
    { $_ -in @("forge-1.20.1", "forge-1.20.2") } { return "forge-1.20.1-1.20.2" }
    { $_ -in @("forge-1.20.3", "forge-1.20.4") } { return "forge-1.20.4" }
    { $_ -in @("forge-1.20.5", "forge-1.20.6") } { return "forge-1.20.3-1.20.6" }
    { $_ -in @("forge-1.21", "forge-1.21.1") } { return "forge-1.21.1" }
    { $_ -in @("neoforge-1.20.1", "neoforge-1.20.2") } { return "neoforge-1.20.1-1.20.2" }
    { $_ -in @("neoforge-1.20.3", "neoforge-1.20.4") } { return "neoforge-1.20.4" }
    { $_ -in @("neoforge-1.20.5", "neoforge-1.20.6") } { return "neoforge-1.20.6" }
    { $_ -in @("neoforge-1.21", "neoforge-1.21.1") } { return "neoforge-1.21.1" }
    { $_ -in @("neoforge-1.21.2", "neoforge-1.21.3", "neoforge-1.21.4") } { return "neoforge-1.21.4" }
    { $_ -in @("neoforge-1.21.5", "neoforge-1.21.6", "neoforge-1.21.7", "neoforge-1.21.8", "neoforge-1.21.9", "neoforge-1.21.10", "neoforge-1.21.11") } { return "neoforge-1.21-1.21.11" }
    default { return $label }
  }
}

function Split-Labels([string] $raw) {
  $raw = Strip-TrailingLabelPunctuation $raw
  if ([string]::IsNullOrWhiteSpace($raw)) { return @() }
  switch ($raw) {
    "all" { return $AllLabels }
    { $_ -in @("fabric", "all:fabric", "fabric:all") } { return @($AllLabels | Where-Object { $_.StartsWith("fabric-") }) }
    { $_ -in @("forge", "all:forge", "forge:all") } { return @($AllLabels | Where-Object { $_.StartsWith("forge-") }) }
    { $_ -in @("neoforge", "all:neoforge", "neoforge:all") } { return @($AllLabels | Where-Object { $_.StartsWith("neoforge-") }) }
  }

  $resolved = New-Object System.Collections.Generic.List[string]
  foreach ($part in $raw.Split(",")) {
    $label = Normalize-RequestedLabel $part
    switch ($label) {
      { $_ -in @("fabric", "all:fabric", "fabric:all") } { $AllLabels | Where-Object { $_.StartsWith("fabric-") } | ForEach-Object { $resolved.Add($_) } }
      { $_ -in @("forge", "all:forge", "forge:all") } { $AllLabels | Where-Object { $_.StartsWith("forge-") } | ForEach-Object { $resolved.Add($_) } }
      { $_ -in @("neoforge", "all:neoforge", "neoforge:all") } { $AllLabels | Where-Object { $_.StartsWith("neoforge-") } | ForEach-Object { $resolved.Add($_) } }
      default { if (-not [string]::IsNullOrWhiteSpace($label)) { $resolved.Add($label) } }
    }
  }
  return $resolved.ToArray()
}

function Resolve-TargetMetadata([string] $label) {
  $map = @{
    "fabric-1.20.1-1.20.2" = @{ Loader = "fabric"; PlatformDir = "platforms\fabric\1_20_1"; JavaVersion = 17; DliMain = "net.fabricmc.loader.impl.launch.knot.KnotClient" }
    "fabric-1.20.4" = @{ Loader = "fabric"; PlatformDir = "platforms\fabric\1_20_4"; JavaVersion = 17; DliMain = "net.fabricmc.loader.impl.launch.knot.KnotClient" }
    "fabric-1.20.3-1.20.6" = @{ Loader = "fabric"; PlatformDir = "platforms\fabric\1_20_6"; JavaVersion = 21; DliMain = "net.fabricmc.loader.impl.launch.knot.KnotClient" }
    "fabric-1.21.1" = @{ Loader = "fabric"; PlatformDir = "platforms\fabric\1_21_1"; JavaVersion = 21; DliMain = "net.fabricmc.loader.impl.launch.knot.KnotClient" }
    "fabric-1.21.4" = @{ Loader = "fabric"; PlatformDir = "platforms\fabric\1_21_4"; JavaVersion = 21; DliMain = "net.fabricmc.loader.impl.launch.knot.KnotClient" }
    "fabric-1.21.9" = @{ Loader = "fabric"; PlatformDir = "platforms\fabric\1_21_9"; JavaVersion = 21; DliMain = "net.fabricmc.loader.impl.launch.knot.KnotClient" }
    "fabric-1.21-1.21.11" = @{ Loader = "fabric"; PlatformDir = "platforms\fabric\1_21_11"; JavaVersion = 21; DliMain = "net.fabricmc.loader.impl.launch.knot.KnotClient" }
    "fabric-26.1" = @{ Loader = "fabric"; PlatformDir = "platforms\fabric\26_1"; JavaVersion = 25; DliMain = "net.fabricmc.loader.impl.launch.knot.KnotClient" }
    "forge-1.20.1-1.20.2" = @{ Loader = "forge"; PlatformDir = "platforms\forge\1_20_1"; JavaVersion = 17; DliMain = "cpw.mods.bootstraplauncher.BootstrapLauncher" }
    "forge-1.20.4" = @{ Loader = "forge"; PlatformDir = "platforms\forge\1_20_4"; JavaVersion = 17; DliMain = "net.minecraftforge.bootstrap.ForgeBootstrap" }
    "forge-1.20.3-1.20.6" = @{ Loader = "forge"; PlatformDir = "platforms\forge\1_20_6"; JavaVersion = 21; DliMain = "net.minecraftforge.bootstrap.ForgeBootstrap" }
    "forge-1.21.1" = @{ Loader = "forge"; PlatformDir = "platforms\forge\1_21_1"; JavaVersion = 21; DliMain = "net.minecraftforge.bootstrap.ForgeBootstrap" }
    "neoforge-1.20.1-1.20.2" = @{ Loader = "neoforge"; PlatformDir = "platforms\neoforge\1_20_1"; JavaVersion = 17; DliMain = "net.neoforged.fml.startup.Client" }
    "neoforge-1.20.4" = @{ Loader = "neoforge"; PlatformDir = "platforms\neoforge\1_20_4"; JavaVersion = 17; DliMain = "net.neoforged.fml.startup.Client" }
    "neoforge-1.20.6" = @{ Loader = "neoforge"; PlatformDir = "platforms\neoforge\1_20_6"; JavaVersion = 21; DliMain = "net.neoforged.fml.startup.Client" }
    "neoforge-1.21.1" = @{ Loader = "neoforge"; PlatformDir = "platforms\neoforge\1_21_1"; JavaVersion = 21; DliMain = "net.neoforged.fml.startup.Client" }
    "neoforge-1.21.4" = @{ Loader = "neoforge"; PlatformDir = "platforms\neoforge\1_21_4"; JavaVersion = 21; DliMain = "net.neoforged.fml.startup.Client" }
    "neoforge-1.21-1.21.11" = @{ Loader = "neoforge"; PlatformDir = "platforms\neoforge\1_21_11"; JavaVersion = 21; DliMain = "net.neoforged.fml.startup.Client" }
    "neoforge-26.1" = @{ Loader = "neoforge"; PlatformDir = "platforms\neoforge\26_1"; JavaVersion = 25; DliMain = "net.neoforged.fml.startup.Client" }
  }
  return $map[$label]
}

function Convert-PlatformDirToProjectPath([string] $platformDir) {
  return ":platform-" + (($platformDir -replace '^platforms\\', '') -replace '\\', '-')
}

function Get-JavaMajor([string] $javaExe) {
  $line = (& $javaExe -version 2>&1 | Select-Object -First 1)
  if ($line -match 'version "([^"]+)"') {
    $raw = $Matches[1]
    if ($raw.StartsWith("1.")) { return [int]($raw.Split(".")[1]) }
    return [int]($raw.Split(".")[0])
  }
  return 0
}

function Get-JavaFromHome([string] $jdkHome) {
  if ([string]::IsNullOrWhiteSpace($jdkHome)) { return $null }
  $candidate = Join-Path $jdkHome "bin\java.exe"
  if (Test-Path $candidate) { return $candidate }
  return $null
}

function Find-JavaBin([int] $minVersion) {
  $envName = "KEYSET_JAVA_${minVersion}_HOME"
  $candidates = New-Object System.Collections.Generic.List[string]
  foreach ($jdkHome in @([Environment]::GetEnvironmentVariable($envName), $env:JAVA_HOME, $env:KEYSET_JAVA_HOME)) {
    $java = Get-JavaFromHome $jdkHome
    if ($java) { $candidates.Add($java) }
  }

  foreach ($root in @(
    (Join-Path $RepoRoot ".gradle\jdks"),
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java",
    "C:\Program Files\Microsoft"
  )) {
    if (Test-Path $root) {
      Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
        ForEach-Object {
          $java = Get-JavaFromHome $_.FullName
          if ($java) { $candidates.Add($java) }
        }
    }
  }

  $pathJava = Get-Command java.exe -ErrorAction SilentlyContinue
  if ($pathJava) { $candidates.Add($pathJava.Source) }

  foreach ($java in ($candidates | Select-Object -Unique)) {
    if ((Test-Path $java) -and (Get-JavaMajor $java) -ge $minVersion) {
      return $java
    }
  }
  return $null
}

function Require-GradleJavaBin {
  $java = Get-JavaFromHome $env:KEYSET_GRADLE_JAVA_HOME
  if ($java) {
    if ((Get-JavaMajor $java) -lt 25) { throw "KEYSET_GRADLE_JAVA_HOME must point to Java 25 or newer." }
    return $java
  }
  $java = Find-JavaBin 25
  if (-not $java) { throw "Unable to find a Java 25+ runtime. Set KEYSET_GRADLE_JAVA_HOME." }
  return $java
}

function Add-CsvRow([string] $csvPath, [string[]] $fields) {
  $line = ($fields | ForEach-Object { '"' + (($_ -replace "`r|`n", " ") -replace '"', '""') + '"' }) -join ","
  Add-Content -LiteralPath $csvPath -Value $line -Encoding UTF8
}

function Resolve-RuntimeJarPath([string] $label, [string] $loader, [string] $platformDir) {
  $devJar = Join-Path $RepoRoot "$platformDir\build\devlibs\$ModId-$label-$ProjectVersion-dev.jar"
  $libsJar = Join-Path $RepoRoot "$platformDir\build\libs\$ModId-$label-$ProjectVersion.jar"
  $releaseJar = Join-Path $RepoRoot "builtJars\$ProjectVersion\$loader\$ModId-$label-$ProjectVersion.jar"
  if (Test-Path $devJar) { return $devJar }
  if (Test-Path $libsJar) { return $libsJar }
  return $releaseJar
}

function Locate-CacheJar([string] $group, [string] $module, [string] $prefix) {
  $root = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\$group\$module"
  if (-not (Test-Path $root)) { return $null }
  $jar = Get-ChildItem $root -Recurse -Filter "$prefix*.jar" -ErrorAction SilentlyContinue |
    Sort-Object FullName |
    Select-Object -Last 1
  if ($jar) { return $jar.FullName }
  return $null
}

function Normalize-PathPrefix([string] $path) {
  if ([string]::IsNullOrWhiteSpace($path)) { return $path }
  # Java @argfile encodes spaces as `" "` (e.g. `MC" "Mods`). Windows paths never
  # contain `"`, so removing all quote chars correctly reconstructs the real path.
  # .Trim() also strips trailing \r\n that Set-Content can append to the last entry
  # of a semicolon-delimited classpath file.
  $normalized = $path.Replace('"', '').Trim()
  $normalized = $normalized.Replace("/Users/beeboyd/Developer/MCMods/Keyset", $RepoRoot.Path.Replace("\", "/"))
  $normalized = $normalized.Replace("/Users/beeboyd/.gradle", (Join-Path $env:USERPROFILE ".gradle").Replace("\", "/"))
  $normalized = $normalized.Replace("/Users/beeboyd", $env:USERPROFILE.Replace("\", "/"))
  return $normalized -replace '/', '\'
}

function Resolve-ExistingClasspathEntry([string] $path) {
  return Normalize-PathPrefix $path
}

function Split-Classpath([string] $classpath) {
  if ($classpath -like "*;*") { return $classpath.Split(";") }
  return $classpath.Split(":")
}

function Build-ClasspathFromArgFile([string] $argfile) {
  $lines = Get-Content -LiteralPath $argfile
  if ($lines.Count -lt 2) { return $null }
  $parts = Split-Classpath $lines[1]
  $filtered = foreach ($part in $parts) {
    $p = Resolve-ExistingClasspathEntry $part
    if ([string]::IsNullOrWhiteSpace($p)) { continue }
    if ($p -match '\\build\\classes\\java\\main$') { continue }
    if ($p -match '\\build\\resources\\main$') { continue }
    if ($p -match "\\build\\libs\\$([regex]::Escape($ModId))-.*\.jar$") { continue }
    if ($p -match "\\build\\devlibs\\$([regex]::Escape($ModId))-.*\.jar$") { continue }
    $p
  }
  if (-not $filtered) { return $null }
  return ($filtered -join $ClasspathSeparator)
}

function Build-ClasspathFromRemap([string] $remapFile) {
  $classpath = Get-Content -LiteralPath $remapFile -Raw
  $classpath = $classpath.TrimStart([char]0xFEFF)
  $parts = Split-Classpath $classpath
  return (($parts | ForEach-Object { Resolve-ExistingClasspathEntry $_ }) -join $ClasspathSeparator)
}

$RunClientClasspathInitScript = $null
function Ensure-RunClientClasspathInitScript {
  if ($script:RunClientClasspathInitScript -and (Test-Path $script:RunClientClasspathInitScript)) { return }
  $script:RunClientClasspathInitScript = Join-Path ([IO.Path]::GetTempPath()) ("keyset-runclient-cp-init-{0}.gradle" -f ([guid]::NewGuid()))
  @'
gradle.projectsEvaluated {
  def projectPath = gradle.startParameter.projectProperties.get('keysetProjectPath')
  if (projectPath == null || projectPath.isBlank()) {
    throw new GradleException('Missing -PkeysetProjectPath for printRunClientClasspathFromInit')
  }

  def p = gradle.rootProject.findProject(projectPath)
  if (p == null) {
    throw new GradleException("Project not found: ${projectPath}")
  }

  p.tasks.register('printRunClientClasspathFromInit') {
    doLast {
      def runClientTask = p.tasks.findByName('runClient')
      if (runClientTask == null || !runClientTask.hasProperty('classpath')) {
        throw new GradleException("runClient classpath is unavailable for ${projectPath}")
      }
      println("RUNCLIENT_CP=${runClientTask.classpath.asPath}")
      (runClientTask.jvmArgs ?: []).each { arg ->
        println("RUNCLIENT_JVMARG=${arg}")
      }
    }
  }
}
'@ | Set-Content -LiteralPath $script:RunClientClasspathInitScript -Encoding UTF8
}

function Get-RunClientInfo([string] $gradleJavaHome, [string] $platformDir) {
  Ensure-RunClientClasspathInitScript
  $projectPath = Convert-PlatformDirToProjectPath $platformDir
  $oldJavaHome = $env:JAVA_HOME
  $oldPath = $env:Path
  try {
    $env:JAVA_HOME = $gradleJavaHome
    $env:Path = "$(Join-Path $gradleJavaHome 'bin');$oldPath"
    Push-Location $RepoRoot
    $output = & $Gradlew -q --console=plain -I $script:RunClientClasspathInitScript "-PkeysetProjectPath=$projectPath" "${projectPath}:printRunClientClasspathFromInit" 2>&1
    if ($LASTEXITCODE -ne 0) { return $null }
    return $output
  } finally {
    Pop-Location
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
  }
}

function Build-ClasspathFromGradleRunClient([string] $gradleJavaHome, [string] $platformDir) {
  $output = Get-RunClientInfo $gradleJavaHome $platformDir
  if (-not $output) { return $null }
  $cpLine = $output | Where-Object { $_ -like "RUNCLIENT_CP=*" } | Select-Object -Last 1
  if (-not $cpLine) { return $null }
  $parts = Split-Classpath ($cpLine -replace '^RUNCLIENT_CP=', '')
  $filtered = foreach ($part in $parts) {
    $p = Resolve-ExistingClasspathEntry $part
    if ([string]::IsNullOrWhiteSpace($p)) { continue }
    if ($p -match '\\build\\classes\\java\\main$') { continue }
    if ($p -match '\\build\\resources\\main$') { continue }
    if ($p -match "\\build\\libs\\$([regex]::Escape($ModId))-.*\.jar$") { continue }
    if ($p -match "\\build\\devlibs\\$([regex]::Escape($ModId))-.*\.jar$") { continue }
    $p
  }
  if (-not $filtered) { return $null }
  return ($filtered -join $ClasspathSeparator)
}

function Get-RunClientJvmArgs([string] $gradleJavaHome, [string] $platformDir) {
  $output = Get-RunClientInfo $gradleJavaHome $platformDir
  if (-not $output) { return @() }
  return @($output |
    Where-Object { $_ -like "RUNCLIENT_JVMARG=*" } |
    ForEach-Object { $_ -replace '^RUNCLIENT_JVMARG=', '' } |
    Where-Object {
      $_ -and
      -not $_.StartsWith("@") -and
      $_ -notin @("-cp", "-classpath") -and
      $_ -notlike "-Dfabric.dli.config=*" -and
      $_ -notlike "-Dfabric.dli.env=*" -and
      $_ -notlike "-Dfabric.dli.main=*"
    })
}

function Resolve-DliMainFromGradleRunClient([string] $gradleJavaHome, [string] $platformDir, [string] $fallbackMain) {
  $output = Get-RunClientInfo $gradleJavaHome $platformDir
  if (-not $output) { return $fallbackMain }
  $line = $output | Where-Object { $_ -like "RUNCLIENT_JVMARG=-Dfabric.dli.main=*" } | Select-Object -First 1
  if ($line) { return ($line -replace '^RUNCLIENT_JVMARG=-Dfabric\.dli\.main=', '') }
  return $fallbackMain
}

function Prepare-LaunchMetadata([string] $gradleJavaHome, [string] $loader, [string] $platformDir) {
  $projectPath = Convert-PlatformDirToProjectPath $platformDir
  $tasks = if ($loader -eq "fabric") {
    @("${projectPath}:configureClientLaunch", "${projectPath}:generateDLIConfig", "${projectPath}:generateRemapClasspath")
  } else {
    @("${projectPath}:configureClientLaunch", "${projectPath}:generateDLIConfig")
  }
  $oldJavaHome = $env:JAVA_HOME
  $oldPath = $env:Path
  try {
    $env:JAVA_HOME = $gradleJavaHome
    $env:Path = "$(Join-Path $gradleJavaHome 'bin');$oldPath"
    Push-Location $RepoRoot
    & $Gradlew @tasks *> $null
    if ($LASTEXITCODE -ne 0) { throw "Launch metadata preparation failed for $platformDir" }
  } finally {
    Pop-Location
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
  }
}

function Ensure-LoomAssetsDirectory {
  $assetsDir = Join-Path $env:USERPROFILE ".gradle\caches\fabric-loom\assets"
  if (-not (Test-Path $assetsDir)) {
    New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null
  }
}

function Prepare-Forge1211Runtime([string] $gradleJavaHome) {
  $oldJavaHome = $env:JAVA_HOME
  $oldPath = $env:Path
  try {
    $env:JAVA_HOME = $gradleJavaHome
    $env:Path = "$(Join-Path $gradleJavaHome 'bin');$oldPath"
    Push-Location $RepoRoot
    & $Gradlew ":platform-forge-1_21_1:prepareForgeDevFmlConfig" ":platform-forge-1_21_1:compileLauncherPatchJava" ":platform-forge-1_21_1:compileEventbusPatchJava" ":platform-forge-1_21_1:bootstrapPatchJar" *> $null
    if ($LASTEXITCODE -ne 0) { throw "Forge 1.21.1 runtime preparation failed" }
  } finally {
    Pop-Location
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
  }
}

function Normalize-FileToTemp([string] $sourceFile, [string] $suffix) {
  $temp = Join-Path ([IO.Path]::GetTempPath()) ("keyset-$suffix-{0}.tmp" -f ([guid]::NewGuid()))
  $text = Get-Content -LiteralPath $sourceFile -Raw
  $text = $text -replace [char]0xFEFF, ""
  $text = $text.Replace("/Users/beeboyd/Developer/MCMods/Keyset", $RepoRoot.Path.Replace("\", "/"))
  $text = $text.Replace("/Users/beeboyd/.gradle", (Join-Path $env:USERPROFILE ".gradle").Replace("\", "/"))
  $text = $text.Replace("/Users/beeboyd", $env:USERPROFILE.Replace("\", "/"))
  if ($suffix -eq "remap-classpath") {
    $text = $text.Replace("`r", "").Replace("`n", "")
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($temp, $text, $utf8NoBom)
    return $temp
  }
  Set-Content -LiteralPath $temp -Value $text -Encoding UTF8
  return $temp
}

function Get-RuntimeExtraClasspath([string] $label) {
  if ($label -eq "forge-1.21.1") {
    return Join-Path $RepoRoot "platforms\forge\1_21_1\build\libs\keyset-forge-1.21.1-$ProjectVersion-bootstrap-patch.jar"
  }
  return $null
}

function Get-RuntimeExtraJavaArgs([string] $label) {
  if ($label -eq "forge-1.21.1") {
    return @(
      "-Dkeyset.fmlloaderPatchPath=$(Join-Path $RepoRoot 'platforms\forge\1_21_1\build\classes\java\launcherPatch')",
      "-Dkeyset.eventbusPatchPath=$(Join-Path $RepoRoot 'platforms\forge\1_21_1\build\classes\java\eventbusPatch')"
    )
  }
  return @()
}

function Launch-Target(
  [string] $label,
  [hashtable] $metadata,
  [string] $dliMain,
  [string] $jarPath,
  [string] $launchCfg,
  [string] $runDir,
  [string] $argfile,
  [string] $remapClasspathFile,
  [string] $javaBin,
  [string] $dliJar,
  [string] $log4jUtilJar,
  [string] $gradleJavaHome
) {
  $runtimeClasspath = $null
  if (Test-Path $argfile) {
    $runtimeClasspath = Build-ClasspathFromArgFile $argfile
  }
  if (-not $runtimeClasspath) {
    $runtimeClasspath = Build-ClasspathFromGradleRunClient $gradleJavaHome $metadata.PlatformDir
  }
  if (-not $runtimeClasspath -and (Test-Path $remapClasspathFile)) {
    $runtimeClasspath = Build-ClasspathFromRemap $remapClasspathFile
    $runtimeClasspath = @($dliJar, $log4jUtilJar, $runtimeClasspath) -join $ClasspathSeparator
  }
  if (-not $runtimeClasspath) { throw "Could not build runtime classpath for $label" }

  $extraCp = Get-RuntimeExtraClasspath $label
  if ($extraCp) { $runtimeClasspath = @($runtimeClasspath, $extraCp) -join $ClasspathSeparator }

  $extraJavaArgs = New-Object System.Collections.Generic.List[string]
  Get-RuntimeExtraJavaArgs $label | ForEach-Object { $extraJavaArgs.Add($_) }
  Get-RunClientJvmArgs $gradleJavaHome $metadata.PlatformDir | ForEach-Object { $extraJavaArgs.Add($_) }

  $javaMajor = Get-JavaMajor $javaBin
  if ($metadata.Loader -in @("forge", "neoforge")) {
    $extraJavaArgs.Add("--add-opens")
    $extraJavaArgs.Add("java.base/java.lang.invoke=ALL-UNNAMED")
  }
  if ($metadata.Loader -eq "neoforge") {
    if ($javaMajor -ge 24) { $extraJavaArgs.Add("--sun-misc-unsafe-memory-access=allow") }
    $extraJavaArgs.Add("--enable-native-access=ALL-UNNAMED")
    $extraJavaArgs.Add("--add-exports")
    $extraJavaArgs.Add("jdk.naming.dns/com.sun.jndi.dns=java.naming")
  }

  $normalizedLaunchCfg = Normalize-FileToTemp $launchCfg "launch-cfg"
  Ensure-LoomAssetsDirectory
  if (Test-Path $remapClasspathFile) {
    $normalizedRemap = Normalize-FileToTemp $remapClasspathFile "remap-classpath"
    $text = Get-Content -LiteralPath $normalizedLaunchCfg -Raw
    $text = ($text -split "`r?`n" | ForEach-Object {
      if ($_ -like "`tfabric.remapClasspathFile=*") { "`tfabric.remapClasspathFile=$normalizedRemap" } else { $_ }
    }) -join [Environment]::NewLine
    Set-Content -LiteralPath $normalizedLaunchCfg -Value $text -Encoding UTF8
  }

  $modsDir = Join-Path $runDir "mods"
  New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
  Get-ChildItem $modsDir -Filter "$ModId-*.jar" -File -ErrorAction SilentlyContinue | Remove-Item -Force
  Copy-Item -LiteralPath $jarPath -Destination $modsDir -Force

  Write-Host ""
  Write-Host "=== Launching $label ==="
  Write-Host "Loader: $($metadata.Loader)"
  Write-Host "Runtime Java: $javaBin (major $javaMajor)"
  Write-Host "Run dir: $runDir"
  Write-Host "Mods jar: $(Join-Path $modsDir (Split-Path $jarPath -Leaf))"
  Write-Host ""

  Push-Location $runDir
  try {
    $javaArgs = @(
      "-cp", $runtimeClasspath,
      "-Dfabric.dli.config=$normalizedLaunchCfg",
      "-Dfabric.dli.env=client",
      "-Dfabric.dli.main=$dliMain",
      "-Dfile.encoding=UTF-8",
      "-Duser.language=en"
    ) + $extraJavaArgs.ToArray() + @("net.fabricmc.devlaunchinjector.Main")
    & $javaBin @javaArgs
    $script:LastLaunchExitCode = $LASTEXITCODE
  } finally {
    Pop-Location
  }
}

$requestedInputs = New-Object System.Collections.Generic.List[string]
$skipInputs = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -lt $args.Count; $i++) {
  switch ($args[$i]) {
    { $_ -in @("--help", "-h") } {
      Show-Usage
      exit 0
    }
    { $_ -in @("--skip", "-s") } {
      if ($i + 1 -ge $args.Count) { throw "Missing value for $($args[$i])" }
      $skipInputs.Add($args[$i + 1])
      $i++
    }
    default {
      $requestedInputs.Add($args[$i])
    }
  }
}

if ($requestedInputs.Count -eq 0) {
  Show-Usage
  exit 1
}

$requestedLabels = New-Object System.Collections.Generic.List[string]
foreach ($inputValue in $requestedInputs) {
  Split-Labels $inputValue | ForEach-Object { if ($_ ) { $requestedLabels.Add($_) } }
}

$skipLookup = @{}
foreach ($inputValue in $skipInputs) {
  Split-Labels $inputValue | ForEach-Object { $skipLookup[$_] = $true }
}

$filteredLabels = New-Object System.Collections.Generic.List[string]
$seenLabels = @{}
foreach ($label in $requestedLabels) {
  if ($skipLookup.ContainsKey($label)) { continue }
  if (-not $seenLabels.ContainsKey($label)) {
    $filteredLabels.Add($label)
    $seenLabels[$label] = $true
  }
}

if ($filteredLabels.Count -eq 0) { throw "All requested targets were excluded by --skip." }

$unknownLabels = @($filteredLabels | Where-Object { $_ -notin $AllLabels })
if ($unknownLabels.Count -gt 0) {
  throw "Unknown build target label(s): $($unknownLabels -join ', '). Valid labels: $($AllLabels -join ', ')"
}

$buildTargetsCsv = ($filteredLabels -join ",")
$gradleJavaBin = Require-GradleJavaBin
$gradleJavaHome = Split-Path (Split-Path $gradleJavaBin -Parent) -Parent

Write-Host "Using Gradle Java: $gradleJavaBin (major $(Get-JavaMajor $gradleJavaBin))"
Write-Host "Building release jars for: $buildTargetsCsv"
Write-Host ""

$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:Path
try {
  $env:JAVA_HOME = $gradleJavaHome
  $env:Path = "$(Join-Path $gradleJavaHome 'bin');$oldPath"
  Push-Location $RepoRoot
  & $Gradlew "validateBuildTargets" "buildSelectedTargets" "-PbuildTargets=$buildTargetsCsv"
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
  Pop-Location
  $env:JAVA_HOME = $oldJavaHome
  $env:Path = $oldPath
}

New-Item -ItemType Directory -Force -Path $ResultsDir | Out-Null
$csvPath = Join-Path $ResultsDir ("{0}-{1}.csv" -f $ProjectVersion, (Get-Date -Format "yyyyMMdd-HHmmss"))
Add-CsvRow $csvPath @("timestamp", "label", "loader", "platform_dir", "jar_path", "launch_exit_code", "result", "problems")

$dliJar = Locate-CacheJar "net.fabricmc" "dev-launch-injector" "dev-launch-injector-"
$log4jUtilJar = Locate-CacheJar "net.fabricmc" "fabric-log4j-util" "fabric-log4j-util-"

foreach ($label in $filteredLabels) {
  $timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:sszzz"
  $metadata = Resolve-TargetMetadata $label
  if (-not $metadata) {
    Add-CsvRow $csvPath @($timestamp, $label, "", "", "", "", "unknown_label", "Label is not mapped in scripts/windows/test-built-clients.ps1")
    continue
  }

  $platformDir = $metadata.PlatformDir
  $dliMain = Resolve-DliMainFromGradleRunClient $gradleJavaHome $platformDir $metadata.DliMain
  $jarPath = Resolve-RuntimeJarPath $label $metadata.Loader $platformDir
  $launchCfg = Join-Path $RepoRoot "$platformDir\.gradle\loom-cache\launch.cfg"
  $runDir = Join-Path $RepoRoot "$platformDir\run"
  $argfile = Join-Path $RepoRoot "$platformDir\build\loom-cache\argFiles\runClient"
  $remapClasspathFile = Join-Path $RepoRoot "$platformDir\.gradle\loom-cache\remapClasspath.txt"

  try { Prepare-LaunchMetadata $gradleJavaHome $metadata.Loader $platformDir | Out-Null } catch {}
  if ($label -eq "forge-1.21.1") { Prepare-Forge1211Runtime $gradleJavaHome }

  $javaBin = Find-JavaBin $metadata.JavaVersion
  if (-not $javaBin) {
    Write-Warning "Skipping ${label}: no Java $($metadata.JavaVersion)+ runtime available."
    Add-CsvRow $csvPath @($timestamp, $label, $metadata.Loader, $platformDir, $jarPath, "", "missing_java", "No Java $($metadata.JavaVersion)+ runtime available")
    continue
  }

  $missing = New-Object System.Collections.Generic.List[string]
  if (-not (Test-Path $jarPath)) { $missing.Add("jar") }
  if (-not (Test-Path $launchCfg)) { $missing.Add("launch.cfg") }
  if (-not (Test-Path $runDir)) { $missing.Add("run directory") }
  if (-not (Test-Path $argfile) -and -not (Test-Path $remapClasspathFile)) { $missing.Add("argfile/remapClasspath") }
  if ($missing.Count -gt 0) {
    $problems = "Missing required launch inputs: $($missing -join ', ')"
    Write-Warning "Skipping ${label}: $problems"
    Add-CsvRow $csvPath @($timestamp, $label, $metadata.Loader, $platformDir, $jarPath, "", "preflight_failed", $problems)
    continue
  }

  try {
    Launch-Target $label $metadata $dliMain $jarPath $launchCfg $runDir $argfile $remapClasspathFile $javaBin $dliJar $log4jUtilJar $gradleJavaHome
    $exitCode = $script:LastLaunchExitCode
  } catch {
    Add-CsvRow $csvPath @($timestamp, $label, $metadata.Loader, $platformDir, $jarPath, "", "launch_failed", $_.Exception.Message)
    continue
  }

  if ($exitCode -ne 0) {
    Write-Host "$label exited with code $exitCode."
  }

  do {
    $response = (Read-Host "Is everything working for ${label}? [yes/no]").Trim().ToLowerInvariant()
  } while ($response -notin @("y", "yes", "n", "no"))

  if ($response -in @("y", "yes")) {
    Add-CsvRow $csvPath @($timestamp, $label, $metadata.Loader, $platformDir, $jarPath, "$exitCode", "passed", "")
  } else {
    $problems = Read-Host "Describe the problems for ${label}"
    Add-CsvRow $csvPath @($timestamp, $label, $metadata.Loader, $platformDir, $jarPath, "$exitCode", "failed", $problems)
  }
}

Write-Host ""
Write-Host "Results written to $csvPath"
