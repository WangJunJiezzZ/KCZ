# 示例 Python 源：用几部开源授权的 Blender 短片演示"源"要返回什么数据。
# 跑通后，把 VIDEOS 换成你自己的片库（比如家里 NAS / Alist / WebDAV 的接口）即可。
#
# 壳调用顺序：homeContent(首页分类) -> categoryContent(分类列表)
#            -> detailContent(详情+剧集) -> playerContent(真正的播放地址)
from base.spider import Spider

WIKI = "https://upload.wikimedia.org/wikipedia/commons"

# 这里是"数据源"。真实项目里一般改成 requests 请求某个 API 后解析出来
VIDEOS = [
    {
        "vod_id": "bbb",
        "vod_name": "大雄兔",
        "vod_pic": f"{WIKI}/c/c5/Big_buck_bunny_poster_big.jpg",
        # 可选：横版剧照，看锤子首页/详情页的大图背景优先用它（不填就用 vod_pic 海报）
        "vod_backdrop": f"{WIKI}/thumb/2/2b/Big.Buck.Bunny.-.Frank.Rinky.Gimera.png/1280px-Big.Buck.Bunny.-.Frank.Rinky.Gimera.png",
        "vod_remarks": "2008 · 动画",
        "type_id": "anim",
        "vod_content": "Blender 基金会开源短片（CC BY 3.0）。",
        "episodes": [("正片(HLS)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")],
    },
    {
        "vod_id": "sintel",
        "vod_name": "辛特尔",
        "vod_pic": f"{WIKI}/8/8f/Sintel_poster.jpg",
        "vod_remarks": "2010 · 奇幻",
        "type_id": "anim",
        "vod_content": "Blender 基金会开源短片（CC BY 3.0）。原片服务器拒绝外部播放器访问，示例里用 Apple 公开的 HLS 测试流代替。",
        "episodes": [("测试流(HLS)", "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8")],
    },
    {
        "vod_id": "tos",
        "vod_name": "钢铁之泪",
        "vod_pic": f"{WIKI}/7/70/Tos-poster.png",
        "vod_remarks": "2012 · 科幻",
        "type_id": "film",
        "vod_content": "Blender 基金会开源短片（CC BY 3.0）。",
        "episodes": [("正片(HLS)", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8")],
    },
]

CLASSES = [
    {"type_id": "anim", "type_name": "动画"},
    {"type_id": "film", "type_name": "短片"},
]


def to_card(v):
    """列表里的一张海报卡片：4 个必填字段 + 可选的横版剧照 vod_backdrop"""
    return {k: v[k] for k in ("vod_id", "vod_name", "vod_pic", "vod_backdrop", "vod_remarks") if k in v}


class Spider(Spider):
    def getName(self):
        return "示例片库"

    def init(self, extend=""):
        pass

    def homeContent(self, filter):
        return {"class": CLASSES}

    def homeVideoContent(self):
        return {"list": [to_card(v) for v in VIDEOS]}

    def categoryContent(self, tid, pg, filter, extend):
        items = [to_card(v) for v in VIDEOS if v["type_id"] == tid]
        return {"list": items, "page": int(pg), "pagecount": 1, "total": len(items)}

    def detailContent(self, ids):
        v = next((x for x in VIDEOS if x["vod_id"] == ids[0]), None)
        if v is None:
            return {"list": []}
        # 剧集格式：名字$地址，多集用 # 连接；多条线路用 $$$ 连接
        play_url = "#".join(f"{name}${url}" for name, url in v["episodes"])
        detail = to_card(v) | {
            "vod_content": v["vod_content"],
            "vod_play_from": "官方线路",
            "vod_play_url": play_url,
        }
        return {"list": [detail]}

    def searchContent(self, key, quick, pg="1"):
        items = [to_card(v) for v in VIDEOS if key.lower() in v["vod_name"].lower()]
        return {"list": items}

    def playerContent(self, flag, id, vipFlags):
        # id 就是 detailContent 里 $ 后面的地址；parse=0 表示直接播放，不走解析
        return {"parse": 0, "url": id, "header": {}}
