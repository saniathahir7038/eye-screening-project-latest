package com.eyescreening.pterygium;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;

final class DryEyeVideoAnalyzer {
    enum State { NO_SIGNAL_CHANGE, EARLY_SIGNAL_CHANGE, SIGNAL_CHANGE, PARTIAL_OBSERVATION, INCONCLUSIVE }
    static final class Result {
        final State state;final double signalIntervalSeconds,blinkSeconds,analysedSeconds;final String detail;
        java.io.File reportZip;
        String reportError;
        Result(State state,double interval,double blink,double duration,String detail){this.state=state;this.signalIntervalSeconds=interval;this.blinkSeconds=blink;this.analysedSeconds=duration;this.detail=detail;}
    }
    private DryEyeVideoAnalyzer() {}
    static Result analyse(Context context,Uri uri) {
        DryEyeReport report=null;String error=null;
        try{report=new DryEyeReport(context,uri);}catch(Exception failure){error=failure.getClass().getSimpleName();}
        Result result=analyse(context,uri,report);
        if(report!=null)try{result.reportZip=report.finish(result);}catch(Exception failure){error=failure.getClass().getSimpleName();}
        result.reportError=error;
        return result;
    }
    private static Result analyse(Context context,Uri uri,DryEyeReport report) {
        MediaMetadataRetriever retriever=new MediaMetadataRetriever();PlacidoSegmentationEngine engine=null;
        try {
            retriever.setDataSource(context,uri);
            String value=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long duration=value==null?0:Long.parseLong(value);
            if(report!=null)report.videoMetadata(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),value);
            if(duration<2500||duration>30000)return inconclusive(duration/1000.0,"Use a 3 to 30 second eye video with a comfortable blink and a stable open-eye interval.");
            engine=new PlacidoSegmentationEngine(context);
            TrackedTearSignal timeline=null;
            for(long time:new long[]{duration/3,duration*2/3,0}) {
                Bitmap anchor=frame(retriever,time);if(anchor==null)continue;
                PlacidoSegmentationEngine.Result ring=engine.analyseAligned(anchor);
                if(ring!=null&&ring.accepted) {
                    if(report!=null)report.anchor(time,anchor,ring);
                    RingVideoTracker.Frame trackingAnchor=trackingFrame(anchor);
                    double scale=trackingAnchor.width/(double)anchor.getWidth();
                    timeline=new TrackedTearSignal(
                            (ring.sourceLeft+ring.centerX*ring.sourceSide/512.0)*scale,
                            (ring.sourceTop+ring.centerY*ring.sourceSide/512.0)*scale,
                            ring.sourceSide*scale);
                }
                anchor.recycle();if(timeline!=null)break;
            }
            engine.close();engine=null;
            if(timeline==null)return inconclusive(duration/1000.0,"A reflected Placido-ring pattern was not recognised in the video. A general eye video is not enough for this test.");
            for(long time=0;time<duration;time+=125) {
                if(Thread.currentThread().isInterrupted())return inconclusive(duration/1000.0,"Analysis cancelled.");
                Bitmap decoded=frame(retriever,time);if(decoded==null)continue;
                RingVideoTracker.Frame image=trackingFrame(decoded);
                RingVideoTracker.Sample sample=timeline.addFrame(time,image);
                if(report!=null)report.frame(time,decoded,image,sample);
                decoded.recycle();
            }
            TearSignalTimeline.Result signal=timeline.analyse();
            if(report!=null)report.trace(timeline.traceCsv());
            if(signal.kind==TearSignalTimeline.Kind.INCONCLUSIVE)return inconclusive(duration/1000.0,signal.detail);
            if(signal.kind==TearSignalTimeline.Kind.PARTIAL_OBSERVATION)
                return new Result(State.PARTIAL_OBSERVATION,signal.intervalSeconds,signal.blinkSeconds,duration/1000.0,signal.detail);
            State state=signal.kind==TearSignalTimeline.Kind.CHANGE?(signal.intervalSeconds<10?State.EARLY_SIGNAL_CHANGE:State.SIGNAL_CHANGE):State.NO_SIGNAL_CHANGE;
            return new Result(state,signal.intervalSeconds,signal.blinkSeconds,duration/1000.0,signal.detail+
                    " This is an experimental image-signal interval, not clinically validated NIBUT or a dry-eye diagnosis.");
        }catch(Exception error){return inconclusive(0,"Video analysis failed ("+error.getClass().getSimpleName()+"). Choose another saved recording.");}
        finally{if(engine!=null)try{engine.close();}catch(Exception ignored){}try{retriever.release();}catch(Exception ignored){}}
    }
    private static RingVideoTracker.Frame trackingFrame(Bitmap source) {
        double scale=Math.min(1,1280.0/Math.max(source.getWidth(),source.getHeight()));
        int width=Math.max(2,(int)Math.round(source.getWidth()*scale));
        int height=Math.max(2,(int)Math.round(source.getHeight()*scale));
        Bitmap small=Bitmap.createScaledBitmap(source,width,height,true);
        int[] pixels=new int[width*height];small.getPixels(pixels,0,width,0,0,width,height);
        double[] gray=new double[pixels.length];
        for(int i=0;i<pixels.length;i++)gray[i]=.299*((pixels[i]>>16)&255)+.587*((pixels[i]>>8)&255)+.114*(pixels[i]&255);
        if(small!=source)small.recycle();
        return new RingVideoTracker.Frame(width,height,gray);
    }
    private static Bitmap frame(MediaMetadataRetriever retriever,long milliseconds) {
        return Build.VERSION.SDK_INT>=27?retriever.getScaledFrameAtTime(milliseconds*1000,MediaMetadataRetriever.OPTION_CLOSEST,1280,1280)
                :retriever.getFrameAtTime(milliseconds*1000,MediaMetadataRetriever.OPTION_CLOSEST);
    }
    private static Result inconclusive(double duration,String detail){return new Result(State.INCONCLUSIVE,Double.NaN,Double.NaN,duration,detail);}
}
