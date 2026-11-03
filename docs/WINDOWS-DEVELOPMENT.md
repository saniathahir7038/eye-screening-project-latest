# Windows development

Checkout created on 2026-10-25 from `dec0f5617fa0faaf022216048d47e50f9708e0a4`.
The original setup branch was `dev/local-setup`. The current publishable source includes the 0.9.1 combined Android app; local environments, APKs, camera exports and signing keys are excluded from Git.

## Everyday commands

From the repository root in PowerShell (activation is unnecessary):

```powershell
./scripts/dev.ps1 -Module pterygium -Action test
./scripts/dev.ps1 -Module pterygium -Action run
./scripts/dev.ps1 -Module dedector -Action test
./scripts/dev.ps1 -Module smartkc -Action check
```

The interface binds to localhost. Stop it with Ctrl+C. The helper uses separate
environments and local Matplotlib caches. It reports nonzero program exits.

## Environments and recreation

The system Python 3.14 is unchanged. Local managed runtimes are under
`shared/python/`; environments are under `shared/environments/`:

| Module | Python | Dependency file |
| --- | --- | --- |
| Pterygium | 3.11.17 | `pterygium/requirements-windows.lock.txt` |
| DEDector | 3.10.22 | `dedector/requirements.txt` |
| SmartKC desktop | 3.10.22 | `smartkc/requirements-windows.lock.txt` |

The `uv` environment manager is installed locally in `shared/tools/bin/uv.exe`.
To recreate one environment, substitute the module, version and requirements path:

```powershell
$env:UV_PYTHON_INSTALL_DIR = "$PWD/shared/python"
$env:UV_CACHE_DIR = "$PWD/shared/tools/cache"
./shared/tools/bin/uv.exe python install 3.11
./shared/tools/bin/uv.exe venv --python 3.11 shared/environments/pterygium
./shared/tools/bin/uv.exe pip install --python shared/environments/pterygium/Scripts/python.exe -r pterygium/requirements-windows.lock.txt
```

SmartKC's original requirements are retained. The added Windows desktop file omits
Linux CUDA wheels, unused web packages and duplicate OpenCV providers, and uses
scikit-image 0.19.3 for a Python 3.10 Windows wheel. It covers desktop inference;
it does not promise compatibility with every historical auxiliary script.
The lock files capture the packages resolved on this PC, not a security audit.

## CLI workflows

SmartKC uses paths relative to its module directory. Its runner processes the
sample directory even when `--image_name` is supplied. Create the output parent:

```powershell
Set-Location smartkc
New-Item -ItemType Directory -Force artifacts | Out-Null
$env:MPLCONFIGDIR = "$PWD/../shared/environments/smartkc/mpl-cache"
$env:MPLBACKEND = 'Agg'
../shared/environments/smartkc/Scripts/python.exe main.py --n_mires 22 --working_distance 75.0 --camera_params "4.8 6.4 4.755" --model_file ./data/ring_distribution.txt --base_dir data/sample_images --center_selection auto --mire_seg_method dl --mire_loc_method graph_cluster --output_dir artifacts/local_setup_smoke --verbose
```

From `dedector/`, use `../shared/environments/dedector/Scripts/python.exe
src/video_processor.py --video C:/path/to/recording.mp4 --out_dir ../results/dedector`.
Use an individual video: folder mode currently stops after the first video and
`--frame` is a no-op. Real Placido footage is needed for meaningful NIBUT testing.

## Git and fork

The connected GitHub login is `CBE-SUHAIL`. The connector provides no fork
operation, and `gh` is not installed. No accessible fork was found at
`CBE-SUHAIL/eye-screening-project`; no fork was created.

`upstream` fetches the original repository. Its push URL deliberately points to
an invalid host to prevent accidental upstream writes. `origin` is intentionally
absent until a real fork exists; `remote.pushDefault` is set to `origin`.

Create the fork using GitHub's Fork button on
https://github.com/saniathahir7038/eye-screening-project, selecting `CBE-SUHAIL`.
Then, after verifying that repository belongs to this upstream network:

```powershell
git remote add origin https://github.com/CBE-SUHAIL/eye-screening-project.git
git fetch origin
git diff
git add docs/WINDOWS-DEVELOPMENT.md scripts/dev.ps1 smartkc/requirements-windows.txt smartkc/requirements-windows.lock.txt pterygium/requirements-windows.lock.txt dedector/src/constants.py .gitignore
git commit -m "Document and configure Windows development"
git push -u origin dev/local-setup
```

For later upstream updates, first commit or stash local work, then use
`git fetch upstream` and `git merge upstream/main`. GitHub connector access does
not automatically authenticate command-line pushes; use Git Credential Manager
if prompted. Never paste a token into a remote URL.

## Android status

Primary app: `pterygium/android_app/`, version `0.5.0-hardware-rc`, ARM64 only.
It uses Android Gradle Plugin 4.1.3, Gradle 7.0.2, compile/target SDK 33,
build-tools 29.0.2, TensorFlow Lite 2.14.0 and PyTorch Lite 1.13.1.
The documented build needs JDK 11. This PC has SDK 36/build-tools 36.x and
JDK 8/23, with no identified JDK 11 or SDK 33. No APK build or device validation
was completed in this setup. Do not silently upgrade the toolchain to make it run.

Install/select JDK 11 and the required SDK packages first, then create the ignored
`pterygium/android_app/local.properties`:

```properties
sdk.dir=C:/Users/admin/AppData/Local/Android/Sdk
```

In `pterygium/android_app/`, set `JAVA_HOME` to JDK 11 and `GRADLE_USER_HOME`
to an ignored workspace directory, then run
`./gradlew.bat testDebugUnitTest assembleDebug`. The APK will be under
`app/build/outputs/apk/debug/`. Repeat camera/gallery, orientation, quality-gate,
latency and model-parity checks on a physical ARM64 phone.

The legacy SmartKC app is separate and needs `-PsmartkcOffline=true` without
the private Firebase configuration. Its cloud path is not configured here.
