# -*- coding: utf-8 -*-
"""
校园淘 · 真实感种子数据生成器

用途：把 sql/campus_tao_full.sql 中的随机演示数据（"测试二手显示器886373"、
"限量1件测试"、"东西很好，好评"、收货人统一"张三"等）替换为贴合真实校园
二手交易场景的数据集，并把改动直接回写进该全量脚本。

数据规模：10 个分类 / 6 名在校学生账号 + admin + ry / 49 件商品 / 24 笔订单
         20 条评价 / 4 单售后 / 购物车、站内通知、物流轨迹、外呼留痕若干。

一致性约束（脚本内强制）：
  - 订单 total_amount == Σ(订单项 subtotal)，subtotal == price × quantity
  - 状态与时间链匹配：1 已付款→pay_time；2 已发货→pay+ship+logistics_no；
    3 已完成→pay+ship+finish；4 已取消→cancel_time；5/6→完整链 + 售后单
  - 买家 != 卖家；商品上架时间早于引用它的订单创建时间
  - 商品剩余库存 == 初始库存 - 已成交数量（唯一商品售罄后为 0）
  - 评价只挂"已完成"订单，文案呼应具体商品
  - 每个用户都有 campus_account；学生账号补 sys_user_role(role_id=2)
"""

import re
from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
SQL_FILE = BASE / "sql" / "campus_tao_full.sql"

# 需要「导入即当前时刻」的时间字段用这个标记，落库时替换为 now_expr() 表达式
NOW_MARK = "NOW_LOCAL"

# ---------------------------------------------------------------- 常量 / 工具


def q(value):
    """SQL 字符串字面量转义（单引号翻倍）。"""
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def is_expr(value):
    """NOW_MARK 标记串按 SQL 表达式原样输出（用于「永远新鲜」的待付款订单）。"""
    return value == NOW_MARK


def sqlv(value):
    """值 -> SQL 片段：表达式标记原样输出，其余按字符串字面量。"""
    return now_expr() if value == NOW_MARK else q(value)


def now_expr():
    """交付环境（GMT+8）的本地当前时刻表达式。

    全量脚本由 mysqldump 导出，脚本头部会执行 /*!40103 SET TIME_ZONE='+00:00' */，
    因此脚本内的 NOW() 拿到的是 UTC 时刻；这里用 UTC + 8h 还原为 GMT+8 的本地墙上时间，
    与实际运行时 JDBC(serverTimezone=GMT+8) 的读取口径一致。
    """
    return "DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR)"


# 商品图片：本地「品类实拍图」，随前端一起交付（campus-tao-ui/public/campus-img/）。
# 前端 firstImage() 直接取 images 首段作为 <img src>，站点根路径可直达，无需拼 base API。
IMG_FILES = {
    1: "books", 2: "books", 3: "books", 4: "books", 5: "books", 6: "books", 7: "books",
    8: "exam", 9: "exam", 10: "exam", 11: "exam", 12: "exam", 13: "exam",
    14: "tablet", 15: "pcacc", 16: "tablet", 17: "pcacc", 18: "pcacc", 19: "pcacc", 20: "pcacc",
    21: "appliance", 22: "appliance", 23: "appliance", 24: "appliance", 25: "appliance",
    26: "sports", 27: "sports", 28: "sports", 29: "sports", 30: "sports",
    31: "vehicle", 32: "vehicle", 33: "vehicle", 34: "vehicle",
    35: "instrument", 36: "instrument", 37: "instrument", 38: "instrument",
    39: "living", 40: "living", 41: "living", 42: "living", 43: "living",
    44: "beauty", 45: "beauty", 46: "beauty",
    47: "card", 48: "card", 49: "card",
}


def img_path(pid):
    """按商品 ID 返回本地品类实拍图路径。"""
    return "/campus-img/%s.jpg" % IMG_FILES[pid]


def ts(order_dt):
    """把 'YYYY-MM-DD HH:MM:SS' 压成 14 位时间戳，用于模拟代码生成的单号。"""
    d, t = order_dt.split(" ")
    return d.replace("-", "") + t.replace(":", "")


# ---------------------------------------------------------------- 分类

CATEGORIES = [
    (1, "教材书籍", 1),
    (2, "考研考证", 2),
    (3, "数码电子", 3),
    (4, "宿舍电器", 4),
    (5, "运动户外", 5),
    (6, "代步工具", 6),
    (7, "乐器文娱", 7),
    (8, "生活百货", 8),
    (9, "美妆个护", 9),
    (10, "票券卡类", 10),
]
CAT_TIME = "2026-08-20 09:00:00"

# ---------------------------------------------------------------- 学生账号
# (user_id, 登录名, 昵称, 性别, 邮箱, 手机号, 备注) —— 登录名即商品的 seller_name 口径
STUDENTS = [
    (3, "liyutong", "李雨桐", "1", "liyutong@stu.edu.cn", "13910000003", "在校学生·2023级计算机科学与技术"),
    (4, "wanghao", "王浩", "0", "wanghao@stu.edu.cn", "13910000004", "在校学生·2022级电子信息工程"),
    (5, "zhaomin", "赵敏", "1", "zhaomin@stu.edu.cn", "13910000005", "在校学生·2024级英语"),
    (6, "chenxinyi", "陈心怡", "1", "chenxinyi@stu.edu.cn", "13910000006", "在校学生·2023级会计学"),
    (7, "zhoukai", "周恺", "0", "zhoukai@stu.edu.cn", "13910000007", "在校学生·2022级机械设计制造"),
    (8, "liuzhiyuan", "刘志远", "0", "liuzhiyuan@stu.edu.cn", "13910000008", "在校学生·2024级软件工程"),
]

