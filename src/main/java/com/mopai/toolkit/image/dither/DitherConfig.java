package com.mopai.toolkit.image.dither;

import lombok.Data;

/**
 * 抖动算法配置类
 */
@Data
public class DitherConfig {

    /**
     * 算法类型（必选）
     */
    private DitherType algorithm;

    /**
     * 抖动模式：GRAYSCALE（默认）或 COLOR
     */
    private DitherType.DitherMode ditherMode = DitherType.DitherMode.GRAYSCALE;

    /**
     * 兼容字段：彩色调色板（COLOR 模式时必传）
     * 每项为 [R, G, B] 数组
     * <p>
     * 未显式配置 quantizePalette/previewPalette 时，同时作为量化调色板和预览调色板使用。
     */
    private ColorPalette palette;

    /**
     * 量化调色板：用于 nearest color 判断和误差计算。
     */
    private ColorPalette quantizePalette;

    /**
     * 预览调色板：用于输出预览图像，并作为 INDEX6 BIN 精确匹配的颜色表。
     */
    private ColorPalette previewPalette;

    /**
     * 二值化阈值 (0-255)
     * 默认 128
     */
    private Integer threshold = 128;

    /**
     * 抖动强度 / 误差扩散系数 (0.0-1.0)
     * 默认 1.0（完全误差扩散）
     * 值越小，扩散的误差越少，图像对比度越高
     */
    private Double ditherStrength = 1.0;

    /**
     * 缩放比例 (0.0-1.0)
     * 如 0.5 表示宽高各缩一半，null 表示不按比例缩放
     * 优先级高于 targetWidth/targetHeight
     */
    private Double scaleRatio;

    /**
     * 目标宽度（像素）
     * null 表示保持原图宽度
     * 仅在 scaleRatio 为 null 时生效
     */
    private Integer targetWidth;

    /**
     * 目标高度（像素）
     * null 表示保持原图高度
     * 仅在 scaleRatio 为 null 时生效
     */
    private Integer targetHeight;

    /**
     * 输出格式 (png/bmp/jpg)
     * 默认 png
     */
    private String outputFormat = "png";

    private static final java.util.Set<String> SUPPORTED_FORMATS =
            java.util.Collections.unmodifiableSet(new java.util.HashSet<String>(
                    java.util.Arrays.asList("png", "bmp", "jpg")));

    /**
     * 是否添加颗粒感
     * 默认 false
     */
    private Boolean addGrain = false;

    /**
     * 颗粒强度 (0-10)
     * 默认 5
     */
    private Integer grainIntensity = 5;

    /**
     * 是否使用 Z 字形扫描（serpentine scanning）
     * 可以减少水平方向的条纹，默认 true
     */
    private Boolean serpentine = true;

    /**
     * 默认保留原图；NATURAL / VIVID 为显式启用的提亮和增艳模式。
     */
    private ImageProcessingMode processingMode = ImageProcessingMode.ORIGINAL;

    /**
     * 参数验证
     */
    public void validate() {
        if (algorithm == null) {
            throw new IllegalArgumentException("算法类型不能为空");
        }
        if (ditherMode == DitherType.DitherMode.COLOR
            && (getEffectiveQuantizePalette() == null || getEffectivePreviewPalette() == null)) {
            throw new IllegalArgumentException("彩色模式下量化/预览调色板不能为空");
        }
        if (threshold != null && (threshold < 0 || threshold > 255)) {
            throw new IllegalArgumentException("阈值必须在 0-255 之间");
        }
        if (ditherStrength != null && (ditherStrength < 0.0 || ditherStrength > 1.0)) {
            throw new IllegalArgumentException("抖动强度必须在 0.0-1.0 之间");
        }
        if (scaleRatio != null && (scaleRatio <= 0.0 || scaleRatio > 1.0)) {
            throw new IllegalArgumentException("缩放比例必须在 0.0-1.0 之间（不含0）");
        }
        if (targetWidth != null && targetWidth <= 0) {
            throw new IllegalArgumentException("目标宽度必须大于 0");
        }
        if (targetHeight != null && targetHeight <= 0) {
            throw new IllegalArgumentException("目标高度必须大于 0");
        }
        if (grainIntensity != null && (grainIntensity < 0 || grainIntensity > 10)) {
            throw new IllegalArgumentException("颗粒强度必须在 0-10 之间");
        }
        if (outputFormat != null && !SUPPORTED_FORMATS.contains(outputFormat.toLowerCase())) {
            throw new IllegalArgumentException("输出格式仅支持: " + SUPPORTED_FORMATS);
        }
    }

    /**
     * 应用默认值
     */
    public void applyDefaults() {
        if (processingMode == null) processingMode = ImageProcessingMode.ORIGINAL;
        if (ditherMode == null) {
            ditherMode = DitherType.DitherMode.GRAYSCALE;
        }
        if (threshold == null) {
            threshold = 128;
        }
        if (ditherStrength == null) {
            ditherStrength = 1.0;
        }
        if (outputFormat == null || outputFormat.isEmpty()) {
            outputFormat = "png";
        }
        if (addGrain == null) {
            addGrain = false;
        }
        if (grainIntensity == null) {
            grainIntensity = 5;
        }
        if (serpentine == null) {
            serpentine = true;
        }
        if (palette != null) {
            if (quantizePalette == null) {
                quantizePalette = palette;
            }
            if (previewPalette == null) {
                previewPalette = palette;
            }
        }
    }

    public void setPalette(ColorPalette palette) {
        this.palette = palette;
        if (quantizePalette == null) {
            quantizePalette = palette;
        }
        if (previewPalette == null) {
            previewPalette = palette;
        }
    }

    public void setColorPalettes(ColorPalette quantizePalette, ColorPalette previewPalette) {
        this.quantizePalette = quantizePalette;
        this.previewPalette = previewPalette;
        this.palette = previewPalette != null ? previewPalette : quantizePalette;
    }

    public ColorPalette getEffectiveQuantizePalette() {
        return quantizePalette != null ? quantizePalette : palette;
    }

    public ColorPalette getEffectivePreviewPalette() {
        return previewPalette != null ? previewPalette : palette;
    }
}
