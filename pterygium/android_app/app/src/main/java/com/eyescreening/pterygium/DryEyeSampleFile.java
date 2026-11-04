package com.eyescreening.pterygium;
import java.io.*;

/** Fixed-size lossless measured pixels for independent phone-report replay. */
final class DryEyeSampleFile {
    static void write(File file,long time,double[] pixels) throws IOException {
        if(time<0||pixels.length!=160*160)throw new IllegalArgumentException("Invalid measured crop");
        for(double value:pixels)if(!Double.isFinite(value))throw new IllegalArgumentException("Invalid pixel");
        try(DataOutputStream output=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            output.writeLong(time);for(double value:pixels)output.writeDouble(value);
        }
    }
}