# ---------------------------------------------------------------- 商品
# 字段：(分类ID, 标题, 售价, 原价, 成色, 描述, 初始库存, 卖家ID, 拍摄主题[备注])
# 成色口径与前端一致：1全新 2几乎全新 3轻微使用 4明显使用 5二手老旧
# 图片不再逐件指定，统一按商品 ID 映射到本地品类实拍图（见 IMG_FILES / img_path）
PRODUCTS = [
    # --- 教材书籍 ---
    (1, "《数据结构（C语言版）》严蔚敏 第2版", 18.00, 45.00, 2, "大二上学期用过一学期，书页干净平整，仅前两章有少量铅笔标注（已擦除）。适合课程配套或考研打基础。", 2, 3, "大学数据结构教材 严蔚敏 封面"),
    (1, "《高等数学》同济第七版 上下册（合售）", 25.00, 89.00, 2, "上下册一起出，附课后习题册。上册目录页有折痕，正文笔记工整，适合期末复习。", 2, 3, "高等数学同济第七版教材 上下册"),
    (1, "《线性代数》同济第六版", 8.00, 29.00, 3, "课堂笔记记了大半本，书角有轻微卷边，不影响阅读。低价出，介意勿拍。", 2, 4, "线性代数教材 同济第六版"),
    (1, "《大学英语综合教程》1-4册（成套）", 30.00, 120.00, 2, "四册成套出，听力光盘只找到第3册，其余正常。书内有课文生词标注。", 2, 5, "大学英语综合教程 四册 课本"),
    (1, "《C Primer Plus》中文版 第6版", 35.00, 89.00, 2, "自学C语言买的正版，只翻过前1/3，后面几乎全新。页码完整无缺页。", 2, 4, "C Primer Plus 中文版 编程教材"),
    (1, "《操作系统概念》第9版（恐龙书）", 42.00, 128.00, 2, "考研复习时买的，书内夹了一份手写重点提纲，可一并赠送。", 2, 6, "操作系统概念 恐龙书 计算机教材"),
    (1, "《计算机网络：自顶向下方法》第8版", 38.00, 99.00, 3, "有荧光笔标记，重点章节划得比较清楚，书脊完好。", 2, 3, "计算机网络 自顶向下方法 教材"),
    # --- 考研考证 ---
    (2, "张宇考研数学基础30讲（2026版）", 45.00, 168.00, 2, "2026版全套，只跟了前5讲，其余讲义未拆封。赠配套视频兑换码（未使用）。", 2, 3, "考研数学 张宇 基础30讲 辅导书"),
    (2, "肖秀荣考研政治1000题（2026版）", 20.00, 55.00, 2, "刷过一轮，答案册有铅笔对勾，可以擦掉再刷。题册无缺页。", 2, 5, "考研政治 肖秀荣 1000题"),
    (2, "考研英语二 张剑黄皮书 真题解析", 35.00, 119.00, 3, "含2015-2024年真题解析，我的笔记都记在附赠的手写本上，不写在书上。", 2, 5, "考研英语 黄皮书 真题解析"),
    (2, "计算机408 王道考研全套（4本）", 88.00, 320.00, 2, "数据结构、操作系统、计算机组成原理、计算机网络四本齐全，无缺页无涂画。", 2, 7, "计算机408 王道考研 四本教材"),
    (2, "中学教师资格证 综合素质教材+真题", 40.00, 130.00, 1, "全新未拆，报名后改考别的科目了，低价转给需要的同学。", 2, 4, "教师资格证 综合素质 教材真题"),
    (2, "雅思词汇（乱序版）+ 剑桥真题11-18", 60.00, 260.00, 2, "词汇书全新未写，剑桥真题做过3套（另外5套空白），答案册齐全。", 2, 8, "雅思词汇书 剑桥真题 备考资料"),
    # --- 数码电子 ---
    (3, "iPad 2022款 64G WiFi版 深空灰", 1650.00, 2599.00, 2, "大一买的，平时只用来记笔记和追剧，电池健康度92%，屏幕无划痕，含原装充电器和保护壳。", 1, 4, "iPad 平板电脑 深空灰 二手"),
    (3, "罗技 G304 无线游戏鼠标", 95.00, 249.00, 3, "用了一年多，左右键和微动都正常，脚贴有磨损，送两节新电池。", 1, 4, "罗技无线游戏鼠标 黑色"),
    (3, "Kindle Paperwhite 4 8G 电子书阅读器", 380.00, 998.00, 3, "看书用，屏幕无划痕，背光均匀，附原装保护套。电池续航正常。", 1, 6, "Kindle 电子书阅读器 墨水屏"),
    (3, "绿联 65W 氮化镓充电器（双口）", 55.00, 129.00, 2, "毕业清仓，只用过几次，双口同时输出正常，线材另送一条。", 1, 6, "氮化镓充电器 65W 双口"),
    (3, "小米移动电源3 10000mAh", 45.00, 89.00, 3, "外壳有轻微使用痕迹，容量输出正常，支持双向快充。", 1, 2, "小米移动电源 充电宝 10000mAh"),
    (3, "索尼 WH-1000XM4 头戴式降噪耳机", 950.00, 2299.00, 2, "图书馆降噪神器，耳罩无脱皮、无掉皮，含原包装盒和收纳袋。", 1, 6, "索尼 头戴式降噪耳机 黑色"),
    (3, "雷柏 V500 Pro 机械键盘（青轴）", 78.00, 199.00, 4, "青轴声音偏大，宿舍环境介意的同学慎拍。键帽有轻微打油，轴体手感正常。", 1, 4, "机械键盘 青轴 黑色"),
    # --- 宿舍电器 ---
    (4, "小熊 迷你电煮锅 1.2L（带蒸屉）", 45.00, 129.00, 3, "宿舍煮面煮粥用，带蒸屉和电源线，已清洁消毒。功率达标不跳闸。", 1, 5, "迷你电煮锅 宿舍小锅 带蒸屉"),
    (4, "小米 台灯Pro 护眼版", 89.00, 199.00, 2, "考研自习用，无频闪、无可视频闪，含说明书和原装电源。", 1, 5, "小米台灯 护眼台灯 白色"),
    (4, "挂脖小风扇（三档风力）", 15.00, 49.00, 3, "夏天军训和自习用，三档风力正常，续航约4小时，已擦拭消毒。", 1, 8, "挂脖小风扇 便携 白色"),
    (4, "桌面迷你小冰箱 6L", 120.00, 299.00, 2, "放饮料和面膜用，制冷正常，送电源适配器。宿舍桌面刚好放得下。", 1, 7, "桌面迷你小冰箱 6L 白色"),
    (4, "小熊 加湿器 4L 大容量", 55.00, 159.00, 3, "冬天宿舍开暖气用，静音款，滤芯上周刚换新的。", 1, 5, "加湿器 家用静音 大容量"),
    # --- 运动户外 ---
    (5, "迪卡侬 羽毛球拍双拍套装", 65.00, 159.00, 3, "双拍套装含3个球和拍包，拉线张力正常，手胶换过一次。", 1, 6, "羽毛球拍 双拍套装 球拍包"),
    (5, "李宁 7号PU篮球", 50.00, 149.00, 3, "打了一年，气密性良好，附赠气针。手感偏软，适合外场。", 1, 6, "李宁 篮球 7号 PU材质"),
    (5, "Keep 瑜伽垫 加厚8mm", 35.00, 99.00, 2, "用过五六次，已用酒精擦洗消毒，无破损无掉渣。", 1, 7, "瑜伽垫 加厚 运动垫 紫色"),
    (5, "阿迪达斯 运动双肩包（可放15.6寸电脑）", 69.00, 249.00, 3, "背过一学期，拉链顺滑，无明显破损，笔记本隔层可放15.6寸电脑。", 1, 8, "阿迪达斯 运动双肩包 黑色"),
    (5, "负重跳绳 + 护腕（套装）", 12.00, 39.00, 2, "减脂期买的，几乎全新，绳长可调，送一副护腕。", 3, 6, "负重跳绳 护腕 健身套装"),
    # --- 代步工具 ---
    (6, "迪卡侬 20寸折叠山地车", 480.00, 1299.00, 3, "校园代步用，变速和刹车都正常，附车锁。毕业转让，校内可试骑。", 1, 2, "折叠山地自行车 20寸 代步"),
    (6, "永久 26寸通勤自行车", 260.00, 799.00, 4, "骑了两年，刹车刚换新，车架有一处掉漆，需自提，校内可看车。", 1, 8, "通勤自行车 26寸 老式单车"),
    (6, "小米电动滑板车 1S", 750.00, 1999.00, 3, "续航约20km，电池健康，含充电器。校内可试骑，介意电量衰减的慎拍。", 1, 2, "电动滑板车 小米 通勤代步"),
    (6, "九号 电动平衡车", 420.00, 1299.00, 4, "闲置较久，电池需定期充电保养，功能正常，附原装充电器。", 1, 7, "电动平衡车 九号 代步"),
    # --- 乐器文娱 ---
    (7, "雅马哈 F310 民谣吉他", 420.00, 899.00, 3, "入门练习用，音准正常，琴颈无变形，送变调夹和琴包。", 1, 7, "民谣吉他 雅马哈 木吉他"),
    (7, "卡西欧 CT-X700 电子琴（61键）", 680.00, 1599.00, 3, "61键，含琴架和原装电源，宿舍放不下才出。所有琴键力度响应正常。", 1, 7, "电子琴 卡西欧 61键"),
    (7, "尤克里里 23寸单板", 180.00, 499.00, 2, "买来学了两周就吃灰了，几乎全新，送教程书和调音器。", 1, 3, "尤克里里 23寸 单板 木色"),
    (7, "得胜 电容麦克风+声卡套装", 260.00, 699.00, 2, "录播客用，全套线材齐全，麦克风支架和防喷罩都在。", 1, 8, "电容麦克风 声卡 录音套装"),
    # --- 生活百货 ---
    (8, "宿舍床上书桌（折叠款）", 55.00, 149.00, 3, "桌面有两处轻微划痕，承重正常，折叠后不占地方。", 1, 8, "宿舍床上书桌 折叠小桌"),
    (8, "遮光窗帘 1.5×2m（宿舍专用）", 30.00, 89.00, 2, "遮光效果好，白天睡觉基本看不到光，含挂钩和绑带。", 1, 8, "遮光窗帘 宿舍 灰色 布艺"),
    (8, "可折叠收纳箱 三件套", 25.00, 79.00, 2, "搬宿舍用完后闲置，三个尺寸成套，无破损无异味。", 1, 5, "折叠收纳箱 三件套 整理箱"),
    (8, "美的 迷你电吹风 1200W", 40.00, 119.00, 3, "低功率适合宿舍使用，线材完好，两档风速正常。", 1, 6, "迷你电吹风 低功率 白色"),
    (8, "保温杯 500ml 316不锈钢", 35.00, 129.00, 1, "全新未使用，收到重复礼物，所以低价出。附原装包装盒。", 2, 5, "保温杯 316不锈钢 500ml"),
    # --- 美妆个护 ---
    (9, "兰蔻 小黑瓶精华 30ml（专柜正装）", 380.00, 760.00, 1, "全新未开封，专柜小票还在，2027年到期。非临期，假一赔十。", 1, 3, "兰蔻 小黑瓶 精华液 正装"),
    (9, "负离子吹风机（戴森平替款）", 129.00, 399.00, 2, "用过两次，风力正常，含集风嘴和柔风嘴，包装盒在。", 1, 6, "负离子吹风机 家用 高颜值"),
    (9, "珂润 润浸保湿乳液 120ml", 85.00, 179.00, 1, "全新，多买了一瓶，适合干敏皮，2027年到期。", 2, 5, "珂润 保湿乳液 护肤品"),
    # --- 票券卡类 ---
    (10, "校园健身房月卡（10.15-11.15）", 80.00, 150.00, 1, "本人转专业后用不上，可陪同到前台办理过户，仅限本校学生。", 1, 1, "健身房月卡 会员卡 校园"),
    (10, "学校游泳馆次卡（剩15次）", 100.00, 300.00, 1, "原20次卡剩15次，有效期到今年年底，可随时去游。", 1, 1, "游泳馆次卡 泳池 门票卡"),
    (10, "电影兑换券 6张（不限影片）", 90.00, 180.00, 1, "学校发的福利券，不限影片和场次，2026年底到期，6张打包出。", 6, 2, "电影兑换券 电影票 优惠券"),
]

