package com.mopai.toolkit.image.dither;

import java.util.ArrayList;
import java.util.List;

/**
 * 在保持目标线性 RGB 的前提下，最小化混色中的 OKLab 彩度平方。
 * 六色凸包内的点至多需要四种墨水；枚举四面体即可求该线性目标的最优解。
 * 这样灰暖色优先使用黑白和必要暖色，不用大量红绿蓝相互抵消。
 */
final class PaletteMixture {
    private static final double WEIGHT_TOLERANCE = 1e-7;
    private final List<Tetrahedron> tetrahedra = new ArrayList<>();
    private final double[] chromaCosts;

    PaletteMixture(double[][] colors) {
        chromaCosts = new double[colors.length];
        for (int i = 0; i < colors.length; i++) {
            double[] lab = LinearColorSpace.lab(colors[i]);
            chromaCosts[i] = lab[1] * lab[1] + lab[2] * lab[2];
        }
        for (int a = 0; a < colors.length; a++) {
            for (int b = a + 1; b < colors.length; b++) {
                for (int c = b + 1; c < colors.length; c++) {
                    for (int d = c + 1; d < colors.length; d++) {
                        Tetrahedron tetrahedron = Tetrahedron.create(colors, a, b, c, d);
                        if (tetrahedron != null) tetrahedra.add(tetrahedron);
                    }
                }
            }
        }
        if (tetrahedra.isEmpty()) throw new IllegalArgumentException("量化调色板必须形成三维色域");
    }

    double[] weights(double[] rgb) {
        double bestCost = Double.POSITIVE_INFINITY;
        double[] result = new double[chromaCosts.length];
        double[] weights = new double[4];
        for (Tetrahedron tetrahedron : tetrahedra) {
            tetrahedron.weights(rgb, weights);
            boolean inside = true;
            double total = 0;
            for (int i = 0; i < 4; i++) {
                if (weights[i] < -WEIGHT_TOLERANCE || weights[i] > 1 + WEIGHT_TOLERANCE) {
                    inside = false;
                    break;
                }
                weights[i] = Math.max(0, weights[i]);
                total += weights[i];
            }
            if (!inside) continue;
            double cost = 0;
            for (int i = 0; i < 4; i++) cost += weights[i] / total * chromaCosts[tetrahedron.indices[i]];
            if (cost < bestCost - 1e-12) {
                bestCost = cost;
                java.util.Arrays.fill(result, 0);
                for (int i = 0; i < 4; i++) result[tetrahedron.indices[i]] = weights[i] / total;
            }
        }
        if (!Double.isFinite(bestCost)) throw new IllegalArgumentException("目标颜色不在量化调色板色域内");
        return result;
    }

    private static final class Tetrahedron {
        final int[] indices;
        final double[] origin;
        final double[][] inverse;

        private Tetrahedron(int[] indices, double[] origin, double[][] inverse) {
            this.indices = indices;
            this.origin = origin;
            this.inverse = inverse;
        }

        static Tetrahedron create(double[][] colors, int a, int b, int c, int d) {
            double[] u = subtract(colors[a], colors[d]);
            double[] v = subtract(colors[b], colors[d]);
            double[] w = subtract(colors[c], colors[d]);
            double[] vw = cross(v, w);
            double determinant = dot(u, vw);
            if (Math.abs(determinant) < 1e-12) return null;
            double[][] inverse = {vw, cross(w, u), cross(u, v)};
            for (double[] row : inverse) for (int i = 0; i < 3; i++) row[i] /= determinant;
            return new Tetrahedron(new int[]{a, b, c, d}, colors[d], inverse);
        }

        void weights(double[] rgb, double[] out) {
            double r = rgb[0] - origin[0];
            double g = rgb[1] - origin[1];
            double b = rgb[2] - origin[2];
            for (int i = 0; i < 3; i++) out[i] = inverse[i][0] * r + inverse[i][1] * g + inverse[i][2] * b;
            out[3] = 1 - out[0] - out[1] - out[2];
        }

        private static double[] subtract(double[] a, double[] b) {
            return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
        }

        private static double[] cross(double[] a, double[] b) {
            return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
        }

        private static double dot(double[] a, double[] b) {
            return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        }
    }
}
