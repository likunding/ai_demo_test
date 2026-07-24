package com.dezhou.springai.texas.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandEvaluationResult {

    private HandRank handRank;
    private String description;
    private List<Card> bestFive;
    private List<Integer> kickers;

    public int getStrength() {
        return handRank.getRank();
    }

    public String getChineseDescription() {
        return handRank.getChinese() + (description != null ? " - " + description : "");
    }
}