# ---------------------------------------------------------------- 收货人档案
# 与买家 user_id 对应（2=ry，3~8=在校学生）
BUYER_INFO = {
    2: ("林俊", "15666666666", "东区6号宿舍楼302室（可放菜鸟驿站）"),
    3: ("李雨桐", "13910000003", "西区9号宿舍楼412室"),
    4: ("王浩", "13910000004", "南区3号宿舍楼215室（快递驿站自提）"),
    5: ("赵敏", "13910000005", "西区7号宿舍楼508室"),
    6: ("陈心怡", "13910000006", "北区4号宿舍楼306室"),
    7: ("周恺", "13910000007", "东区2号宿舍楼118室（快递驿站自提）"),
    8: ("刘志远", "13910000008", "南区1号宿舍楼601室"),
}

# ---------------------------------------------------------------- 订单
# status: 0待付款 1已付款 2已发货 3已完成 4已取消 5退款中 6已退款
ORDERS = [
    # ---- 已完成（11 笔，均带评价）----
    dict(k="O1", buyer=2, st=3, items=[(6, 1), (12, 1)], remark="尽快发货，谢谢",
         create="2026-09-12 10:12:00", pay="2026-09-12 10:20:00", ship="2026-09-12 10:45:00",
         finish="2026-09-14 19:30:00", cancel=None, logi="SF1020304050601"),
    dict(k="O2", buyer=3, st=3, items=[(15, 1)], remark="宿舍楼下取货，方便的话今晚就可以",
         create="2026-09-15 19:30:00", pay="2026-09-15 19:35:00", ship="2026-09-16 09:10:00",
         finish="2026-09-17 12:20:00", cancel=None, logi="JD0987654321101"),
    dict(k="O3", buyer=4, st=3, items=[(9, 1), (10, 1)], remark="考研党，希望书里笔记别擦掉",
         create="2026-09-18 14:22:00", pay="2026-09-18 14:30:00", ship="2026-09-18 17:05:00",
         finish="2026-09-20 10:15:00", cancel=None, logi="YT7788990011201"),
    dict(k="O4", buyer=5, st=3, items=[(26, 1), (30, 1)], remark="周末约球用，麻烦包装结实点",
         create="2026-09-21 16:45:00", pay="2026-09-21 16:50:00", ship="2026-09-22 08:40:00",
         finish="2026-09-23 20:05:00", cancel=None, logi="ZTO6677889900101"),
    dict(k="O5", buyer=6, st=3, items=[(2, 1), (3, 1)], remark="",
         create="2026-09-24 09:11:00", pay="2026-09-24 09:20:00", ship="2026-09-24 15:30:00",
         finish="2026-09-26 11:40:00", cancel=None, logi="SF1020304050602"),
    dict(k="O6", buyer=7, st=3, items=[(14, 1)], remark="麻烦确认下电池健康度，谢谢",
         create="2026-09-27 18:30:00", pay="2026-09-27 18:45:00", ship="2026-09-28 09:05:00",
         finish="2026-09-30 16:20:00", cancel=None, logi="SF1020304050603"),
    dict(k="O7", buyer=8, st=3, items=[(37, 1), (42, 1)], remark="送的东西都在吧？",
         create="2026-10-01 08:45:00", pay="2026-10-01 08:55:00", ship="2026-10-01 14:10:00",
         finish="2026-10-03 09:35:00", cancel=None, logi="JD0987654321102"),
    dict(k="O8", buyer=2, st=3, items=[(41, 1), (43, 1)], remark="搬宿舍急用，能今天就发吗",
         create="2026-10-03 12:03:00", pay="2026-10-03 12:15:00", ship="2026-10-03 17:40:00",
         finish="2026-10-05 10:50:00", cancel=None, logi="YT7788990011202"),
    dict(k="O9", buyer=3, st=3, items=[(22, 1), (23, 1)], remark="",
         create="2026-10-05 17:10:00", pay="2026-10-05 17:25:00", ship="2026-10-06 09:20:00",
         finish="2026-10-08 15:05:00", cancel=None, logi="ZTO6677889900102"),
    dict(k="O10", buyer=4, st=3, items=[(35, 1), (36, 1)], remark="吉他能不能帮我调好弦再寄",
         create="2026-10-07 20:58:00", pay="2026-10-07 21:10:00", ship="2026-10-08 10:35:00",
         finish="2026-10-10 12:45:00", cancel=None, logi="SF1020304050604"),
    dict(k="O11", buyer=5, st=3, items=[(32, 1), (34, 1)], remark="自行车和平衡车一起去南区自提可以吗",
         create="2026-10-09 10:56:00", pay="2026-10-09 11:05:00", ship="2026-10-09 16:20:00",
         finish="2026-10-11 09:15:00", cancel=None, logi="JD0987654321103"),
    # ---- 已发货（4 笔，物流在途）----
    dict(k="O12", buyer=6, st=2, items=[(31, 1)], remark="校内可以自提，什么时候方便",
         create="2026-10-12 13:40:00", pay="2026-10-12 13:50:00", ship="2026-10-12 18:05:00",
         finish=None, cancel=None, logi="YT7788990011203"),
    dict(k="O13", buyer=7, st=2, items=[(21, 1)], remark="锅麻烦擦干净一点再寄",
         create="2026-10-12 15:08:00", pay="2026-10-12 15:20:00", ship="2026-10-13 09:30:00",
         finish=None, cancel=None, logi="ZTO6677889900103"),
    dict(k="O14", buyer=8, st=2, items=[(11, 1)], remark="四本一起发，谢谢",
         create="2026-10-13 18:25:00", pay="2026-10-13 18:35:00", ship="2026-10-14 10:15:00",
         finish=None, cancel=None, logi="SF1020304050605"),
    dict(k="O15", buyer=2, st=2, items=[(24, 1)], remark="小冰箱有点重，麻烦包厚一点",
         create="2026-10-14 09:37:00", pay="2026-10-14 09:45:00", ship="2026-10-14 16:40:00",
         finish=None, cancel=None, logi="JD0987654321104"),
    # ---- 已付款（3 笔，待发货）----
    dict(k="O16", buyer=3, st=1, items=[(33, 1)], remark="约个时间校内试骑一下",
         create="2026-10-14 16:02:00", pay="2026-10-14 16:10:00", ship=None,
         finish=None, cancel=None, logi=""),
    dict(k="O17", buyer=5, st=1, items=[(13, 1)], remark="真题答案册一定要带上",
         create="2026-10-14 11:20:00", pay="2026-10-14 11:30:00", ship=None,
         finish=None, cancel=None, logi=""),
    dict(k="O18", buyer=6, st=1, items=[(44, 1)], remark="麻烦把小票一起寄过来，谢谢",
         create="2026-10-14 19:45:00", pay="2026-10-14 19:50:00", ship=None,
         finish=None, cancel=None, logi=""),
    # ---- 待付款（1 笔：创建时间取导入时刻，始终落在 30 分钟支付窗口内）----
    dict(k="O19", buyer=7, st=0, items=[(38, 1)],
         remark="先下单占着，明天发生活费就付，麻烦帮我留一下",
         create=NOW_MARK, pay=None, ship=None, finish=None, cancel=None, logi=""),
    # ---- 已取消（2 笔：1 笔用户主动取消，1 笔超时未支付自动取消）----
    dict(k="O20", buyer=8, st=4, items=[(46, 1)], remark="",
         create="2026-10-15 06:30:00", pay=None, ship=None, finish=None,
         cancel="2026-10-15 07:00:00", logi=""),
    dict(k="O21", buyer=4, st=4, items=[(28, 1)], remark="想想还是不用了",
         create="2026-09-30 16:02:00", pay=None, ship=None, finish=None,
         cancel="2026-09-30 18:00:00", logi=""),
    # ---- 退款中（1 笔）----
    dict(k="O22", buyer=8, st=5, items=[(19, 1)], remark="希望能当面验货",
         create="2026-10-05 20:15:00", pay="2026-10-05 20:25:00", ship="2026-10-06 09:40:00",
         finish="2026-10-08 11:20:00", cancel=None, logi="YT7788990011204"),
    # ---- 已退款（2 笔）----
    dict(k="O23", buyer=4, st=6, items=[(45, 1)], remark="",
         create="2026-09-08 14:33:00", pay="2026-09-08 14:45:00", ship="2026-09-08 18:20:00",
         finish="2026-09-10 10:05:00", cancel=None, logi="ZTO6677889900104"),
    dict(k="O24", buyer=7, st=6, items=[(16, 1)], remark="屏幕一定要无亮点，介意勿发",
         create="2026-10-03 18:30:00", pay="2026-10-03 18:40:00", ship="2026-10-04 09:15:00",
         finish="2026-10-05 15:30:00", cancel=None, logi="SF1020304050606"),
]

