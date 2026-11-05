# 我的 TVBox 配置

App 首次打开会自动加载本仓库的 `config.json`（地址写在 TV 仓库的 `gradle.properties` → `customVodUrl`）。
改源只需改这里并推送，**不用重新编译 APK**。

## 目录

| 文件 | 作用 |
|---|---|
| `config.json` | 配置入口：站点 `sites`、直播 `lives`、解析 `parses` |
| `py/demo.py` | Python 源示例（开源短片），演示 5 个接口该返回什么 |

## 加一个 Python 源

1. 在 `py/` 下新建 `xxx.py`，照 `demo.py` 实现 `homeContent / categoryContent / detailContent / searchContent / playerContent`。
2. 在 `config.json` 的 `sites` 里加一项，`api` 写 `./py/xxx.py`，`type` 固定为 `3`。
3. 推送后，在 App 里刷新配置。

本地自测语法：`python -m py_compile py/xxx.py`
