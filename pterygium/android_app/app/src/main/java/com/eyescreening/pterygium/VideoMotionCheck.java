package com.eyescreening.pterygium;
final class VideoMotionCheck {
    static double[] registration(double[] reference,double[] current) {
        double best=-2;int bestX=0,bestY=0;
        for(int dy=-8;dy<=8;dy+=2)for(int dx=-8;dx<=8;dx+=2) {
            double a=0,b=0,aa=0,bb=0,ab=0;int n=0;
            for(int y=20;y<140;y+=4)for(int x=20;x<140;x+=4) {
                double av=reference[y*160+x],bv=current[(y+dy)*160+x+dx];a+=av;b+=bv;aa+=av*av;bb+=bv*bv;ab+=av*bv;n++;
            }
            double corr=(ab-a*b/n)/Math.sqrt(Math.max(1e-9,(aa-a*a/n)*(bb-b*b/n)));
            if(corr>best){best=corr;bestX=dx;bestY=dy;}
        }
        return new double[]{bestX,bestY,best};
    }
}
