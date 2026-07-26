package com.dezhou.springai.texas.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实战行动建议结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionAdvice {

    /** 建议行动 */
    private SuggestedAction action;

    /** 建议加注到的总注额（仅 RAISE 时有意义，单位与 pot/toCall 一致） */
    private Double raiseTo;

    /** 底池赔率（0~1，例如 0.25 表示需要约 25% 胜率） */
    private double potOdds;

    /** 做出该决策所需的大致胜率阈值（百分比 0~100） */
    private double requiredEquity;

    /** 当前使用的胜率（百分比） */
    private double equity;

    /** 位置 */
    private Position position;

    /** 场面 */
    private ActionSpot spot;

    /** 简短理由 */
    private String reason;

    public String getActionLabel() {
        if (action == null) {
            return "未知";
        }
        return switch (action) {
            case FOLD -> "弃牌";
            case CHECK -> "过牌";
            case CALL -> "跟注";
            case RAISE -> "加注";
        };
    }
}
