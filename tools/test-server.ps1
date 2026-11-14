# Copia el JAR al servidor de pruebas, lo arranca, ejecuta comandos de consola y lo detiene.
# Uso: .\tools\test-server.ps1 -Server <carpeta del servidor> [-Java <java.exe>] [-Port 25590] -Commands "goals help","goals reload"
#   -Server  carpeta del servidor de pruebas (o variable de entorno GOALSPL_TEST_SERVER)
#   -Java    java.exe a usar (o GOALSPL_TEST_JAVA; por defecto, el java del PATH)
#   -Port    puerto alternativo (util si otro servidor usa el del server.properties)
#   El servidor debe arrancarse con un JAR llamado purpur*.jar o paper*.jar en su carpeta.
param(
    [string]$Server = $env:GOALSPL_TEST_SERVER,
    [string]$Java = $(if ($env:GOALSPL_TEST_JAVA) { $env:GOALSPL_TEST_JAVA } else { 'java' }),
    [int]$Port = 0,
    [string[]]$Commands = @(),
    [int]$StartTimeout = 180,
    [int]$CommandDelay = 3
)
$ErrorActionPreference = 'Stop'
if (-not $Server -or -not (Test-Path $Server)) {
    throw 'Indica la carpeta del servidor de pruebas con -Server o con la variable GOALSPL_TEST_SERVER.'
}
$server = $Server
$java = $Java
# El JAR lleva la version al final (GoalsPL-<version>.jar); se usa el mas reciente de target.
$jar = Get-ChildItem (Join-Path $PSScriptRoot '..\target') -Filter 'GoalsPL-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) {
    throw 'No hay ningun target\GoalsPL-<version>.jar. Compila antes con .\mvnw.cmd clean package.'
}
$serverJar = Get-ChildItem $server -Filter '*.jar' | Where-Object { $_.Name -match '^(purpur|paper)' } | Select-Object -First 1
if (-not $serverJar) {
    throw "No hay ningun purpur*.jar ni paper*.jar en $server."
}

# Quitar cualquier GoalsPL anterior (con o sin version) para no cargar dos copias.
Get-ChildItem (Join-Path $server 'plugins') -Filter 'GoalsPL*.jar' | Remove-Item -Force
Copy-Item $jar.FullName (Join-Path $server ('plugins\' + $jar.Name)) -Force
$log = Join-Path $server 'logs\latest.log'

$psi = New-Object System.Diagnostics.ProcessStartInfo $java
$psi.Arguments = "-Xmx2G -jar `"$($serverJar.Name)`" --nogui" + $(if ($Port -gt 0) { " --port $Port" } else { '' })
$psi.WorkingDirectory = $server
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$proc = [System.Diagnostics.Process]::Start($psi)
$out = New-Object System.Collections.Concurrent.ConcurrentQueue[string]
$handler = { if ($EventArgs.Data) { $Event.MessageData.Enqueue($EventArgs.Data) } }
$e1 = Register-ObjectEvent $proc OutputDataReceived -Action $handler -MessageData $out
$e2 = Register-ObjectEvent $proc ErrorDataReceived -Action $handler -MessageData $out
$proc.BeginOutputReadLine(); $proc.BeginErrorReadLine()

$lines = New-Object System.Collections.Generic.List[string]
function Drain { $l = $null; while ($out.TryDequeue([ref]$l)) { $lines.Add($l) } }

$deadline = (Get-Date).AddSeconds($StartTimeout)
while ((Get-Date) -lt $deadline -and -not $proc.HasExited) {
    Start-Sleep -Milliseconds 500; Drain
    if ($lines | Where-Object { $_ -match 'Done \(' }) { break }
}
# La primera escritura en stdin lleva una marca BOM; se envia una linea vacia para absorberla.
$proc.StandardInput.WriteLine(''); $proc.StandardInput.Flush()
foreach ($c in $Commands) {
    $lines.Add(">>> $c")
    $proc.StandardInput.WriteLine($c); $proc.StandardInput.Flush()
    Start-Sleep -Seconds $CommandDelay; Drain
}
if (-not $proc.HasExited) {
    $proc.StandardInput.WriteLine('stop'); $proc.StandardInput.Flush()
    if (-not $proc.WaitForExit(60000)) { $proc.Kill() }
}
Start-Sleep -Milliseconds 500; Drain
Unregister-Event -SourceIdentifier $e1.Name; Unregister-Event -SourceIdentifier $e2.Name
$first = [Math]::Max(0, $lines.FindIndex({ param($l) $l -like '>>>*' }))
$lines | Select-Object -First $first | Where-Object { $_ -match 'ERROR|WARN|Exception|^\s+at |GoalsPL' }
$lines | Select-Object -Skip $first
