param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$PluginJar = "target/CustomSpawners-1.0.0.jar"
)
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$output = Join-Path $projectRoot ".tools/smoke"
New-Item -ItemType Directory -Path (Join-Path $output "classes") -Force | Out-Null
$pluginPath = (Resolve-Path (Join-Path $projectRoot $PluginJar)).Path
$paperPath = Join-Path $env:USERPROFILE ".m2/repository/io/papermc/paper/paper-api/1.21.11-R0.1-SNAPSHOT/paper-api-1.21.11-R0.1-SNAPSHOT.jar"
if (!(Test-Path -LiteralPath $paperPath)) { throw "Build the main project first to cache the Paper dependency." }
$dependencies = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE ".m2/repository") -Filter "*.jar" -Recurse | Where-Object { $_.FullName -notmatch "[\\/]org[\\/]spigotmc[\\/]|[\\/]io[\\/]papermc[\\/]paper[\\/]paper-api[\\/]" } | Select-Object -ExpandProperty FullName
$classPath = (@($paperPath, $pluginPath) + @($dependencies)) -join ";"
$argumentsFile = Join-Path $output "javac.args"
$arguments = @("--release", "21", "-proc:none", "-encoding", "UTF-8", "--class-path", ('"' + $classPath.Replace('\', '/') + '"'), "-d", ('"' + (Join-Path $output "classes").Replace('\', '/') + '"'))
Get-ChildItem -LiteralPath $PSScriptRoot -Filter "*.java" | ForEach-Object { $arguments += '"' + $_.FullName.Replace('\', '/') + '"' }
Set-Content -LiteralPath $argumentsFile -Value $arguments -Encoding utf8NoBOM
& (Join-Path $JavaHome "bin/javac.exe") "@$argumentsFile"
if ($LASTEXITCODE -ne 0) { throw "Smoke harness compilation failed." }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "plugin.yml") -Destination (Join-Path $output "classes/plugin.yml")
$jarPath = Join-Path $output "CustomSpawnersSmoke.jar"
& (Join-Path $JavaHome "bin/jar.exe") --create --file $jarPath -C (Join-Path $output "classes") .
if ($LASTEXITCODE -ne 0) { throw "Smoke harness packaging failed." }
Write-Output $jarPath
