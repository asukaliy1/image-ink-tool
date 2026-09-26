package com.mopai.toolkit.image.dither;

import java.awt.image.BufferedImage;

/**
 * 抖动算法接口
 * 定义所有抖动算法的统一行为
 */
public interface DitherAlgorithm {

    /**
     * 应用抖动算法到灰度图像
     *
     * @param grayscaleInput 输入的灰度图像
     * @param config 抖动配置参数
     * @return 处理后的黑白二值图像
     */
    BufferedImage apply(BufferedImage grayscaleInput, DitherConfig config);

    /**
     * 获取算法名称
     *
     * @return 算法名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
