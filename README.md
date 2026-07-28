# FindMe 1.3

FindMe is a Minecraft mount, vehicle, and companion management mod. It can bind,
store, summon, switch, rescue, organize, and house supported creatures and vehicles.

FindMe 是一套 Minecraft 坐骑、载具与伙伴管理模组，可对受支持的生物和载具
进行绑定、收纳、召唤、切换、救援、编队与家园安置。

> [!WARNING]
> FindMe 1.3 is a test release. It was repeatedly reworked and may be less stable or
> compatible than earlier versions. Back up the world before installing or upgrading.
>
> FindMe 1.3 是测试版本，经历过多次重构，稳定性和兼容性可能不如旧版。
> 安装或更新前请务必备份存档，并先在复制存档中测试重要生物与载具。

## Downloads / 下载

Use the JAR matching both your Minecraft version and loader:

- `find_me-1.3.0-test.3-1.21.1-neoforge.jar`
- `find_me-1.3.0-test.3-1.20.1-forge.jar`

ApricityUI is a required client dependency. Use the ApricityUI build intended for
your Minecraft version and loader.

ApricityUI 是必需的客户端前置，请安装与游戏版本和加载器对应的版本。

## Guides / 使用指南

- [中文使用指南](docs/USER_GUIDE_zh_CN.md)
- [English User Guide](docs/USER_GUIDE_en_US.md)
- [更新日志 / Changelog](CHANGELOG.md)

## Source Layout / 源码结构

- `neoforge-1.21.1/`: NeoForge 1.21.1, Java 21
- `forge-1.20.1/`: Forge 1.20.1, Java 17

Build from the matching directory with `./gradlew build` or `gradlew.bat build`.

## License

FindMe source code and original assets are licensed under GPL-3.0-or-later.
