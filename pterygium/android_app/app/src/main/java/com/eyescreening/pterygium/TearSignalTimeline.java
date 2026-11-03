package com.eyescreening.pterygium;

/** Timing of an experimental image signal, not a validated NIBUT algorithm. */
final class TearSignalTimeline {
    enum Kind { CHANGE, NO_CHANGE_OBSERVED, INCONCLUSIVE }
    static final class Result {
        final Kind kind;final double intervalSeconds,blinkSeconds;final int baselineIndex;final String detail;
        Result(Kind kind,double interval,double blink,int baseline,String detail){this.kind=kind;this.intervalSeconds=interval;this.blinkSeconds=blink;this.baselineIndex=baseline;this.detail=detail;}
    }
    static Result analyse(long[] times,double[] means,double[] edges) {
        int n=times.length;
        if(n<10 || means.length!=n || edges.length!=n)return unknown("Not enough decoded frames.");
        for(int i=0;i<n;i++) {
            if(!Double.isFinite(means[i])||!Double.isFinite(edges[i])||edges[i]<0)return unknown("Invalid video signal.");
            if(i>0 && (times[i]<=times[i-1]||times[i]-times[i-1]>500))return unknown("Missing or irregular frames interrupted the timing. Try another recording.");
        }
        int blink=-1;
        for(int i=1;i<n-2 && blink<0;i++) {
            double jump=Math.abs(means[i]-means[i-1]);
            if(edges[i]>edges[i-1]*0.65 || jump<8)continue;
            for(int j=i+1;j<n && times[j]-times[i]<=750;j++) {
                if(edges[j]>=edges[i-1]*0.8 && Math.abs(means[j]-means[i-1])<=jump*0.6){blink=j;break;}
            }
        }
        if(blink<0)return unknown("No clear close-and-reopen blink signal was found. A brightness change alone is not treated as a blink.");
        int start=blink+1,end=start;
        while(end<n && times[end]-times[start]<1000)end++;
        if(end-start<3)return unknown("Too little stable open-eye video followed the blink.");
        double baseline=0;for(int i=start;i<end;i++)baseline+=edges[i];baseline/=(end-start);
        if(baseline<4)return unknown("Reflected rings have insufficient edge detail after the blink.");
        double variance=0;for(int i=start;i<end;i++)variance+=Math.pow(edges[i]-baseline,2);
        if(Math.sqrt(variance/(end-start))/baseline>0.18)return unknown("The post-blink baseline is unstable. Hold the phone and attachment steadier.");
        int sustained=0,firstSustained=-1;
        for(int i=end;i<n;i++) {
            if(edges[i]<baseline*0.45 && Math.abs(means[i]-means[start])>8)
                return unknown("Another blink or strong occlusion interrupted the observation.");
            if(edges[i]<baseline*0.78){if(sustained==0)firstSustained=i;sustained++;}
            else {sustained=0;firstSustained=-1;}
            if(sustained>=3 && times[i]-times[firstSustained]>=500) {
                int first=firstSustained;
                return new Result(Kind.CHANGE,(times[first]-times[blink])/1000.0,times[blink]/1000.0,start,
                        "Sustained reflected-ring edge-signal reduction. Focus, motion, illumination and tear-film changes can all contribute.");
            }
        }
        double observed=(times[n-1]-times[blink])/1000.0;
        if(observed<10)return unknown("No sustained change was found, but fewer than 10 seconds after the candidate blink were observed.");
        return new Result(Kind.NO_CHANGE_OBSERVED,observed,times[blink]/1000.0,start,
                "No sustained edge-signal reduction during this interval. This is not a negative dry-eye diagnosis.");
    }
    static Result unknown(String reason){return new Result(Kind.INCONCLUSIVE,Double.NaN,Double.NaN,-1,reason);}
}
