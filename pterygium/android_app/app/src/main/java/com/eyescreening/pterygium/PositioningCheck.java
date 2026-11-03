package com.eyescreening.pterygium;

import java.util.ArrayList;
import java.util.List;

/** Engineering checks of a photographed reflection, independent of calibration. */
final class PositioningCheck {
    static final class Result {
        final boolean ringsVisible, framingOkay, detailOkay, exposureOkay, usable;
        final double detail, contrast, clippedFraction;
        final List<String> guidance;
        Result(boolean rings,boolean frame,boolean detailOkay,boolean exposure,double detail,
                double contrast,double clipped,List<String> guidance) {
            this.ringsVisible=rings;this.framingOkay=frame;this.detailOkay=detailOkay;
            this.exposureOkay=exposure;this.detail=detail;this.contrast=contrast;
            this.clippedFraction=clipped;this.guidance=guidance;
            this.usable=rings && frame && detailOkay && exposure;
        }
    }
    static Result assess(int[] pixels,int size,double cx,double cy,int rings,double frameX,double frameY) {
        if(size<3 || pixels.length!=size*size)throw new IllegalArgumentException("Invalid analysis image");
        boolean centre=Double.isFinite(cx)&&Double.isFinite(cy)&&cx>=0&&cy>=0&&cx<size&&cy<size;
        boolean visible=rings>=8 && centre;
        boolean frame=Double.isFinite(frameX)&&Double.isFinite(frameY)
                &&frameX>=0.30&&frameX<=0.70&&frameY>=0.30&&frameY<=0.70;
        double sum=0,squares=0,lapSum=0,lapSquares=0;int count=0,clipped=0;
        if(centre) {
            double radius=size*0.30;
            for(int y=1;y<size-1;y++)for(int x=1;x<size-1;x++) {
                if(Math.hypot(x-cx,y-cy)>radius)continue;
                int i=y*size+x;double value=gray(pixels[i]);
                double lap=gray(pixels[i-1])+gray(pixels[i+1])+gray(pixels[i-size])+gray(pixels[i+size])-4*value;
                sum+=value;squares+=value*value;lapSum+=lap;lapSquares+=lap*lap;
                if(value>=248)clipped++;count++;
            }
        }
        double variance=count==0?0:Math.max(0,squares/count-Math.pow(sum/count,2));
        double contrast=Math.sqrt(variance);
        double detail=count==0?0:Math.max(0,lapSquares/count-Math.pow(lapSum/count,2))/Math.max(1,variance);
        double clipping=count==0?0:(double)clipped/count;
        boolean edges=centre&&detail>=0.01&&contrast>=12;
        boolean exposure=centre&&contrast>=12&&clipping<0.15;
        List<String> guidance=new ArrayList<>();
        if(!visible)guidance.add("Too few rings recognised. Keep the reflected pattern visible, refocus on the eye and try again.");
        if(centre && !frame) {
            if(frameX<0.30)guidance.add("The reflection is far left in the photo. Bring it toward the middle of the camera view.");
            else if(frameX>0.70)guidance.add("The reflection is far right in the photo. Bring it toward the middle of the camera view.");
            if(frameY<0.30)guidance.add("The reflection is high in the photo. Bring it toward the middle of the camera view.");
            else if(frameY>0.70)guidance.add("The reflection is low in the photo. Bring it toward the middle of the camera view.");
        }
        if(centre&&!edges)guidance.add("Fine ring edges are weak. Tap to focus on the reflected rings and hold both hands steadier for the next photo.");
        if(centre&&!exposure)guidance.add(clipping>=0.15?"Bright detail is clipped. Reduce glare or camera exposure and try again.":"Ring contrast is low. Check attachment illumination and focus.");
        if(visible&&frame&&edges&&exposure)guidance.add("Keep this framing and focus. The reflection passed the photo checks; use this position as a starting point for repeat captures.");
        return new Result(visible,frame,edges,exposure,detail,contrast,clipping,guidance);
    }
    private static double gray(int argb) {
        return 0.299*((argb>>16)&255)+0.587*((argb>>8)&255)+0.114*(argb&255);
    }
}
