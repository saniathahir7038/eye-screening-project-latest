package com.eyescreening.pterygium;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrackedTearSignalTest {
    private RingVideoTracker.Frame frame(int shift, boolean closed, boolean obscured) {
        double[] image=new double[400*240]; java.util.Arrays.fill(image, 40);
        java.util.Random random=new java.util.Random(731);
        for(int y=0;y<240;y++)for(int x=0;x<300;x++) {
            double value=40+random.nextDouble()*100;
            if(x+shift>=0&&x+shift<400)image[y*400+x+shift]=closed?180:value;
        }
        if(obscured)java.util.Arrays.fill(image,80);
        return new RingVideoTracker.Frame(400,240,image);
    }
    private TearSignalTimeline.Result replay(boolean move, int lostAt) {
        TrackedTearSignal timeline=new TrackedTearSignal(150,120,160);
        for(int i=0;i<64;i++)timeline.addFrame(i*250,frame(move?i:0,i==4,i==lostAt));
        return timeline.analyse();
    }
    @Test public void translationIsCompensatedWithoutInventingAChange() {
        TearSignalTimeline.Result result=replay(true,-1);
        assertEquals(TearSignalTimeline.Kind.NO_CHANGE_OBSERVED,result.kind);
        assertEquals(14.5,result.intervalSeconds,1e-9);
    }
    @Test public void occlusionStopsObservationEvenWhenLaterFramesRecover() {
        TearSignalTimeline.Result result=replay(true,24);
        assertEquals(TearSignalTimeline.Kind.PARTIAL_OBSERVATION,result.kind);
        assertEquals(4.5,result.intervalSeconds,1e-9);
    }
    @Test public void baselineOcclusionDoesNotCreateAPartialOrDiseaseResult() {
        assertEquals(TearSignalTimeline.Kind.INCONCLUSIVE,replay(false,7).kind);
    }
    @Test public void videoWithoutBlinkDoesNotStartAnInterval() {
        TrackedTearSignal timeline=new TrackedTearSignal(150,120,160);
        for(int i=0;i<64;i++)timeline.addFrame(i*250,frame(0,false,false));
        assertEquals(TearSignalTimeline.Kind.INCONCLUSIVE,timeline.analyse().kind);
    }
    @Test public void traceDistinguishesRecoveryFromActualIntervalInclusion() {
        TrackedTearSignal timeline=new TrackedTearSignal(150,120,160);
        for(int i=0;i<40;i++)timeline.addFrame(i*250,frame(i,i==4,i==24));
        timeline.analyse();String[] rows=timeline.traceCsv().split("\n");
        assertEquals(41,rows.length);
        assertEquals("false",rows[25].split(",")[8]);
        assertEquals("false",rows[25].split(",")[9]);
        assertEquals("true",rows[26].split(",")[8]);
        assertEquals("false",rows[26].split(",")[9]);
        assertEquals("true",rows[7].split(",")[9]);
    }
    @Test public void traceDoesNotTreatFramesBeforeReferenceAsMeasuredInterval() {
        TrackedTearSignal timeline=new TrackedTearSignal(150,120,160);
        for(int i=0;i<64;i++)timeline.addFrame(i*250,frame(0,i==4,false));
        String[] rows=timeline.traceCsv().split("\n");
        assertEquals("NaN",rows[1].split(",")[3]);
        assertEquals("false",rows[1].split(",")[9]);
        assertEquals("true",rows[7].split(",")[9]);
    }
    private TrackedTearSignal gainChange(boolean guard,double gain) {
        TrackedTearSignal timeline=new TrackedTearSignal(150,120,160,guard);
        for(int i=0;i<64;i++) {
            RingVideoTracker.Frame f=frame(0,i==4,false);
            if(i>=20)for(int p=0;p<f.gray.length;p++)f.gray[p]*=gain;
            timeline.addFrame(i*250,f);
        }
        return timeline;
    }
    @Test public void darkeningThatPassesCorrelationMustNotCreateASignalInterval() {
        // Same image, uniformly darker: the legacy edge rule reports an event.
        assertEquals(TearSignalTimeline.Kind.CHANGE,gainChange(false,.65).analyse().kind);
        TrackedTearSignal fixed=gainChange(true,.65);TearSignalTimeline.Result r=fixed.analyse();
        assertEquals(TearSignalTimeline.Kind.PARTIAL_OBSERVATION,r.kind);
        assertTrue(r.detail.contains("brightness"));assertEquals(3.5,r.intervalSeconds,1e-9);
        String[] bad=fixed.traceCsv().split("\n")[21].split(",");
        assertEquals("true",bad[4]);assertEquals("false",bad[8]);assertEquals("false",bad[14]);
    }
    @Test public void substantialBrighteningAlsoInterruptsObservation() {
        TearSignalTimeline.Result r=gainChange(true,1.30).analyse();
        assertEquals(TearSignalTimeline.Kind.PARTIAL_OBSERVATION,r.kind);assertTrue(r.detail.contains("brightness"));
    }
    @Test public void smallStableGainVariationDoesNotForceARetake() {
        assertEquals(TearSignalTimeline.Kind.NO_CHANGE_OBSERVED,gainChange(true,.95).analyse().kind);
    }
    private TearSignalTimeline.Result constantMeanChange(boolean laterDarkening) {
        TrackedTearSignal timeline=new TrackedTearSignal(150,120,160);
        for(int i=0;i<64;i++) {
            RingVideoTracker.Frame f=frame(0,i==4,false);
            if(i>=20)for(int p=0;p<f.gray.length;p++)f.gray[p]=90+(f.gray[p]-90)*.70;
            if(laterDarkening&&i>=50)for(int p=0;p<f.gray.length;p++)f.gray[p]*=.65;
            timeline.addFrame(i*250,f);
        }
        return timeline.analyse();
    }
    @Test public void constantMeanEdgeChangeIsStillReportedAsAnUnconfirmedImageSignal() {
        TearSignalTimeline.Result r=constantMeanChange(false);
        assertEquals(TearSignalTimeline.Kind.CHANGE,r.kind);assertEquals(3.75,r.intervalSeconds,1e-9);
    }
    @Test public void laterDarkeningDoesNotEraseAnAlreadyConfirmedImageChange() {
        TearSignalTimeline.Result r=constantMeanChange(true);
        assertEquals(TearSignalTimeline.Kind.CHANGE,r.kind);assertEquals(3.75,r.intervalSeconds,1e-9);
    }
}
