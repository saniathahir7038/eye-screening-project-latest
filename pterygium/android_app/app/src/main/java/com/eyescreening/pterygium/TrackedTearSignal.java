package com.eyescreening.pterygium;

import java.util.ArrayList;
import java.util.List;

/** Shared phone/replay path: measure translated pixels, then gate a continuous interval. */
final class TrackedTearSignal {
    // Prototype image-quality guard, not a clinical threshold. Correlation alone
    // is insensitive to gain changes that also reduce the raw edge signal.
    static final double MAX_MEAN_CHANGE_FRACTION=.20, MIN_MEAN_CHANGE_GRAY=8;
    private final List<Long> times = new ArrayList<>();
    private final List<double[]> images = new ArrayList<>();
    private final List<Boolean> tracked = new ArrayList<>();
    private final List<Double> means = new ArrayList<>(), edges = new ArrayList<>();
    private final double cx, cy, side;
    private RingVideoTracker tracker;
    private boolean baselineNext;
    private final List<RingVideoTracker.Sample> samples = new ArrayList<>();
    private boolean[] finalValid;
    private double[][] residuals;
    private TearSignalTimeline.Result lastResult;
    private final boolean checkBrightness;
    private double baselineMean=Double.NaN;
    private boolean[] brightnessPass;
    private String[] reasons;

    TrackedTearSignal(double cx, double cy, double side) {
        this(cx,cy,side,true);
    }
    TrackedTearSignal(double cx,double cy,double side,boolean checkBrightness) {
        this.cx = cx; this.cy = cy; this.side = side;
        this.checkBrightness=checkBrightness;
    }

    RingVideoTracker.Sample addFrame(long time, RingVideoTracker.Frame frame) {
        if (tracker == null && baselineNext) tracker = new RingVideoTracker(frame, cx, cy, side);
        RingVideoTracker.Sample sample = tracker == null
                ? new RingVideoTracker.Sample(RingVideoTracker.patch(frame, cx, cy, side), cx, cy, Double.NaN, false)
                : tracker.track(frame);
        addMeasuredSample(time, sample);
        if (tracker == null) {
            int n = times.size(); long[] t = new long[n]; double[] m = new double[n], e = new double[n];
            for (int i = 0; i < n; i++) { t[i] = times.get(i); m[i] = means.get(i); e[i] = edges.get(i); }
            baselineNext = TearSignalTimeline.findBlink(t, m, e) >= 0;
        }
        return sample;
    }

    // Also used by the offline report replay; no tracking or image decoding is
    // repeated when replaying the phone's exact measured pixels.
    void addMeasuredSample(long time, RingVideoTracker.Sample sample) {
        lastResult=null;
        times.add(time); images.add(sample.image); tracked.add(sample.reliable); samples.add(sample);
        double mean = 0, edge = 0; double[] image = sample.image;
        for (double value : image) mean += value;
        for (int y = 1; y < 159; y++) for (int x = 1; x < 159; x++) {
            int p = y * 160 + x;
            edge += Math.abs(image[p] - image[p - 1]) + Math.abs(image[p] - image[p - 160]);
        }
        means.add(mean / image.length); edges.add(edge / (158.0 * 158.0 * 2));
    }

    TearSignalTimeline.Result analyse() {
        int n = times.size();
        long[] t = new long[n]; double[] m = new double[n], e = new double[n];
        boolean[] valid = new boolean[n];
        finalValid = valid; residuals = new double[n][3];
        reasons=new String[n];brightnessPass=new boolean[n];java.util.Arrays.fill(brightnessPass,true);baselineMean=Double.NaN;
        for(double[] residual:residuals)java.util.Arrays.fill(residual,Double.NaN);
        for (int i = 0; i < n; i++) {
            t[i] = times.get(i); valid[i] = tracked.get(i);
            m[i] = means.get(i); e[i] = edges.get(i);
            if(!valid[i])reasons[i]=Double.isNaN(samples.get(i).correlation)?"Before post-blink reference":"Ring tracking stopped";
        }
        TearSignalTimeline.Result result = TearSignalTimeline.analyse(t, m, e, valid);
        if (result.baselineIndex < 0) { lastResult=result; return result; }
        int start=result.baselineIndex,end=start;
        baselineMean=0;
        while(end<n&&t[end]-t[start]<1000){baselineMean+=m[end];end++;}
        baselineMean/=(end-start);
        // Translation compensation must also agree with the actual post-blink
        // baseline. Keep the old residual displacement and correlation gates.
        double[] baseline = images.get(result.baselineIndex);
        for (int i = result.baselineIndex + 1; i < n; i++) {
            double[] motion = VideoMotionCheck.registration(baseline, images.get(i));
            residuals[i]=motion;
            if (Math.abs(motion[0]) > 6 || Math.abs(motion[1]) > 6 || motion[2] < .65) {
                valid[i] = false;reasons[i]="The ring pattern no longer matched the post-blink reference";
            }
        }
        if(checkBrightness)for(int i=start;i<n;i++) {
            brightnessPass[i]=Math.abs(m[i]-baselineMean)<=Math.max(MIN_MEAN_CHANGE_GRAY,MAX_MEAN_CHANGE_FRACTION*baselineMean);
            if(!brightnessPass[i]){valid[i]=false;reasons[i]="Image brightness changed substantially";}
        }
        lastResult=TearSignalTimeline.analyse(t, m, e, valid,reasons);
        return lastResult;
    }

    String traceCsv() {
        if(lastResult==null)analyse();
        StringBuilder csv=new StringBuilder("requested_time_ms,centre_x,centre_y,template_correlation,template_pass,residual_x,residual_y,baseline_correlation,quality_pass,used_for_interval,mean_gray,edge_signal,baseline_mean_gray,mean_change_gray,brightness_pass,quality_reason\n");
        double until=1000*(lastResult.blinkSeconds+lastResult.intervalSeconds);
        if(lastResult.kind==TearSignalTimeline.Kind.CHANGE) {
            int sustained=0;
            for(int i=0;i<times.size();i++)if(times.get(i)>=until) {
                sustained++;
                if(sustained>=3&&times.get(i)-until>=500){until=times.get(i);break;}
            }
        }
        for(int i=0;i<times.size();i++) {
            RingVideoTracker.Sample sample=samples.get(i);
            boolean used=i>=lastResult.baselineIndex&&lastResult.baselineIndex>=0&&times.get(i)<=until&&finalValid[i];
            String brightnessStatus=checkBrightness&&Double.isFinite(baselineMean)&&lastResult.baselineIndex>=0&&i>=lastResult.baselineIndex?Boolean.toString(brightnessPass[i]):"not-evaluated";
            csv.append(String.format(java.util.Locale.US,"%d,%.9f,%.9f,%.9f,%b,%.9f,%.9f,%.9f,%b,%b,%.9f,%.9f,%.9f,%.9f,%s,%s\n",
                    times.get(i),sample.x,sample.y,sample.correlation,sample.reliable,residuals[i][0],residuals[i][1],residuals[i][2],finalValid[i],used,means.get(i),edges.get(i),baselineMean,Math.abs(means.get(i)-baselineMean),brightnessStatus,reasons[i]==null?"none":reasons[i]));
        }
        return csv.toString();
    }
}
