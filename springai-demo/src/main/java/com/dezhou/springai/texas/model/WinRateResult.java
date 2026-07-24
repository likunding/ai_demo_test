package com.dezhou.springai.texas.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WinRateResult {

    private double winRate;
    private double tieRate;
    private double loseRate;
    private int wins;
    private int ties;
    private int losses;

    public String getWinRateFormatted() {
        return String.format("%.2f%%", winRate);
    }

    public String getTieRateFormatted() {
        return String.format("%.2f%%", tieRate);
    }

    public String getLoseRateFormatted() {
        return String.format("%.2f%%", loseRate);
    }
}