# ---------------------------------------------------------------- 评价
# (订单key, 商品ID, 评分, 内容)
REVIEWS = [
    ("O1", 6, 5, "书和描述一致，还附赠了手写提纲，考研复习帮了大忙。"),
    ("O1", 12, 5, "全新未拆封，比书店便宜一半，卖家发货也快。"),
    ("O2", 15, 5, "鼠标反应灵敏，还送了新电池，成色比描述的还好。"),
    ("O3", 9, 4, "题册里确实有铅笔勾过的痕迹，擦干净还能继续刷，可以接受。"),
    ("O3", 10, 5, "真题解析很全，笔记在单独的本子上，不影响使用，很划算。"),
    ("O4", 26, 5, "球拍和球都挺新的，周末约球正好用上，卖家还多送了一个球。"),
    ("O4", 30, 4, "跳绳没问题，护腕有点旧，不过这个价格也值了。"),
    ("O5", 2, 5, "上下册都在，笔记记得很整齐，期末复习帮了大忙。"),
    ("O5", 3, 4, "书角有点卷，内容没问题，八块钱很划算了。"),
    ("O6", 14, 5, "iPad 成色很好，电池健康度确实是92%，卖家还贴了膜，值。"),
    ("O7", 37, 5, "尤克里里音色不错，教程和调音器都送了，新手够用。"),
    ("O7", 42, 4, "吹风风力一般，但宿舍用正好不跳闸，线材完好。"),
    ("O8", 41, 5, "收纳箱三个尺寸都有，搬宿舍装东西很方便，没有异味。"),
    ("O8", 43, 5, "全新未拆，包装完好，比双十一还便宜，谢谢卖家。"),
    ("O9", 22, 5, "台灯没有频闪，晚上自习眼睛不酸了，说明书也在。"),
    ("O9", 23, 5, "风扇三档风力够用，军训那几天全靠它了。"),
    ("O10", 35, 5, "吉他很新，音准没问题，卖家还帮我调了一次弦。"),
    ("O10", 36, 4, "电子琴所有键都能用，琴架有点旧，整体满意。"),
    ("O11", 32, 4, "车架掉漆的地方和描述一致，刹车确实是新换的，能骑。"),
    ("O11", 34, 5, "平衡车功能正常，卖家教我怎么保养电池，很耐心。"),
]

