param(
    [string]$CapturePackage,
    [string]$CalibrationProfile,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$PythonExecutable = Join-Path $ProjectRoot 'shared/environments/smartkc/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $PythonExecutable)) { throw 'The SmartKC Windows environment is missing. Follow docs/WINDOWS-DEVELOPMENT.md.' }
if (-not $CapturePackage) {
    Add-Type -AssemblyName System.Windows.Forms
    $Picker = New-Object System.Windows.Forms.OpenFileDialog
    $Picker.Title = 'Choose the capture ZIP exported by Eye Screening'
    $Picker.Filter = 'Capture package (*.zip)|*.zip'
    if ($Picker.ShowDialog() -ne 'OK') { return }
    $CapturePackage = $Picker.FileName
}
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $ProjectRoot ('smartkc/artifacts/reconstruction-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$RunnerArguments = @((Join-Path $ProjectRoot 'smartkc/reconstruct_capture.py'), '--capture', $CapturePackage, '--output', $OutputDirectory)
if ($CalibrationProfile) { $RunnerArguments += @('--profile', $CalibrationProfile) }
& $PythonExecutable @RunnerArguments
if ($LASTEXITCODE -ne 0) { throw 'Reconstruction did not complete. Check the message above; unknown geometry must be measured before maps can be generated.' }
Write-Host "Open this package in the phone app: $OutputDirectory/result.zip"
