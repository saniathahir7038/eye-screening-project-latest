package com.eyescreening.pterygium;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;

/** Optional on-device map activity. Source capture is read-only; outputs have their own folder. No server, result import, personal diagnosis, or reference-map replay. */
public class PhoneMapsActivity extends AppCompatActivity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private TextView status,details;
    private Button generate,share;
    private ImageView photo,axial,tangential;
    private ProgressBar progress;
    private File source,resultFolder;
    private boolean ready=false,busy=false;
    private long started;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try {
            source=PhoneMapRecord.source(getFilesDir(),getIntent().getStringExtra("capture_id"));
            resultFolder=PhoneMapRecord.results(getFilesDir(),getIntent().getStringExtra("capture_id"));
        } catch(IOException error) {
            android.widget.Toast.makeText(this,"Choose a saved photo in Keratoconus first.",android.widget.Toast.LENGTH_LONG).show();finish();return;
        }
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20),dp(24),dp(20),dp(32));scroll.addView(body);setContentView(scroll);
        text(body,"Phone maps",28,Color.rgb(16,73,79));
        text(body,"Calculated on this phone",16,Color.DKGRAY);
        text(body,"Generate maps for the photo selected in Keratoconus. Use an original 2x photo from the OnePlus 9R. No computer or map import is needed.",17,Color.DKGRAY);
        text(body,"EXPERIMENTAL • Assumed distances\nThese maps cannot tell whether someone has keratoconus.",16,Color.rgb(145,72,12));
        generate=button(body,"Generate maps on this phone",v->calculate());generate.setEnabled(false);
        progress=new ProgressBar(this);progress.setVisibility(View.GONE);body.addView(progress);
        status=text(body,"Ready. Use the same original photo you tested in Eye Screening.",17,Color.DKGRAY);
        photo=image(body);
        text(body,"Axial map",22,Color.rgb(16,73,79));axial=image(body);
        text(body,"Tangential map",22,Color.rgb(16,73,79));tangential=image(body);
        details=text(body,"",15,Color.DKGRAY);
        share=button(body,"Share test report",v->share());share.setEnabled(false);
        text(body,"Experimental OnePlus 9R preset: portrait 3000 x 4000 photo at 2x zoom, original SmartKC attachment. Optical distances are assumed. These results are saved separately from imported maps.",14,Color.DKGRAY);
        if(source.isFile()){
            ready=true;generate.setEnabled(true);status.setText("Your last photo is saved. Tap Generate maps to calculate it again.");
            restoreImages();
            // The main-screen action starts the first calculation; revisits restore its result/report.
            if(needsConsistencyCheck())generate.post(this::calculate);
        }
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private boolean needsConsistencyCheck(){
        File file=new File(resultFolder,"report.json");if(!file.isFile())return true;
        try{
            JSONObject report=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            return "complete".equals(report.optString("status"))&&!report.has("map_quality");
        }catch(Exception error){return false;}
    }
    private TextView text(LinearLayout parent,String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(8),0,dp(10));parent.addView(t);return t;
    }
    private Button button(LinearLayout parent,String title,View.OnClickListener listener){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(listener);parent.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private ImageView image(LinearLayout parent){ImageView v=new ImageView(this);v.setAdjustViewBounds(true);v.setScaleType(ImageView.ScaleType.FIT_CENTER);v.setVisibility(View.GONE);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    private Bitmap decode(File file)throws IOException{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),bounds);
        if(bounds.outWidth<1)throw new IOException("This is not a readable photo.");
        ExifInterface exif=new ExifInterface(file.getAbsolutePath());int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        boolean swapped=orientation>=5&&orientation<=8;
        int w=swapped?bounds.outHeight:bounds.outWidth,h=swapped?bounds.outWidth:bounds.outHeight;
        if(w!=3000||h!=4000)throw new IOException("For this first test, choose your original portrait 3000 × 4000 photo, not a screenshot or cropped copy.");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap decoded=BitmapFactory.decodeFile(file.getPath(),options);if(decoded==null)throw new IOException("Could not decode the photo.");
        Matrix m=new Matrix();
        switch(orientation){
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:m.setScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_180:m.setRotate(180);break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:m.setScale(1,-1);break;
            case ExifInterface.ORIENTATION_TRANSPOSE:m.setRotate(90);m.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_90:m.setRotate(90);break;
            case ExifInterface.ORIENTATION_TRANSVERSE:m.setRotate(270);m.postScale(-1,1);break;
            case ExifInterface.ORIENTATION_ROTATE_270:m.setRotate(270);break;
        }
        if(!m.isIdentity()){Bitmap oriented=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),m,true);if(oriented!=decoded)decoded.recycle();decoded=oriented;}
        return decoded;
    }
    private void calculate(){
        if(!ready||busy)return;setBusy(true);share.setEnabled(false);axial.setVisibility(View.GONE);tangential.setVisibility(View.GONE);details.setText("");started=System.currentTimeMillis();
        worker.submit(()->{
            PlacidoSegmentationEngine engine=null;
            try{
                clearResults();resultFolder.mkdirs();
                JSONObject initialReport=new JSONObject();initialReport.put("schema","eye-map-lab/1");initialReport.put("app_version",BuildConfig.VERSION_NAME);initialReport.put("status","started");initialReport.put("capture_id",getIntent().getStringExtra("capture_id"));initialReport.put("source_sha256",hash(source));initialReport.put("phone_model",android.os.Build.MODEL);initialReport.put("geometry_confirmed",false);initialReport.put("diagnosis_available",false);write(new File(resultFolder,"report.json"),initialReport.toString(2));
                update("Finding the reflected rings…");Bitmap original=decode(source);
                engine=new PlacidoSegmentationEngine(this);
                PlacidoSegmentationEngine.Result detection=engine.analyseAligned(original);
                if(detection==null||!detection.accepted)throw new IOException(detection==null?"No rings found. Choose a clearer photo.":detection.failureReason);
                double cx=detection.sourceLeft+detection.centerX*detection.sourceSide/512.0;
                double cy=detection.sourceTop+detection.centerY*detection.sourceSide/512.0;
                Bitmap checkBitmap=Bitmap.createScaledBitmap(detection.crop,512,512,true);int[] checkPixels=new int[512*512];checkBitmap.getPixels(checkPixels,0,512,0,0,512,512);
                PositioningCheck.Result quality=PositioningCheck.assess(checkPixels,512,detection.centerX,detection.centerY,detection.detectedRings,cx/original.getWidth(),cy/original.getHeight());
                if(!quality.usable)throw new IOException(android.text.TextUtils.join(" ",quality.guidance));
                int left=(int)Math.rint(cx)-250,top=(int)Math.rint(cy)-250;
                if(left<0||top<0||left+500>original.getWidth()||top+500>original.getHeight())throw new IOException("The reflection is too close to the edge of the photo. Centre it and try again.");
                save(detection.overlay,new File(resultFolder,"rings.png"));
                ui(()->{photo.setImageBitmap(detection.overlay);photo.setVisibility(View.VISIBLE);});
                initialReport.put("rings_detected",detection.detectedRings);
                initialReport.put("detected_center_normalized_x",cx/original.getWidth());initialReport.put("detected_center_normalized_y",cy/original.getHeight());
                Reconstruction reconstruction=reconstructWithRetries(engine,original,left,top,initialReport);
                PhoneMapMath.Maps maps=reconstruction.maps;
                engine.close();engine=null;
                Bitmap axialBitmap=render(maps.axial,maps.supported,maps.size,!reconstruction.quality.consistent,"AXIAL • PHONE CALCULATION");
                Bitmap tanBitmap=render(maps.tangential,maps.supported,maps.size,!reconstruction.quality.consistent,"TANGENTIAL • PHONE CALCULATION");
                double seconds=(System.currentTimeMillis()-started)/1000.0;
                resultFolder.mkdirs();save(axialBitmap,new File(resultFolder,"axial.png"));save(tanBitmap,new File(resultFolder,"tangential.png"));
                JSONObject report=initialReport;report.put("status","complete");report.put("schema","eye-map-lab/1");report.put("app_version",BuildConfig.VERSION_NAME);report.put("computed_on_phone",true);report.put("mode","assumed-geometry-native-port");report.put("geometry_confirmed",false);report.put("diagnosis_available",false);report.put("source_sha256",hash(source));report.put("phone_model",android.os.Build.MODEL);report.put("elapsed_seconds",seconds);
                report.put("rings_detected",detection.detectedRings);report.put("surface_points",maps.points);report.put("supported_meridians",maps.meridians);report.put("fit_rms_mm",maps.fitRms);report.put("fit_condition",maps.condition);report.put("surface_radius_assumed_mm",maps.scale);report.put("assumed_gap_correction_mm",maps.gap);report.put("source_center_normalized_x",(reconstruction.left+250.0)/original.getWidth());report.put("source_center_normalized_y",(reconstruction.top+250.0)/original.getHeight());
                report.put("map_quality",qualityJson(reconstruction.quality));
                report.put("preset","OnePlus 9R; portrait sensor 4.8x6.4mm; focal 4.745mm; 2x; nominal 75mm; camera gap 4mm; study distance curve");
                report.put("limitations","Experimental native port; desktop pixel parity is not established. Grey areas have insufficient support. No disease classification, calibrated K readings or probability is produced.");
                write(new File(resultFolder,"report.json"),report.toString(2));
                String summary=String.format(Locale.US,"Calculated on this phone in %.1f seconds.\n%d rings detected.\n\nCapture check: %s\n%s\n\nGrey areas have insufficient ring information. These experimental maps do not diagnose keratoconus.",seconds,detection.detectedRings,reconstruction.quality.consistent?"prototype fit check passed":"retry recommended",reconstruction.quality.reason);
                write(new File(resultFolder,"summary.txt"),summary);
                ui(()->{photo.setImageBitmap(detection.overlay);photo.setVisibility(View.VISIBLE);axial.setImageBitmap(axialBitmap);axial.setVisibility(View.VISIBLE);tangential.setImageBitmap(tanBitmap);tangential.setVisibility(View.VISIBLE);details.setText(summary);status.setText(reconstruction.quality.consistent?"Maps calculated - prototype fit check passed":"Maps calculated - retry recommended");setBusy(false);share.setEnabled(true);});
            }catch(Exception|OutOfMemoryError e){recordFailure(e);fail(e,true);}
            finally{if(engine!=null)engine.close();}
        });
    }
    private static final class Reconstruction {
        final PhoneMapMath.Maps maps;final PhoneMapQuality quality;
        final int left,top,attempt;final Bitmap input,colour;final byte[] mask;
        Reconstruction(PhoneMapMath.Maps m,int l,int t,int attempt,Bitmap input,Bitmap colour,byte[] mask){
            maps=m;quality=PhoneMapQuality.assess(m);left=l;top=t;this.attempt=attempt;this.input=input;this.colour=colour;this.mask=mask;
        }
    }
    private static JSONObject qualityJson(PhoneMapQuality quality)throws org.json.JSONException {
        JSONObject value=new JSONObject();value.put("method","engineering-fit-check/1");value.put("consistent",quality.consistent);value.put("relative_fit_rms",quality.relativeRms);value.put("reason",quality.reason);
        value.put("max_relative_fit_rms",PhoneMapQuality.MAX_RELATIVE_RMS);value.put("max_condition",PhoneMapQuality.MAX_CONDITION);value.put("clinically_validated",false);return value;
    }
    private Reconstruction select(Reconstruction candidate,JSONObject report)throws Exception {
        report.put("selected_attempt",candidate.attempt);report.put("map_quality",qualityJson(candidate.quality));
        save(candidate.input,new File(resultFolder,"reconstruction-crop.png"));save(candidate.colour,new File(resultFolder,"reconstruction-colour-crop.png"));
        try(OutputStream out=new FileOutputStream(new File(resultFolder,"ring-mask.bin"))){out.write(candidate.mask);}
        write(new File(resultFolder,"report.json"),report.toString(2));return candidate;
    }
    /** Bounded retries favour a consistent fit. Numerical map creation alone is insufficient. */
    private Reconstruction reconstructWithRetries(PlacidoSegmentationEngine engine,Bitmap original,int left,int top,JSONObject report)throws Exception {
        java.util.List<int[]> trials=new java.util.ArrayList<>();trials.add(new int[]{left,top,0});
        org.json.JSONArray attempts=new org.json.JSONArray();report.put("attempts",attempts);
        IllegalArgumentException last=null;Reconstruction best=null;
        for(int index=0;index<trials.size()&&index<4;index++){
            PhoneMapMath.checkCancelled();int[] trial=trials.get(index);boolean rgb=trial[2]==1;
            update("Reading rings ("+(rgb?"colour":"grayscale")+", attempt "+(index+1)+")...");
            Bitmap crop=Bitmap.createBitmap(original,trial[0],trial[1],500,500),input=rgb?crop:grayscale(crop);
            int[] network=engine.segmentLabels(input),mask=new int[500*500];
            for(int y=0;y<500;y++)for(int x=0;x<500;x++)mask[y*500+x]=network[(y*512/500)*512+x*512/500];
            byte[] bytes=new byte[mask.length];for(int i=0;i<mask.length;i++)bytes[i]=(byte)mask[i];
            save(input,new File(resultFolder,"attempt-"+(index+1)+"-input.png"));save(crop,new File(resultFolder,"attempt-"+(index+1)+"-colour.png"));
            try(OutputStream out=new FileOutputStream(new File(resultFolder,"attempt-"+(index+1)+"-mask.bin"))){out.write(bytes);}
            // Retain the most recent inputs even if every candidate is rejected.
            save(input,new File(resultFolder,"reconstruction-crop.png"));save(crop,new File(resultFolder,"reconstruction-colour-crop.png"));
            try(OutputStream out=new FileOutputStream(new File(resultFolder,"ring-mask.bin"))){out.write(bytes);}
            JSONObject attempt=new JSONObject();attempt.put("left_half_resolution",trial[0]);attempt.put("top_half_resolution",trial[1]);attempt.put("model_input",rgb?"rgb":"grayscale");attempts.put(attempt);
            report.put("status","calculating");write(new File(resultFolder,"report.json"),report.toString(2));
            try{
                PhoneMapMath.Maps maps=PhoneMapMath.generate(mask,500,this::update);
                Reconstruction candidate=new Reconstruction(maps,trial[0],trial[1],index+1,input,crop,bytes);
                attempt.put("status",candidate.quality.consistent?"accepted":"low-consistency");attempt.put("map_quality",qualityJson(candidate.quality));
                attempt.put("surface_points",maps.points);attempt.put("supported_meridians",maps.meridians);attempt.put("fit_rms_mm",maps.fitRms);attempt.put("surface_radius_assumed_mm",maps.scale);attempt.put("fit_condition",maps.condition);
                if(PhoneMapQuality.prefer(candidate.quality,best==null?null:best.quality))best=candidate;
                if(candidate.quality.consistent)return select(candidate,report);
            }catch(IllegalArgumentException e){last=e;attempt.put("status","rejected");attempt.put("reason",e.getMessage());}
            if(index==0||rgb){
                double[] centre=maskCentre(network);
                if(centre!=null){
                    double dx=centre[0]*500/512.0-250,dy=centre[1]*500/512.0-250;
                    int refinedLeft=trial[0]+(int)Math.rint(dx),refinedTop=trial[1]+(int)Math.rint(dy);
                    if(Math.hypot(refinedLeft-left,refinedTop-top)<=40&&(refinedLeft!=trial[0]||refinedTop!=trial[1])&&refinedLeft>=0&&refinedTop>=0&&refinedLeft+500<=original.getWidth()&&refinedTop+500<=original.getHeight()){
                        boolean duplicate=false;for(int[] existing:trials)if(existing[0]==refinedLeft&&existing[1]==refinedTop&&existing[2]==trial[2])duplicate=true;
                        if(!duplicate)trials.add(new int[]{refinedLeft,refinedTop,trial[2]});
                    }
                }
            }
            if(index==0)trials.add(new int[]{left,top,1});write(new File(resultFolder,"report.json"),report.toString(2));
        }
        if(best!=null)return select(best,report); // The visible result is explicitly marked low-consistency.
        throw last==null?new IllegalArgumentException("No usable reconstruction crop. Share the test report."):last;
    }
    private static double[] maskCentre(int[] labels){
        int count=0;double x=0,y=0;
        for(int i=0;i<labels.length;i++)if(labels[i]==1){count++;x+=i%512;y+=i/512;}
        return count>=80?new double[]{x/count,y/count}:PlacidoCenterEstimator.estimate(labels,512);
    }
    private void recordFailure(Throwable e){
        try{
            File file=new File(resultFolder,"report.json");JSONObject report;
            try(InputStream in=new FileInputStream(file)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);report=new JSONObject(new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));}
            report.put("status","failed");report.put("failure",e.getMessage());report.put("elapsed_seconds",(System.currentTimeMillis()-started)/1000.0);write(file,report.toString(2));
            StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));write(new File(resultFolder,"error.txt"),trace.toString());
        }catch(Exception ignored){android.util.Log.e("EyeMapLab","Could not save failure report",ignored);}
    }
    private static Bitmap grayscale(Bitmap bitmap){int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);
        for(int i=0;i<pixels.length;i++){int p=pixels[i],v=(77*Color.red(p)+150*Color.green(p)+29*Color.blue(p)+128)>>8;pixels[i]=Color.rgb(v,v,v);}return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);}
    private static final int[] PALETTE={0xff020551,0xff010579,0xff0201a1,0xff0201b5,0xff0101d5,0xff025da9,0xff028d79,0xff20b54d,0xff2cf131,0xffa8f125,0xfff4f125,0xfff0b525,0xfff87d25,0xfff46119,0xfff82521,0xfff13945,0xfff25965,0xffee7981,0xffed8591,0xffee919d,0xffeda1ad,0xffeeadbd,0xffeeb9c9,0xffedcddd,0xffede9f9};
    private static final double[] POWER={14,19,24,29,33.9,37,38.5,40,41.5,43,44.5,46,47.5,49,51.4,55.5,60.5,65.5,70.5,75.5,80.5,85.5,90.5,95.5,100.5};
    private static int colour(double d){if(!Double.isFinite(d))return Color.LTGRAY;int best=0;double diff=Double.POSITIVE_INFINITY;for(int i=0;i<POWER.length;i++)if(Math.abs(d-POWER[i])<diff){diff=Math.abs(d-POWER[i]);best=i;}return PALETTE[best];}
    private static Bitmap render(double[] values,boolean[] supported,int size,boolean lowConsistency,String title){
        int[] pixels=new int[size*size];for(int i=0;i<pixels.length;i++)pixels[i]=supported[i]?colour(values[i]):0xffdedede;
        Bitmap raw=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888),result=Bitmap.createBitmap(500,628,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(result);canvas.drawColor(Color.WHITE);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(0xffa84812);paint.setTextSize(19);canvas.drawText(lowConsistency?"LOW CONSISTENCY - RETAKE RECOMMENDED":"EXPERIMENTAL - ASSUMED DISTANCES",12,25,paint);paint.setColor(0xff10494f);paint.setTextSize(18);canvas.drawText(title,12,50,paint);
        canvas.drawBitmap(raw,null,new android.graphics.Rect(10,60,490,540),paint);paint.setTextSize(15);paint.setColor(Color.DKGRAY);canvas.drawText("Assumed power (D) • not calibrated measurements",12,562,paint);
        for(int i=0;i<460;i++){paint.setColor(colour(25+50*i/459.0));canvas.drawRect(20+i,573,21+i,589,paint);}
        paint.setColor(Color.DKGRAY);canvas.drawText("25",20,607,paint);canvas.drawText("50",242,607,paint);canvas.drawText("75+",450,607,paint);paint.setTextSize(13);canvas.drawText("Grey: unsupported area • Not a diagnosis",12,624,paint);raw.recycle();return result;
    }
    private void update(String message){ui(()->status.setText(message));}
    private void ui(Runnable action){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())action.run();});}
    private void setBusy(boolean value){busy=value;generate.setEnabled(!value&&ready);progress.setVisibility(value?View.VISIBLE:View.GONE);}
    private void fail(Throwable e){fail(e,false);}
    private void fail(Throwable e,boolean reportAvailable){android.util.Log.e("EyeMapLab","Calculation failed",e);String raw=e instanceof OutOfMemoryError?"The phone ran out of memory. Close other apps and try again.":e.getMessage();String message=raw!=null&&raw.startsWith("Surface information incomplete")?"Not enough consistent rings for maps yet. Tap Share test report and send the ZIP so we can check the calculation.":raw;ui(()->{setBusy(false);share.setEnabled(reportAvailable&&new File(resultFolder,"report.json").isFile());status.setText("Could not finish: "+(message==null?e.getClass().getSimpleName():message));});}
    private static void save(Bitmap image,File file)throws IOException{try(OutputStream out=new FileOutputStream(file)){if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Could not save a map.");}}
    private static void write(File f,String value)throws IOException{try(OutputStream out=new FileOutputStream(f)){out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
    private static String hash(File f)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
    private void clearResults(){if(resultFolder==null)return;File[] files=resultFolder.listFiles();if(files!=null)for(File file:files)file.delete();}
    private void restoreImages(){try{Bitmap b=decode(source);photo.setImageBitmap(b);photo.setVisibility(View.VISIBLE);File a=new File(resultFolder,"axial.png"),t=new File(resultFolder,"tangential.png"),r=new File(resultFolder,"report.json");if(a.isFile()&&t.isFile()&&r.isFile()){
        axial.setImageBitmap(BitmapFactory.decodeFile(a.getPath()));axial.setVisibility(View.VISIBLE);tangential.setImageBitmap(BitmapFactory.decodeFile(t.getPath()));tangential.setVisibility(View.VISIBLE);share.setEnabled(true);try{
            String saved=new String(java.nio.file.Files.readAllBytes(r.toPath()),java.nio.charset.StandardCharsets.UTF_8);JSONObject record=new JSONObject(saved);
            JSONObject quality=record.optJSONObject("map_quality");
            String label=quality==null?"This saved result predates the consistency check. Generate maps again to check it.":quality.optBoolean("consistent",false)?"Saved maps - prototype fit check passed.":"Saved maps - retry recommended. "+quality.optString("reason","");
            details.setText(label);status.setText("Saved phone result. Generate maps to calculate again.");
        }catch(Exception error){details.setText("Saved maps. Generate maps again to update the capture check.");}
    }else if(r.isFile()){share.setEnabled(true);status.setText("The last calculation did not finish. You can share its test report, or try Generate maps again.");}
    }catch(IOException e){ready=false;generate.setEnabled(false);status.setText("Choose the original photo again.");}}
    private static java.util.List<String> reportFiles(){
        java.util.List<String> names=new java.util.ArrayList<>(java.util.Arrays.asList("report.json","error.txt","summary.txt","axial.png","tangential.png","rings.png","reconstruction-crop.png","reconstruction-colour-crop.png","ring-mask.bin"));
        for(int i=1;i<=4;i++)for(String suffix:new String[]{"input.png","colour.png","mask.bin"})names.add("attempt-"+i+"-"+suffix);
        return names;
    }
    private void share(){
        try{
            File directory=new File(getCacheDir(),"exports");directory.mkdirs();File zip=new File(directory,"eye-screening-phone-maps-report.zip");
            try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){for(String n:reportFiles()){File f=new File(resultFolder,n);if(!f.isFile())continue;out.putNextEntry(new ZipEntry(n));try(InputStream in=new FileInputStream(f)){byte[] b=new byte[8192];int length;while((length=in.read(b))!=-1)out.write(b,0,length);}out.closeEntry();}}
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",zip);Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/zip");send.putExtra(Intent.EXTRA_STREAM,uri);send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(send,"Share test report"));
        }catch(Exception e){fail(e);}
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
