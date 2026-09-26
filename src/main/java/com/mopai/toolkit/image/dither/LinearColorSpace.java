package com.mopai.toolkit.image.dither;

/** sRGB 编解码与 OKLab 转换。误差缓冲始终使用未截断的线性 RGB。 */
final class LinearColorSpace {
    private LinearColorSpace() {}

    static double decode(int channel) {
        double s = channel / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    static double[] rgb(int r, int g, int b) {
        return new double[]{decode(r), decode(g), decode(b)};
    }

    static double[] lab(double[] rgb) {
        double r=rgb[0], g=rgb[1], b=rgb[2];
        // cbrt 对负值有定义，允许选色时观察越界的累计误差，不丢弃补偿。
        double l=Math.cbrt(0.4122214708*r+0.5363325363*g+0.0514459929*b);
        double m=Math.cbrt(0.2119034982*r+0.6806995451*g+0.1073969566*b);
        double s=Math.cbrt(0.0883024619*r+0.2817188376*g+0.6299787005*b);
        return new double[]{0.2104542553*l+0.7936177850*m-0.0040720468*s,
            1.9779984951*l-2.4285922050*m+0.4505937099*s,
            0.0259040371*l+0.7827717662*m-0.8086757660*s};
    }

    static double[] fromLab(double l, double a, double b) {
        double x=l+0.3963377774*a+0.2158037573*b;
        double y=l-0.1055613458*a-0.0638541728*b;
        double z=l-0.0894841775*a-1.2914855480*b;
        x=x*x*x; y=y*y*y; z=z*z*z;
        return new double[]{4.0767416621*x-3.3077115913*y+0.2309699292*z,
            -1.2684380046*x+2.6097574011*y-0.3413193965*z,
            -0.0041960863*x-0.7034186147*y+1.7076147010*z};
    }
}
