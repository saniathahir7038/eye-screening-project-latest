package com.eyescreening.pterygium;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Re-run signal and baseline checks from a phone ZIP without decoding its video. */
public final class ReplayDryEyeReport {
    public static void main(String[] args) throws Exception {
        if(args.length<1||args.length>2||(args.length==2&&!args[1].equals("--current")))throw new IllegalArgumentException("Supply one dry-eye analysis ZIP and optionally --current");
        try(ZipFile zip=new ZipFile(args[0])) {
            String metadata=text(zip,"report.json",65536).replace("\\/","/");
            boolean method2=metadata.contains("post-blink-translation/2");
            if(!method2&&!metadata.contains("post-blink-translation/1"))throw new IOException("Unknown analysis method");
            boolean brightness=method2||args.length==2;
            System.out.println("Brightness guard enabled: "+brightness);
            ZipEntry trace=zip.getEntry("trace.csv");
            if(trace==null)throw new IOException("Report has no completed trace");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=zip.getInputStream(trace)) {
                byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>262144)throw new IOException("Oversized trace");}
            }
            String[] rows=new String(bytes.toByteArray(),StandardCharsets.UTF_8).trim().split("\n");
            if(rows.length<2||rows.length>241||!rows[0].startsWith("requested_time_ms,centre_x,centre_y,"))throw new IOException("Invalid trace");
            TrackedTearSignal timeline=new TrackedTearSignal(0,0,160,brightness);
            long previous=-1;
            for(int i=1;i<rows.length;i++) {
                String[] fields=rows[i].trim().split(",");if(fields.length!=12&&fields.length!=16)throw new IOException("Invalid trace row");
                long time=Long.parseLong(fields[0]);
                if(time<=previous||time<0||time>=30000)throw new IOException("Invalid frame time");previous=time;
                String name=String.format(Locale.US,"sample-%05d.bin",time);
                ZipEntry entry=zip.getEntry(name);
                if(entry==null||entry.getSize()!=204808)throw new IOException("Missing or truncated measured crop: "+name);
                double[] image=new double[25600];
                try(DataInputStream input=new DataInputStream(zip.getInputStream(entry))) {
                    if(input.readLong()!=time)throw new IOException("Crop/trace time mismatch");
                    for(int p=0;p<image.length;p++){image[p]=input.readDouble();if(!Double.isFinite(image[p]))throw new IOException("Non-finite crop");}
                    if(input.read()!=-1)throw new IOException("Extra crop data");
                }
                if(!fields[4].equals("true")&&!fields[4].equals("false"))throw new IOException("Invalid tracking flag");
                timeline.addMeasuredSample(time,new RingVideoTracker.Sample(image,Double.parseDouble(fields[1]),Double.parseDouble(fields[2]),Double.parseDouble(fields[3]),Boolean.parseBoolean(fields[4])));
            }
            TearSignalTimeline.Result result=timeline.analyse();
            System.out.printf(Locale.US,"samples=%d kind=%s interval=%.9f blink=%.9f%n%s%n",rows.length-1,result.kind,result.intervalSeconds,result.blinkSeconds,result.detail);
        }
    }
    private static String text(ZipFile zip,String name,int limit) throws IOException {
        ZipEntry entry=zip.getEntry(name);if(entry==null)throw new IOException("Missing "+name);
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(InputStream input=zip.getInputStream(entry)) {
            byte[] buffer=new byte[8192];int n;
            while((n=input.read(buffer))!=-1){output.write(buffer,0,n);if(output.size()>limit)throw new IOException("Oversized "+name);}
        }
        return new String(output.toByteArray(),StandardCharsets.UTF_8);
    }
}
