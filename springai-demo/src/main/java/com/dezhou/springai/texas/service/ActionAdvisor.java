package com.dezhou.springai.texas.service;

import com.dezhou.springai.texas.model.ActionAdvice;
import com.dezhou.springai.texas.model.ActionSpot;
import com.dezhou.springai.texas.model.Position;
import com.dezhou.springai.texas.model.SuggestedAction;
import org.springframework.stereotype.Service;

/**
 * 基于胜率与底池赔率的实战行动建议（启发式，非 GTO）。
 */
@Service
public class ActionAdvisor {

    /** 相对底池赔率高出多少（百分点）可考虑反加 */
    private static final double RAISE_EDGE_PERCENT = 15.0;

    /** 全过时：低于该胜率则过牌 */
    private static final double CHECK_MAX_EQUITY = 35.0;

    /** 全过时：高于该胜率可加大到 2/3 池 */
    private static final double STRONG_BET_EQUITY = 55.0;

    /**
     * @param equityPercent 胜率百分比，例如 42.5 表示 42.5%
     * @param pot           当前底池（跟注前）
     * @param toCall        需要跟注的金额；全过/可过牌时为 0
     * @param position      位置，可为 null（按 MP 处理）
     * @param spot          场面，必填
     * @param bigBlind      大盲，用于开池尺度；null 时用 pot 或 1 推算
     */
    public ActionAdvice advise(double equityPercent,
                               double pot,
                               double toCall,
                               Position position,
                               ActionSpot spot,
                               Double bigBlind) {
        if (spot == null) {
            throw new IllegalArgumentException("spot 不能为空");
        }
        if (pot < 0 || toCall < 0) {
            throw new IllegalArgumentException("pot/toCall 不能为负");
        }

        Position pos = position != null ? position : Position.MP;
        double equity = clamp(equityPercent, 0, 100);
        double safePot = Math.max(0, pot);
        double safeToCall = Math.max(0, toCall);
        double bb = (bigBlind != null && bigBlind > 0) ? bigBlind : inferBigBlind(safePot, pos);

        return switch (spot) {
            case FACING_RAISE -> adviseFacingRaise(equity, safePot, safeToCall, pos);
            case EVERYONE_CHECKED -> adviseEveryoneChecked(equity, safePot, pos);
            case OPEN_UNOPENED -> adviseOpenUnopened(equity, safePot, pos, bb);
        };
    }

    private ActionAdvice adviseFacingRaise(double equity, double pot, double toCall, Position position) {
        if (toCall <= 0) {
            // 无需跟注时按全过处理
            return adviseEveryoneChecked(equity, pot, position);
        }

        double potOdds = toCall / (pot + toCall);
        double requiredEquity = potOdds * 100;

        if (equity + 0.01 < requiredEquity) {
            return base(equity, potOdds, requiredEquity, position, ActionSpot.FACING_RAISE)
                    .action(SuggestedAction.FOLD)
                    .reason(String.format(
                            "胜率 %.1f%% 低于底池赔率所需 %.1f%%，建议弃牌。",
                            equity, requiredEquity))
                    .build();
        }

        if (equity >= requiredEquity + RAISE_EDGE_PERCENT || equity >= 65.0) {
            double raiseTo = roundChip(toCall * 2.5 + pot * 0.5);
            raiseTo = Math.max(raiseTo, toCall * 2);
            return base(equity, potOdds, requiredEquity, position, ActionSpot.FACING_RAISE)
                    .action(SuggestedAction.RAISE)
                    .raiseTo(raiseTo)
                    .reason(String.format(
                            "胜率 %.1f%% 明显高于所需 %.1f%%（或属于强牌区间），建议反加到约 %.0f。",
                            equity, requiredEquity, raiseTo))
                    .build();
        }

        return base(equity, potOdds, requiredEquity, position, ActionSpot.FACING_RAISE)
                .action(SuggestedAction.CALL)
                .reason(String.format(
                        "胜率 %.1f%% 满足底池赔率（需约 %.1f%%），建议跟注。",
                        equity, requiredEquity))
                .build();
    }

