package com.eyescreening.pterygium;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

final class DryEyeVideoAnalyzer {
    enum State { NO_SIGNAL_CHANGE, EARLY_SIGNAL_CHANGE, SIGNAL_CHANGE, INCONCLUSIVE }
    static final class Result {
        final State state;final double signalIntervalSeconds,blinkSeconds,analysedSeconds;final String detail;
        Result(State state,double interval,double blink,double duration,String detail){this.state=state;this.signalIntervalSeconds=interval;this.blinkSeconds=blink;this.analysedSeconds=duration;this.detail=detail;}
    }
    private DryEyeVideoAnalyzer() {}
    static Result analyse(Context context,Uri uri) {
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();PlacidoSegmentationEngine engine=null;
        try {
            retriever.setDataSource(context,uri);
            String value=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long duration=value==null?0:Long.parseLong(value);
            if(duration<2500||duration>30000)return inconclusive(duration/1000.0,"Use a 3 to 30 second eye video with a comfortable blink and a stable open-eye interval.");
            engine=new PlacidoSegmentationEngine(context);
            double centreX=Double.NaN,centreY=Double.NaN,sideRatio=0;
            for(long time:new long[]{duration/3,duration*2/3,0}) {
                Bitmap anchor=frame(retriever,time);if(anchor==null)continue;
                PlacidoSegmentationEngine.Result ring=engine.analyseAligned(anchor);
                if(ring!=null&&ring.accepted) {
                    centreX=(ring.sourceLeft+ring.centerX*ring.sourceSide/512.0)/anchor.getWidth();
                    centreY=(ring.sourceTop+ring.centerY*ring.sourceSide/512.0)/anchor.getHeight();
                    sideRatio=ring.sourceSide/(double)Math.min(anchor.getWidth(),anchor.getHeight());
                }
                anchor.recycle();if(Double.isFinite(centreX))break;
            }
            if(!Double.isFinite(centreX))return inconclusive(duration/1000.0,"A reflected Placido-ring pattern was not recognised in the video. A general eye video is not enough for this test.");
            List<Long> times=new ArrayList<>();List<Double> means=new ArrayList<>(),edges=new ArrayList<>();List<double[]> images=new ArrayList<>();
            for(long time=0;time<duration;time+=125) {
                if(Thread.currentThread().isInterrupted())return inconclusive(duration/1000.0,"Analysis cancelled.");
                Bitmap decoded=frame(retriever,time);if(decoded==null)continue;
                PlacidoCropSearch.Window roi=PlacidoCropSearch.around(decoded.getWidth(),decoded.getHeight(),centreX*decoded.getWidth(),centreY*decoded.getHeight(),
                        (int)Math.round(sideRatio*Math.min(decoded.getWidth(),decoded.getHeight())));
                Bitmap crop=Bitmap.createBitmap(decoded,roi.left,roi.top,roi.side,roi.side);
                Bitmap small=Bitmap.createScaledBitmap(crop,160,160,true);
                int[] pixels=new int[160*160];small.getPixels(pixels,0,160,0,0,160,160);
                double[] gray=new double[pixels.length];double mean=0,edge=0;
                for(int i=0;i<pixels.length;i++){gray[i]=0.299*((pixels[i]>>16)&255)+0.587*((pixels[i]>>8)&255)+0.114*(pixels[i]&255);mean+=gray[i];}
                for(int y=1;y<159;y++)for(int x=1;x<159;x++){int i=y*160+x;edge+=Math.abs(gray[i]-gray[i-1])+Math.abs(gray[i]-gray[i-160]);}
                times.add(time);means.add(mean/gray.length);edges.add(edge/(158.0*158.0*2));images.add(gray);
                if(small!=crop&&small!=decoded)small.recycle();if(crop!=decoded)crop.recycle();decoded.recycle();
            }
            int n=times.size();long[] t=new long[n];double[] m=new double[n],e=new double[n];
            for(int i=0;i<n;i++){t[i]=times.get(i);m[i]=means.get(i);e[i]=edges.get(i);}
            TearSignalTimeline.Result signal=TearSignalTimeline.analyse(t,m,e);
            if(signal.kind==TearSignalTimeline.Kind.INCONCLUSIVE)return inconclusive(duration/1000.0,signal.detail);
            double[] baseline=images.get(signal.baselineIndex);
            for(int i=signal.baselineIndex+1;i<n;i++) {
                if(t[i]/1000.0>signal.blinkSeconds+signal.intervalSeconds+0.5)break;
                double[] motion=VideoMotionCheck.registration(baseline,images.get(i));
                if(Math.abs(motion[0])>6||Math.abs(motion[1])>6||motion[2]<0.65)
                    return inconclusive(duration/1000.0,"Motion, occlusion or focus change makes the ring-signal interval unreliable. Retake with steadier framing.");
            }
            State state=signal.kind==TearSignalTimeline.Kind.CHANGE?(signal.intervalSeconds<10?State.EARLY_SIGNAL_CHANGE:State.SIGNAL_CHANGE):State.NO_SIGNAL_CHANGE;
            return new Result(state,signal.intervalSeconds,signal.blinkSeconds,duration/1000.0,signal.detail+
                    " This is an experimental image-signal interval, not clinically validated NIBUT or a dry-eye diagnosis.");
        }catch(Exception error){return inconclusive(0,"Video analysis failed ("+error.getClass().getSimpleName()+"). Choose another saved recording.");}
        finally{if(engine!=null)try{engine.close();}catch(Exception ignored){}try{retriever.release();}catch(Exception ignored){}}
    }
    private static Bitmap frame(MediaMetadataRetriever retriever,long milliseconds) {
        return Build.VERSION.SDK_INT>=27?retriever.getScaledFrameAtTime(milliseconds*1000,MediaMetadataRetriever.OPTION_CLOSEST,1280,1280)
                :retriever.getFrameAtTime(milliseconds*1000,MediaMetadataRetriever.OPTION_CLOSEST);
    }
    private static Result inconclusive(double duration,String detail){return new Result(State.INCONCLUSIVE,Double.NaN,Double.NaN,duration,detail);}
}
