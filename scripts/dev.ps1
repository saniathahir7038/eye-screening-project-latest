param(
    [ValidateSet('pterygium', 'dedector', 'smartkc')]
    [string]$Module = 'pterygium',
    [ValidateSet('test', 'run', 'check')]
    [string]$Action = 'check'
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$env:MPLCONFIGDIR = Join-Path $repo "shared/environments/$Module/mpl-cache"
New-Item -ItemType Directory -Force -Path $env:MPLCONFIGDIR | Out-Null
$python = Join-Path $repo "shared/environments/$Module/Scripts/python.exe"
if (-not (Test-Path $python)) { throw "Environment missing: $python. See docs/WINDOWS-DEVELOPMENT.md." }
Push-Location (Join-Path $repo $Module)
try {
    switch ($Action) {
        'check' {
            if ($Module -eq 'pterygium') { & $python -c 'import tensorflow, cv2, streamlit; print("Pterygium imports OK")' }
            elseif ($Module -eq 'dedector') { & $python src/video_processor.py --help }
            else { & $python main.py --help }
        }
        'test' {
            if ($Module -eq 'smartkc') { throw 'No maintained SmartKC unit suite. Use check and the documented bundled-image smoke command.' }
            if ($Module -eq 'pterygium') { & $python -m pytest tests -q }
            else { & $python -m unittest discover -s tests -v }
        }
        'run' {
            if ($Module -ne 'pterygium') { throw 'Use the documented CLI with explicit input and output paths for this module.' }
            & $python -m streamlit run app.py --server.address 127.0.0.1 --server.headless true --browser.gatherUsageStats false
        }
    }
    if ($LASTEXITCODE -ne 0) { throw "$Module $Action failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
