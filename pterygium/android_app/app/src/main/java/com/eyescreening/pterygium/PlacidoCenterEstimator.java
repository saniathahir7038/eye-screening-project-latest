package com.eyescreening.pterygium;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Recover a missing central label only when several segmented rings agree. */
final class PlacidoCenterEstimator {
    static double[] estimate(int[] labels, int size) {
        List<double[]> fits = new ArrayList<>();
        double origin = size / 2.0;
        for (int label = 2; label <= 9; label++) {
            double[][] m = new double[3][4];
            int count = 0;
            for (int i = 0; i < labels.length; i++) {
                if (labels[i] != label) continue;
                double x = i % size - origin, y = i / size - origin;
                double[] row = {2*x, 2*y, 1};
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < 3; b++) m[a][b] += row[a]*row[b];
                    m[a][3] += row[a]*(x*x+y*y);
                }
                count++;
            }
            if (count < 80) continue;
            double[] fit = solve(m);
            if (fit == null) continue;
            double cx = fit[0]+origin, cy = fit[1]+origin;
            double radius = Math.sqrt(fit[2]+fit[0]*fit[0]+fit[1]*fit[1]);
            if (!Double.isFinite(radius) || radius < 15 || radius > size*0.47
                    || cx < 0 || cy < 0 || cx >= size || cy >= size) continue;
            double residual = 0;
            boolean[] angles = new boolean[360];
            int covered = 0;
            for (int i = 0; i < labels.length; i++) {
                if (labels[i] != label) continue;
                double dx = i % size-cx, dy = i / size-cy;
                double error = Math.hypot(dx,dy)-radius;
                residual += error*error;
                int angle = (int)Math.floor(Math.toDegrees(Math.atan2(dy,dx))+360)%360;
                if (!angles[angle]) { angles[angle] = true; covered++; }
            }
            if (covered >= 180 && Math.sqrt(residual/count)/radius < 0.12)
                fits.add(new double[]{cx,cy});
        }
        if (fits.size() < 4) return null;
        double cx = median(fits,0), cy = median(fits,1);
        int agreed = 0;
        for (double[] fit : fits)
            if (Math.hypot(fit[0]-cx,fit[1]-cy) <= size*0.04) agreed++;
        return agreed >= 4 ? new double[]{cx,cy} : null;
    }
    private static double median(List<double[]> fits, int axis) {
        double[] values = new double[fits.size()];
        for (int i=0;i<values.length;i++) values[i]=fits.get(i)[axis];
        Arrays.sort(values);
        return (values[(values.length-1)/2]+values[values.length/2])/2;
    }
    private static double[] solve(double[][] m) {
        for (int c=0;c<3;c++) {
            int pivot=c;
            for (int r=c+1;r<3;r++) if (Math.abs(m[r][c])>Math.abs(m[pivot][c])) pivot=r;
            double[] swap=m[c];m[c]=m[pivot];m[pivot]=swap;
            if (Math.abs(m[c][c])<1e-8) return null;
            double divisor=m[c][c];
            for (int j=c;j<4;j++) m[c][j]/=divisor;
            for (int r=0;r<3;r++) {
                if (r==c) continue;
                double multiplier=m[r][c];
                for (int j=c;j<4;j++) m[r][j]-=multiplier*m[c][j];
            }
        }
        return new double[]{m[0][3],m[1][3],m[2][3]};
    }
}
