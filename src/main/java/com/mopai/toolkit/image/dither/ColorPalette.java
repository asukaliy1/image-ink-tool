package com.mopai.toolkit.image.dither;

import java.util.ArrayList;
import java.util.List;

/**
 * 调色板，用于彩色抖动量化
 * <p>
 * 提供 RGB 最近色兼容接口；Floyd-Steinberg 彩色路径使用独立的线性 RGB / OKLab 计算。
 * 调色板由外部传入，不硬编码。
 */
public class ColorPalette {

    private final int[] r;
    private final int[] g;
    private final int[] b;
    private final int size;

    public ColorPalette(List<int[]> palette) {
        if (palette == null || palette.isEmpty()) {
            throw new IllegalArgumentException("调色板不能为空");
        }
        this.size = palette.size();
        this.r = new int[size];
        this.g = new int[size];
        this.b = new int[size];
        for (int i = 0; i < size; i++) {
            int[] color = palette.get(i);
            if (color == null || color.length != 3) {
                throw new IllegalArgumentException("调色板每项必须包含 R、G、B 三个分量");
            }
            for (int channel : color) {
                if (channel < 0 || channel > 255) throw new IllegalArgumentException("RGB 分量必须在 0-255 之间");
            }
            this.r[i] = color[0];
            this.g[i] = color[1];
            this.b[i] = color[2];
        }
    }

    /**
     * 精确匹配调色板颜色索引
     * 用于抖动输出已量化到调色板颜色的图像，避免最近邻查找的精度风险
     *
     * @return 匹配的索引，未找到返回 -1
     */
    public int findExactIndex(int pr, int pg, int pb) {
        for (int i = 0; i < size; i++) {
            if (r[i] == pr && g[i] == pg && b[i] == pb) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查找最近的调色板颜色索引
     * 采用通用标准欧氏距离 (dr^2 + dg^2 + db^2)，避免加权距离非对称惩罚导致蓝天等冷色严重被黄色吞噬
     *
     * @param pr R 分量
     * @param pg G 分量
     * @param pb B 分量
     * @return 最近颜色的索引
     */
    public int findNearestIndex(int pr, int pg, int pb) {
        int bestIndex = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            int dr = pr - r[i];
            int dg = pg - g[i];
            int db = pb - b[i];
            long dist = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    /**
     * 常用的 redmean 加权 RGB 距离。绿色通道对亮度观感影响更大，红蓝权重随红色均值轻微调整。
     */
    private long weightedRgbDistance(int pr, int qr, int dr, int dg, int db) {
        int redMean = (pr + qr) >> 1;
        return ((long) (512 + redMean) * dr * dr >> 8)
            + 4L * dg * dg
            + ((long) (767 - redMean) * db * db >> 8);
    }

    /**
     * 查找最近的调色板颜色并返回 RGB 合并值
     */
    public int findNearestRGB(int pr, int pg, int pb) {
        int idx = findNearestIndex(pr, pg, pb);
        return (r[idx] << 16) | (g[idx] << 8) | b[idx];
    }

    /**
     * 获取指定索引的 R 分量
     */
    public int getR(int index) {
        return r[index];
    }

    public int size() {
        return size;
    }

    /**
     * 获取指定索引的 G 分量
     */
    public int getG(int index) {
        return g[index];
    }

    /**
     * 获取指定索引的 B 分量
     */
    public int getB(int index) {
        return b[index];
    }

    /**
     * 获取默认 6 色电子墨水屏预览调色板。
     * <p>
     * 兼容旧调用，等同于 eInk6PreviewColors()。
     * <p>
     * 调色板顺序需与 BmpToBinConverter.COLOR_PALETTE_TO_BIN 映射一致：
     * 0=黑, 1=白, 2=红, 3=绿, 4=蓝, 5=黄
     */
    public static ColorPalette eInk6Colors() {
        return eInk6PreviewColors();
    }

    /**
     * 按设备型号获取 6 色电子墨水屏预览调色板。
     */
    public static ColorPalette eInk6Colors(String deviceType) {
        return eInk6PreviewColors(deviceType);
    }

    /**
     * 默认量化调色板：用于最近色判断和误差扩散计算。
     * 尚无面板实测数据时的理想 sRGB 回退值，不代表墨水的实测颜色。
     */
    public static ColorPalette eInk6QuantizeColors() {
        return eInk6QuantizeColors((String) null);
    }

    /**
     * 按设备型号获取 6 色电子墨水屏量化调色板。
     * 当前两种型号共用未校准回退值；有实测数据后在这里按型号替换，索引顺序不变。
     */
    public static ColorPalette eInk6QuantizeColors(String deviceType) {
        return of(new int[][]{
            {0, 0, 0},          // 黑 Black   0x000000 (0000)
            {255, 255, 255},    // 白 White   0xFFFFFF (0001)
            {255, 0, 0},        // 红 Red     0xFF0000 (0011)
            {0, 255, 0},        // 绿 Green   0x00FF00 (0110)
            {0, 0, 255},        // 蓝 Blue    0x0000FF (0101)
            {255, 255, 0}       // 黄 Yellow  0xFFFF00 (0010)
        });
    }

    /**
     * 默认预览调色板：用于生成预览图和 INDEX6 BIN 精确匹配。
     * 严格遵循官方 Datasheet 标准基准色。
     */
    public static ColorPalette eInk6PreviewColors() {
        return eInk6PreviewColors((String) null);
    }

    /**
     * 按设备型号获取 6 色电子墨水屏预览调色板。
     */
    public static ColorPalette eInk6PreviewColors(String deviceType) {
        return of(new int[][]{
            {0, 0, 0},          // 黑 Black   0x000000
            {255, 255, 255},    // 白 White   0xFFFFFF
            {255, 0, 0},        // 红 Red     0xFF0000
            {0, 255, 0},        // 绿 Green   0x00FF00
            {0, 0, 255},        // 蓝 Blue    0x0000FF
            {255, 255, 0}       // 黄 Yellow  0xFFFF00
        });
    }

    private static ColorPalette of(int[][] colors) {
        List<int[]> palette = new ArrayList<>();
        for (int[] color : colors) {
            palette.add(color);
        }
        return new ColorPalette(palette);
    }
}
