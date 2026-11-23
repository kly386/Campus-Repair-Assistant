package com.example.campusrepair.service;

import java.util.List;

/**
 * 自助诊断规则常量（唯一来源）
 *
 * @Description 检索服务（落库时判定紧急等级）与诊断引擎（流式回答时分层拦截）
 *              共用同一套规则，避免两处判断不一致导致数据前后矛盾。
 * @Author 闵娟
 * @Date 2026-11-23
 * @Version 1.0
 */
public final class DiagnosisRules {

    /** 涉及水电火气的高危词：命中后只给安全提示，绝不让模型输出操作步骤 */
    public static final List<String> EMERGENCY_WORDS = List.of(
            "爆管", "漏电", "起火", "着火", "冒烟", "煤气", "燃气味", "瓦斯",
            "跳闸合不上", "合不上闸", "电火花", "烧焦", "触电感", "触电", "水浸",
            // 学生口语里的高危说法，一律只给安全提示
            "冒火星", "有焦味", "焦糊味", "糊味", "电人", "电击", "打火",
            "水漫", "水淹", "漏气", "煤气味", "明火", "烧起来", "冒火", "短路起火");

    /** 维修领域词：命中后才认为是维修咨询，否则按闲聊礼貌拒答 */
    public static final List<String> REPAIR_WORDS = List.of(
            // 通用动词 / 现象
            "修", "维修", "坏", "故障", "报修", "安装", "更换", "闪", "响",
            // 设备名（标准 + 学生口语）
            "水龙头", "马桶", "坐便器", "门锁", "门窗", "台灯", "灯具", "灯", "空调",
            "热水器", "路由器", "网络", "wifi", "WiFi", "下水", "地漏", "水管",
            "电闸", "空开", "空气开关", "插座", "插排", "花洒", "淋浴", "水箱",
            "洗衣机", "冰箱", "电风扇", "门窗", "窗帘", "水槽", "洗脸池",
            // 故障现象
            "漏", "堵", "跳闸", "断电", "没电", "停电", "红灯", "不加热", "水温",
            "异味", "打不开", "关不上", "点不着", "不出水", "不制冷", "不转",
            "不好用", "失灵", "松了", "掉了", "裂了", "卡住", "反味", "发臭",
            // 宿舍 / 场景词
            "宿舍", "寝室", "浴室", "卫生间", "厕所", "阳台", "房间",
            // 学生常用口语描述，不能因为没有标准术语就被甩闲聊模板
            "洗澡", "冷热", "忽冷忽热", "忽上忽下",
            "忽高忽低", "一会烫", "一会凉", "一会儿烫", "一会儿凉");

    /** 常见追问：没有上下文时不能当闲聊拒答，也不能假装记得上一轮 */
    public static final List<String> FOLLOW_UP_WORDS = List.of(
            "然后呢", "之后呢", "然后", "接着呢", "接下来", "试过了", "试过", "已试",
            "还是不行", "还是没用", "没用", "不管用", "不行", "那怎么办",
            // 同一对话里的"还有别的方法吗"是追问，不含维修词也不能当闲聊拒答
            "其他方法", "其他办法", "别的办法", "另外的办法", "还有别的", "还有吗",
            "有没有其他", "还有没有其他", "换个方法", "换种方法", "其他招", "别的招");

    /**
     * 明确表达"上一轮方法试过且无效"的说法：用户常常不会点"没解决"按钮，
     * 而是直接打字反馈。这类消息要当成反馈处理，否则会被误判成普通追问，
     * 输出"没有上下文"并丢掉眼前上一轮的回答。
     */
    public static final List<String> TRIED_FAILED_WORDS = List.of(
            "试过了", "试过", "试了", "已经试", "已试", "试了没用", "试了还是",
            "还是不行", "还是没用", "还是没效果", "仍然不行", "没用", "不管用",
            "不顶用", "没效果", "不见好", "没好转", "试了也不行", "弄了还是",
            "搞了还是", "弄了没用", "搞了没用", "没起作用");

    /**
     * 同对话收尾语：只在整句就是这句话时算收尾，避免把"好的热水器怎么修"误判。
     * 这类消息不需要再检索或生成，直接短回一句即可。
     * "我试试/我试试看"是学生接受建议、准备去试的口头语，等同收尾，
     * 不能再甩闲聊拒答模板，也不该继续追问"解决了吗"。
     */
    public static final List<String> CLOSING_WORDS = List.of(
            "好", "好吧", "好的", "行", "行吧", "嗯", "嗯嗯", "收到",
            "知道", "知道了", "明白", "明白了", "谢谢", "谢谢啦", "ok", "okay",
            "我试试", "试试看", "我去试试", "我试试看", "我先试试", "我试一下",
            "好的我试试", "行我试试", "好我试试", "那我试试", "好的我试试看");

    private DiagnosisRules() {
    }

    public static boolean containsAny(String text, List<String> words) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        return words.stream().anyMatch(w -> lower.contains(w.toLowerCase()));
    }

    public static boolean isEmergency(String question) {
        return containsAny(question, EMERGENCY_WORDS);
    }

    public static boolean isRepairRelated(String question) {
        return containsAny(question, REPAIR_WORDS);
    }

    public static boolean isFollowUp(String question) {
        return containsAny(question, FOLLOW_UP_WORDS);
    }

    /** 是否自然语言反馈"试过且无效"（不等同于普通追问） */
    public static boolean isTriedAndFailed(String question) {
        return containsAny(question, TRIED_FAILED_WORDS);
    }

    /** 是否整句都是收尾语（允许首尾标点和空白） */
    public static boolean isClosing(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim()
                .replaceAll("^[\\s，。！？!?、,.~～]+|[\\s，。！？!?、,.~～]+$", "")
                .toLowerCase();
        return CLOSING_WORDS.contains(normalized);
    }

    /** 紧急等级由规则推导，不让模型生成，避免同一问题两次结果不同 */
    public static String emergencyLevel(boolean emergency) {
        return emergency ? "HIGH" : "NORMAL";
    }
}
