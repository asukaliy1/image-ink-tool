package com.mopai.toolkit.image.dither;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

/**
 * 将抖动后的 BufferedImage 转换为墨水屏设备 BIN 格式
 * <p>
 * BIN 文件仅包含纯像素数据（无文件头），设备端根据已知分辨率和色深直接解析。
 * <p>
 * 支持三种色深:
 * <ul>
 *   <li>1-bit 黑白 (TYPE_BYTE_BINARY)</li>
 *   <li>4-bit 灰度 (TYPE_BYTE_GRAY) — 16 级灰度</li>
 *   <li>6 色索引 (TYPE_INT_RGB + ColorPalette) — 4-bit/像素</li>
 * </ul>
 */
public class BmpToBinConverter {

    private static final Logger log = LoggerFactory.getLogger(BmpToBinConverter.class);

    /**
     * 6 色索引映射表：ColorPalette 索引 → BIN 索引
     * <p>
     * ColorPalette 顺序: 0=黑, 1=白, 2=红, 3=绿, 4=蓝, 5=黄
     * BIN 顺序(设备端):  0=黑, 1=白, 2=黄, 3=红, 5=蓝, 6=绿
     */
    private static final int[] COLOR_PALETTE_TO_BIN = {0, 1, 3, 6, 5, 2};

    /**
     * 色深枚举
     */
    public enum BinColorDepth {
        BIT1(1, "1-bit 黑白"),
        BIT4(4, "4-bit 灰度"),
        INDEX6(4, "6 色索引");

        public final int bitsPerPixel;
        public final String description;

        BinColorDepth(int bitsPerPixel, String description) {
            this.bitsPerPixel = bitsPerPixel;
            this.description = description;
        }
    }

    /**
     * 转换结果
     */
    public static final class BinConvertResult {
        private final byte[] binData;
        private final BinColorDepth colorDepth;
        private final int width;
        private final int height;

        public BinConvertResult(byte[] binData, BinColorDepth colorDepth, int width, int height) {
            this.binData = binData;
            this.colorDepth = colorDepth;
            this.width = width;
            this.height = height;
        }

        public byte[] binData() {
            return binData;
        }

        public BinColorDepth colorDepth() {
            return colorDepth;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }
    }

    /**
     * 自动检测色深并转换
     * <ul>
     *   <li>TYPE_BYTE_BINARY → 1-bit 黑白</li>
     *   <li>TYPE_BYTE_GRAY → 4-bit 灰度</li>
     *   <li>TYPE_INT_RGB → 6 色索引 (需 palette)</li>
     * </ul>
     *
     * @param image   抖动后的 BufferedImage
     * @param palette 调色板（仅 6 色索引模式需要）
     * @return 转换结果
     */
    public static BinConvertResult convert(BufferedImage image, ColorPalette palette) {
        int type = image.getType();
        if (type == BufferedImage.TYPE_BYTE_BINARY) {
            return convertBit1(image);
        } else if (type == BufferedImage.TYPE_BYTE_GRAY) {
            return convertBit4(image);
        } else {
            // TYPE_INT_RGB 或其他彩色类型 → 6 色索引
            if (palette == null) {
                palette = ColorPalette.eInk6PreviewColors();
            }
            return convertIndex6(image, palette);
        }
    }

    /**
     * 指定色深转换
     *
     * @param image      抖动后的 BufferedImage
     * @param palette    调色板（6 色索引模式需要）
     * @param colorDepth 目标色深
     * @return 转换结果
     */
    public static BinConvertResult convert(BufferedImage image, ColorPalette palette, BinColorDepth colorDepth) {
        switch (colorDepth) {
            case BIT1:
                return convertBit1(image);
            case BIT4:
                return convertBit4(image);
            case INDEX6:
            default:
                return convertIndex6(image, palette != null ? palette : ColorPalette.eInk6PreviewColors());
        }
    }