# ---------------------------------------------------------------- 售后
# (关联订单key, 类型, 原因, 状态, 处理备注, 处理时间)
AFTERSALES = [
    ("O22", 2, "耳机左声道有轻微杂音，与商品描述不符，申请退货退款。", 0, "", None),
    ("O23", 2, "吹风机风力比描述偏小，宿舍用不太够，申请退货退款。", 3,
     "已核实买家提交的视频，同意退货退款；商品不影响二次销售，退款已退回沙箱余额。", "2026-09-11 09:30:00"),
    ("O24", 1, "阅读器屏幕右下角有轻微亮点，申请仅退款（商品留用）。", 3,
     "亮点情况属实，经与买卖双方协商按仅退款处理，商品由买家留用。", "2026-10-06 10:15:00"),
    ("O6", 2, "收到后发现边框有一处磕碰，申请退货退款。", 2,
     "经核实磕碰为快递运输造成，已协调卖家补偿差价，本次退货退款申请不予受理。", "2026-10-01 09:20:00"),
]

# ---------------------------------------------------------------- 物流轨迹
CARRIERS = {"SF": "顺丰速运", "JD": "京东物流", "YT": "圆通速递", "ZTO": "中通快递"}
# 需要落轨迹的订单：已发货（在途 2 节点）+ 已完成（全链 4 节点）
TRACE_KEYS = ("O12", "O13", "O14", "O15", "O6", "O11")

# ---------------------------------------------------------------- 外呼留痕
# 交付默认未注入短信/邮件网关，故历史留痕均为「未启用(已降级为仅站内通知)」，口径与实况一致
# (user_id, 渠道, 接收目标, 业务类型, 标题, 内容, 业务号占位)
NOTIFY_LOGS = [
    (2, "sms", "15666666666", "order", "订单支付成功", "订单 {O8} 已支付成功，金额 60.00 元，卖家正在准备发货。", "{O8}"),
    (2, "email", "ry@qq.com", "order", "订单支付成功", "订单 {O8} 已支付成功，金额 60.00 元，卖家正在准备发货。", "{O8}"),
    (3, "sms", "13910000003", "ship", "订单已发货", "订单 {O9} 已发货，物流单号 ZTO6677889900102，请注意查收。", "{O9}"),
    (3, "email", "liyutong@stu.edu.cn", "ship", "订单已发货", "订单 {O9} 已发货，物流单号 ZTO6677889900102，请注意查收。", "{O9}"),
    (6, "sms", "13910000006", "ship", "订单已发货", "订单 {O12} 已发货，物流单号 YT7788990011203，请注意查收。", "{O12}"),
    (8, "sms", "13910000008", "aftersale", "售后申请已提交", "售后单 {A1} 已提交，管理员会在 1 个工作日内处理。", "{A1}"),
    (7, "email", "zhoukai@stu.edu.cn", "aftersale", "退款已完成", "售后单 {A3} 审核通过，退款 380.00 元已退回沙箱余额。", "{A3}"),
    (5, "sms", "13910000005", "system", "校园淘使用提示", "发布闲置请如实描述成色，交易尽量在校内公共场所当面验货。", ""),
]

