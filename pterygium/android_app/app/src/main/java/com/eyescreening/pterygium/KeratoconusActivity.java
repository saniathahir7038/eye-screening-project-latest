package com.eyescreening.pterygium;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class KeratoconusActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Spinner sampleSpinner;
    private ImageView sampleImage;
    private ImageView axialMap;
    private ImageView tangentialMap;
    private TextView resultText;
    private TextView metricsText;
    private View resultContainer;
    private ProgressBar progress;
    private PlacidoSegmentationEngine engine;
    private boolean modelLoading=true, showingReference;
    private Uri cameraUri;
    private File lastSourceFile;
    private String captureId;
    private int sourceWidth, sourceHeight, analysisWidth, analysisHeight;
    private PlacidoSegmentationEngine.Result lastDetection;
    private String exportEye="right";
    private boolean exportUntouched;
    private Uri pendingExport;
    private final ActivityResultLauncher<Intent> phoneMapsScreen = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> reloadModelAfterMaps());
    private final ActivityResultLauncher<String> exportPicker = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), uri -> { if(uri!=null) {
                if(lastDetection!=null)exportCapture(uri);else pendingExport=uri;
            } });
    private final ActivityResultLauncher<String> resultPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> { if(uri!=null) importMaps(uri); });

    private final ActivityResultLauncher<String> imagePicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) analyseLive(uri);
            });

    private final ActivityResultLauncher<Intent> cameraCapture = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && cameraUri != null) {
                    analyseLive(cameraUri);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keratoconus);
        if(savedInstanceState!=null && savedInstanceState.getString("camera_uri")!=null)
            cameraUri=Uri.parse(savedInstanceState.getString("camera_uri"));
        exportEye=getSharedPreferences("smartkc_capture",0).getString("export_eye","right");
        exportUntouched=getSharedPreferences("smartkc_capture",0).getBoolean("export_untouched",false);
        sampleSpinner = findViewById(R.id.sampleSpinner);
        sampleImage = findViewById(R.id.sampleImage);
        axialMap = findViewById(R.id.axialMap);
        tangentialMap = findViewById(R.id.tangentialMap);
        resultText = findViewById(R.id.keratoconusResultText);
        metricsText = findViewById(R.id.keratoconusMetricsText);
        resultContainer = findViewById(R.id.datasetResultContainer);
        progress = findViewById(R.id.keratoconusProgress);

        String[] names = new String[KeratoconusSample.ALL.length];
        for (int index = 0; index < names.length; index++) names[index] = KeratoconusSample.ALL[index].displayName;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sampleSpinner.setAdapter(adapter);

        findViewById(R.id.runSampleButton).setOnClickListener(view -> showSelectedSample());
        findViewById(R.id.captureKeratoconusButton).setOnClickListener(view -> capturePhoto());
        findViewById(R.id.chooseKeratoconusButton).setOnClickListener(view -> imagePicker.launch("image/*"));
        findViewById(R.id.backToModulesButton).setOnClickListener(view -> finish());
        findViewById(R.id.retryPositionButton).setOnClickListener(view -> capturePhoto());
        findViewById(R.id.savedKeratoconusCapturesButton).setOnClickListener(view -> showSavedCaptures());
        findViewById(R.id.toggleAdvancedKeratoconusButton).setOnClickListener(view -> {
            View controls=findViewById(R.id.advancedKeratoconusControls);
            controls.setVisibility(controls.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
        });
        findViewById(R.id.calibrationButton).setOnClickListener(view ->
                KeratoconusTransfer.editCalibration(this, this::updateCalibrationStatus));
        findViewById(R.id.exportCaptureButton).setOnClickListener(view -> chooseExportOptions());
        findViewById(R.id.importMapsButton).setOnClickListener(view -> resultPicker.launch("*/*"));
        findViewById(R.id.generatePhoneMapsButton).setOnClickListener(view -> openPhoneMaps());
        updateCalibrationStatus();
        setBusy(true);
        executor.execute(() -> {
            try {
                engine = new PlacidoSegmentationEngine(this);
                runOnUiThread(() -> {
                    modelLoading=false;
                    setBusy(false);
                    String saved=getSharedPreferences("smartkc_capture",0).getString("source","");
                    String savedId=getSharedPreferences("smartkc_capture",0).getString("id","");
                    if(!saved.isEmpty() && new File(saved).isFile()) {
                        lastSourceFile=new File(saved);captureId=savedId;
                        analyseSource(Uri.fromFile(lastSourceFile),true);
                    }
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {modelLoading=false;showFailure("The Placido model could not be loaded. Reinstall the APK.");});
            }
        });
    }

    private void openPhoneMaps() {
        if(lastSourceFile==null||engine==null||modelLoading||showingReference)return;
        if(android.os.Build.VERSION.SDK_INT<26){android.widget.Toast.makeText(this,"Phone maps require Android 8 or later.",android.widget.Toast.LENGTH_LONG).show();return;}
        final String selectedId=captureId;
        setBusy(true);
        executor.execute(()->{
            // Only one model stays loaded while the optional map screen is open.
            try{engine.close();}catch(Exception error){android.util.Log.w("Keratoconus","Model release failed",error);}finally{engine=null;}
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                try{phoneMapsScreen.launch(new Intent(this,PhoneMapsActivity.class).putExtra("capture_id",selectedId));}
                catch(Exception error){reloadModelAfterMaps();android.widget.Toast.makeText(this,"Could not open phone maps.",android.widget.Toast.LENGTH_LONG).show();}
            });
        });
    }

    private void reloadModelAfterMaps() {
        if(isFinishing()||isDestroyed()||modelLoading)return;
        if(engine!=null){setBusy(false);return;}
        modelLoading=true;setBusy(true);
        executor.execute(()->{
            try{
                engine=new PlacidoSegmentationEngine(this);
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;modelLoading=false;setBusy(false);});
            }catch(Exception error){runOnUiThread(()->{modelLoading=false;showFailure("Could not reload the ring detector. Reopen Keratoconus.");});}
        });
    }

    private void showSavedCaptures() {
        File root=new File(getFilesDir(),"smartkc-captures");
        File[] records=root.listFiles(file->file.isDirectory()&&new File(file,"source_image").isFile());
        if(records==null||records.length==0) {
            android.widget.Toast.makeText(this,"No saved captures yet. Choose or take a photo first.",android.widget.Toast.LENGTH_LONG).show();return;
        }
        java.util.Arrays.sort(records,(a,b)->Long.compare(new File(b,"source_image").lastModified(),new File(a,"source_image").lastModified()));
        String[] names=new String[records.length];java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("dd MMM HH:mm",Locale.getDefault());
        for(int i=0;i<records.length;i++)names[i]=date.format(new java.util.Date(new File(records[i],"source_image").lastModified()))
                +" | "+records[i].getName().substring(0,Math.min(8,records[i].getName().length()));
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Saved photo captures")
                .setItems(names,(dialog,index)->{lastSourceFile=new File(records[index],"source_image");captureId=records[index].getName();analyseSource(Uri.fromFile(lastSourceFile),true);})
                .setNegativeButton("Cancel",null).show();
    }

    private void capturePhoto() {
        try {
            File directory = new File(getCacheDir(), "placido-images");
            if (!directory.exists() && !directory.mkdirs()) throw new IOException("cache");
            File file = File.createTempFile("placido-", ".jpg", directory);
            cameraUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".files", file);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraCapture.launch(intent);
        } catch (Exception error) {
            showFailure("The camera could not be opened. Choose a saved Placido photo instead.");
        }
    }

    private void analyseLive(Uri uri) { analyseSource(uri,false); }

    private void analyseSource(Uri uri,boolean restore) {
        showingReference=true;
        lastDetection=null;
        findViewById(R.id.mapsContainer).setVisibility(View.GONE);
        setBusy(true);
        executor.execute(() -> {
            try {
                File source=lastSourceFile;
                String id=captureId;
                if(!restore) {
                    id=java.util.UUID.randomUUID().toString();
                    File directory=new File(getFilesDir(),"smartkc-captures/"+id);
                    if(!directory.mkdirs())throw new IOException("Cannot save capture");
                    source=new File(directory,"source_image");
                    try(InputStream input=getContentResolver().openInputStream(uri);
                        java.io.OutputStream output=new java.io.FileOutputStream(source)) {
                        if(input==null)throw new IOException("Cannot open image");
                        byte[] buffer=new byte[8192];int n,total=0;
                        while((n=input.read(buffer))!=-1) {
                            total+=n;if(total>40*1024*1024)throw new IOException("Image exceeds 40 MB");
                            output.write(buffer,0,n);
                        }
                    }
                }
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeFile(source.getPath(),bounds);
                if(bounds.outWidth<1 || bounds.outHeight<1 || (long)bounds.outWidth*bounds.outHeight>60000000)
                    throw new IOException("Image dimensions are unsupported");
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inSampleSize=1;
                while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>4096)options.inSampleSize*=2;
                Bitmap bitmap=BitmapFactory.decodeFile(source.getPath(),options);
                if(bitmap==null || engine==null)throw new IOException("Image or model unavailable");
                bitmap=orientPhoto(bitmap,Uri.fromFile(source));
                PlacidoSegmentationEngine.Result result=engine.analyseAligned(bitmap);
                int rotation=new ExifInterface(source.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
                boolean swap=rotation==ExifInterface.ORIENTATION_ROTATE_90 || rotation==ExifInterface.ORIENTATION_ROTATE_270
                        || rotation==ExifInterface.ORIENTATION_TRANSPOSE || rotation==ExifInterface.ORIENTATION_TRANSVERSE;
                int fullWidth=swap?bounds.outHeight:bounds.outWidth,fullHeight=swap?bounds.outWidth:bounds.outHeight;
                attachPositioning(bitmap,result);
                final Bitmap overview=positioningOverview(bitmap,result);
                File selected=source;String selectedId=id;int width=bitmap.getWidth(),height=bitmap.getHeight();
                runOnUiThread(() -> {
                    if(isFinishing() || isDestroyed())return;
                    lastSourceFile=selected;captureId=selectedId;sourceWidth=fullWidth;sourceHeight=fullHeight;analysisWidth=width;analysisHeight=height;lastDetection=result;
                    showingReference=false;
                    getSharedPreferences("smartkc_capture",0).edit().putString("source",selected.getPath()).putString("id",selectedId).apply();
                    ((ImageView)findViewById(R.id.positioningOverview)).setImageBitmap(overview);
                    findViewById(R.id.positioningOverview).setVisibility(View.VISIBLE);
                    showLiveResult(result);
                    if(pendingExport!=null) {Uri destination=pendingExport;pendingExport=null;exportCapture(destination);}
                });
            } catch(Exception error) {
                android.util.Log.e("Keratoconus","Image analysis failed",error);
                runOnUiThread(() -> showFailure("The saved image could not be processed ("+error.getClass().getSimpleName()+"). Choose another original image."));
            }
        });
    }

    private void updateCalibrationStatus() {
        boolean ready=KeratoconusTransfer.profile(this).optBoolean("geometry_confirmed",false);
        ((TextView)findViewById(R.id.calibrationStatus)).setText(ready
                ? "Geometry recorded. Export an untouched photo taken with this lens and zoom."
                : "Photo checks and experimental phone maps are available. Calibration is needed for measured corneal maps.");
    }

    private void chooseExportOptions() {
        if(lastDetection==null || lastSourceFile==null)return;
        android.widget.LinearLayout form=new android.widget.LinearLayout(this);form.setOrientation(1);form.setPadding(24,12,24,12);
        android.widget.Spinner eye=new android.widget.Spinner(this);
        eye.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Right eye","Left eye"}));form.addView(eye);
        android.widget.CheckBox untouched=new android.widget.CheckBox(this);
        untouched.setText("This is the untouched original photo, taken with the lens and zoom recorded in calibration.");form.addView(untouched);
        TextView help=new TextView(this);help.setText("Cropped images can be exported for detection review, but reconstruction will require an untouched original and completed calibration.");form.addView(help);
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Export this capture").setView(form)
                .setNegativeButton("Cancel",null).setPositiveButton("Save ZIP",(dialog,which)->{
                    exportEye=eye.getSelectedItemPosition()==0?"right":"left";exportUntouched=untouched.isChecked();
                    getSharedPreferences("smartkc_capture",0).edit().putString("export_eye",exportEye).putBoolean("export_untouched",exportUntouched).apply();
                    exportPicker.launch("smartkc-"+captureId+".zip");
                }).show();
    }

    private void exportCapture(Uri destination) {
        if(lastSourceFile==null || lastDetection==null)return;
        setBusy(true);
        executor.execute(() -> {
            try(java.io.OutputStream output=getContentResolver().openOutputStream(destination)) {
                if(output==null)throw new IOException("Cannot open destination");
                KeratoconusTransfer.exportCapture(this,output,lastSourceFile,captureId,exportEye,exportUntouched,
                        sourceWidth,sourceHeight,analysisWidth,analysisHeight,lastDetection);
                runOnUiThread(()->{setBusy(false);android.widget.Toast.makeText(this,"Capture package saved. Reconstruct it on the computer, then open result.zip here.",android.widget.Toast.LENGTH_LONG).show();});
            } catch(Exception error) {runOnUiThread(()->showFailure("Capture export failed: "+error.getMessage()));}
        });
    }

    private void importMaps(Uri uri) {
        if(lastSourceFile==null)return;
        setBusy(true);
        executor.execute(() -> {
            try(InputStream input=getContentResolver().openInputStream(uri)) {
                if(input==null)throw new IOException("Cannot open result");
                KeratoconusTransfer.Imported result=KeratoconusTransfer.importResult(input,lastSourceFile,captureId,exportEye);
                org.json.JSONObject metrics=result.manifest.getJSONObject("metrics");
                String measuredText=String.format(Locale.US,"%s eye | Desktop research reconstruction\nSim-K: %.2f / %.2f D\nMean K: %.2f D | Difference: %.2f D\nAxis: %.1f degrees\n\nMeasured geometry recorded. These maps and measurements are research outputs, not a diagnosis.",
                        result.manifest.getString("eye"),metrics.getDouble("sim_k1_d"),metrics.getDouble("sim_k2_d"),
                        metrics.getDouble("mean_k_d"),metrics.getDouble("difference_d"),metrics.getDouble("axis_degrees"));
                double ppk=metrics.getDouble("ppk_research");
                String simulationFlag=ppk>=0.45?"KC-like study threshold triggered under these assumptions"
                        :ppk>=0.20?"Borderline study threshold under these assumptions"
                        :"Study threshold not triggered; keratoconus is not ruled out";
                String text=result.simulation?String.format(Locale.US,
                        "SUPERVISED DEMONSTRATION ONLY\n\n%s\n\nSimulated K values: %.2f / %.2f D\nPPK algorithm score: %.2f (not this person's disease probability)\n\nAttachment distance is assumed, not measured. Holding angle, camera position and tear film may cause a false flag. Do not use this simulation to decide whether the person has keratoconus.\n\nGeometry assumptions: %s",
                        simulationFlag,metrics.getDouble("sim_k1_d"),metrics.getDouble("sim_k2_d"),ppk,
                        result.manifest.getJSONObject("calibration").optString("warning","Study-default geometry; not calibrated for this hold")):measuredText;
                File resultDirectory=new File(lastSourceFile.getParentFile(),result.simulation?"simulation":"measured-reconstruction");
                if(!resultDirectory.isDirectory()&&!resultDirectory.mkdirs())throw new IOException("Cannot save result record");
                File record=new File(resultDirectory,"reconstruction.json");
                try(java.io.FileOutputStream output=new java.io.FileOutputStream(record)){output.write(result.manifest.toString(2).getBytes("UTF-8"));}
                for(int i=0;i<2;i++)try(java.io.FileOutputStream output=new java.io.FileOutputStream(new File(resultDirectory,i==0?"axial.png":"tangential.png"))){(i==0?result.axial:result.tangential).compress(Bitmap.CompressFormat.PNG,100,output);}
                runOnUiThread(()->{
                    setBusy(false);resultContainer.setVisibility(View.VISIBLE);
                    findViewById(R.id.mapsContainer).setVisibility(View.VISIBLE);
                    axialMap.setVisibility(View.VISIBLE);tangentialMap.setVisibility(View.VISIBLE);
                    axialMap.setImageBitmap(result.axial);tangentialMap.setImageBitmap(result.tangential);
                    resultText.setText(result.simulation?"Research simulation - assumed geometry":"Corneal maps reconstructed on computer");metricsText.setText(text);
                    resultText.setTextColor(ContextCompat.getColor(this,R.color.text_primary));
                });
            } catch(Exception error){runOnUiThread(()->showFailure("Maps could not be opened: "+error.getMessage()));}
        });
    }

    private Bitmap orientPhoto(Bitmap bitmap, Uri uri) throws IOException {
        int orientation;
        try (InputStream metadata = getContentResolver().openInputStream(uri)) {
            if (metadata == null) return bitmap;
            orientation = new ExifInterface(metadata).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90); break;
            default: return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void attachPositioning(Bitmap source,PlacidoSegmentationEngine.Result result) {
        Bitmap scaled=Bitmap.createScaledBitmap(result.crop,512,512,true);
        int[] pixels=new int[512*512];scaled.getPixels(pixels,0,512,0,0,512,512);
        double x=(result.sourceLeft+result.centerX*result.sourceSide/512.0)/source.getWidth();
        double y=(result.sourceTop+result.centerY*result.sourceSide/512.0)/source.getHeight();
        result.positioning=PositioningCheck.assess(pixels,512,result.centerX,result.centerY,result.detectedRings,x,y);
    }

    private Bitmap positioningOverview(Bitmap source,PlacidoSegmentationEngine.Result result) {
        float scale=Math.min(1f,900f/Math.max(source.getWidth(),source.getHeight()));
        Bitmap image=Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*scale)),
                Math.max(1,Math.round(source.getHeight()*scale)),true).copy(Bitmap.Config.ARGB_8888,true);
        android.graphics.Canvas canvas=new android.graphics.Canvas(image);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(android.graphics.Color.WHITE);
        canvas.drawRect(image.getWidth()*0.3f,image.getHeight()*0.3f,image.getWidth()*0.7f,image.getHeight()*0.7f,paint);
        float cx=image.getWidth()/2f,cy=image.getHeight()/2f;
        canvas.drawLine(cx-12,cy,cx+12,cy,paint);canvas.drawLine(cx,cy-12,cx,cy+12,paint);
        if(Float.isFinite(result.centerX)&&Float.isFinite(result.centerY)) {
            float x=(result.sourceLeft+result.centerX*result.sourceSide/512f)*scale;
            float y=(result.sourceTop+result.centerY*result.sourceSide/512f)*scale;
            paint.setColor(ContextCompat.getColor(this,result.positioning.usable?R.color.result_normal:R.color.result_suspected));
            canvas.drawCircle(x,y,14,paint);
        }
        return image;
    }

    private void showLiveResult(PlacidoSegmentationEngine.Result result) {
        setBusy(false);
        resultContainer.setVisibility(View.VISIBLE);
        sampleImage.setImageBitmap(result.overlay);
        findViewById(R.id.mapsContainer).setVisibility(View.GONE);
        axialMap.setVisibility(View.GONE);
        tangentialMap.setVisibility(View.GONE);
        PositioningCheck.Result check=result.positioning;
        if(check==null) {
            resultText.setText("Photo check unavailable");metricsText.setText(result.failureReason);return;
        }
        RingPatternPrototype.Descriptor descriptor=result.patternDescriptor==null
                ?new RingPatternPrototype.Descriptor(0,null):result.patternDescriptor;
        RingPatternPrototype.Result pattern=RingPatternPrototype.compare(descriptor,check.usable);
        String headline=!check.usable?"Adjust the capture and try again"
                :pattern.outsideReferenceRange?"Ring-shape variation flagged - research only":"Ring-shape comparison - research only";
        resultText.setText(headline);
        resultText.setTextColor(ContextCompat.getColor(this,R.color.result_suspected));
        StringBuilder report=new StringBuilder("RESEARCH DEMONSTRATION ONLY\nNot validated for testing patients.\n\n");
        report.append("Keratoconus status: undetermined. The four-example comparison failed a held-out audit and cannot classify disease.\n\n");
        report.append(pattern.outsideReferenceRange?"The ring-shape measurements differ from all four reference examples. This can reflect capture geometry, tear-film effects or segmentation error as well as eye shape.":"Reference similarity is shown for demonstration, not as a prediction.").append("\n\n");
        report.append("Comparison uses only four labelled project examples; it is not the published SmartKC disease classifier.\n");
        if(Double.isFinite(pattern.kcDistance))report.append(String.format(Locale.US,
                "Reference distance (lower = closer): KC %.3f | non-KC %.3f\nShape-comparison rings: %d\n\n",
                pattern.kcDistance,pattern.nonKcDistance,descriptor.rings));
        report.append("Photo checks: ").append(check.usable?"passed":"adjust and retake").append("\n");
        report.append("Rings recognised: ").append(result.detectedRings).append(" of 14\n");
        report.append("Framing: ").append(check.framingOkay?"near the middle":"adjust toward the middle").append("\n");
        report.append("Ring edge detail: ").append(check.detailOkay?"clear":"weak - refocus").append("\n");
        report.append("Contrast / bright clipping: ").append(check.exposureOkay?"passed":"needs adjustment").append("\n\n");
        for(String tip:check.guidance)report.append(tip).append("\n\n");
        report.append("The overview shows the photo centre target and detected ring centre. These checks assess the photographed reflection; they do not verify exact phone-to-attachment alignment or produce calibrated corneal measurements.");
        report.append("\n\nDo not use this output to decide whether a person has keratoconus. Similarity values are not disease probabilities. An ophthalmologist must assess any clinical concern.");
        metricsText.setText(report.toString());
    }

    private void showSelectedSample() {
        showingReference=true;
        findViewById(R.id.positioningOverview).setVisibility(View.GONE);
        lastDetection=null;
        KeratoconusSample sample = KeratoconusSample.ALL[sampleSpinner.getSelectedItemPosition()];
        setBusy(true);
        executor.execute(() -> {
            try {
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), sample.imageResource);
                PlacidoSegmentationEngine.Result mobile = engine.analyse(bitmap);
                runOnUiThread(() -> showSelectedSampleResult(sample, mobile));
            } catch (Exception error) {
                runOnUiThread(() -> showFailure("The sample could not be processed by the mobile Placido model."));
            }
        });
    }

    private void showSelectedSampleResult(KeratoconusSample sample,
                                          PlacidoSegmentationEngine.Result mobile) {
        setBusy(false);
        KeratoconusDecision.Outcome outcome = KeratoconusDecision.classify(sample.ppk);
        sampleImage.setImageResource(sample.imageResource);
        findViewById(R.id.mapsContainer).setVisibility(View.VISIBLE);
        axialMap.setVisibility(View.VISIBLE);
        tangentialMap.setVisibility(View.VISIBLE);
        axialMap.setImageResource(sample.axialMapResource);
        tangentialMap.setImageResource(sample.tangentialMapResource);
        int label = outcome == KeratoconusDecision.Outcome.NO_PATTERN ? R.string.result_no_kc
                : outcome == KeratoconusDecision.Outcome.POSSIBLE ? R.string.result_possible_kc
                : R.string.result_suspected_kc;
        resultText.setText(label);
        resultText.setTextColor(ContextCompat.getColor(this,
                outcome == KeratoconusDecision.Outcome.NO_PATTERN ? R.color.result_normal : R.color.result_suspected));
        metricsText.setText(String.format(Locale.US,
                "Saved reference result\nDataset label: %s\nPPK: %.0f%%  |  aCLMI: %.2f\nSim-K: %.2f D / %.2f D  |  Difference: %.2f D\n\nMobile model check: %s, %d rings, irregularity %.3f",
                sample.datasetLabel, sample.ppk * 100.0, sample.aClmi,
                sample.simK1, sample.simK2, Math.abs(sample.simK1 - sample.simK2),
                mobile.accepted ? "passed" : "failed", mobile.detectedRings, mobile.irregularity));
        if(mobile.patternDescriptor!=null) {
            RingPatternPrototype.Result match=RingPatternPrototype.compare(mobile.patternDescriptor,mobile.accepted);
            metricsText.append("\n\nReference-library self-match: "+match.group+"\nReference self-match demonstration; this example is also in the comparison library and is not an independent accuracy test.");
        }
        resultContainer.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        boolean enabled = !busy && engine != null;
        findViewById(R.id.captureKeratoconusButton).setEnabled(enabled);
        findViewById(R.id.retryPositionButton).setEnabled(enabled);
        findViewById(R.id.savedKeratoconusCapturesButton).setEnabled(enabled);
        findViewById(R.id.chooseKeratoconusButton).setEnabled(enabled);
        findViewById(R.id.runSampleButton).setEnabled(enabled);
        findViewById(R.id.calibrationButton).setEnabled(!busy);
        findViewById(R.id.exportCaptureButton).setEnabled(enabled && lastDetection!=null);
        findViewById(R.id.importMapsButton).setEnabled(enabled && lastDetection!=null);
        findViewById(R.id.generatePhoneMapsButton).setEnabled(enabled && lastSourceFile!=null && !showingReference && android.os.Build.VERSION.SDK_INT>=26);
    }

    private void showFailure(String message) {
        findViewById(R.id.mapsContainer).setVisibility(View.GONE);
        setBusy(false);
        resultContainer.setVisibility(View.VISIBLE);
        resultText.setText("Processing did not complete");
        resultText.setTextColor(ContextCompat.getColor(this, R.color.result_suspected));
        metricsText.setText(message);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if(cameraUri!=null)state.putString("camera_uri",cameraUri.toString());
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
