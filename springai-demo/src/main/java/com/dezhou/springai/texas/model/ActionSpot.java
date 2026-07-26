package com.dezhou.springai.texas.model;

/**
 * 实战场面类型。
 */
public enum ActionSpot {
    /** 有人对你加注，需要决定跟/反加/弃 */
    FACING_RAISE("有人加注"),
    /** 前面玩家全部过牌，轮到你 */
    EVERYONE_CHECKED("全部过牌"),
    /** 无人入池，你决定是否开池 */
    OPEN_UNOPENED("开池决策");

    private final String label;

    ActionSpot(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ActionSpot fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ActionSpot.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
