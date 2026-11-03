package com.eyescreening.pterygium;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Four-example research comparison. This is not a disease classifier. */
final class RingPatternPrototype {
    enum Group { KC_REFERENCE, NON_KC_REFERENCE, INCONCLUSIVE }
    static final String VERSION="ring-reference-comparison/1";
    static final String[] NAMES={"nokc_left","nokc_right","kc_left","kc_right"};
    // Derived from bundled example masks, not a patient training cohort.
    static final double[][] REFERENCES={
        {0.01980387992827275,0.030532039555734924,0.012845787904895418},
        {0.019893864349861512,0.023680538166785316,0.0376105496627066},
        {0.022767312795762475,0.03261963847313018,0.021383341333587883},
        {0.01840216666371204,0.04841469269651038,0.029718829597032693}};
    // Engineering scaling and abstention tolerances; not clinical cut-offs.
    private static final double[] SCALE={0.01,0.02,0.02};
    static final class Descriptor {
        final int rings;final double[] features;
        Descriptor(int rings,double[] features){this.rings=rings;this.features=features;}
    }
    static final class Result {
        final Group group;final String explanation;final Descriptor descriptor;
        final double kcDistance,nonKcDistance,margin;final boolean outsideReferenceRange;
        Result(Group group,String explanation,Descriptor descriptor,double positive,double negative,double margin,boolean outside) {
            this.group=group;this.explanation=explanation;this.descriptor=descriptor;
            this.kcDistance=positive;this.nonKcDistance=negative;this.margin=margin;this.outsideReferenceRange=outside;
        }
    }
    static Descriptor describe(int[] labels,int size,double cx,double cy) {
        if(labels.length!=size*size || !Double.isFinite(cx)||!Double.isFinite(cy))return new Descriptor(0,null);
        List<Double> residuals=new ArrayList<>(),radii=new ArrayList<>();
        List<double[]> offsets=new ArrayList<>(),ellipses=new ArrayList<>();
        for(int label=2;label<=15;label++) {
            @SuppressWarnings("unchecked") List<Double>[] bins=(List<Double>[])new List<?>[360];
            for(int i=0;i<labels.length;i++) {
                if(labels[i]!=label)continue;
                double dx=i%size-cx,dy=i/size-cy,r=Math.hypot(dx,dy);
                if(r<5 || r>size*0.48)continue;
                int angle=(int)Math.floor(Math.toDegrees(Math.atan2(dy,dx))+360)%360;
                if(bins[angle]==null)bins[angle]=new ArrayList<>();bins[angle].add(r);
            }
            int covered=0;for(List<Double> bin:bins)if(bin!=null)covered++;
            if(covered<240)continue;
            double[][] matrix=new double[5][6];double[][] rows=new double[covered][5];double[] values=new double[covered];int index=0;
            for(int angle=0;angle<360;angle++) {
                if(bins[angle]==null)continue;
                double theta=Math.toRadians(angle);double[] row={1,Math.cos(theta),Math.sin(theta),Math.cos(2*theta),Math.sin(2*theta)};
                double value=median(bins[angle]);rows[index]=row;values[index++]=value;
                for(int a=0;a<5;a++){
                    for(int b=0;b<5;b++)matrix[a][b]+=row[a]*row[b];
                    matrix[a][5]+=row[a]*value;
                }
            }
            double[] coefficients=solve(matrix);
            if(coefficients==null || coefficients[0]<=5)continue;
            double error=0;
            for(int j=0;j<covered;j++) {
                double predicted=0;for(int k=0;k<5;k++)predicted+=rows[j][k]*coefficients[k];
                error+=Math.pow(values[j]-predicted,2);
            }
            residuals.add(Math.sqrt(error/covered)/coefficients[0]);radii.add(coefficients[0]);
            offsets.add(new double[]{coefficients[1],coefficients[2]});
            ellipses.add(new double[]{coefficients[3]/coefficients[0],coefficients[4]/coefficients[0]});
        }
        if(residuals.size()<5)return new Descriptor(residuals.size(),null);
        return new Descriptor(residuals.size(),new double[]{median(residuals),scatter(offsets)/median(radii),scatter(ellipses)});
    }
    static Result compare(Descriptor descriptor,boolean qualityPassed) {
        return compare(descriptor,qualityPassed,-1);
    }
    // excludedReference is used only for an engineering leave-one-example-out audit.
    static Result compare(Descriptor descriptor,boolean qualityPassed,int excludedReference) {
        double[] values=descriptor.features;
        if(!qualityPassed)return unknown(descriptor,"Photo checks failed. Retake before comparing ring patterns.");
        if(values==null || values.length!=3 || descriptor.rings<5)return unknown(descriptor,"Fewer than five rings had enough coverage for a shape comparison.");
        for(double value:values)if(!Double.isFinite(value))return unknown(descriptor,"Ring-shape features were not finite.");
        double positive=Double.POSITIVE_INFINITY,negative=Double.POSITIVE_INFINITY;boolean outside=false;
        for(int axis=0;axis<3;axis++) {
            double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(int i=0;i<REFERENCES.length;i++)if(i!=excludedReference){low=Math.min(low,REFERENCES[i][axis]);high=Math.max(high,REFERENCES[i][axis]);}
            if(values[axis]<low-SCALE[axis] || values[axis]>high+SCALE[axis])outside=true;
        }
        for(int i=0;i<REFERENCES.length;i++) {
            if(i==excludedReference)continue;
            double distance=0;for(int axis=0;axis<3;axis++)distance+=Math.pow((values[axis]-REFERENCES[i][axis])/SCALE[axis],2);
            distance=Math.sqrt(distance);
            if(i<2)negative=Math.min(negative,distance);else positive=Math.min(positive,distance);
        }
        double margin=Math.abs(positive-negative)/Math.max(1e-9,positive+negative);
        if(outside)return new Result(Group.INCONCLUSIVE,"The ring shape is outside the limited reference range. Holding angle, tear film or segmentation may contribute; the prototype cannot assign a disease result.",descriptor,positive,negative,margin,true);
        if(margin<0.15)return new Result(Group.INCONCLUSIVE,"The two reference groups are too similar for a clear match. No disease result can be assigned.",descriptor,positive,negative,margin,false);
        Group group=positive<negative?Group.KC_REFERENCE:Group.NON_KC_REFERENCE;
        return new Result(group,group==Group.KC_REFERENCE
                ?"This pattern is closer to the project's KC-labelled examples. This is an unvalidated prototype flag; professional corneal examination is recommended."
                :"This pattern is closer to the project's non-KC examples. This does not rule out keratoconus or establish that the eye is healthy.",
                descriptor,positive,negative,margin,false);
    }
    private static Result unknown(Descriptor descriptor,String reason) {
        return new Result(Group.INCONCLUSIVE,reason,descriptor,Double.NaN,Double.NaN,Double.NaN,false);
    }
    private static double median(List<Double> values) {
        double[] sorted=new double[values.size()];for(int i=0;i<sorted.length;i++)sorted[i]=values.get(i);Arrays.sort(sorted);
        return (sorted[(sorted.length-1)/2]+sorted[sorted.length/2])/2;
    }
    private static double scatter(List<double[]> values) {
        double x=0,y=0;for(double[] v:values){x+=v[0];y+=v[1];}x/=values.size();y/=values.size();
        double sum=0;for(double[] v:values)sum+=Math.pow(v[0]-x,2)+Math.pow(v[1]-y,2);return Math.sqrt(sum/values.size());
    }
    private static double[] solve(double[][] matrix) {
        int n=matrix.length;
        for(int c=0;c<n;c++) {
            int pivot=c;for(int r=c+1;r<n;r++)if(Math.abs(matrix[r][c])>Math.abs(matrix[pivot][c]))pivot=r;
            double[] swap=matrix[c];matrix[c]=matrix[pivot];matrix[pivot]=swap;
            if(Math.abs(matrix[c][c])<1e-9)return null;
            double divisor=matrix[c][c];for(int j=c;j<=n;j++)matrix[c][j]/=divisor;
            for(int r=0;r<n;r++)if(r!=c){double multiplier=matrix[r][c];for(int j=c;j<=n;j++)matrix[r][j]-=multiplier*matrix[c][j];}
        }
        double[] values=new double[n];for(int i=0;i<n;i++)values[i]=matrix[i][n];return values;
    }
}
