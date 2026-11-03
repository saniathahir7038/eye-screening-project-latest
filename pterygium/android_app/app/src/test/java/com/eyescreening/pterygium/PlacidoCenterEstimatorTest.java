package com.eyescreening.pterygium;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlacidoCenterEstimatorTest {
    private int[] rings(int count, boolean inconsistent) {
        int[] labels=new int[512*512];
        for(int label=2;label<count+2;label++) {
            double cx=inconsistent ? 100+label*22 : 284;
            double cy=inconsistent ? 180+label*14 : 231;
            double radius=20+label*11;
            for(int y=0;y<512;y++) for(int x=0;x<512;x++) {
                double angle=Math.toDegrees(Math.atan2(y-cy,x-cx));
                if(angle > -25 && angle < 25) continue; // attachment support gap
                if(Math.abs(Math.hypot(x-cx,y-cy)-radius)<1.5) labels[y*512+x]=label;
            }
        }
        return labels;
    }
    @Test public void missingCentralRegionRecoveredFromBrokenConcentricRings() {
        double[] center=PlacidoCenterEstimator.estimate(rings(6,false),512);
        assertNotNull(center);assertEquals(284,center[0],1);assertEquals(231,center[1],1);
    }
    @Test public void blankIsRejected() { assertNull(PlacidoCenterEstimator.estimate(new int[512*512],512)); }
    @Test public void tooFewRingsAreRejected() { assertNull(PlacidoCenterEstimator.estimate(rings(3,false),512)); }
    @Test public void inconsistentCentresAreRejected() { assertNull(PlacidoCenterEstimator.estimate(rings(6,true),512)); }
}
