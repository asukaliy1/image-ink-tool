# image-ink-tool 抖动与 BIN 转换演示

本目录包含若干可直接运行的 `main` 方法演示，帮助理解 `image-ink-tool` 的用法。

## 运行方式

在 IDE 中直接运行对应类的 `main` 方法，或在项目根目录执行：

```bash
./mvnw test-compile exec:java \
  -Dexec.mainClass="com.mopai.toolkit.image.dither.demo.BasicDitherDemo" \
  -Dexec.classpathScope=test
```

## 演示列表

| 类名 | 说明 |
|------|------|
| `BasicDitherDemo` | 生成灰度渐变图，使用所有支持的算法进行抖动，输出到临时目录 |
| `ColorDitherDemo` | 生成彩色图，使用 6 色电子墨水屏调色板进行彩色抖动 |
| `BinConversionDemo` | 完整流程：图片 → 彩色抖动 → INDEX6 BIN 文件 + BMP 预览图 |
| `LoadAndDitherDemo` | 从文件加载图片（或内置测试图），缩放后抖动输出 |
| `SimpleColorDitherToBinDemo` | **最简案例**：读取图片 → 6 色电子墨水屏彩色抖动 → 生成 BIN 文件 |

## 输出位置

所有演示结果默认保存在系统临时目录下的 `image-ink-tool-demos/` 文件夹中：

- macOS/Linux: `/tmp/image-ink-tool-demos/`
- Windows: `%TEMP%\image-ink-tool-demos\`
