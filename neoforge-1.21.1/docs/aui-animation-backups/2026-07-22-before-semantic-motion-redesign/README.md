# FindMe AUI 动画备份（语义化重构前）

备份时间：2026-07-22

这是用户要求的可回退源码备份。静态 UI、业务数据和服务器逻辑不属于本备份；本轮只重构 AUI 动画语法。

## 基线

- 旧动画控制器：`FindMeAuiTransitionController.java.old`
- 旧共享页面/overlay 生命周期：`FindMeAuiOverlayScreen.java.old`
- 旧右键菜单动画模板：`context-menu.html.old`
- 管理页与小屋页原有 `fm-page-*`、`fm-house-*` 动画块在正式模板中原样保留，不会被新版删除。
- 重构前已部署 JAR SHA-256：`2F908B9EE4C4D63D62A88308E4B66AFF5CF5FDF422AE7ABD4FF8918E76994F38`

## 回退方法

1. 用本目录的 `.old` 文件覆盖对应正式文件（去掉 `.old` 后缀）。
2. 从 `manager.html` 和 `house.html` 删除标记为 `Semantic motion layer` 的新增 CSS 块。
3. 重新运行 `gradlew.bat compileJava processResources --rerun-tasks` 与 `gradlew.bat build`。
4. 将 `build/libs/find_me-1.3.jar` 覆盖到测试实例。

因为旧结构动画 CSS 被保留，以上回退不依赖 Git，也不会触碰项目中其他未提交改动。
