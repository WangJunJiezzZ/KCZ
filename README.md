# 我的 TVBox 配置

可以在「看锤子」App 里填这个配置地址（国内用 jsDelivr，不要用 raw.githubusercontent.com）：

```
https://cdn.jsdelivr.net/gh/WangJunJiezzZ/KCZ@config/config.json
```

改源只需改这里并推送，**不用重新编译 APK**。jsDelivr 最多缓存 12 小时，想立即生效就访问一次：
`https://purge.jsdelivr.net/gh/WangJunJiezzZ/KCZ@config/config.json`

## 目录

| 文件 | 作用 |
|---|---|
| `config.json` | 配置入口：站点 `sites`、直播 `lives`、解析 `parses`（目前为空） |
| `py/` | 放自己写的 Python 源 |
| `tools/run_local.py` | 在电脑上调试 Python 源 |

## 加一个 Python 源

1. 在 `py/` 下新建 `xxx.py`，继承 `base.spider.Spider`，实现 `homeContent / categoryContent / detailContent / searchContent / playerContent`。
2. 在 `config.json` 的 `sites` 里加一项：`{"key": "xxx", "name": "名称", "type": 3, "api": "./py/xxx.py"}`。
3. 推送后，在 App 里刷新配置。
4. 可选：影片数据里加 `vod_backdrop`（横版剧照地址），看锤子首页 / 详情页的大图背景会优先用它。

## 在电脑上调试源

`python tools/run_local.py py/xxx.py [搜索关键词]`：按电视的调用顺序打印每一步返回值。
