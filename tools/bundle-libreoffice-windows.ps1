# Copies your installed LibreOffice into the project so the Windows installer
# carries the office engine with it (no separate LibreOffice install needed on
# the target PC). Only needed when you start distributing the app.
#
# Usage (PowerShell, from the project folder):
#     powershell -ExecutionPolicy Bypass -File tools\bundle-libreoffice-windows.ps1
#
# Adds roughly 350–450 MB to the installer.

$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $PSScriptRoot
$target = Join-Path $project "composeApp\resources\windows\libreoffice"

$candidates = @(
    "$env:ProgramFiles\LibreOffice",
    "$env:ProgramW6432\LibreOffice",
    "${env:ProgramFiles(x86)}\LibreOffice",
    "$env:LOCALAPPDATA\Programs\LibreOffice"
) | Where-Object { $_ -and (Test-Path (Join-Path $_ "program\soffice.exe")) }

if (-not $candidates) {
    Write-Error "LibreOffice isn't installed. Install it from https://www.libreoffice.org/download/ and run this again."
}
$source = $candidates[0]

Write-Host "Copying $source -> $target"
if (Test-Path $target) { Remove-Item -Recurse -Force $target }
New-Item -ItemType Directory -Force -Path $target | Out-Null
Copy-Item -Recurse -Force (Join-Path $source "*") $target

# Help files and the user-facing launchers aren't needed for headless rendering.
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path $target "help")

$size = "{0:N0} MB" -f ((Get-ChildItem -Recurse $target | Measure-Object -Sum Length).Sum / 1MB)
Write-Host "Done ($size). Rebuild the installer with: .\gradlew.bat :composeApp:packageReleaseMsi"
