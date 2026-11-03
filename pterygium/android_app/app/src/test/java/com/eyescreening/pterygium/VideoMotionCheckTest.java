package com.eyescreening.pterygium;
import org.junit.Test;import static org.junit.Assert.*;
public class VideoMotionCheckTest {
    private double[] texture(int seed){java.util.Random r=new java.util.Random(seed);double[] image=new double[160*160];for(int i=0;i<image.length;i++)image[i]=40+r.nextDouble()*150;return image;}
    @Test public void stableImageHasZeroShift() {
        double[] image=texture(1),r=VideoMotionCheck.registration(image,image);
        assertEquals(0,r[0],0);assertEquals(0,r[1],0);assertEquals(1,r[2],1e-9);
    }
    @Test public void knownTranslationIsRecovered() {
        double[] source=texture(1),moved=new double[source.length];
        for(int y=2;y<158;y++)for(int x=0;x<156;x++)moved[(y-2)*160+x+4]=source[y*160+x];
        double[] r=VideoMotionCheck.registration(source,moved);assertEquals(4,r[0],0);assertEquals(-2,r[1],0);assertEquals(1,r[2],1e-9);
    }
    @Test public void unrelatedImagesDoNotAppearStable() {
        assertTrue(VideoMotionCheck.registration(texture(1),texture(2))[2]<.65);
    }
}
