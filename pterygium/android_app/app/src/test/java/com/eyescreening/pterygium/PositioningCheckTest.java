package com.eyescreening.pterygium;
import org.junit.Test;
import static org.junit.Assert.*;
public class PositioningCheckTest {
    private int[] rings() {
        int[] image=new int[512*512];
        for(int y=0;y<512;y++)for(int x=0;x<512;x++) {
            int value=((int)(Math.hypot(x-256,y-256)/6))%2==0?40:190;
            image[y*512+x]=0xff000000|(value<<16)|(value<<8)|value;
        }
        return image;
    }
    @Test public void centredDetailedReflectionPasses() {
        assertTrue(PositioningCheck.assess(rings(),512,256,256,9,0.5,0.5).usable);
    }
    @Test public void tooFewRingsCannotPassEvenOnSharpPhoto() {
        PositioningCheck.Result r=PositioningCheck.assess(rings(),512,256,256,7,0.5,0.5);
        assertFalse(r.usable);assertFalse(r.ringsVisible);assertTrue(r.detailOkay);
    }
    @Test public void farRightReflectionGetsCorrectImageDirection() {
        PositioningCheck.Result r=PositioningCheck.assess(rings(),512,256,256,9,0.85,0.5);
        assertFalse(r.usable);assertFalse(r.framingOkay);assertTrue(r.guidance.get(0).contains("right"));
    }
    @Test public void uniformBrightImageFailsDetailAndClipping() {
        int[] white=new int[512*512];java.util.Arrays.fill(white,0xffffffff);
        PositioningCheck.Result r=PositioningCheck.assess(white,512,256,256,9,0.5,0.5);
        assertFalse(r.usable);assertFalse(r.detailOkay);assertFalse(r.exposureOkay);
    }
    @Test public void smoothIntensityGradientDoesNotPassAsFineDetail() {
        int[] smooth=new int[512*512];
        for(int y=0;y<512;y++)for(int x=0;x<512;x++){int value=x/2;smooth[y*512+x]=0xff000000|(value<<16)|(value<<8)|value;}
        assertFalse(PositioningCheck.assess(smooth,512,256,256,9,0.5,0.5).detailOkay);
    }
    @Test public void missingCentreFailsWithoutSuggestingAValidPose() {
        PositioningCheck.Result r=PositioningCheck.assess(rings(),512,Double.NaN,Double.NaN,0,Double.NaN,Double.NaN);
        assertFalse(r.usable);assertFalse(r.ringsVisible);assertFalse(r.detailOkay);
    }
    @Test(expected=IllegalArgumentException.class) public void malformedImageRejected() {
        PositioningCheck.assess(new int[4],512,256,256,9,0.5,0.5);
    }
}
