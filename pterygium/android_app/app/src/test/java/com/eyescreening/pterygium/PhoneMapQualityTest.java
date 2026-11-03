package com.eyescreening.pterygium;
import org.junit.Test;
import static org.junit.Assert.*;

public class PhoneMapQualityTest {
    @Test public void reportedPhotoFitsReceiveDifferentEngineeringChecks(){
        assertTrue(PhoneMapQuality.assess(.0028874969530860387,3.9379170655041293,2407129.4213048536,198).consistent);
        assertTrue(PhoneMapQuality.assess(.016909798015961385,3.6070426721600617,52989.62740929513,255).consistent);
        assertFalse(PhoneMapQuality.assess(.11154611199383578,4.708789333619422,5.632232897641281e7,228).consistent);
    }
    @Test public void checkIsInvariantUnderUniformGeometryScale(){
        PhoneMapQuality original=PhoneMapQuality.assess(.016,3.6,50000,255),scaled=PhoneMapQuality.assess(.032,7.2,50000,255);
        assertEquals(original.consistent,scaled.consistent);assertEquals(original.relativeRms,scaled.relativeRms,1e-14);
    }
    @Test public void moreCoverageDoesNotExcusePoorFit(){assertFalse(PhoneMapQuality.assess(.1,4,10000,360).consistent);}
    @Test public void invalidNumbersNeverPass(){
        assertFalse(PhoneMapQuality.assess(Double.NaN,4,1000,255).consistent);
        assertFalse(PhoneMapQuality.assess(.001,0,1000,255).consistent);
        assertFalse(PhoneMapQuality.assess(-.001,4,1000,255).consistent);
    }
    @Test public void unstableNumericsAndTooLittleCoverageFlagged(){
        assertFalse(PhoneMapQuality.assess(.001,4,1e10,255).consistent);
        assertFalse(PhoneMapQuality.assess(.001,4,1000,179).consistent);
    }
    @Test public void retrySelectionPrefersConsistentResult(){
        PhoneMapQuality lowResidualButUnstable=PhoneMapQuality.assess(.001,4,1e10,255);
        PhoneMapQuality consistent=PhoneMapQuality.assess(.01,4,1000,255);
        assertTrue(PhoneMapQuality.prefer(consistent,lowResidualButUnstable));assertFalse(PhoneMapQuality.prefer(lowResidualButUnstable,consistent));
    }
    @Test public void equivalentResultsKeepEarlierAttempt(){
        PhoneMapQuality first=PhoneMapQuality.assess(.01,4,1000,255),same=PhoneMapQuality.assess(.01,4,1000,255);
        assertFalse(PhoneMapQuality.prefer(same,first));assertTrue(PhoneMapQuality.prefer(first,null));
    }
}
