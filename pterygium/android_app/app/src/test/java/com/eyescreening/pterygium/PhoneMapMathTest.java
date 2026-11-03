package com.eyescreening.pterygium;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PhoneMapMathTest {
    private double[][] fixture(String name)throws Exception{
        List<double[]> rows=new ArrayList<>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/"+name),StandardCharsets.UTF_8))){String line;
            while((line=r.readLine())!=null){String[] fields=line.split(",");double[] row=new double[fields.length];for(int i=0;i<row.length;i++)row[i]=fields[i].equals("nan")?Double.NaN:Double.parseDouble(fields[i]);rows.add(row);}}
        return rows.toArray(new double[0][]);
    }
    @Test public void arcStepMatchesOriginalPythonAtThreeMeridians()throws Exception{
        double[][] rows=fixture("arc-reference.csv");assertEquals(66,rows.length);
        for(int base=0;base<rows.length;base+=22){double[] k=new double[22],oz=new double[22],oy=new double[22];
            for(int i=0;i<22;i++){k[i]=rows[base+i][2];oz[i]=rows[base+i][3];oy[i]=rows[base+i][4];}
            double[][] actual=PhoneMapMath.arc(k,oz,oy,rows[base][7]);
            for(int i=0;i<22;i++)if(Double.isFinite(rows[base+i][5])){
                assertEquals("radius @"+base+"/"+i,rows[base+i][5],actual[i][0],1e-7);
                assertEquals("elevation @"+base+"/"+i,rows[base+i][6],actual[i][1],1e-7);
            }
        }
    }
    @Test public void zernikeFitAndCurvatureMatchOriginalPython()throws Exception{
        double[][] points=fixture("fit-reference-points.csv"),expected=fixture("curvature-reference.csv");
        double scale=expected[0][3];PhoneMapMath.Fit fit=PhoneMapMath.fit(points,scale);
        for(double[] row:expected)assertEquals(row[2],PhoneMapMath.curvature(row[0],row[1],scale,fit.coefficients),2e-7);
    }
    private double[][] paraboloid(double rx,double ry,double scale){
        List<double[]> points=new ArrayList<>();
        for(int a=0;a<360;a+=5)for(int step=1;step<=10;step++){
            double x=scale*step/10*Math.cos(Math.toRadians(a)),y=scale*step/10*Math.sin(Math.toRadians(a));
            points.add(new double[]{x,y,x*x/(2*rx)+y*y/(2*ry)});
        }return points.toArray(new double[0][]);
    }
    @Test public void symmetricSurfaceHasExpectedCurvature(){
        PhoneMapMath.Fit fit=PhoneMapMath.fit(paraboloid(8,8,3),3);
        assertTrue(fit.rms<1e-10);
        for(double a:new double[]{0,.5,1,2})assertEquals((1.0/8)/Math.pow(1+Math.pow(1.5/8,2),1.5),PhoneMapMath.curvature(.5*Math.cos(a),.5*Math.sin(a),3,fit.coefficients),1e-9);
    }
    @Test public void astigmaticSurfaceRetainsDifferentMeridians(){
        PhoneMapMath.Fit fit=PhoneMapMath.fit(paraboloid(7,9,3),3);
        assertEquals((1.0/7)/Math.pow(1+Math.pow(1.5/7,2),1.5),PhoneMapMath.curvature(.5,0,3,fit.coefficients),1e-9);
        assertEquals((1.0/9)/Math.pow(1+Math.pow(1.5/9,2),1.5),PhoneMapMath.curvature(0,.5,3,fit.coefficients),1e-9);
    }
    @Test public void cameraProjectionHasConsistentPortraitPixelScale(){
        assertEquals(PhoneMapMath.slope(100,0),PhoneMapMath.slope(100,90),1e-14);
        assertEquals(100*4.8/3000/4.745,PhoneMapMath.slope(100,45),1e-14);
    }
    @Test(expected=IllegalArgumentException.class)public void blankPhotoCannotProduceMaps(){PhoneMapMath.generate(new int[500*500],500,m->{});}
    @Test(expected=IllegalArgumentException.class)public void solidMaskCannotProduceMaps(){int[] mask=new int[250000];Arrays.fill(mask,1);PhoneMapMath.generate(mask,500,m->{});}
    @Test(expected=IllegalArgumentException.class)public void badMaskDimensionsRejected(){PhoneMapMath.generate(new int[256],16,m->{});}
    @Test(expected=IllegalArgumentException.class)public void singularSurfaceFitRejected(){PhoneMapMath.fit(new double[][]{{0,0,0},{.1,0,0}},1);}
    @Test public void circularMedianHandlesMissingValues(){
        double[] d={1,2,Double.NaN,4,5};assertArrayEquals(new double[]{2,1.5,3,4.5,4},PhoneMapMath.median(d,3),1e-14);
    }
    @Test public void unsupportedDirectionsRemainGrey(){
        double[][] points=paraboloid(8,8,3);PhoneMapMath.Fit fit=PhoneMapMath.fit(points,3);
        int[] count=new int[360];double[] extent=new double[360];Arrays.fill(count,10);Arrays.fill(extent,3);
        for(int a=0;a<180;a++)count[a]=0;
        PhoneMapMath.Surface s=new PhoneMapMath.Surface(points,count,extent,3);
        PhoneMapMath.Maps maps=PhoneMapMath.maps(s,fit,0,32);
        assertFalse(maps.supported[20*32+20]);assertTrue(maps.supported[10*32+20]);
    }
    @Test public void mapsDoNotExtrapolatePastObservedRadius(){
        double[][] points=paraboloid(8,8,3);PhoneMapMath.Fit fit=PhoneMapMath.fit(points,3);
        int[] count=new int[360];double[] extent=new double[360];Arrays.fill(count,10);Arrays.fill(extent,.5);
        PhoneMapMath.Maps maps=PhoneMapMath.maps(new PhoneMapMath.Surface(points,count,extent,3),fit,0,32);
        assertFalse(maps.supported[16*32+24]);assertTrue(maps.supported[16*32+16]);
    }
    @Test(expected=IllegalStateException.class)public void calculationCanBeCancelled(){
        Thread.currentThread().interrupt();try{PhoneMapMath.checkCancelled();}finally{Thread.interrupted();}
    }
    private int[] maskFixture(String name)throws Exception{
        try(InputStream in=getClass().getResourceAsStream("/"+name);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);byte[] bytes=out.toByteArray();int[] mask=new int[bytes.length];for(int i=0;i<bytes.length;i++)mask[i]=bytes[i]&255;return mask;
        }
    }
    @Test public void supportedPhotoMaskProducesFiniteMaps()throws Exception{
        PhoneMapMath.Maps maps=PhoneMapMath.generate(maskFixture("supported-photo-mask.bin"),500,m->{});
        assertEquals(190,maps.meridians);assertTrue(maps.fitRms<.02);
        int supported=0,missing=0;for(int i=0;i<maps.supported.length;i++)if(maps.supported[i]){assertTrue(Double.isFinite(maps.axial[i]));assertTrue(Double.isFinite(maps.tangential[i]));supported++;}else missing++;
        assertTrue(supported>1000);assertTrue(missing>1000);
    }
    @Test public void marginalMaskStillRejectedAndExplained()throws Exception{
        try{PhoneMapMath.generate(maskFixture("insufficient-photo-mask.bin"),500,m->{});fail("Do not relax the coverage check to force a map.");}
        catch(IllegalArgumentException e){assertTrue(e.getMessage().contains("155/360"));assertTrue(e.getMessage().contains("need 180"));assertTrue(e.getMessage().contains("Share the test report"));}
    }
    @Test public void observedPointsAfterMissingRingsAreRetained()throws Exception{
        PhoneMapMath.Maps maps=PhoneMapMath.generate(maskFixture("successful-phone-mask.bin"),500,m->{});
        // Before the missing-neighbour fix: 3302 points / 194 supported directions.
        assertEquals(3488,maps.points);assertEquals(198,maps.meridians);
        assertTrue(maps.fitRms<.004);
    }
}
