package com.mopai.toolkit.image.dither;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 抖动处理器
 * 统一的图像抖动处理入口
 */
@Slf4j
public class DitherProcessor {

    private static final Map<DitherType, DitherAlgorithm> ALGORITHM_MAP = new HashMap<>();

    static {
        // 注册所有算法
        ALGORITHM_MAP.put(DitherType.FLOYD_STEINBERG, new FloydSteinbergDither());
        ALGORITHM_MAP.put(DitherType.ORDERED, new OrderedDither());
        ALGORITHM_MAP.put(DitherType.ATKINSON, new AtkinsonDither());
        ALGORITHM_MAP.put(DitherType.STUCKI, new StuckiDither());
        ALGORITHM_MAP.put(DitherType.BURKES, new BurkesDither());
       // ALGORITHM_MAP.put(DitherType.GRAY16, new Gray16Dither());
    }

    /**
     * 处理图像
     *
     * @param input 输入图像
     * @param config 配置参数
     * @return 处理后的黑白二值图像
     */
    public BufferedImage process(BufferedImage input, DitherConfig config) throws IOException {
        log.info("开始处理图像，算法：{}，模式：{}，尺寸：{}x{}",
            config.getAlgorithm(), config.getDitherMode(), input.getWidth(), input.getHeight());

        long startTime = System.currentTimeMillis();

        // 1. 缩放（如果指定了目标尺寸）
        BufferedImage resized = resize(input, config);

        // 2. 灰度模式才转灰度，彩色模式保留原色
        BufferedImage processed;
        if (config.getDitherMode() == DitherType.DitherMode.COLOR) {
            processed = toRgb(resized);
        } else {
            processed = toGrayscale(resized);
        }

        // 3. 应用抖动算法
        DitherAlgorithm algorithm = getAlgorithm(config.getAlgorithm());
        BufferedImage dithered = algorithm.apply(processed, config);

        // 4. 可选：添加颗粒感
        if (config.getAddGrain() && config.getDitherMode() != DitherType.DitherMode.COLOR) {
            dithered = addGrain(dithered, config.getGrainIntensity());
        } else if (config.getAddGrain()) {
            log.warn("彩色抖动结果必须保持调色板颜色，已跳过颗粒感后处理");
        }

        long processingTime = System.currentTimeMillis() - startTime;
        log.info("图像处理完成，耗时：{} ms", processingTime);

        return dithered;
    }

    /**
     * 转换为不带 Alpha 的 RGB 图像。透明区域铺白，避免透明 PNG 的空白像素被当成黑色扩散。
     */
    private BufferedImage toRgb(BufferedImage input) {
        if (input.getType() == BufferedImage.TYPE_INT_RGB) {
            return input;
        }
        BufferedImage rgbImg = new BufferedImage(
            input.getWidth(),
            input.getHeight(),
            BufferedImage.TYPE_INT_RGB
        );
        Graphics2D g2d = rgbImg.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, input.getWidth(), input.getHeight());
        g2d.drawImage(input, 0, 0, null);
        g2d.dispose();
        return rgbImg;
    }

    /**
     * 缩放图像
     * scaleRatio 优先于 targetWidth/targetHeight
     */
    private BufferedImage resize(BufferedImage input, DitherConfig config) throws IOException {
        // 优先使用比例缩放
        if (config.getScaleRatio() != null) {
            int targetWidth = (int) Math.round(input.getWidth() * config.getScaleRatio());
            int targetHeight = (int) Math.round(input.getHeight() * config.getScaleRatio());
            log.info("比例缩放：{} -> {}x{} (ratio={})",
                input.getWidth() + "x" + input.getHeight(), targetWidth, targetHeight, config.getScaleRatio());
            return Thumbnails.of(input)
                .size(targetWidth, targetHeight)
                .asBufferedImage();
        }

        // 其次使用绝对尺寸
        if (config.getTargetWidth() == null && config.getTargetHeight() == null) {
            return input;
        }

        int targetWidth = config.getTargetWidth() != null ? config.getTargetWidth() : input.getWidth();
        int targetHeight = config.getTargetHeight() != null ? config.getTargetHeight() : input.getHeight();

        return Thumbnails.of(input)
            .size(targetWidth, targetHeight)
            .asBufferedImage();
    }

    /**
     * 转换为灰度图
     * 复用 ImageToEinkWithThumbnails 的逻辑
     */
    private BufferedImage toGrayscale(BufferedImage input) {
        BufferedImage grayImg = new BufferedImage(
            input.getWidth(),
            input.getHeight(),
            BufferedImage.TYPE_BYTE_GRAY
        );
        Graphics g = grayImg.getGraphics();
        g.drawImage(input, 0, 0, null);
        g.dispose();
        return grayImg;
    }

    /**
     * 添加颗粒感
     * 复用 ImageToEinkWithThumbnails 的逻辑
     */
    private BufferedImage addGrain(BufferedImage input, int intensity) {
        BufferedImage output = new BufferedImage(
            input.getWidth(),
            input.getHeight(),
            input.getType()
        );

        Random random = new Random();
        int maxGrain = intensity; // 0-10 映射到像素偏移

        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                int pixel = input.getRGB(x, y) & 0xFF;
                int grain = random.nextInt(maxGrain * 2 + 1) - maxGrain;
                int newPixel = Math.max(0, Math.min(255, pixel + grain));
                output.setRGB(x, y, (newPixel << 16) | (newPixel << 8) | newPixel);
            }
        }

        return output;
    }

    /**
     * 获取算法实例
     */
    private DitherAlgorithm getAlgorithm(DitherType type) {
        DitherAlgorithm algorithm = ALGORITHM_MAP.get(type);
        if (algorithm == null) {
            throw new IllegalArgumentException("不支持的算法类型：" + type);
        }
        return algorithm;
    }
}
