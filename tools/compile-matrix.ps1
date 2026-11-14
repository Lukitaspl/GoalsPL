# Compila GoalsPL contra todas las APIs de Paper soportadas (1.21.4 - 26.3).
# Uso: .\tools\compile-matrix.ps1 [-Tests]   (al terminar, vuelve a generar target\GoalsPL-<version>.jar normal)
#   -Tests  ademas de compilar, pasa las pruebas contra cada API (clean verify).
# La minima es 1.21.4 (la API contra la que se compila por defecto). Paper no publico 26.1.
param([switch]$Offline = $true, [switch]$Tests)
$root = Join-Path $PSScriptRoot '..'
Push-Location $root
$versions = @(
    '1.21.4-R0.1-SNAPSHOT',
    '1.21.5-R0.1-SNAPSHOT',
    '1.21.6-R0.1-SNAPSHOT',
    '1.21.7-R0.1-SNAPSHOT',
    '1.21.8-R0.1-SNAPSHOT',
    '1.21.9-R0.1-SNAPSHOT',
    '1.21.10-R0.1-SNAPSHOT',
    '1.21.11-R0.1-SNAPSHOT',
    '26.1.1.build.29-alpha',
    '26.1.2.build.74-stable',
    '26.2.build.135-stable',
    '26.3.build.249-stable'
)
$mvnArgs = @('-q')
if ($Offline) { $mvnArgs += '-o' }
$failed = @()
try {
    foreach ($v in $versions) {
        $args2 = $mvnArgs + @("-Dpaper.version=$v", 'clean', $(if ($Tests) { 'verify' } else { 'compile' }))
        $out = & .\mvnw.cmd @args2 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Output "OK     $v"
        } else {
            Write-Output "FALLO  $v"
            $out | Select-String 'ERROR.*\.java|cannot find symbol|symbol:' | Select-Object -First 10 | ForEach-Object { Write-Output "    $($_.Line)" }
            $failed += $v
        }
    }
    & .\mvnw.cmd @mvnArgs clean package 2>&1 | Out-Null
    Write-Output "JAR por defecto regenerado (exit $LASTEXITCODE)."
} finally {
    Pop-Location
}
if ($failed.Count -gt 0) { exit 1 }
