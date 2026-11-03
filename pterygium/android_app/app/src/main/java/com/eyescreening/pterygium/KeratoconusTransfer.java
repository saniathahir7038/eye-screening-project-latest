package com.eyescreening.pterygium;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.InputType;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Local calibration and file exchange with the original desktop reconstruction. */
final class KeratoconusTransfer {
    private static final String PREFS="smartkc_calibration";
    private static final String[] KEYS={"sensor_width_mm","sensor_height_mm","focal_length_mm",
            "capture_zoom","working_distance_mm","camera_gap_mm"};
    private static final String[] TITLES={"Effective sensor width along photo width (mm)",
            "Effective sensor height along photo height (mm)","Lens focal length (mm)",
            "Capture digital zoom (for example 1 or 2)",
            "Smallest-ring plane to corneal apex (mm)","Camera pupil behind smallest-ring plane (mm)"};
    static JSONObject profile(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREFS,0).getString("profile","{}")); }
        catch(Exception error) { return new JSONObject(); }
    }
    static void editCalibration(Activity activity, Runnable changed) {
        JSONObject saved=profile(activity);
        LinearLayout form=new LinearLayout(activity);form.setOrientation(LinearLayout.VERTICAL);
        int padding=(int)(16*activity.getResources().getDisplayMetrics().density);form.setPadding(padding,padding,padding,padding);
        Button cameraInfo=new Button(activity);cameraInfo.setText("Show phone camera details");
        cameraInfo.setOnClickListener(view->new AlertDialog.Builder(activity).setTitle("Available rear cameras")
                .setMessage(cameraDetails(activity)).setPositiveButton("Close",null).show());form.addView(cameraInfo);
        TextView explanation=new TextView(activity);
        explanation.setText("Original SmartKC attachment. Leave unknown measurements blank; detection still works. Use effective sensor dimensions for the full image field of view, in the photo's orientation. The two distances below add up to camera-to-cornea distance.");
        form.addView(explanation);
        EditText[] inputs=new EditText[KEYS.length];
        for(int i=0;i<KEYS.length;i++) {
            TextView label=new TextView(activity);label.setText(TITLES[i]);form.addView(label);
            EditText value=new EditText(activity);value.setSingleLine(true);
            value.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if(saved.has(KEYS[i])) value.setText(saved.optString(KEYS[i],""));
            inputs[i]=value;form.addView(value);
        }
        CheckBox confirmed=new CheckBox(activity);
        confirmed.setText("These values were measured or verified for this lens, zoom and attachment.");
        confirmed.setChecked(saved.optBoolean("geometry_confirmed",false));form.addView(confirmed);
        ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Reconstruction calibration")
                .setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            try {
                JSONObject profile=new JSONObject();profile.put("schema","eye-screening-calibration/1");
                profile.put("attachment","original-smartkc");profile.put("device",android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL);
                double[] low={1,1,1,1,30,0},high={20,20,30,10,150,30};
                for(int i=0;i<KEYS.length;i++) {
                    String text=inputs[i].getText().toString().trim();
                    if(text.isEmpty()) { if(confirmed.isChecked()) throw new IllegalArgumentException("Fill every measurement before confirming calibration"); continue; }
                    double number=Double.parseDouble(text);
                    if(!Double.isFinite(number)||number<low[i]||number>high[i]) throw new IllegalArgumentException("Check "+TITLES[i]);
                    profile.put(KEYS[i],number);
                }
                profile.put("geometry_confirmed",confirmed.isChecked());
                activity.getSharedPreferences(PREFS,0).edit().putString("profile",profile.toString()).apply();
                changed.run();dialog.dismiss();
            } catch(Exception error) { Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show(); }
        }));
        dialog.show();
    }
    private static String cameraDetails(Context context) {
        StringBuilder text=new StringBuilder("Camera metadata helps identify the lens; it does not verify zoom, optical alignment or working distance. Sensor axes below are native camera axes; use the photo orientation when entering width and height.\n\n");
        try {
            android.hardware.camera2.CameraManager manager=(android.hardware.camera2.CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
            for(String id:manager.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics camera=manager.getCameraCharacteristics(id);
                Integer facing=camera.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if(facing==null || facing!=android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)continue;
                android.util.SizeF size=camera.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                float[] focal=camera.get(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                text.append("Camera ").append(id).append("\nSensor: ").append(size).append(" mm\nFocal lengths: ").append(Arrays.toString(focal)).append(" mm\n\n");
            }
        } catch(Exception error) {text.append("Camera details unavailable: ").append(error.getClass().getSimpleName());}
        return text.toString();
    }
    static String hash(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream input=new FileInputStream(file)) {byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1) digest.update(buffer,0,n);}
        StringBuilder value=new StringBuilder();for(byte b:digest.digest())value.append(String.format(Locale.US,"%02x",b&255));return value.toString();
    }
    static void exportCapture(Context context, OutputStream output, File source, String id, String eye,
            boolean untouched, int width,int height,int analysisWidth,int analysisHeight,PlacidoSegmentationEngine.Result result) throws Exception {
        JSONObject calibration=profile(context);calibration.put("image_width",width);calibration.put("image_height",height);
        JSONObject request=new JSONObject();request.put("schema","eye-screening-capture/1");
        request.put("capture_id",id);request.put("eye",eye);request.put("source_sha256",hash(source));
        request.put("source_uncropped",untouched);request.put("app_version",BuildConfig.VERSION_NAME);
        request.put("oriented_width",width);request.put("oriented_height",height);
        if(result.accepted && result.sourceSide>0) {
            JSONArray center=new JSONArray();center.put((result.sourceLeft+result.centerX*result.sourceSide/512.0)/analysisWidth);
            center.put((result.sourceTop+result.centerY*result.sourceSide/512.0)/analysisHeight);request.put("center_normalized",center);
        }
        request.put("detected_rings",result.detectedRings);
        if(result.patternDescriptor!=null && result.patternDescriptor.features!=null) {
            JSONObject shape=new JSONObject();shape.put("method",RingPatternPrototype.VERSION);
            shape.put("rings",result.patternDescriptor.rings);JSONArray values=new JSONArray();
            for(double value:result.patternDescriptor.features)values.put(value);
            shape.put("features",values);shape.put("disease_classification_enabled",false);
            shape.put("reason","Four-example held-out audit failed; features are research measurements only");
            request.put("ring_shape_prototype",shape);
        }
        if(result.positioning!=null) {
            PositioningCheck.Result check=result.positioning;JSONObject photo=new JSONObject();
            photo.put("schema","eye-screening-photo-check/1");photo.put("usable_for_detection",check.usable);
            photo.put("framing_ok",check.framingOkay);photo.put("edge_detail_ok",check.detailOkay);
            photo.put("contrast_clipping_ok",check.exposureOkay);photo.put("normalised_edge_detail",check.detail);
            photo.put("contrast",check.contrast);photo.put("clipped_fraction",check.clippedFraction);
            photo.put("guidance",new JSONArray(check.guidance));request.put("photo_checks",photo);
        }
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            entry(zip,"request.json",request.toString(2).getBytes("UTF-8"));
            entry(zip,"calibration.json",calibration.toString(2).getBytes("UTF-8"));
            zip.putNextEntry(new ZipEntry("source_image"));
            try(InputStream input=new FileInputStream(source)) {byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)zip.write(buffer,0,n);}
            zip.closeEntry();
            ByteArrayOutputStream png=new ByteArrayOutputStream();result.overlay.compress(Bitmap.CompressFormat.PNG,100,png);
            entry(zip,"detection.png",png.toByteArray());
        }
    }
    private static void entry(ZipOutputStream zip,String name,byte[] value)throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(value);zip.closeEntry();
    }
    static final class Imported {
        final JSONObject manifest;final Bitmap axial,tangential;final boolean simulation;
        Imported(JSONObject manifest,Bitmap axial,Bitmap tangential){this.manifest=manifest;this.axial=axial;this.tangential=tangential;this.simulation="assumed-study-geometry-demo".equals(manifest.optString("mode"));}
    }
    static Imported importResult(InputStream input, File source,String captureId,String expectedEye)throws Exception {
        Map<String,byte[]> files=SafeResultArchive.read(input);
        JSONObject manifest=new JSONObject(new String(files.get("manifest.json"),"UTF-8"));
        boolean simulation="assumed-study-geometry-demo".equals(manifest.optString("mode"));
        JSONObject calibration=manifest.getJSONObject("calibration");
        if(simulation) {
            SafeResultArchive.validateSimulationIdentity(manifest.optString("schema"),manifest.optString("mode"),
                    manifest.optString("source_sha256"),manifest.optString("capture_id"),hash(source),captureId);
            if(!manifest.optBoolean("research_demo",false)||calibration.optBoolean("geometry_confirmed",false)
                    ||!"assumed-study-geometry-demo".equals(calibration.optString("mode")))
                throw new IOException("Simulation assumptions are not explicitly declared");
        } else {
            SafeResultArchive.validateIdentity(manifest.optString("schema"),manifest.optString("mode"),
                    manifest.optString("source_sha256"),manifest.optString("capture_id"),hash(source),captureId);
            if(!calibration.optBoolean("geometry_confirmed",false))throw new IOException("Result calibration is not confirmed");
        }
        if(!Arrays.asList("left","right").contains(manifest.optString("eye")) || !expectedEye.equals(manifest.optString("eye")))throw new IOException("The result eye does not match the eye selected for this capture export");
        JSONObject metrics=manifest.getJSONObject("metrics");
        for(String key:new String[]{"sim_k1_d","sim_k2_d","axis_degrees","mean_k_d","difference_d","ppk_research"})
            if(!Double.isFinite(metrics.getDouble(key)))throw new IOException("Invalid reconstruction measurements");
        return new Imported(manifest,decode(files.get("axial.png")),decode(files.get("tangential.png")));
    }
    private static Bitmap decode(byte[] png)throws IOException {
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(png,0,png.length,bounds);
        if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>2048||bounds.outHeight>2048)throw new IOException("Map dimensions are invalid");
        Bitmap map=BitmapFactory.decodeByteArray(png,0,png.length);
        if(map==null)throw new IOException("Map image could not be opened");return map;
    }
}
