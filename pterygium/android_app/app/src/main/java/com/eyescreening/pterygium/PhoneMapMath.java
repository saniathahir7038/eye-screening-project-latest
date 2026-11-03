package com.eyescreening.pterygium;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native research port of SmartKC ring localisation, Arc-Step and radial-curvature maps.
 * The model inference and bitmap handling live outside this class, allowing CPU parity tests.
 * Geometry is an explicitly assumed OnePlus 9R / original attachment preset, never calibration.
 */
final class PhoneMapMath {
    static final int ANGLES=360, RINGS=21, DEGREE=8, TERMS=45;
    static final double FOCAL=4.745, SENSOR_W=4.8, SENSOR_H=6.4;
    static final double[] RADIUS={5.7,7,8.1,8.9,9.6,10.2,10.7,11.1,11.5,11.9,12.2,12.5,12.7,12.9,13.2,13.4,13.5,13.7,13.9,14,14.2,14.3};
    static final double[] HEIGHT={10.9,19.3,25.9,31.2,35.6,39.4,42.6,45.4,47.9,50,52,53.8,55.4,56.9,58.3,59.6,60.8,61.9,62.9,63.9,64.9,65.8};
    interface Progress { void update(String message); }
    static final class Located {
        final double[][] radii;
        final boolean[][] valid;
        final double gap;
        Located(double[][] r,boolean[][] v,double gap){radii=r;valid=v;this.gap=gap;}
    }
    static final class Surface {
        final double[][] points;
        final int[] support;
        final double[] extent;
        final double scale;
        Surface(double[][] p,int[] support,double[] extent,double scale){points=p;this.support=support;this.extent=extent;this.scale=scale;}
    }
    static final class Fit {
        final double[] coefficients;
        final double rms, condition;
        Fit(double[] c,double rms,double condition){coefficients=c;this.rms=rms;this.condition=condition;}
    }
    static final class Maps {
        final int size;
        final double[] tangential,axial;
        final boolean[] supported;
        final int points,meridians;
        final double scale,fitRms,gap,condition;
        Maps(int size,double[] t,double[] a,boolean[] supported,Surface s,Fit fit,double gap){
            this.size=size;tangential=t;axial=a;this.supported=supported;
            points=s.points.length;scale=s.scale;fitRms=fit.rms;this.gap=gap;condition=fit.condition;
            int count=0;for(int n:s.support)if(n>=5)count++;meridians=count;
        }
    }
    private static final class Node {
        int mire,angle,component=-1;
        double x,y,r;
        final List<Integer> edges=new ArrayList<>();
        Node(int m,int a,double x,double y,double cx,double cy){mire=m;angle=a;this.x=x;this.y=y;r=Math.hypot(x-cx,y-cy);}
    }
    static void checkCancelled(){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Calculation cancelled.");}

    static Maps generate(int[] mask,int size,Progress progress){
        if(mask==null||mask.length!=size*size||size!=500)throw new IllegalArgumentException("Expected a 500 pixel reconstruction mask.");
        int nonzero=0;for(int n:mask)if(n>0)nonzero++;
        if(nonzero<500||nonzero>mask.length*.80)throw new IllegalArgumentException("The ring pattern is missing. Choose a clear original Placido photo.");
        progress.update("Following the rings…");
        Located located=locate(mask,size);
        progress.update("Calculating the surface on this phone…");
        Surface surface=reconstruct(located);
        progress.update("Smoothing the surface…");
        Fit fit=fit(surface.points,surface.scale);
        if(!Double.isFinite(fit.rms)||fit.rms>.15||fit.condition>1e12)throw new IllegalArgumentException("The surface fit is unstable. Try a clearer, better centred photo.");
        progress.update("Drawing the two experimental maps…");
        return maps(surface,fit,located.gap,256);
    }

    /** Alternating bright/dark ray segments, followed by the project's graph corrections. */
    static Located locate(int[] mask,int size){
        double cx=size/2,cy=size/2;
        List<Node> nodes=new ArrayList<>();
        for(int a=0;a<360;a++){
            checkCancelled();
            double c=Math.cos(Math.toRadians(a)),s=Math.sin(Math.toRadians(a));
            int length=1;
            while(true){int x=(int)(cx+length*c),y=(int)(cy+length*s);if(x<0||y<0||x>=size||y>=size)break;length++;}
            int ex=(int)(cx+(length-1)*c),ey=(int)(cy+(length-1)*s);
            int[][] line=line((int)cx,(int)cy,ex,ey);
            double sumX=0,sumY=0;int count=0,last=-1,mire=0;
            for(int[] p:line){
                int value=mask[p[1]*size+p[0]]>0?1:0;
                if(last!=-1&&value!=last){
                    nodes.add(new Node(mire++,a,sumX/count,sumY/count,cx,cy));sumX=0;sumY=0;count=0;
                    if(mire>=22)break;
                }
                last=value;sumX+=p[0];sumY+=p[1];count++;
            }
            if(mire<22&&count>0)nodes.add(new Node(mire,a,sumX/count,sumY/count,cx,cy));
        }
        // Symmetric graph: either endpoint's local tolerance can create an edge.
        for(int i=0;i<nodes.size();i++){
            if(i%100==0)checkCancelled();
            Node n=nodes.get(i);if(n.r<1e-10)continue;
            double nx=(n.x-cx)/n.r,ny=(n.y-cy)/n.r,tol=Math.max(2*n.r*Math.PI/180,1.5);
            for(int j=i+1;j<nodes.size();j++){
                Node m=nodes.get(j);if(n.angle==m.angle||m.r<1e-10)continue;
                double dx=m.x-n.x,dy=m.y-n.y;
                boolean edge=Math.abs(dx*nx+dy*ny)<=1 && Math.abs(-dx*ny+dy*nx)<=tol;
                if(!edge){double mx=(m.x-cx)/m.r,my=(m.y-cy)/m.r;
                    edge=Math.abs(dx*mx+dy*my)<=1&&Math.abs(-dx*my+dy*mx)<=Math.max(2*m.r*Math.PI/180,1.5);}
                if(edge){n.edges.add(j);m.edges.add(i);}
            }
        }
        int component=0;
        for(int i=0;i<nodes.size();i++){
            if(nodes.get(i).component!=-1)continue;
            boolean[] queuedAngle=new boolean[360];queuedAngle[nodes.get(i).angle]=true;
            List<Integer> stack=new ArrayList<>();stack.add(i);
            while(!stack.isEmpty()){
                Node n=nodes.get(stack.remove(stack.size()-1));if(n.component!=-1)continue;n.component=component;
                // The Python implementation builds its whole new-node list before updating angles.
                List<Integer> next=new ArrayList<>();
                for(int e:n.edges){Node m=nodes.get(e);if(m.component==-1&&!stack.contains(e)&&!queuedAngle[m.angle])next.add(e);}
                stack.addAll(next);for(int e:next)queuedAngle[nodes.get(e).angle]=true;
            }
            component++;
        }
        nodes.sort(Comparator.comparingDouble((Node n)->n.r).thenComparingInt(n->n.mire).thenComparingInt(n->n.angle));
        Map<Integer,List<Node>> components=new LinkedHashMap<>();
        for(Node n:nodes)components.computeIfAbsent(n.component,k->new ArrayList<>()).add(n);
        for(List<Node> part:components.values()){
            if(part.size()<10)for(Node n:part)n.component=-2;
            // Snapshot mire membership, as in the original pandas subset.
            Map<Integer,List<Node>> membership=new LinkedHashMap<>();
            for(Node n:part)membership.computeIfAbsent(n.mire,k->new ArrayList<>()).add(n);
            List<Map.Entry<Integer,List<Node>>> groups=new ArrayList<>(membership.entrySet());
            groups.sort((a,b)->Integer.compare(b.getValue().size(),a.getValue().size()));
            int mode=groups.get(0).getKey();
            for(int g=1;g<groups.size();g++){
                int old=groups.get(g).getKey(),shift=mode-old;
                boolean[] angles=new boolean[360];for(Node n:groups.get(g).getValue())angles[n.angle]=true;
                for(Node n:nodes)if(angles[n.angle]&&n.mire>=old)n.mire+=shift;
            }
        }
        int[][] counts=new int[22][360];
        for(Node n:nodes)if(n.component>=0&&n.mire>=0&&n.mire<22)counts[n.mire][n.angle]++;
        double[][] raw=new double[22][360];for(double[] r:raw)Arrays.fill(r,Double.NaN);
        for(Node n:nodes)if(n.component>=0&&n.mire>=0&&n.mire<22&&counts[n.mire][n.angle]==1)raw[n.mire][n.angle]=n.r;
        double[][] radii=new double[RINGS][360];boolean[][] valid=new boolean[RINGS][360];
        for(int m=0;m<RINGS;m++){
            double[] narrow=median(raw[m+1],25),wide=median(raw[m+1],51);
            for(int a=0;a<360;a++){
                double r=narrow[a];if(Math.abs(r-wide[a])/(wide[a]+1e-9)>.05)r=wide[a];
                radii[m][a]=r;valid[m][a]=Double.isFinite(raw[m+1][a])&&Double.isFinite(r)&&r>0;
            }
        }
        for(int a=0;a<360;a++){
            double[] widths=new double[RINGS-1];for(int m=0;m<widths.length;m++)widths[m]=radii[m+1][a]-radii[m][a];
            for(int m=5;m<widths.length;m++){
                double sum=0,sum2=0;int n=0;
                for(int i=0;i<m;i++)if(Double.isFinite(widths[i])){sum+=widths[i];sum2+=widths[i]*widths[i];n++;}
                if(n>0&&widths[m]>sum/n+10*Math.sqrt(Math.max(0,sum2/n-(sum/n)*(sum/n))))valid[m+1][a]=false;
            }
        }
        double min=Double.POSITIVE_INFINITY,max=0;
        for(int a=0;a<360;a++)if(valid[20][a]){min=Math.min(min,radii[20][a]);max=Math.max(max,radii[20][a]);}
        if(!Double.isFinite(min)||max<=0)throw new IllegalArgumentException("Not enough outer ring points for the assumed-distance calculation. Keep the whole reflection visible.");
        double diameter=(2*max+min)/3*2;
        double gap=3617.81645183/diameter-17.2737687;
        if(!Double.isFinite(gap)||75+gap+4<30||75+gap+4>150)throw new IllegalArgumentException("The ring scale is outside this test app's supported range.");
        return new Located(radii,valid,gap);
    }
    static double[] median(double[] data,int width){
        double[] out=new double[data.length],buffer=new double[width];
        for(int a=0;a<data.length;a++){
            int n=0;for(int i=-width/2;i<=width/2;i++){double v=data[(a+i+data.length)%data.length];if(Double.isFinite(v))buffer[n++]=v;}
            Arrays.sort(buffer,0,n);out[a]=n==0?Double.NaN:n%2==1?buffer[n/2]:(buffer[n/2-1]+buffer[n/2])/2;
        }return out;
    }
    static int[][] line(int x0,int y0,int x1,int y1){
        List<int[]> points=new ArrayList<>();
        int dx=Math.abs(x1-x0),dy=Math.abs(y1-y0),sx=x0<x1?1:-1,sy=y0<y1?1:-1,err=dx-dy;
        while(true){points.add(new int[]{x0,y0});if(x0==x1&&y0==y1)break;int e=2*err;
            if(e>-dy){err-=dy;x0+=sx;}if(e<dx){err+=dx;y0+=sy;}}
        return points.toArray(new int[0][]);
    }
    static double slope(double radius,int angle){
        double a=Math.toRadians(angle);
        return Math.hypot(radius*Math.cos(a)*SENSOR_W/3000,radius*Math.sin(a)*SENSOR_H/4000)/FOCAL;
    }
    static Surface reconstruct(Located located){
        List<double[]> points=new ArrayList<>();int[] support=new int[360];double[] extent=new double[360];double scale=0;
        for(int a=0;a<360;a++){
            checkCancelled();
            if(!located.valid[0][a])continue;
            double[] k=new double[22],oy=new double[22],oz=new double[22];Arrays.fill(k,Double.NaN);
            for(int m=0;m<RINGS;m++)if(located.valid[m][a]){
                k[m+1]=slope(located.radii[m][a],a);
                oy[m+1]=(RADIUS[m]+RADIUS[m+1]-1.5)/2;
                oz[m+1]=(HEIGHT[m]+HEIGHT[m+1])/2-(75+located.gap);
            }
            int opposite=(a+180)%360;
            // A missing opposite ray uses this ray's inner slope. The desktop implementation
            // accidentally drops that case before initialising its synthetic central point.
            k[0]=located.valid[0][opposite]?(k[1]+slope(located.radii[0][opposite],opposite))/2:k[1];
            oy[0]=oy[1];oz[0]=oz[1];
            double[][] yz=arc(k,oz,oy,-(75+located.gap+4));
            for(int m=1;m<yz.length;m++){
                // Arc-Step already carries the last valid point across a missing ring.
                // Keep a converged, observed point even when the previous ring is absent.
                double y=yz[m][0],z=yz[m][1];
                if(Double.isFinite(y)&&Double.isFinite(z)&&z<2&&y>0){
                    points.add(new double[]{y*Math.cos(Math.toRadians(a)),y*Math.sin(Math.toRadians(a)),z});
                    scale=Math.max(scale,y);support[a]++;extent[a]=Math.max(extent[a],y);
                }
            }
        }
        int meridians=0;for(int n:support)if(n>=5)meridians++;
        if(meridians<180||points.size()<1000||scale<.5||scale>10)throw new IllegalArgumentException(String.format(java.util.Locale.US,
                "Surface information incomplete: %d/360 directions (need 180), %d points (need 1000), assumed radius %.3f mm (supported 0.5–10). Share the test report so this calculation can be checked.",meridians,points.size(),scale));
        points.add(new double[]{0,0,0});
        return new Surface(points.toArray(new double[0][]),support,extent,scale);
    }
    /** Direct numerical port of arc_step_method.construct_surface; missing rings stay missing. */
    static double[][] arc(double[] k,double[] oz,double[] oy,double p){
        double[][] out=new double[k.length][2];for(double[] row:out)Arrays.fill(row,Double.NaN);
        double y=0,z=0,yOld=0,zOld=0,slopeOld=0,quadOld=0;
        for(int i=0;i<k.length;i++){
            if(!Double.isFinite(k[i]))continue;
            double step=.01,cube=0,slope=0;
            if(i<2){zOld=0;yOld=0;slopeOld=0;z=0;}
            if(i>0)z=zOld+slopeOld*(y-yOld)+.5*quadOld*(y-yOld)*(y-yOld);
            boolean ok=true;
            for(int count=0;;count++){
                y=(-p+z)*k[i];
                if(i==0){quadOld=2*z/(y*y);cube=0;}
                else cube=6*(z-zOld-slopeOld*(y-yOld)-.5*quadOld*(y-yOld)*(y-yOld))/Math.pow(y-yOld,3);
                slope=slopeOld+quadOld*(y-yOld)+.5*cube*(y-yOld)*(y-yOld);
                double obj=(oy[i]-y)/(-oz[i]+z);
                double cosO=(obj-slope)/Math.sqrt((1+obj*obj)*(1+slope*slope));
                double cosP=(k[i]+slope)/Math.sqrt((1+k[i]*k[i])*(1+slope*slope));
                if(!Double.isFinite(cosO)||!Double.isFinite(cosP)){ok=false;break;}
                if((cosO-cosP)*step<0)step=-step/3;
                z+=step;
                if(Math.abs(step)<=1e-7)break;
                if(count>=10000){ok=false;break;}
            }
            if(!ok)break;
            quadOld+=cube*(y-yOld);zOld=z;yOld=y;slopeOld=slope;
            out[i][0]=y;out[i][1]=z;
        }return out;
    }

    // Real, normalised Zernike basis through degree 8. Ordering is immaterial to the fit.
    static double factorial(int n){double v=1;for(int i=2;i<=n;i++)v*=i;return v;}
    static void basis(double r,double theta,int derivative,double[] out){
        int index=0;
        for(int n=0;n<=DEGREE;n++)for(int m=-n;m<=n;m+=2){
            int am=Math.abs(m);double radial=0;
            for(int s=0;s<=(n-am)/2;s++){
                int power=n-2*s;if(power<derivative)continue;
                double c=(s%2==0?1:-1)*factorial(n-s)/(factorial(s)*factorial((n+am)/2-s)*factorial((n-am)/2-s));
                for(int d=0;d<derivative;d++)c*=power-d;
                radial+=c*Math.pow(r,power-derivative);
            }
            double angular=m<0?Math.sin(am*theta):Math.cos(am*theta);
            out[index++]=Math.sqrt((m==0?1:2)*(n+1))*radial*angular;
        }
    }
    static Fit fit(double[][] points,double scale){
        double[][] gram=new double[TERMS][TERMS];double[] rhs=new double[TERMS],v=new double[TERMS];int used=0;
        for(double[] p:points){
            double r=Math.hypot(p[0],p[1])/scale;if(r>1+1e-12||!Double.isFinite(p[2]))continue;
            basis(r,Math.atan2(p[1],p[0]),0,v);used++;
            for(int i=0;i<TERMS;i++){rhs[i]+=v[i]*p[2];for(int j=0;j<=i;j++)gram[i][j]+=v[i]*v[j];}
        }
        for(int i=0;i<TERMS;i++)for(int j=0;j<i;j++)gram[j][i]=gram[i][j];
        double[] solution=solve(gram,rhs);
        double error=0;
        for(double[] p:points){double r=Math.hypot(p[0],p[1])/scale;if(r>1+1e-12)continue;
            basis(r,Math.atan2(p[1],p[0]),0,v);double predicted=dot(v,solution);error+=(predicted-p[2])*(predicted-p[2]);}
        return new Fit(Arrays.copyOf(solution,TERMS),Math.sqrt(error/used),solution[TERMS]);
    }
    /** Symmetric Jacobi eigen solve, with rank rejection rather than unstable normal-equation inversion. */
    static double[] solve(double[][] a,double[] b){
        int n=b.length;double[][] q=new double[n][n];for(int i=0;i<n;i++)q[i][i]=1;
        for(int sweep=0;sweep<100;sweep++){
            double maximum=0,diag=0;for(int i=0;i<n;i++)diag=Math.max(diag,Math.abs(a[i][i]));
            for(int p=0;p<n-1;p++)for(int r=p+1;r<n;r++){
                maximum=Math.max(maximum,Math.abs(a[p][r]));if(Math.abs(a[p][r])<=diag*1e-14)continue;
                double tau=(a[r][r]-a[p][p])/(2*a[p][r]);
                double t=Math.copySign(1,tau)/(Math.abs(tau)+Math.sqrt(1+tau*tau));
                double c=1/Math.sqrt(1+t*t),s=t*c,apr=a[p][r];
                a[p][p]-=t*apr;a[r][r]+=t*apr;a[p][r]=a[r][p]=0;
                for(int k=0;k<n;k++)if(k!=p&&k!=r){double kp=a[k][p],kr=a[k][r];a[k][p]=a[p][k]=c*kp-s*kr;a[k][r]=a[r][k]=s*kp+c*kr;}
                for(int k=0;k<n;k++){double kp=q[k][p],kr=q[k][r];q[k][p]=c*kp-s*kr;q[k][r]=s*kp+c*kr;}
            }
            if(maximum<=diag*1e-12)break;
        }
        double max=0,min=Double.POSITIVE_INFINITY;for(int i=0;i<n;i++){max=Math.max(max,a[i][i]);min=Math.min(min,a[i][i]);}
        if(min<=max*1e-12||!Double.isFinite(min))throw new IllegalArgumentException("Too little independent ring information to fit the surface.");
        double[] x=new double[n+1];x[n]=max/min;
        for(int i=0;i<n;i++){double c=0;for(int k=0;k<n;k++)c+=q[k][i]*b[k];c/=a[i][i];for(int k=0;k<n;k++)x[k]+=q[k][i]*c;}
        return x;
    }
    static double dot(double[] a,double[] b){double sum=0;for(int i=0;i<a.length;i++)sum+=a[i]*b[i];return sum;}
    static double curvature(double x,double y,double scale,double[] coefficients){
        double r=Math.hypot(x,y),theta=Math.atan2(y,x);double[] v=new double[TERMS];
        basis(r,theta,1,v);double first=dot(v,coefficients)/scale;
        basis(r,theta,2,v);double second=dot(v,coefficients)/(scale*scale);
        return Math.abs(second/Math.pow(1+first*first,1.5));
    }
    static Maps maps(Surface surface,Fit fit,double gap,int size){
        double[] tan=new double[size*size],axial=new double[size*size];Arrays.fill(tan,Double.NaN);Arrays.fill(axial,Double.NaN);
        boolean[] supported=new boolean[size*size];
        double[] curvature=new double[size*size];
        for(int y=0;y<size;y++){
            checkCancelled();
            for(int x=0;x<size;x++){
                double xx=2.0*x/(size-1)-1,yy=2.0*y/(size-1)-1;
                curvature[y*size+x]=Math.hypot(xx,yy)<=1?curvature(xx,yy,surface.scale,fit.coefficients):Double.NaN;
            }
        }
        for(int y=0;y<size;y++){
            checkCancelled();
            for(int x=0;x<size;x++){
                double xx=2.0*x/(size-1)-1,yy=2.0*y/(size-1)-1,r=Math.hypot(xx,yy);
                if(r>.90)continue;int a=((int)Math.round(Math.toDegrees(Math.atan2(yy,xx)))+360)%360;
                if(surface.support[a]<5||r*surface.scale>surface.extent[a])continue;
                double k=curvature[y*size+x];
                if(!Double.isFinite(k))continue;
                int index=y*size+x;tan[index]=337.5*k;supported[index]=true;
                // The desktop algorithm defines axial curvature by averaging along a radial pixel line.
                double sum=0;int count=0;
                for(int[] p:line(size/2,size/2,x,y)){
                    double current=curvature[p[1]*size+p[0]];
                    if(Double.isFinite(current)){sum+=current;count++;}
                }
                if(count>0)axial[index]=337.5*sum/count;
            }
        }
        return new Maps(size,tan,axial,supported,surface,fit,gap);
    }
}
