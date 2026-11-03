"""Supervised research simulation using explicitly assumed study geometry.

This is NOT the calibrated reconstruction workflow or a disease diagnosis.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import zipfile
from reconstruct_capture import read_capture, finite_number, run_pipeline, RESULT_SCHEMA


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture",required=True,type=Path)
    parser.add_argument("--output",required=True,type=Path)
    parser.add_argument("--assumed-study-geometry",action="store_true",required=True)
    parser.add_argument("--confirm-original",action="store_true",help="Confirm an untouched original when the app export checkbox was not selected")
    parser.add_argument("--sensor-width-mm",required=True,type=float)
    parser.add_argument("--sensor-height-mm",required=True,type=float)
    parser.add_argument("--focal-length-mm",required=True,type=float)
    parser.add_argument("--zoom",required=True,type=float)
    parser.add_argument("--assumed-distance-mm",type=float,default=75.0)
    args=parser.parse_args(argv)
    capture=args.capture.resolve();output=args.output.resolve()
    request,_,source=read_capture(capture,require_original=not args.confirm_original)
    from PIL import Image,ImageOps,ImageDraw,ImageFont
    with Image.open(io.BytesIO(source)) as image:
        if ImageOps.exif_transpose(image).size!=(3000,4000):
            raise ValueError("This initial simulation requires an untouched oriented 3000x4000 source")
    for value,name,low,high in [(args.sensor_width_mm,"sensor width",1,20),(args.sensor_height_mm,"sensor height",1,20),
            (args.focal_length_mm,"focal length",1,30),(args.zoom,"zoom",1,10),(args.assumed_distance_mm,"assumed distance",30,150)]:
        finite_number(value,name,low,high)
    if abs(args.sensor_width_mm/args.sensor_height_mm-.75)>.02:raise ValueError("Sensor axes must match portrait orientation")
    center=request.get("center_normalized")
    if not center or len(center)!=2:raise ValueError("Capture has no source centre")
    for value in center:finite_number(value,"normalised centre",.001,.999)
    profile={"mode":"assumed-study-geometry-demo","attachment":"original-smartkc","geometry_confirmed":False,
        "image_width":3000,"image_height":4000,"sensor_width_mm":args.sensor_width_mm,"sensor_height_mm":args.sensor_height_mm,
        "focal_length_mm":args.focal_length_mm,"capture_zoom":args.zoom,
        "working_distance_mm":args.assumed_distance_mm,"camera_gap_mm":4.0,
        "warning":f"Nominal distance {args.assumed_distance_mm:g} mm, camera gap 4 mm and the study gap-correction curve are assumed, not measured for this hold. Research demonstration only."}
    previous=Path.cwd();root=Path(__file__).resolve().parent
    output.parent.mkdir(parents=True,exist_ok=True)
    try:
        os.chdir(root)
        with tempfile.TemporaryDirectory(dir=output.parent) as temp:
            path=Path(temp)/"source";path.write_bytes(source)
            metrics=run_pipeline(path,profile,output,center=center,reference=True)
        for name in ("axial.png","tangential.png"):
            with Image.open(output/name) as image:
                canvas=Image.new("RGB",(image.width,image.height+54),"#ffffff")
                canvas.paste(image,(0,54));draw=ImageDraw.Draw(canvas)
                draw.text((8,7),"SIMULATION: ASSUMED GEOMETRY",fill="#b00020")
                draw.text((8,25),"NOT A DIAGNOSIS OR MEASURED CORNEAL MAP",fill="#b00020")
                canvas.save(output/name)
        score=metrics["ppk_research"]
        manifest={"schema":RESULT_SCHEMA,"mode":"assumed-study-geometry-demo","research_demo":True,
            "capture_id":request["capture_id"],"eye":request["eye"],"source_sha256":hashlib.sha256(source).hexdigest(),
            "original_confirmed_via_cli":args.confirm_original,"center_normalized":center,"calibration":profile,"metrics":metrics,
            "algorithm":"Original SmartKC dl/graph_cluster, Arc-Step and degree-8 Zernike under study-geometry assumptions",
            "algorithm_source_sha256":hashlib.sha256((root/"main.py").read_bytes()+(root/"preprocess.py").read_bytes()).hexdigest(),
            "study_threshold_flag":"triggered" if score>=.45 else "borderline" if score>=.20 else "not-triggered",
            "notice":"Thresholds originate from published reference-device conventions. This simulated score is not the person's probability of keratoconus, and this workflow has no established clinical accuracy."}
        (output/"manifest.json").write_text(json.dumps(manifest,indent=2,allow_nan=False))
        with zipfile.ZipFile(output/"SIMULATION-result.zip","w",zipfile.ZIP_DEFLATED) as archive:
            for name in ("manifest.json","axial.png","tangential.png"):archive.write(output/name,name)
        print(json.dumps(manifest,indent=2));print("SUPERVISED DEMONSTRATION ONLY: "+str(output/"SIMULATION-result.zip"))
    finally:os.chdir(previous)

if __name__=="__main__":main()
