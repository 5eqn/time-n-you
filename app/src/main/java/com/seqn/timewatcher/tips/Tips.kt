package com.seqn.timewatcher.tips

import com.seqn.timewatcher.data.model.DefaultConfig

enum class TipId { SLEEP, LIFE, SNOOZE, WORK, HOBBY, PHILOSOPHY }

data class Tip(
    val id: TipId,
    val title: String,
    val message: String,
    /** label shown in the settings "提醒管理" list. */
    val settingLabel: String,
)

object Tips {

    /** state id -> tip shown before entering that state (default states only). */
    val stateTip: Map<String, TipId> = mapOf(
        DefaultConfig.ID_SLEEP to TipId.SLEEP,
        DefaultConfig.ID_LIFE to TipId.LIFE,
        DefaultConfig.ID_SNOOZE to TipId.SNOOZE,
        DefaultConfig.ID_WORK to TipId.WORK,
        DefaultConfig.ID_HOBBY to TipId.HOBBY,
    )

    private val all = mapOf(
        TipId.SLEEP to Tip(
            TipId.SLEEP, "准备睡觉？",
            "进入「睡觉」状态后请立刻开始专心睡觉，不要再玩手机。这个状态只应涵盖你真正在睡觉的时间。",
            "进入「睡觉」时提醒",
        ),
        TipId.LIFE to Tip(
            TipId.LIFE, "开始处理生活事务？",
            "「生活」包含洗漱、做饭、吃饭、如厕、洗澡等不可避免的事。进入后请立刻开始做这件事，中途不要玩手机。",
            "进入「生活」时提醒",
        ),
        TipId.SNOOZE to Tip(
            TipId.SNOOZE, "小憩一下？",
            "这段时间请起身走动、向窗外远眺、适量喝水。不要玩手机——玩手机不是小憩，而是摆烂。",
            "进入「小憩」时提醒",
        ),
        TipId.WORK to Tip(
            TipId.WORK, "开始主业？",
            "进入后请立刻专心投入主业。番茄钟使用原则：周期内绝不玩手机；尽量忽略外界干扰；若有必须处理的事，先切换到对应状态；尽量保证完整的高专注周期，做不到就缩短番茄钟时长。",
            "进入「主业」时提醒",
        ),
        TipId.HOBBY to Tip(
            TipId.HOBBY, "开始爱好？",
            "进入后请立刻专心投入这件让你享受的事。番茄钟周期内不分心、不玩手机，给自己一段完整、高质量的心流时光。",
            "进入「爱好」时提醒",
        ),
        TipId.PHILOSOPHY to Tip(
            TipId.PHILOSOPHY, "这与设计哲学相悖",
            "本软件是为时间利用率存在明显短板的人设计的——不是为了榨干每一秒专注。不受番茄钟约束的「有效」状态，往往会让人沿用旧习惯、极易分心。\n\n确认即代表你执意违背本软件的设计哲学。",
            "无番茄钟却设为「有效」时提醒",
        ),
    )

    fun get(id: TipId): Tip = all[id]!!

    /** Tips shown in the settings "提醒管理" list (everything except the philosophy one is toggleable too). */
    val manageable: List<Tip> = TipId.entries.map { all[it]!! }

    val philosophy: Tip = all[TipId.PHILOSOPHY]!!

    /** True if a state config (category + pomodoro) is "effective but unmanaged" — the case the philosophy tip warns about. */
    fun triggersPhilosophy(categoryName: String, pomodoroEnabled: Boolean): Boolean =
        categoryName == "EFFECTIVE" && !pomodoroEnabled

    const val CHANGELOG_TITLE = "时间&你 已更新到 1.3"
    const val CHANGELOG = """• 性能大幅优化：列表滚动、页面切换更流畅，整体更省电。
• 番茄钟现在在后台、锁屏、甚至应用被清理后都能可靠响铃（全屏告警，可一键停止）。
• 响铃链路进一步加固：番茄钟直接唤起响铃服务，并新增 Vivo 环境下的响铃测试与日志复制工具。
• 配色焕新：默认状态拥有更悦目的专属配色，整体色调更统一。
• 新增「备注」：可为任意状态添加备注并显示在首页（「生活」默认带「吃饭、洗漱、运动……」）。
• 首页状态列表显示备注与番茄钟时长，当前状态改为勾选徽标与描边，不再把未选状态做半透明。
• 番茄钟进行时，首页展示剩余时间与已用时。
• 设置页新增「补卡」：可选择日期时间和已有状态，补录后统计页会按补录记录重新计算。
• 「爱好」提示改为独立文案；番茄钟结束状态不再允许设为「有效」类（违背设计哲学）。
• 软件更名为「时间&你」。"""
}
