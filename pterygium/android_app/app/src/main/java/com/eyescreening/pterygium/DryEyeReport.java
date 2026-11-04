package com.eyescreening.pterygium;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Local diagnostics only. No source URI, original video or automatic upload. */
final class DryEyeReport {
    private final File directory, export;
    private final JSONObject metadata=new JSONObject();
    private int count;
    private String failure;

    DryEyeReport(Context context,Uri source) throws Exception {
        String id=UUID.randomUUID().toString();
        directory=new File(context.getCacheDir(),"dry-eye-report-"+id);
        File exports=new File(context.getCacheDir(),"exports");
        if(!directory.mkdirs()||(!exports.isDirectory()&&!exports.mkdirs()))throw new IOException("Cannot create report directory");
        export=new File(exports,"dry-eye-analysis-"+id+".zip");
        metadata.put("schema","dry-eye-analysis/2");metadata.put("analysis_method","post-blink-translation/2");
        metadata.put("mean_change_fraction_limit",TrackedTearSignal.MAX_MEAN_CHANGE_FRACTION);metadata.put("mean_change_gray_floor",TrackedTearSignal.MIN_MEAN_CHANGE_GRAY);
        metadata.put("app_version",BuildConfig.VERSION_NAME);metadata.put("version_code",BuildConfig.VERSION_CODE);
        metadata.put("device",Build.MANUFACTURER+" "+Build.MODEL);metadata.put("android_api",Build.VERSION.SDK_INT);
        metadata.put("requested_sample_interval_ms",125);metadata.put("is_diagnosis",false);
        metadata.put("decode_request",Build.VERSION.SDK_INT>=27?"getScaledFrameAtTime OPTION_CLOSEST 1280x1280":"getFrameAtTime OPTION_CLOSEST");
        metadata.put("template_correlation_min",RingVideoTracker.MIN_CORRELATION);metadata.put("baseline_correlation_min",0.65);metadata.put("baseline_residual_limit_pixels",6);
        metadata.put("preview_note","JPEG previews are for visual review. Aligned PNG crops are rounded to 8-bit gray. sample-*.bin preserves the actual double-precision measured pixels. Requested frame times are not decoder-reported presentation timestamps.");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=context.getContentResolver().openInputStream(source)) {
            if(input==null)throw new IOException("Cannot read source video");
            byte[] buffer=new byte[65536];int size;long bytes=0;
            while((size=input.read(buffer))!=-1){digest.update(buffer,0,size);bytes+=size;}
            metadata.put("source_bytes",bytes);
        }
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.US,"%02x",value&255));
        metadata.put("source_sha256",hash.toString());
    }

    void videoMetadata(String width,String height,String rotation,String duration) {
        try{metadata.put("video_width",width);metadata.put("video_height",height);metadata.put("video_rotation",rotation);metadata.put("video_duration_ms",duration);}catch(Exception error){fail(error);}
    }

    void anchor(long time,Bitmap image,PlacidoSegmentationEngine.Result ring) {
        try {
            JSONObject anchor=new JSONObject();anchor.put("requested_time_ms",time);anchor.put("decoded_width",image.getWidth());anchor.put("decoded_height",image.getHeight());
            anchor.put("source_left",ring.sourceLeft);anchor.put("source_top",ring.sourceTop);anchor.put("source_side",ring.sourceSide);
            anchor.put("centre_x_512",ring.centerX);anchor.put("centre_y_512",ring.centerY);anchor.put("rings",ring.detectedRings);
            metadata.put("anchor",anchor);writeBitmap("anchor.png",image,Bitmap.CompressFormat.PNG,100);
        }catch(Exception error){fail(error);}
    }

    void frame(long time,Bitmap decoded,RingVideoTracker.Frame trackingFrame,RingVideoTracker.Sample sample) {
        if(failure!=null)return;
        try {
            String name=String.format(Locale.US,"%05d",time);
            double scale=Math.min(1,640.0/Math.max(decoded.getWidth(),decoded.getHeight()));
            Bitmap preview=Bitmap.createScaledBitmap(decoded,Math.max(1,(int)Math.round(decoded.getWidth()*scale)),Math.max(1,(int)Math.round(decoded.getHeight()*scale)),true);
            try{writeBitmap("preview-"+name+".jpg",preview,Bitmap.CompressFormat.JPEG,85);}finally{if(preview!=decoded)preview.recycle();}
            Bitmap patch=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);int[] pixels=new int[160*160];
            for(int i=0;i<pixels.length;i++){int value=Math.max(0,Math.min(255,(int)Math.round(sample.image[i])));pixels[i]=0xff000000|(value<<16)|(value<<8)|value;}
            patch.setPixels(pixels,0,160,0,0,160,160);
            try{writeBitmap("aligned-"+name+".png",patch,Bitmap.CompressFormat.PNG,100);}finally{patch.recycle();}
            // Stream individual frame records: a failed export cannot corrupt the
            // completed analysis, and no large in-memory bitmap collection is kept.
            DryEyeSampleFile.write(new File(directory,"sample-"+name+".bin"),time,sample.image);
            count++;
            metadata.put("decoded_width",decoded.getWidth());metadata.put("decoded_height",decoded.getHeight());
            metadata.put("tracking_frame_width",trackingFrame.width);metadata.put("tracking_frame_height",trackingFrame.height);
        }catch(Exception error){fail(error);}
    }

    void trace(String csv) { try{writeText("trace.csv",csv);}catch(Exception error){fail(error);} }

    File finish(DryEyeVideoAnalyzer.Result result) throws Exception {
        metadata.put("result",result.state.name());metadata.put("detail",result.detail);
        metadata.put("interval_seconds",finite(result.signalIntervalSeconds));metadata.put("candidate_blink_seconds",finite(result.blinkSeconds));
        metadata.put("analysed_duration_seconds",result.analysedSeconds);metadata.put("exported_samples",count);
        metadata.put("export_complete",failure==null);if(failure!=null)metadata.put("export_error",failure);
        String readme="Dry-eye phone analysis report\n\nThis is an engineering report, not a dry-eye diagnosis or validated NIBUT.\nResult: "+result.state+"\n"+result.detail+
                "\n\nreport.json: source hash, app/device/decoder information and result.\ntrace.csv: requested times, measured signal, tracking and baseline checks, and interval inclusion. NaN means not evaluated.\naligned-*.png: measured crops rounded to 8-bit gray for viewing.\nsample-*.bin: exact measured crop pixels: big-endian int64 requested time in ms, followed by 25600 IEEE754 float64 gray values in row-major order, 160x160.\npreview-*.jpg: reduced, lossy source previews, not analysis input.\nanchor.png: lossless decoded anchor frame used for model localisation.\n\nNo original video or source URI is included. The report contains eye images. It stays on the phone until you choose to share it.\n";
        writeText("summary.txt",readme);writeText("report.json",metadata.toString(2));
        File[] files=directory.listFiles();if(files==null)throw new IOException("Cannot list report files");
        java.util.Arrays.sort(files,(a,b)->a.getName().compareTo(b.getName()));
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(export)))) {
            byte[] buffer=new byte[65536];
            for(File file:files)if(file.isFile()) {
                zip.putNextEntry(new ZipEntry(file.getName()));
                try(InputStream input=new FileInputStream(file)){int n;while((n=input.read(buffer))!=-1)zip.write(buffer,0,n);}
                zip.closeEntry();
            }
        }
        for(File file:files)file.delete();directory.delete();
        return export;
    }

    private void writeBitmap(String name,Bitmap image,Bitmap.CompressFormat format,int quality) throws IOException {
        try(OutputStream output=new BufferedOutputStream(new FileOutputStream(new File(directory,name)))) {
            if(!image.compress(format,quality,output))throw new IOException("Image export failed");
        }
    }
    private void writeText(String name,String text) throws IOException {
        try(Writer output=new OutputStreamWriter(new FileOutputStream(new File(directory,name)),"UTF-8")){output.write(text);}
    }
    private static Object finite(double value){return Double.isFinite(value)?value:JSONObject.NULL;}
    private void fail(Exception error){if(failure==null)failure=error.getClass().getSimpleName();}
}
