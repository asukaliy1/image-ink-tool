# Image-Ink-Tool 墨水屏图像处理与抖动算法工具包

[![Java](https://img.shields.io/badge/Java-1.8%2B-blue.svg)](https://www.oracle.com/java/)
[![GitHub Repo](https://img.shields.io/badge/GitHub-asukaliy1%2Fimage--ink--tool-181717?logo=github)](https://github.com/asukaliy1/image-ink-tool)
[![Version](https://img.shields.io/badge/Version-1.0.0-orange.svg)](pom.xml)

`image-ink-tool` 是专为低色数反射式电子墨水屏（E-ink / E-paper）量身打造的高性能图像处理工具包。提供多色空间色彩量化、空间误差扩散抖动、多通道墨水覆盖率分解、暗部与肤色保护预处理，以及面向硬件芯片的专有 BIN 格式流转换。

---

## 🌟 核心特性

- **多算法支持**：支持 Floyd-Steinberg、Atkinson、Ordered（有序抖动）、Stucki、Burkes 等多种经典抖动算法。
- **六色电子纸专用凸包分解模型**：
  - 基于线性 RGB 空间构建六色调色板的凸包与非退化四面体。
  - 通过重心坐标精确求取保持原始亮度的低彩色用量墨水混合比例，极大消除平坦区域由于互补色相互抵消所产生的彩色颗粒杂斑。
- **纯色区域黑墨强力抑制**：
  - 针对红、绿、蓝、黄、橙、青等高彩度主导区域，强力剔除由于 Gamma 2.4 解码物理光强衰减而误分配的黑色成分（原算法高达 30%~70% 的黑墨），将权重转移至对应的纯色墨水，彻底告别色块发黑发脏。
- **全图中间调提亮补偿**：
  - 反向补偿 Gamma 2.4 引起的中间调（sRGB 50~200）严重下凹，使中性灰与中间调向白墨水舒展，彻底消除墨水屏“煤渣感”与整体死黑现象。
- **硬件协议 BIN 编码转换**：
  - 支持将处理后的图像快速编码为 1-bit（黑白）、4-bit 灰阶以及官方标准的 6 色 INDEX6 硬件 BIN 文件（4-bit/像素），可直接刷写到电子墨水屏控制器。
- **双版本双环境极佳兼容性**：
  - 源码采用 Java 1.8 语法标准编译，零冲突兼容 Java 8（如 Spring Boot 2.x、传统服务端）与 Java 17/21（如 Spring Boot 3.x 等最新技术栈）。

---

## 📦 Maven 依赖引入

在消费工程（如 `mopai` 或 `mqtt_album`）的 `pom.xml` 中引入：

```xml
<dependency>
    <groupId>com.mopai.toolkit</groupId>
    <artifactId>image-ink-tool</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 🚀 快速上手

### 1. 基础图像六色抖动

```java
import com.mopai.toolkit.image.dither.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

// 1. 读取原图
BufferedImage input = ImageIO.read(new File("input.jpg"));

// 2. 配置抖动参数
DitherConfig config = new DitherConfig();
config.setAlgorithm(DitherType.FLOYD_STEINBERG);
config.setDitherMode(DitherType.DitherMode.COLOR);
config.setColorPalettes(
    ColorPalette.eInk6QuantizeColors(), // 量化色表
    ColorPalette.eInk6PreviewColors()   // 预览色表
);
// 启用自然模式（带暗调补偿与肤色保护）
config.setProcessingMode(ImageProcessingMode.NATURAL);
config.setSerpentine(true); // 启用蛇形扫描消除水平条纹

// 3. 执行抖动处理
DitherProcessor processor = new DitherProcessor();
BufferedImage ditheredImage = processor.process(input, config);

// 4. 保存预览图
ImageIO.write(ditheredImage, "png", new File("dithered_preview.png"));
```

### 2. 生成硬件 INDEX6 BIN 文件

```java
// 将抖动后的图像直接转换为硬件芯片格式 BIN 数据
BmpToBinConverter.BinConvertResult binResult = BmpToBinConverter.convert(
    ditheredImage, 
    config.getEffectivePreviewPalette(), 
    BmpToBinConverter.BinColorDepth.INDEX6
);

byte[] rawBinBytes = binResult.binData();
System.out.printf("BIN 转换就绪，大小: %d 字节, 分辨率: %dx%d\n",
    rawBinBytes.length, binResult.width(), binResult.height());
```

---

## 🛠️ 构建与发布

### 本地构建与测试
```bash
./mvnw clean test
```

### 本地安装到 Maven 仓库
```bash
./mvnw clean install
```

### 发布到远程私有 Maven 仓库（如 CODING / 阿里云效 / 私有 Nexus）

在 `~/.m2/settings.xml` 中配置私库认证服务器：
```xml
<servers>
  <server>
    <id>mopai-releases</id>
    <username>your-username</username>
    <password>your-password-or-token</password>
  </server>
  <server>
    <id>mopai-snapshots</id>
    <username>your-username</username>
    <password>your-password-or-token</password>
  </server>
</servers>
```

配置环境变量并执行发布：
```bash
export MAVEN_REPO_RELEASE_URL="https://your-nexus-or-coding-domain/repository/maven-releases/"
export MAVEN_REPO_SNAPSHOT_URL="https://your-nexus-or-coding-domain/repository/maven-snapshots/"

./mvnw clean deploy -DskipTests
```

---

## 📄 算法详细文档

算法的数学推导、凸包四面体求解原理及详细测试规范，参见 [COLOR_DITHER.md](COLOR_DITHER.md)。