# ---------------------------------------------------------------- 站内通知
# (user_id, type, 标题, 内容, 业务号占位, is_read, 时间)
NOTICES = [
    (2, "order", "订单支付成功", "订单 {O8} 已支付成功，金额 60.00 元，卖家正在准备发货。", "{O8}", 1, "2026-10-03 12:15:00", "2026-10-03 12:40:00"),
    (2, "ship", "订单已发货", "订单 {O8} 已发货，物流单号 YT7788990011202，请留意收货。", "{O8}", 1, "2026-10-03 17:40:00", "2026-10-03 19:05:00"),
    (3, "order", "订单已完成", "订单 {O9} 已确认收货，别忘了给卖家一个评价哦。", "{O9}", 1, "2026-10-08 15:05:00", "2026-10-08 20:30:00"),
    (6, "ship", "订单已发货", "订单 {O12} 已发货，物流单号 YT7788990011203，请留意收货。", "{O12}", 0, "2026-10-12 18:05:00", None),
    (7, "ship", "订单已发货", "订单 {O13} 已发货，物流单号 ZTO6677889900103，请留意收货。", "{O13}", 0, "2026-10-13 09:30:00", None),
    (8, "ship", "订单已发货", "订单 {O14} 已发货，物流单号 SF1020304050605，请留意收货。", "{O14}", 0, "2026-10-14 10:15:00", None),
    (8, "aftersale", "售后申请已提交", "售后单 {A1} 已提交（退货退款），管理员会在 1 个工作日内处理。", "{A1}", 0, "2026-10-09 10:05:00", None),
    (3, "order", "订单已支付", "订单 {O16} 已支付成功，金额 750.00 元，卖家正在准备发货。", "{O16}", 0, "2026-10-14 16:10:00", None),
    (4, "aftersale", "退款已完成", "售后单 {A2} 审核通过，退款 129.00 元已退回沙箱余额。", "{A2}", 1, "2026-09-11 09:30:00", "2026-09-11 12:15:00"),
    (7, "aftersale", "退款已完成", "售后单 {A3} 审核通过，退款 380.00 元已退回沙箱余额。", "{A3}", 0, "2026-10-06 10:15:00", None),
    (7, "aftersale", "售后申请未通过", "售后单 {A4} 未通过审核：{A4remark}", "{A4}", 1, "2026-10-01 09:20:00", "2026-10-01 18:40:00"),
    (8, "order", "订单已自动取消", "订单 {O20} 因超时未支付已被自动取消，占用库存已释放。", "{O20}", 1, "2026-10-15 07:00:00", "2026-10-15 08:20:00"),
    (5, "system", "校园淘使用提示", "发布闲置请如实描述成色与瑕疵，交易建议在校内公共场所当面验货。", "", 0, "2026-09-01 10:00:00", None),
    (7, "order", "订单待支付提醒", "您提交的订单尚未支付，请在 30 分钟内完成付款，超时系统将自动取消并释放库存。", "", 0, NOW_MARK, None),
]

# 沙箱余额（user_id -> 余额），与订单规模匹配
BALANCES = {
    1: "1000.00", 2: "500.00", 3: "860.00", 4: "420.00",
    5: "1150.00", 6: "300.00", 7: "680.00", 8: "95.00",
}

# 购物车（user_id, 商品ID, 数量, 勾选, 添加时间）
# 约束：不加入自己的商品；不加入已售罄（下架）商品；数量不超过剩余库存
CARTS = [
    (2, 1, 1, 1, "2026-10-14 20:10:00"),
    (2, 5, 1, 1, "2026-10-15 08:20:00"),
    (3, 12, 1, 1, "2026-10-13 19:05:00"),
    (3, 46, 1, 1, "2026-10-13 19:08:00"),
    (4, 30, 1, 1, "2026-10-12 12:30:00"),
    (4, 43, 1, 0, "2026-10-12 12:32:00"),
    (5, 45, 1, 1, "2026-10-14 21:15:00"),
    (6, 39, 1, 1, "2026-10-14 22:40:00"),
    (6, 40, 1, 1, "2026-10-14 22:41:00"),
    (7, 29, 1, 0, "2026-10-11 10:05:00"),
    (8, 49, 1, 1, "2026-10-15 09:10:00"),
    (8, 27, 1, 1, "2026-10-15 09:12:00"),
]


def add_days(dtstr, days, hhmmss=None):
    """在 'YYYY-MM-DD HH:MM:SS' 上加天数，可顺带改写时分秒。"""
    import datetime as _dt
    d = _dt.datetime.strptime(dtstr, "%Y-%m-%d %H:%M:%S") + _dt.timedelta(days=days)
    if hhmmss:
        h, m, s = (int(x) for x in hhmmss.split(":"))
        d = d.replace(hour=h, minute=m, second=s)
    return d.strftime("%Y-%m-%d %H:%M:%S")


def add_hours(dtstr, hours):
    import datetime as _dt
    d = _dt.datetime.strptime(dtstr, "%Y-%m-%d %H:%M:%S") + _dt.timedelta(hours=hours)
    return d.strftime("%Y-%m-%d %H:%M:%S")


def prod_time(i):
    """商品上架时间：在 2026-08-18 ~ 2026-09-05 间确定性打散，避免首页按时间倒序时扎堆，
    同时保证早于最早订单创建时间（2026-09-08）。"""
    import datetime as _dt
    d = _dt.datetime(2026, 8, 18, 0, 0, 0) + _dt.timedelta(days=(i * 7) % 19)
    return d.replace(hour=8 + (i * 5) % 14, minute=(i * 17) % 60, second=0
                     ).strftime("%Y-%m-%d %H:%M:%S")


# 售后单 key 与文案占位符的对应（A1~A4 依次为 4 笔售后）
AS_KEYS = ("O22", "O23", "O24", "O6")


def placeholder_map(order_no, aftersale_no):
    """文案占位符 -> 实际单号（订单号 O1~O24、售后单号 A1~A4）。"""
    ph = dict(order_no)
    for i, key in enumerate(AS_KEYS, start=1):
        ph["A%d" % i] = aftersale_no[key]
    ph["A4remark"] = AFTERSALES[3][4]
    return ph


# ---------------------------------------------------------------- 组装 SQL

