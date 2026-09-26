package com.mopai.toolkit.image.dither;

import java.util.ArrayList;
import java.util.List;

/** 六色线性混合凸包：优先保留 OKLab 亮度和色相，压缩超色域彩度。 */
final class PaletteGamut {
    private final List<double[]> planes = new ArrayList<>();
    private final double[] center = new double[3];
    private double minL = Double.POSITIVE_INFINITY;
    private double maxL = Double.NEGATIVE_INFINITY;

    PaletteGamut(double[][] colors) {
        for (double[] c : colors) {
            for (int k=0;k<3;k++) center[k]+=c[k]/colors.length;
            double l=LinearColorSpace.lab(c)[0];
            minL=Math.min(minL,l); maxL=Math.max(maxL,l);
        }
        for (int i=0;i<colors.length;i++) for (int j=i+1;j<colors.length;j++)
            for (int k=j+1;k<colors.length;k++) addPlane(colors, i,j,k);
        if (planes.isEmpty() || !hasInterior())
            throw new IllegalArgumentException("彩色量化调色板必须形成三维色域");
    }

    private void addPlane(double[][] colors, int i, int j, int k) {
        double[] a=colors[i], b=colors[j], c=colors[k];
        double ux=b[0]-a[0], uy=b[1]-a[1], uz=b[2]-a[2];
        double vx=c[0]-a[0], vy=c[1]-a[1], vz=c[2]-a[2];
        double nx=uy*vz-uz*vy, ny=uz*vx-ux*vz, nz=ux*vy-uy*vx;
        double norm=Math.sqrt(nx*nx+ny*ny+nz*nz);
        if (norm<1e-10) return;
        nx/=norm; ny/=norm; nz/=norm;
        double d=nx*a[0]+ny*a[1]+nz*a[2];
        boolean positive=false, negative=false;
        for (double[] p:colors) {
            double side=nx*p[0]+ny*p[1]+nz*p[2]-d;
            positive |= side>1e-9; negative |= side< -1e-9;
        }
        if (positive && negative) return;
        if (positive) {nx=-nx;ny=-ny;nz=-nz;d=-d;}
        planes.add(new double[]{nx,ny,nz,d});
    }

    private boolean hasInterior() {
        for (double[] p:planes)
            if (p[3]-p[0]*center[0]-p[1]*center[1]-p[2]*center[2]<1e-10) return false;
        return true;
    }

    boolean contains(double[] rgb) {
        for (double[] p:planes)
            if (p[0]*rgb[0]+p[1]*rgb[1]+p[2]*rgb[2]-p[3]>1e-8) return false;
        return true;
    }

    double[] map(double[] rgb) {
        if (contains(rgb)) return rgb;
        double[] lab=LinearColorSpace.lab(rgb);
        double l=Math.max(minL,Math.min(maxL,lab[0]));
        double[] neutral=LinearColorSpace.fromLab(l,0,0);
        if (contains(neutral)) {
            double low=0,high=1;
            double[] result=neutral;
            for (int i=0;i<14;i++) {
                double mid=(low+high)/2;
                double[] candidate=LinearColorSpace.fromLab(l,lab[1]*mid,lab[2]*mid);
                if (contains(candidate)) {low=mid;result=candidate;} else high=mid;
            }
            return result;
        }
        // 实测黑白可能偏暖，中性轴未必在色域内：从内部中心向目标求边界交点。
        double t=1;
        for (double[] p:planes) {
            double direction=0, offset=0;
            for (int k=0;k<3;k++) {direction+=p[k]*(rgb[k]-center[k]);offset+=p[k]*center[k];}
            if (direction>1e-12) t=Math.min(t,Math.max(0,(p[3]-offset)/direction));
        }
        return new double[]{center[0]+t*(rgb[0]-center[0]),
            center[1]+t*(rgb[1]-center[1]),center[2]+t*(rgb[2]-center[2])};
    }
}
