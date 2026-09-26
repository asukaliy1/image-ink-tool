package com.mopai.toolkit.image.dither;

/**
 * 面向六色墨水屏的图片预处理模式
 */
public enum ImageProcessingMode {
    /** 保留原图色调，仅将透明区域铺白；通用默认模式。 */
    ORIGINAL,

    /**
     * 自然增强模式：温和提亮暗部
     */
    NATURAL,

    /**
     * 鲜艳模式：更强的中间调提亮与饱和度增益
     */
    VIVID;

    public static ImageProcessingMode from(String value) {
        if ("vivid".equalsIgnoreCase(value)) {
            return VIVID;
        }
        if ("natural".equalsIgnoreCase(value)) return NATURAL;
        return ORIGINAL;
    }
}
