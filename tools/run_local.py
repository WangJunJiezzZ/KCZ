"""在电脑上模拟电视调用一个 Python 源，按顺序打印每一步返回的数据。

用法：python tools/run_local.py py/xxx.py [搜索关键词]
"""
import importlib.util
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "stub"))  # 让 `from base.spider import Spider` 能找到替身


def load(path):
    spec = importlib.util.spec_from_file_location("source", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.Spider()


def show(title, data):
    print(f"\n===== {title} =====")
    print(json.dumps(data, ensure_ascii=False, indent=2))


def main():
    if len(sys.argv) < 2:
        return print("用法：python tools/run_local.py py/xxx.py [搜索关键词]")
    path = sys.argv[1]
    keyword = sys.argv[2] if len(sys.argv) > 2 else ""
    spider = load(path)
    spider.init("")
    print("源名称:", spider.getName())

    home = spider.homeContent(False)
    show("1. homeContent 首页分类", home)
    if hasattr(spider, "homeVideoContent"):
        show("1b. homeVideoContent 首页推荐", spider.homeVideoContent())

    classes = home.get("class", [])
    if not classes:
        return print("\n没有分类，结束")
    tid = classes[0]["type_id"]
    category = spider.categoryContent(tid, "1", False, {})
    show(f"2. categoryContent 分类[{tid}] 第1页", category)

    items = category.get("list", [])
    if not items:
        return print("\n分类为空，结束")
    detail = spider.detailContent([items[0]["vod_id"]])
    show(f"3. detailContent 详情[{items[0]['vod_id']}]", detail)

    vod = detail["list"][0]
    flag = vod["vod_play_from"].split("$$$")[0]
    first_episode = vod["vod_play_url"].split("$$$")[0].split("#")[0]
    episode_name, episode_id = first_episode.split("$", 1)
    show(f"4. playerContent 播放[{flag} / {episode_name}]", spider.playerContent(flag, episode_id, []))

    if keyword:
        show(f"5. searchContent 搜索[{keyword}]", spider.searchContent(keyword, False, "1"))


if __name__ == "__main__":
    main()
