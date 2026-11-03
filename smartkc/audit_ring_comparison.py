"""Reproduce the four-example ring-feature audit, not clinical model training."""
import argparse,json
from pathlib import Path
import numpy as np
from PIL import Image
import torch
import torch.jit.mobile
NAMES=["nokc_left","nokc_right","kc_left","kc_right"]
SCALE=np.array([.01,.02,.02])

def features(labels):
    y,x=np.where(labels==1)
    if len(x)<80:raise ValueError("Reference centre was not segmented")
    cx,cy=x.mean(),y.mean();residuals=[];offsets=[];ellipses=[];radii=[]
    for label in range(2,16):
        y,x=np.where(labels==label);radius=np.hypot(x-cx,y-cy)
        angle=np.floor(np.degrees(np.arctan2(y-cy,x-cx))+360).astype(int)%360
        values=[];angles=[]
        for a in range(360):
            r=radius[(angle==a)&(radius>=5)&(radius<=512*.48)]
            if len(r):values.append(np.median(r));angles.append(a)
        if len(values)<240:continue
        theta=np.radians(angles)
        matrix=np.column_stack([np.ones(len(theta)),np.cos(theta),np.sin(theta),np.cos(2*theta),np.sin(2*theta)])
        coefficients=np.linalg.lstsq(matrix,values,rcond=None)[0]
        if coefficients[0]<=5:continue
        residuals.append(np.sqrt(np.mean((np.asarray(values)-matrix@coefficients)**2))/coefficients[0])
        radii.append(coefficients[0]);offsets.append(coefficients[1:3]);ellipses.append(coefficients[3:5]/coefficients[0])
    if len(residuals)<5:raise ValueError("Insufficient reference coverage")
    scatter=lambda a:np.sqrt(np.mean(np.sum((np.asarray(a)-np.mean(a,axis=0))**2,axis=1)))
    return np.array([np.median(residuals),scatter(offsets)/np.median(radii),scatter(ellipses)])

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--output",type=Path);args=parser.parse_args()
    repo=Path(__file__).resolve().parents[1]
    torch.set_num_threads(1)
    model=torch.jit.mobile._load_for_lite_interpreter(str(repo/"pterygium/android_app/app/src/main/assets/placido_segmentation.ptl"))
    mean=np.array([.485,.456,.406],dtype=np.float32)[:,None,None];std=np.array([.229,.224,.225],dtype=np.float32)[:,None,None]
    vectors=[]
    for name in NAMES:
        # Android's bundled resource replay is represented by the stored raster.
        # This is distinct from the EXIF-oriented live-photo input path.
        image=Image.open(repo/f"smartkc/data/sample_images/{name}.jpg").convert("RGB")
        w,h=image.size;side=min(w,h)//3;x=(w-side)//2;y=(h-side)//2
        rgb=np.asarray(image.crop((x,y,x+side,y+side)).resize((512,512),Image.Resampling.BILINEAR),dtype=np.float32)
        tensor=torch.from_numpy(((rgb.transpose(2,0,1)/255-mean)/std).copy()).unsqueeze(0)
        with torch.no_grad():labels=model.forward(tensor).argmax(1)[0].numpy()
        vectors.append(features(labels))
    vectors=np.asarray(vectors);rows=[]
    for i,name in enumerate(NAMES):
        others=np.arange(4)!=i;distance=np.sqrt(np.sum(((vectors-vectors[i])/SCALE)**2,axis=1));distance[i]=np.inf
        negative=float(distance[:2].min());positive=float(distance[2:].min());margin=abs(positive-negative)/max(1e-9,positive+negative)
        outside=bool(np.any(vectors[i]<vectors[others].min(0)-SCALE)|np.any(vectors[i]>vectors[others].max(0)+SCALE))
        outcome="INCONCLUSIVE" if outside or margin<.15 else "KC_REFERENCE" if positive<negative else "NON_KC_REFERENCE"
        rows.append(dict(example=name,features=vectors[i].tolist(),project_label="non-KC" if i<2 else "KC",held_out_outcome=outcome))
    report=dict(purpose="Engineering audit only; four examples are insufficient for clinical accuracy evaluation",examples=rows,
                outcome="Two wrong-group matches and two inconclusive outcomes. Candidate disease classifier not supported.",clinical_accuracy_established=False)
    text=json.dumps(report,indent=2,allow_nan=False);print(text)
    if args.output:args.output.write_text(text)
if __name__=="__main__":main()
