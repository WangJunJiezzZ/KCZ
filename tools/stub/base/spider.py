# 本地调试用的替身：电视上 base.spider 由 App 提供，这里只放一个空壳父类，
# 让源文件能在电脑上直接 import 运行。不要把这个目录放进 config.json。
class Spider:
    def __init__(self):
        self.extend = ""