def build():
    order_no = {}          # key -> 订单号
    order_id = {}          # key -> 订单ID
    aftersale_no = {}      # key -> 售后单号
    out = {}

    # 商品：id 顺序与 PRODUCTS 一致；剩余库存 = 初始库存 - 成交数量（取消/已退款回补）
    deducted = {}
    for o in ORDERS:
        if o["st"] in (4, 6):      # 已取消 / 已退款：库存已回滚，不占用
            continue
        for pid, qty in o["items"]:
            deducted[pid] = deducted.get(pid, 0) + qty

    prod_rows = []
    for i, (cat, title, price, orig, cond, desc, stock, seller, _subject) in enumerate(PRODUCTS, start=1):
        remain = max(stock - deducted.get(i, 0), 0)
        ctime = prod_time(i)
        status = 1 if remain > 0 else 0      # 售罄商品自动下架，避免"上架却买不到"
        prod_rows.append("(%d,%d,%s,%.2f,%.2f,%d,%s,%s,%d,%d,%s,%d,0,%s,%s)" % (
            i, cat, q(title), price, orig, cond, q(desc), q(img_path(i)), remain,
            seller, q(SELLER_NAME[seller]), status, q(ctime), q(ctime)))

    # 订单 + 订单项
    order_rows, item_rows = [], []
    for oid, o in enumerate(ORDERS, start=1):
        key = o["k"]
        buyer = o["buyer"]
        name, phone, addr = BUYER_INFO[buyer]
        ctime_sql = sqlv(o["create"])
        if is_expr(o["create"]):
            # 待付款订单的创建时间取导入时刻，使其始终落在 30 分钟支付超时窗口内
            no_sql = "CONCAT('CT',DATE_FORMAT(" + now_expr() + ",'%Y%m%d%H%i%s'),'1')"
            order_no[key] = "<导入时刻生成>"
        else:
            no = "CT" + ts(o["create"]) + "1"
            no_sql = q(no)
            order_no[key] = no
        order_id[key] = oid
        total = 0.0
        for pid, qty in o["items"]:
            price = PRODUCTS[pid - 1][2]
            sub = round(price * qty, 2)
            total = round(total + sub, 2)
            item_rows.append("(%d,%d,%s,%d,%s,%s,%.2f,%d,%.2f)" % (
                0, oid, no_sql, pid, q(PRODUCTS[pid - 1][1]),
                q(img_path(pid)), price, qty, sub))
        updated = o["finish"] or o["cancel"] or o["pay"]
        order_rows.append("(%d,%s,%d,%s,%.2f,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)" % (
            oid, no_sql, buyer, q(USER_NAME[buyer]), total, o["st"],
            q(name), q(phone), q(addr), q(o["remark"]),
            q(o["pay"]), q(o["ship"]), q(o["logi"]), q(o["finish"]),
            q(o["cancel"]), ctime_sql, q(updated) if updated else ctime_sql))

    # 评价（编号按 订单项 顺序重排）
    review_rows = []
    for idx, (key, pid, rating, content) in enumerate(REVIEWS, start=1):
        o = next(x for x in ORDERS if x["k"] == key)
        rtime = add_days(o["finish"], 1, "20:00:00") if idx % 2 else add_days(o["finish"], 1, "11:00:00")
        review_rows.append("(%d,%d,%d,%d,%s,%d,%s,'',%s)" % (
            idx, order_id[key], pid, o["buyer"], q(USER_NAME[o["buyer"]]),
            rating, q(content), q(rtime)))

    # 售后
    as_seq = 0
    aftersale_rows = []
    for key, atype, reason, status, remark, handle_time in AFTERSALES:
        as_seq += 1
        o = next(x for x in ORDERS if x["k"] == key)
        ctime = add_days(o["finish"], 1, "10:00:00") if status else add_days(o["finish"], 1, "10:05:00")
        no = "AS" + ts(ctime) + str(as_seq)
        aftersale_no[key] = no
        order_total = round(sum(PRODUCTS[p - 1][2] * qty for p, qty in o["items"]), 2)
        aftersale_rows.append("(%d,%s,%d,%s,%d,%s,%d,%s,%.2f,%d,%s,%s,%s,%s)" % (
            as_seq, q(no), order_id[key], q(order_no[key]), o["buyer"], q(USER_NAME[o["buyer"]]),
            atype, q(reason), order_total, status,
            q("admin" if status else ""), q(remark), q(ctime), q(handle_time)))

    # 物流轨迹：唯一键 (order_no, logistics_no, status_label, context)
    trace_rows = []
    tid = 0
    for key in TRACE_KEYS:
        o = next(x for x in ORDERS if x["k"] == key)
        no = order_no[key]
        logi = o["logi"]
        carrier = logi[:3] if logi[:3] in CARRIERS else logi[:2]
        cname = CARRIERS[carrier]
        nodes = [
            (add_hours(o["ship"], 1), "已揽收", "%s已揽收，包裹正在发往本地分拨中心。" % cname),
            (add_hours(o["ship"], 8), "运输中", "快件已到达校区所在城市分拨中心，正在发往下一站。"),
        ]
        if o["finish"]:
            nodes += [
                (add_days(o["finish"], 0, "08:30:00"), "派送中", "快件已到达校园快递驿站，正在派送中。"),
                (o["finish"], "已签收", "快件已由本人签收，感谢使用%s。" % cname),
            ]
        for t, label, ctx in nodes:
            tid += 1
            trace_rows.append("(%d,%s,%s,%s,%s,%s,%s,%s)" % (
                tid, q(no), q(logi), q(carrier), q(label), q(ctx), q(t), q(t)))

    # 外呼留痕（交付默认通道未启用 → 均为降级留痕）
    ph = placeholder_map(order_no, aftersale_no)
    log_rows = []
    for i, (uid, channel, target, ntype, title, content, biz) in enumerate(NOTIFY_LOGS, start=1):
        ctime = add_days("2026-10-14 20:00:00", i - 1, "20:05:00")
        log_rows.append("(%d,%d,%s,%s,%s,%s,%s,%s,2,'',%s,0,%s)" % (
            i, uid, q(channel), q(target), q(ntype), q(title),
            q(content.format(**ph)), q(biz.format(**ph) if biz else ""),
            q("通道未配置，已降级为仅站内通知"), q(ctime)))

    # 站内通知
    notice_rows = []
    for i, (uid, ntype, title, content, biz, read, ctime, rtime) in enumerate(NOTICES, start=1):
        notice_rows.append("(%d,%d,%s,%s,%s,%s,%d,%s,%s)" % (
            i, uid, q(ntype), q(title), q(content.format(**ph)),
            q(biz.format(**ph) if biz else ""), read, sqlv(ctime), sqlv(rtime)))

    out["campus_category"] = ",".join(
        "(%d,%s,0,%d,%s,%s)" % (cid, q(name), sort, q(CAT_TIME), q(CAT_TIME))
        for cid, name, sort in CATEGORIES)
    out["campus_product"] = ",".join(prod_rows)
    out["campus_account"] = ",".join(
        "(%d,%d,%s,%s,0.00,%s)" % (i, uid, q(USER_NAME[uid]), bal, q("2026-10-15 09:30:00"))
        for i, (uid, bal) in enumerate(sorted(BALANCES.items()), start=1))
    out["campus_cart"] = ",".join(
        "(%d,%d,%s,%d,%d,%d,%s,%s)" % (i, uid, q(USER_NAME[uid]), pid, qty, chk, q(t), q(t))
        for i, (uid, pid, qty, chk, t) in enumerate(CARTS, start=1))
    out["campus_order"] = ",".join(order_rows)
    out["campus_order_item"] = ",".join(
        r.replace("(0,", "(%d," % i, 1) for i, r in enumerate(item_rows, start=1))
    out["campus_review"] = ",".join(review_rows)
    out["campus_aftersale"] = ",".join(aftersale_rows)
    out["campus_logistics_trace"] = ",".join(trace_rows)
    out["campus_notify_log"] = ",".join(log_rows)
    out["campus_notice"] = ",".join(notice_rows)
    out["sys_user"] = ",".join(_student_row(s) for s in STUDENTS)
    out["sys_user_role"] = ",".join("(%d,2)" % s[0] for s in STUDENTS)
    return out


