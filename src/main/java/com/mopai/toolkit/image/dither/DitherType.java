package com.mopai.toolkit.image.dither;

/**
 * 抖动算法类型枚举
 */
public enum DitherType {

    /**
     * Floyd-Steinberg 抖动
     * 最常用的误差扩散算法，细节保留最好
     */
    FLOYD_STEINBERG("Floyd-Steinberg", "经典误差扩散算法，细节保留最好", "中等", true),

    /**
     * Ordered 抖动（Bayer 矩阵）
     * 最快的抖动算法，适合实时预览
     */
    ORDERED("Ordered Dithering", "基于 Bayer 矩阵，速度最快", "快", false),

    /**
     * Atkinson 抖动
     * 高对比度，产生明亮效果
     */
    ATKINSON("Atkinson", "高对比度，适合明亮图像", "中等", false),

    /**
     * Stucki 抖动
     * 扩散范围大，过渡平滑
     */
    STUCKI("Stucki", "扩散范围大，过渡最平滑", "慢", false),

    /**
     * Burkes 抖动
     * Floyd-Steinberg 和 Stucki 的折中
     */
    BURKES("Burkes", "平衡性能和质量", "中等", false),

    /**
     * 16 级灰度抖动
     * 适用于墨水屏，保留 16 级灰度而非二值
     */
    GRAY16("Gray16", "16级灰度，适合墨水屏416x240", "中等", false);

    private final String displayName;
    private final String description;
    private final String performance;
    private final boolean recommended;

    DitherType(String displayName, String description, String performance, boolean recommended) {
        this.displayName = displayName;
        this.description = description;
        this.performance = performance;
        this.recommended = recommended;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getPerformance() {
        return performance;
    }

    public boolean isRecommended() {
        return recommended;
    }

    /**
     * 抖动模式：灰度 or 彩色
     */
    public enum DitherMode {
        /** 灰度模式：原图 → 灰度 → 黑白二值 */
        GRAYSCALE,
        /** 彩色模式：原图 → 调色板量化 + 三通道误差扩散 */
        COLOR
    }
}
