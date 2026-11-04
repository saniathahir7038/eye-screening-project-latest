package com.eyescreening.pterygium;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DryEyeSampleFileTest {
    @Test public void measuredPixelsSurviveExportWithoutEightBitRounding() throws Exception {
        File file=File.createTempFile("dry-eye-sample-",".bin");
        try {
            double[] pixels=new double[25600];
            for(int i=0;i<pixels.length;i++)pixels[i]=(i%255)+.123456789012345;
            DryEyeSampleFile.write(file,5500,pixels);assertEquals(204808,file.length());
            try(DataInputStream input=new DataInputStream(new FileInputStream(file))) {
                assertEquals(5500,input.readLong());
                for(double value:pixels)assertEquals(Double.doubleToLongBits(value),Double.doubleToLongBits(input.readDouble()));
                assertEquals(-1,input.read());
            }
        } finally {file.delete();}
    }
    @Test(expected=IllegalArgumentException.class) public void wrongSizeCannotProduceAValidLookingSample() throws Exception {
        DryEyeSampleFile.write(new File("not-created.bin"),125,new double[10]);
    }
    @Test(expected=IllegalArgumentException.class) public void nonFiniteMeasurementsCannotBeExported() throws Exception {
        double[] pixels=new double[25600];pixels[100]=Double.NaN;
        DryEyeSampleFile.write(new File("not-created.bin"),125,pixels);
    }
}