PASSWORD_HASH = "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2"


def _student_row(s):
    uid, login, nick, sex, email, phone, remark = s
    # 列序：user_id,dept_id,user_name,nick_name,user_type,email,phonenumber,sex,avatar,
    #       password,status,del_flag,login_ip,login_date,pwd_update_date,create_by,
    #       create_time,update_by,update_time,remark
    return ("(%d,105,%s,%s,'00',%s,%s,%s,'',%s,'0','0',NULL,NULL,NULL,'admin',%s,'',NULL,%s)"
            % (uid, q(login), q(nick), q(email), q(phone), q(sex), q(PASSWORD_HASH),
               q("2026-08-18 09:00:00"), q(remark)))


USER_NAME = {1: "admin", 2: "ry"}
USER_NAME.update({s[0]: s[1] for s in STUDENTS})
SELLER_NAME = {s[0]: s[1] for s in STUDENTS}
SELLER_NAME.update({1: "admin", 2: "ry"})


def patch(text, table, values):
    """把某张表的 INSERT 内容整体替换为给定值串（空串则保持空表）。"""
    pattern = re.compile(
        r"(LOCK TABLES `%s` WRITE;\s*\n/\*!40000 ALTER TABLE `%s` DISABLE KEYS \*/;\s*\n)"
        r"(.*?)"
        r"(/\*!40000 ALTER TABLE `%s` ENABLE KEYS \*/;)" % (table, table, table),
        re.S)
    stmt = ("INSERT INTO `%s` VALUES %s;\n" % (table, values)) if values else ""
    new, n = pattern.subn(lambda m: m.group(1) + stmt + m.group(3), text, count=1)
    if n != 1:
        raise SystemExit("[FAIL] 未能在全量脚本中定位表 %s 的数据块" % table)
    return new


def append_rows(text, table, extra):
    """在既有 INSERT 的 VALUES 列表末尾追加若干行（按行首 ID 去重，保证脚本可重复执行）。"""
    pattern = re.compile(r"(INSERT INTO `%s` VALUES )(.*?)(;\r?\n)" % table, re.S)
    m = pattern.search(text)
    if not m:
        raise SystemExit("[FAIL] 未找到表 %s 的 INSERT 语句" % table)
    block = m.group(2)
    have = set(re.findall(r"(?:^|,|\)\r?\n|\n)\((\d+),", block))
    keep = [row for row in re.findall(r"\((?:[^()]|\([^()]*\))*\)", extra)
            if row[1:].split(",", 1)[0].strip() not in have]
    if not keep:
        return text
    return text[:m.start(2)] + block + "," + ",".join(keep) + text[m.end(2):]


def main():
    if not SQL_FILE.exists():
        raise SystemExit("[FAIL] 找不到全量脚本：%s" % SQL_FILE)
    text = SQL_FILE.read_text(encoding="utf-8")
    original = text

    values = build()
    for table in ["campus_category", "campus_account", "campus_cart", "campus_product",
                  "campus_order", "campus_order_item", "campus_review", "campus_aftersale",
                  "campus_logistics_trace", "campus_notify_log", "campus_notice"]:
        text = patch(text, table, values[table])

    text = append_rows(text, "sys_user", values["sys_user"])
    text = append_rows(text, "sys_user_role", values["sys_user_role"])

    SQL_FILE.write_text(text, encoding="utf-8")

    # 一致性自检
    print("== 种子数据一致性自检 ==")
    print("分类 %d / 商品 %d / 订单 %d / 订单项 %d / 评价 %d / 售后 %d / 轨迹 %d / 外呼留痕 %d / 站内通知 %d"
          % (len(CATEGORIES), len(PRODUCTS), len(ORDERS), len(values["campus_order_item"].split("),(")),
             len(REVIEWS), len(AFTERSALES),
             len(values["campus_logistics_trace"].split("),(")),
             len(NOTIFY_LOGS), len(NOTICES)))
    print("学生账号 %d 个（角色 2）" % len(STUDENTS))
    bad = []
    for o in ORDERS:
        if o["st"] in (1, 2, 3, 5, 6) and not o["pay"]:
            bad.append("%s 缺少 pay_time" % o["k"])
        if o["st"] in (2, 3, 5, 6) and (not o["ship"] or not o["logi"]):
            bad.append("%s 缺少 ship_time/logistics_no" % o["k"])
        if o["st"] in (3, 5, 6) and not o["finish"]:
            bad.append("%s 缺少 finish_time" % o["k"])
        if o["st"] == 4 and not o["cancel"]:
            bad.append("%s 缺少 cancel_time" % o["k"])
        for pid, _ in o["items"]:
            if PRODUCTS[pid - 1][7] == o["buyer"]:
                bad.append("%s 买家=卖家（商品 %d）" % (o["k"], pid))
            if PRODUCTS[pid - 1][0] == 0:
                bad.append("%s 商品 %d 无分类" % (o["k"], pid))

    # 商品剩余库存 / 售罄集合（与 build 同一口径）
    deducted = {}
    for o in ORDERS:
        if o["st"] in (4, 6):
            continue
        for pid, qty in o["items"]:
            deducted[pid] = deducted.get(pid, 0) + qty
    remain = {i: max(PRODUCTS[i - 1][6] - deducted.get(i, 0), 0)
              for i in range(1, len(PRODUCTS) + 1)}
    sold_out = {i for i, r in remain.items() if r == 0}
    for uid, pid, qty, _chk, _t in CARTS:
        if PRODUCTS[pid - 1][7] == uid:
            bad.append("购物车用户 %d 加入了自己的商品 %d" % (uid, pid))
        if pid in sold_out:
            bad.append("购物车用户 %d 加入了售罄商品 %d" % (uid, pid))
        if qty > remain[pid]:
            bad.append("购物车用户 %d 商品 %d 数量超过剩余库存" % (uid, pid))
    literals = [o["create"] for o in ORDERS if not is_expr(o["create"])]
    latest_prod = max(prod_time(i) for i in range(1, len(PRODUCTS) + 1))
    if latest_prod >= min(literals):
        bad.append("商品上架时间(%s)晚于最早订单创建时间(%s)" % (latest_prod, min(literals)))
    print("售罄下架商品 %d 件 / 商品上架时间 %s ~ %s / 待付款订单创建时间取导入时刻"
          % (len(sold_out), min(prod_time(i) for i in remain), latest_prod))

    print("一致性检查：%s" % ("全部通过" if not bad else "; ".join(bad)))
    print("已回写：%s（%d -> %d 字符）" % (SQL_FILE, len(original), len(text)))


if __name__ == "__main__":
    main()
