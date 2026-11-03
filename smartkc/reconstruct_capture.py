"""Reconstruct one auditable capture using the original SmartKC pipeline.

No server or internet is needed. Measured profiles and reference examples are
separate modes; this tool never substitutes study geometry for a phone profile.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import tempfile
import zipfile

SCHEMA = "eye-screening-capture/1"
PROFILE_SCHEMA = "eye-screening-calibration/1"
RESULT_SCHEMA = "eye-screening-result/1"
MAX_ENTRY = 40 * 1024 * 1024


def finite_number(value, name, low, high):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not low <= value <= high:
        raise ValueError(f"{name} must be a finite number between {low} and {high}")
    return float(value)


def validate_profile(profile, width, height):
    if profile.get("schema") != PROFILE_SCHEMA:
        raise ValueError("Unsupported calibration profile")
    if profile.get("attachment") != "original-smartkc":
        raise ValueError("This runner currently supports the unchanged original SmartKC attachment only")
    if profile.get("geometry_confirmed") is not True:
        raise ValueError("Calibration is a draft. Confirm measured geometry before reconstruction")
    if profile.get("image_width") != width or profile.get("image_height") != height:
        raise ValueError("Calibration resolution does not match the oriented source image")
    if width < 3000 or height < 4000 or abs(width/height - 0.75) > 0.001:
        raise ValueError("Use an untouched portrait 3:4 image, at least 3000x4000. Cropped images are detection-only")
    for field, low, high in [
        ("sensor_width_mm", 1, 20), ("sensor_height_mm", 1, 20),
        ("focal_length_mm", 1, 30), ("capture_zoom", 1, 10),
        ("working_distance_mm", 30, 150), ("camera_gap_mm", 0, 30)]:
        finite_number(profile.get(field), field, low, high)
    if abs(profile["sensor_width_mm"]/profile["sensor_height_mm"] - width/height) > 0.02:
        raise ValueError("Effective sensor dimensions must match the oriented image aspect ratio")
    return profile


def read_capture(path, require_original=True):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate capture entries")
        required = {"request.json", "calibration.json", "source_image"}
        if not required.issubset(names) or len(names) > 6:
            raise ValueError("Incomplete capture package")
        if any(i.file_size > MAX_ENTRY for i in archive.infolist()):
            raise ValueError("Capture entry too large")
        for name in ("request.json", "calibration.json"):
            if archive.getinfo(name).file_size > 65536:
                raise ValueError("Capture metadata too large")
        request = json.loads(archive.read("request.json"))
        profile = json.loads(archive.read("calibration.json"))
        image = archive.read("source_image")
    if request.get("schema") != SCHEMA:
        raise ValueError("Unsupported capture schema")
    if hashlib.sha256(image).hexdigest() != request.get("source_sha256"):
        raise ValueError("Source checksum mismatch")
    if require_original and request.get("source_uncropped") is not True:
        raise ValueError("Capture was not confirmed as an untouched original; detection only")
    if request.get("eye") not in ("left", "right"):
        raise ValueError("Capture must identify left or right eye")
    return request, profile, image


def validate_metrics(values):
    if len(values) != 6 or not all(math.isfinite(float(v)) for v in values):
        raise ValueError("Reconstruction returned incomplete or non-finite measurements")
    return dict(zip(["sim_k1_d", "sim_k2_d", "axis_degrees", "mean_k_d", "difference_d", "ppk_research"], map(float, values)))


def run_pipeline(image_path, profile, output, center=None, reference=False):
    print("Loading SmartKC reconstruction...", flush=True)
    # Imports are delayed so input/profile validation does not load the ML stack.
    cache = Path(output).resolve().parent / ".plot-cache"
    cache.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("MPLCONFIGDIR", str(cache))
    os.environ.setdefault("MPLBACKEND", "Agg")
    os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
    os.environ.setdefault("OMP_NUM_THREADS", "1")
    os.environ.setdefault("MKL_NUM_THREADS", "1")
    import cv2
    import numpy as np
    import torch
    from PIL import Image, ImageOps
    from main import corneal_top_gen
    from constants import Constants
    torch.set_num_threads(1)
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=False)
    with Image.open(image_path) as opened:
        image = ImageOps.exif_transpose(opened).convert("RGB")
        # OpenCV already honours JPEG EXIF orientation. Preserve original JPEG
        # bytes at the supported resolution; avoid changing ring edges by re-encoding.
        untouched_jpeg = opened.format == "JPEG" and image.size == (3000,4000)
    if not reference:
        validate_profile(profile, *image.size)
    # Uniform resampling preserves the full field of view. No crop or stretch.
    image = image.resize((3000, 4000), Image.Resampling.BILINEAR)
    inputs = output / "input"
    inputs.mkdir()
    if reference or untouched_jpeg:
        shutil.copyfile(image_path, inputs / "capture.jpg")
    else:
        image.save(inputs / "capture.jpg", quality=100, subsampling=0)
    curve = np.poly1d([3617.81645183, -17.2737687]) if reference else None
    pipeline = corneal_top_gen(str(Path(__file__).parent/"data/ring_distribution.txt"),
        profile["working_distance_mm"],
        (profile["sensor_width_mm"], profile["sensor_height_mm"]),
        profile["focal_length_mm"], 0, 360, 1,
        Constants.IMG_PROC_SEG_PARAMS["UPSAMPLE"], 22, curve, str(output / "pipeline"), zernike_degree=[8])
    print("Segmenting rings and reconstructing the surface; this may take several minutes...", flush=True)
    errors, sims, images = pipeline.generate_topography_maps(str(inputs), "capture.jpg", "dl", "graph_cluster",
        err2=profile["camera_gap_mm"],
        center_selection="manual-android" if center is not None else "auto", marked_center=center,
        capture_zoom=profile["capture_zoom"], measured_gap_top=None if reference else 0.0,
        anchor_marked_center=center is not None)
    metrics = validate_metrics(sims)
    if not cv2.imwrite(str(output/"axial.png"), images[4]) or not cv2.imwrite(str(output/"tangential.png"), images[3]):
        raise IOError("Could not write map images")
    return metrics


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture", type=Path, help="ZIP exported by the Android app")
    parser.add_argument("--profile", type=Path, help="Optional completed calibration JSON replacing the exported draft")
    parser.add_argument("--output", required=True, type=Path, help="New output directory (must not already exist)")
    parser.add_argument("--reference", choices=["nokc_left", "nokc_right", "kc_left", "kc_right"],
                        help="Engineering reference only; never applies these parameters to a user capture")
    args = parser.parse_args(argv)
    if bool(args.capture) == bool(args.reference):
        parser.error("Choose exactly one of --capture and --reference")
    args.output = args.output.resolve()
    if args.capture: args.capture = args.capture.resolve()
    if args.profile: args.profile = args.profile.resolve()
    root = Path(__file__).resolve().parent
    previous = Path.cwd()
    os.chdir(root) # original modules resolve their model weights from the module directory
    try:
        if args.reference:
            if args.profile:
                parser.error("Reference mode cannot use a user calibration profile")
            source = root / f"data/sample_images/{args.reference}.jpg"
            source_bytes = source.read_bytes()
            profile = {"mode":"reference-only", "attachment":"original-smartkc", "sensor_width_mm":4.8,
                       "sensor_height_mm":6.4, "focal_length_mm":4.755, "working_distance_mm":75.0,
                       "camera_gap_mm":4.0, "capture_zoom":2.0}
            request = {"eye": args.reference.split("_")[-1], "capture_id":"reference-"+args.reference}
            center = None
            metrics = run_pipeline(source, profile, args.output, reference=True)
        else:
            request, profile, source_bytes = read_capture(args.capture)
            if args.profile:
                profile = json.loads(args.profile.read_text(encoding="utf-8"))
            from PIL import Image, ImageOps
            import io
            with Image.open(io.BytesIO(source_bytes)) as opened:
                size = ImageOps.exif_transpose(opened).size
            validate_profile(profile, *size)
            center = request.get("center_normalized")
            if center is None or len(center)!=2 or not all(isinstance(v,(float,int)) and math.isfinite(v) and 0<v<1 for v in center):
                raise ValueError("Export a capture with a detected centre before reconstruction")
            args.output.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(dir=args.output.parent) as temp:
                source = Path(temp)/"source"
                source.write_bytes(source_bytes)
                metrics = run_pipeline(source, profile, args.output, center=center)
        source_hash = hashlib.sha256()
        for name in ("reconstruct_capture.py","main.py","preprocess.py","arc_step_method.py","zernike_smartkc.py",
                     "mire_detection_graph.py","segment_mires.py","metrics.py","get_maps.py"):
            source_hash.update(name.encode());source_hash.update((root/name).read_bytes())
        manifest = {"algorithm_source_sha256":source_hash.hexdigest(),
                    "ring_distribution_sha256":hashlib.sha256((root/"data/ring_distribution.txt").read_bytes()).hexdigest(),
                    "schema":RESULT_SCHEMA, "capture_id":request["capture_id"], "eye":request["eye"],
                    "source_sha256":hashlib.sha256(source_bytes).hexdigest(),
                    "mode":"reference-only" if args.reference else "measured-research",
                    "algorithm":"SmartKC original Python; dl/graph_cluster; Arc-Step; Zernike degree 8",
                    "calibration":profile, "center_normalized":center, "metrics":metrics,
                    "notice":"Research reconstruction, not a diagnosis or validated clinical measurement"}
        (args.output/"manifest.json").write_text(json.dumps(manifest, indent=2, allow_nan=False))
        with zipfile.ZipFile(args.output/"result.zip", "w", zipfile.ZIP_DEFLATED) as archive:
            for name in ("manifest.json", "axial.png", "tangential.png"):
                archive.write(args.output/name, name)
        print(json.dumps(manifest, indent=2))
        print(f"Result package: {args.output/'result.zip'}")
    finally:
        os.chdir(previous)


if __name__ == "__main__":
    main()
