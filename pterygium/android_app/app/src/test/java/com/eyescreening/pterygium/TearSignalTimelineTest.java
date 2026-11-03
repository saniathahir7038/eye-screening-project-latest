package com.eyescreening.pterygium;
import org.junit.Test;import static org.junit.Assert.*;
public class TearSignalTimelineTest {
    private long[] times(){long[] t=new long[64];for(int i=0;i<t.length;i++)t[i]=i*250;return t;}
    private double[] means(){double[] m=new double[64];java.util.Arrays.fill(m,50);m[4]=120;return m;}
    private double[] edges(){double[] e=new double[64];java.util.Arrays.fill(e,10);e[4]=1;return e;}
    @Test public void pairedBlinkAndSustainedChangeProduceMeasuredInterval() {
        double[] e=edges();for(int i=20;i<e.length;i++)e[i]=7;
        TearSignalTimeline.Result r=TearSignalTimeline.analyse(times(),means(),e);
        assertEquals(TearSignalTimeline.Kind.CHANGE,r.kind);assertEquals(3.75,r.intervalSeconds,1e-9);assertEquals(1.25,r.blinkSeconds,1e-9);
    }
    @Test public void stableLongObservationIsCensoredNotDiseaseNegative() {
        TearSignalTimeline.Result r=TearSignalTimeline.analyse(times(),means(),edges());
        assertEquals(TearSignalTimeline.Kind.NO_CHANGE_OBSERVED,r.kind);assertEquals(14.5,r.intervalSeconds,1e-9);
    }
    @Test public void brightnessFlashAloneIsNotABlink() {
        double[] e=edges();e[4]=10;
        assertEquals(TearSignalTimeline.Kind.INCONCLUSIVE,TearSignalTimeline.analyse(times(),means(),e).kind);
    }
    @Test public void missingTimeGapCannotCreateFalseTiming() {
        long[] t=times();for(int i=20;i<t.length;i++)t[i]+=750;
        assertEquals(TearSignalTimeline.Kind.INCONCLUSIVE,TearSignalTimeline.analyse(t,means(),edges()).kind);
    }
    @Test public void transientEdgeDipDoesNotBecomeBreakup() {
        double[] e=edges();e[20]=7;e[21]=7;
        assertEquals(TearSignalTimeline.Kind.NO_CHANGE_OBSERVED,TearSignalTimeline.analyse(times(),means(),e).kind);
    }
    @Test public void repeatedBlinkAbstains() {
        double[] e=edges(),m=means();e[20]=1;m[20]=120;
        assertEquals(TearSignalTimeline.Kind.INCONCLUSIVE,TearSignalTimeline.analyse(times(),m,e).kind);
    }
    @Test public void unstableBaselineAbstains() {
        double[] e=edges();e[6]=15;e[8]=15;
        assertEquals(TearSignalTimeline.Kind.INCONCLUSIVE,TearSignalTimeline.analyse(times(),means(),e).kind);
    }
    @Test public void shortPostBlinkObservationAbstains() {
        assertEquals(TearSignalTimeline.Kind.INCONCLUSIVE,TearSignalTimeline.analyse(java.util.Arrays.copyOf(times(),25),java.util.Arrays.copyOf(means(),25),java.util.Arrays.copyOf(edges(),25)).kind);
    }
    @Test public void nonFiniteSignalAbstains() {
        double[] e=edges();e[20]=Double.NaN;
        assertEquals(TearSignalTimeline.Kind.INCONCLUSIVE,TearSignalTimeline.analyse(times(),means(),e).kind);
    }
    @Test public void timeOriginDoesNotChangeElapsedInterval() {
        long[] t=times();for(int i=0;i<t.length;i++)t[i]+=100;
        double[] e=edges();for(int i=20;i<e.length;i++)e[i]=7;
        assertEquals(3.75,TearSignalTimeline.analyse(t,means(),e).intervalSeconds,1e-9);
    }
    @Test public void sustainedChangeUsesTimeRatherThanFrameCount() {
        long[] t=new long[160];double[] m=new double[160],e=new double[160];
        for(int i=0;i<160;i++){t[i]=i*100;m[i]=50;e[i]=10;}
        m[10]=120;e[10]=1;for(int i=50;i<160;i++)e[i]=7;
        TearSignalTimeline.Result result=TearSignalTimeline.analyse(t,m,e);
        assertEquals(TearSignalTimeline.Kind.CHANGE,result.kind);assertEquals(3.9,result.intervalSeconds,1e-9);
    }
}