    /**
     * 1-bit 黑白转换
     * <p>
     * 每像素 1 bit，0=黑，1=白。MSB 在前，行按字节对齐。
     */
    public static BinConvertResult convertBit1(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int rowBytes = rowBytes(width, 1);
        byte[] data = new byte[rowBytes * height];

        for (int y = 0; y < height; y++) {
            int rowOffset = y * rowBytes;
            int srcY = height - 1 - y; // 垂直翻转：设备期望行序从下到上
            for (int x = 0; x < width; x++) {
                int srcX = width - 1 - x; // 水平翻转：设备像素序从右到左
                int rgb = image.getRGB(srcX, srcY);
                // 取灰度值判定黑白
                int gray = (rgb >> 16) & 0xFF;
                if (gray >= 128) {
                    // 白色 → 置 1
                    int byteIndex = rowOffset + (x / 8);
                    int bitIndex = 7 - (x % 8); // MSB 在前
                    data[byteIndex] |= (1 << bitIndex);
                }
                // 黑色保持 0，无需操作
            }
        }

        return new BinConvertResult(data, BinColorDepth.BIT1, width, height);
    }

    /**
     * 4-bit 灰度转换（16 级）
     * <p>
     * 8-bit gray >> 4 映射到 4-bit。每两像素合 1 字节，左像素高 4 位，右像素低 4 位。
     */
    public static BinConvertResult convertBit4(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int rowBytes = rowBytes(width, 4);
        byte[] data = new byte[rowBytes * height];

        for (int y = 0; y < height; y++) {
            int rowOffset = y * rowBytes;
            int srcY = height - 1 - y; // 垂直翻转：设备期望行序从下到上
            for (int x = 0; x < width; x++) {
                int srcX = width - 1 - x; // 水平翻转：设备像素序从右到左
                int rgb = image.getRGB(srcX, srcY);
                int gray = (rgb >> 16) & 0xFF;
                int nibble = gray >> 4; // 8-bit → 4-bit

                int byteIndex = rowOffset + (x / 2);
                if (x % 2 == 0) {
                    // 左像素 → 高 4 位
                    data[byteIndex] |= (nibble << 4);
                } else {
                    // 右像素 → 低 4 位
                    data[byteIndex] |= nibble;
                }
            }
        }

        return new BinConvertResult(data, BinColorDepth.BIT4, width, height);
    }

    /**
     * 6 色索引转换（4-bit/像素）
     * <p>
     * BIN 索引: 0=黑, 1=白, 2=黄, 3=红, 5=蓝, 6=绿
     * <p>
     * 每两像素合 1 字节，左像素高 4 位，右像素低 4 位。
     * 奇数宽度时末尾低 4 位填 0。
     */
    public static BinConvertResult convertIndex6(BufferedImage image, ColorPalette palette) {
        int width = image.getWidth();
        int height = image.getHeight();
        int rowBytes = rowBytes(width, 4);
        byte[] data = new byte[rowBytes * height];

        int exactMiss = 0;

        for (int y = 0; y < height; y++) {
            int rowOffset = y * rowBytes;
            int srcY = height - 1 - y; // 垂直翻转：设备期望行序从下到上
            for (int x = 0; x < width; x++) {
                int srcX = width - 1 - x; // 水平翻转：设备像素序从右到左
                int rgb = image.getRGB(srcX, srcY);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // ColorPalette 索引 → BIN 索引（优先精确匹配，避免舍入偏差）
                int cpIndex = palette.findExactIndex(r, g, b);
                if (cpIndex < 0) {
                    cpIndex = palette.findNearestIndex(r, g, b);
                    exactMiss++;
                    if (exactMiss <= 10) {
                        log.warn("精确匹配失败: pixel({},{}) rgb=({},{},{}), nearestIdx={}",
                            srcX, srcY, r, g, b, cpIndex);
                    }
                }
                int binIndex = (cpIndex < COLOR_PALETTE_TO_BIN.length)
                    ? COLOR_PALETTE_TO_BIN[cpIndex]
                    : cpIndex;

                int byteIndex = rowOffset + (x / 2);
                if (x % 2 == 0) {
                    data[byteIndex] |= (binIndex << 4);
                } else {
                    data[byteIndex] |= binIndex;
                }
            }
        }

        log.info("convertIndex6 完成: {}x{}, 精确匹配失败: {}/{} 像素",
            width, height, exactMiss, width * height);

        return new BinConvertResult(data, BinColorDepth.INDEX6, width, height);
    }

    /**
     * 计算行字节数
     *
     * @param width       图像宽度
     * @param bitsPerPixel 每像素位数
     * @return 行字节数（向上取整到字节）
     */
    static int rowBytes(int width, int bitsPerPixel) {
        return (width * bitsPerPixel + 7) / 8;
    }
}
