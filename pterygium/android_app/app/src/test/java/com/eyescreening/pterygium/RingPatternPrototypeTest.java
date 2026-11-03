package com.eyescreening.pterygium;
import org.junit.Test;
import static org.junit.Assert.*;
public class RingPatternPrototypeTest {
    @Test public void referenceSelfMatchesAreExplicitlyOnlyLibraryChecks() {
        for(int i=0;i<4;i++) {
            RingPatternPrototype.Result r=RingPatternPrototype.compare(new RingPatternPrototype.Descriptor(10,RingPatternPrototype.REFERENCES[i].clone()),true);
            assertEquals(i<2?RingPatternPrototype.Group.NON_KC_REFERENCE:RingPatternPrototype.Group.KC_REFERENCE,r.group);
        }
    }
    @Test public void failedPhotoCannotBecomeNegativeDiseaseResult() {
        assertEquals(RingPatternPrototype.Group.INCONCLUSIVE,RingPatternPrototype.compare(new RingPatternPrototype.Descriptor(10,RingPatternPrototype.REFERENCES[0]),false).group);
    }
    @Test public void unsupportedFeatureRangeAbstains() {
        RingPatternPrototype.Result r=RingPatternPrototype.compare(new RingPatternPrototype.Descriptor(8,new double[]{.1,.1,.1}),true);
        assertEquals(RingPatternPrototype.Group.INCONCLUSIVE,r.group);assertTrue(r.outsideReferenceRange);
    }
    @Test public void closeGroupsAbstain() {
        double[] midpoint=new double[3];for(int i=0;i<3;i++)midpoint[i]=(RingPatternPrototype.REFERENCES[0][i]+RingPatternPrototype.REFERENCES[2][i])/2;
        assertEquals(RingPatternPrototype.Group.INCONCLUSIVE,RingPatternPrototype.compare(new RingPatternPrototype.Descriptor(9,midpoint),true).group);
    }
    @Test public void insufficientShapeCoverageAbstains() {
        assertEquals(RingPatternPrototype.Group.INCONCLUSIVE,RingPatternPrototype.compare(new RingPatternPrototype.Descriptor(4,null),true).group);
    }
    @Test public void nonFiniteFeaturesAbstain() {
        assertEquals(RingPatternPrototype.Group.INCONCLUSIVE,RingPatternPrototype.compare(new RingPatternPrototype.Descriptor(9,new double[]{Double.NaN,.03,.03}),true).group);
    }
    private int[] geometry(int cx,int cy,double localDistortion) {
        int[] labels=new int[512*512];
        for(int y=0;y<512;y++)for(int x=0;x<512;x++) {
            double angle=Math.atan2(y-cy,x-cx),radius=Math.hypot(x-cx,y-cy);
            for(int label=2;label<=10;label++) {
                double expected=(15+label*15)*(1+localDistortion*Math.cos(3*angle));
                if(Math.abs(radius-expected)<1.6){labels[y*512+x]=label;break;}
            }
        }
        return labels;
    }
    @Test public void shapeFeaturesAreTranslationInvariant() {
        RingPatternPrototype.Descriptor a=RingPatternPrototype.describe(geometry(220,230,.04),512,220,230);
        RingPatternPrototype.Descriptor b=RingPatternPrototype.describe(geometry(280,270,.04),512,280,270);
        assertNotNull(a.features);assertArrayEquals(a.features,b.features,1e-8);
    }
    @Test public void localDeformationChangesMeasuredResidual() {
        RingPatternPrototype.Descriptor a=RingPatternPrototype.describe(geometry(256,256,0),512,256,256);
        RingPatternPrototype.Descriptor b=RingPatternPrototype.describe(geometry(256,256,.08),512,256,256);
        assertTrue(b.features[0]>a.features[0]+.02);
    }
}