    private ActionAdvice adviseEveryoneChecked(double equity, double pot, Position position) {
        double potOdds = 0.0;
        double requiredEquity = CHECK_MAX_EQUITY;

        if (equity < CHECK_MAX_EQUITY) {
            return base(equity, potOdds, requiredEquity, position, ActionSpot.EVERYONE_CHECKED)
                    .action(SuggestedAction.CHECK)
                    .reason(String.format(
                            "前面全过，胜率 %.1f%% 偏低，建议过牌控制底池。",
                            equity))
                    .build();
        }

        double fraction = equity >= STRONG_BET_EQUITY ? (2.0 / 3.0) : 0.5;
        double raiseTo = roundChip(Math.max(pot * fraction, 1));
        String sizeLabel = equity >= STRONG_BET_EQUITY ? "2/3 底池" : "半池";

        return base(equity, potOdds, requiredEquity, position, ActionSpot.EVERYONE_CHECKED)
                .action(SuggestedAction.RAISE)
                .raiseTo(raiseTo)
                .reason(String.format(
                        "前面全过，胜率 %.1f%%，建议下注约 %s（%.0f）。位置：%s。",
                        equity, sizeLabel, raiseTo, position.getLabel()))
                .build();
    }

    private ActionAdvice adviseOpenUnopened(double equity, double pot, Position position, double bigBlind) {
        double openThreshold = openEquityThreshold(position);
        double potOdds = 0.0;

        if (equity < openThreshold) {
            // 大盲位可免费看翻牌
            if (position == Position.BB) {
                return base(equity, potOdds, openThreshold, position, ActionSpot.OPEN_UNOPENED)
                        .action(SuggestedAction.CHECK)
                        .reason(String.format(
                                "大盲位无人加注，胜率 %.1f%% 未达主动标准，建议过牌看翻牌。",
                                equity))
                        .build();
            }
            return base(equity, potOdds, openThreshold, position, ActionSpot.OPEN_UNOPENED)
                    .action(SuggestedAction.FOLD)
                    .reason(String.format(
                            "%s 开池阈值约 %.0f%%，当前胜率 %.1f%% 偏弱，建议弃牌。",
                            position.getLabel(), openThreshold, equity))
                    .build();
        }

        double raiseTo = openRaiseSize(position, bigBlind, pot);
        return base(equity, potOdds, openThreshold, position, ActionSpot.OPEN_UNOPENED)
                .action(SuggestedAction.RAISE)
                .raiseTo(raiseTo)
                .reason(String.format(
                        "%s 胜率 %.1f%% 达到开池标准（约 %.0f%%），建议加注到约 %.0f（约 %.1fxBB）。",
                        position.getLabel(), equity, openThreshold, raiseTo, raiseTo / bigBlind))
                .build();
    }

    private double openEquityThreshold(Position position) {
        return switch (position) {
            case EP -> 48.0;
            case MP -> 44.0;
            case CO -> 40.0;
            case BTN -> 36.0;
            case SB -> 42.0;
            case BB -> 38.0;
        };
    }

    private double openRaiseSize(Position position, double bigBlind, double pot) {
        double mult = switch (position) {
            case EP, MP -> 3.0;
            case CO, BTN -> 2.5;
            case SB -> 3.5;
            case BB -> 3.0;
        };
        double fromBb = bigBlind * mult;
        // 若池子已有盲注，开池至少盖过现有底池
        return roundChip(Math.max(fromBb, pot + bigBlind * 2));
    }

    private double inferBigBlind(double pot, Position position) {
        if (pot > 0) {
            // 翻前常见底池 ≈ 1.5bb
            return Math.max(1, pot / 1.5);
        }
        return 1;
    }

    private ActionAdvice.ActionAdviceBuilder base(double equity,
                                                  double potOdds,
                                                  double requiredEquity,
                                                  Position position,
                                                  ActionSpot spot) {
        return ActionAdvice.builder()
                .equity(equity)
                .potOdds(potOdds)
                .requiredEquity(requiredEquity)
                .position(position)
                .spot(spot);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double roundChip(double value) {
        if (value < 1) {
            return Math.round(value * 10) / 10.0;
        }
        return Math.round(value);
    }
}
