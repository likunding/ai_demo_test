package com.dezhou.springai.texas.controller;

import com.dezhou.springai.dto.ApiResponse;
import com.dezhou.springai.texas.model.*;
import com.dezhou.springai.texas.service.TexasHoldemService;
import com.dezhou.springai.texas.service.WinRateCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/texas")
@RequiredArgsConstructor
public class TexasHoldemController {

    private final TexasHoldemService texasHoldemService;
    private final WinRateCalculator winRateCalculator;

    @PostMapping("/game")
    public ApiResponse<GameState> createGame(@RequestBody Map<String, Object> params) {
        String gameId = (String) params.get("gameId");
        int initialChips = params.containsKey("initialChips") ? ((Number) params.get("initialChips")).intValue() : 10000;
        int bigBlind = params.containsKey("bigBlind") ? ((Number) params.get("bigBlind")).intValue() : 100;
        GameState game = texasHoldemService.createGame(gameId, initialChips, bigBlind);
        return ApiResponse.ok(game);
    }

    @GetMapping("/game/{gameId}")
    public ApiResponse<GameState> getGame(@PathVariable String gameId) {
        GameState game = texasHoldemService.getGame(gameId);
        return game != null ? ApiResponse.ok(game) : ApiResponse.error(400, "游戏不存在");
    }

    @DeleteMapping("/game/{gameId}")
    public ApiResponse<Void> deleteGame(@PathVariable String gameId) {
        texasHoldemService.deleteGame(gameId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/game/{gameId}/player")
    public ApiResponse<GameState> addPlayer(@PathVariable String gameId, @RequestBody Map<String, Object> params) {
        String playerId = (String) params.get("playerId");
        String playerName = (String) params.get("playerName");
        int chips = params.containsKey("chips") ? ((Number) params.get("chips")).intValue() : 10000;
        GameState game = texasHoldemService.addPlayer(gameId, playerId, playerName, chips);
        return game != null ? ApiResponse.ok(game) : ApiResponse.error(400, "游戏不存在");
    }

    @PostMapping("/game/{gameId}/start")
    public ApiResponse<GameState> startGame(@PathVariable String gameId) {
        GameState game = texasHoldemService.startGame(gameId);
        if (game == null) {
            return ApiResponse.error(404, "游戏不存在");
        }
        if (game.getPlayers().size() < 2) {
            return ApiResponse.error(400, "至少需要2名玩家");
        }
        return ApiResponse.ok(game);
    }

    @PostMapping("/game/{gameId}/flop")
    public ApiResponse<GameState> dealFlop(@PathVariable String gameId) {
        GameState game = texasHoldemService.dealFlop(gameId);
        return game != null ? ApiResponse.ok(game) : ApiResponse.error(400, "游戏不存在");
    }

    @PostMapping("/game/{gameId}/turn")
    public ApiResponse<GameState> dealTurn(@PathVariable String gameId) {
        GameState game = texasHoldemService.dealTurn(gameId);
        return game != null ? ApiResponse.ok(game) : ApiResponse.error(400, "游戏不存在");
    }

    @PostMapping("/game/{gameId}/river")
    public ApiResponse<GameState> dealRiver(@PathVariable String gameId) {
        GameState game = texasHoldemService.dealRiver(gameId);
        return game != null ? ApiResponse.ok(game) : ApiResponse.error(400, "游戏不存在");
    }

    @PostMapping("/game/{gameId}/action")
    public ApiResponse<GameState> playerAction(@PathVariable String gameId, @RequestBody Map<String, Object> params) {
        String playerId = (String) params.get("playerId");
        String action = (String) params.get("action");
        Integer amount = params.containsKey("amount") ? ((Number) params.get("amount")).intValue() : null;
        GameState game = texasHoldemService.playerAction(gameId, playerId, action, amount);
        return game != null ? ApiResponse.ok(game) : ApiResponse.error(400, "游戏不存在");
    }

    @GetMapping("/game/{gameId}/winners")
    public ApiResponse<List<PlayerState>> getWinners(@PathVariable String gameId) {
        List<PlayerState> winners = texasHoldemService.getWinners(gameId);
        return ApiResponse.ok(winners);
    }

    @GetMapping("/game/{gameId}/player/{playerId}/hand")
    public ApiResponse<HandEvaluationResult> evaluatePlayerHand(@PathVariable String gameId, @PathVariable String playerId) {
        HandEvaluationResult result = texasHoldemService.evaluatePlayerHand(gameId, playerId);
        return result != null ? ApiResponse.ok(result) : ApiResponse.error(404, "玩家不存在");
    }

    @GetMapping("/round/{round}")
    public ApiResponse<String> getRoundName(@PathVariable int round) {
        return ApiResponse.ok(texasHoldemService.getRoundName(round));
    }

    @GetMapping("/game/{gameId}/player/{playerId}/winrate")
    public ApiResponse<WinRateResult> calculateWinRate(@PathVariable String gameId, @PathVariable String playerId) {
        GameState game = texasHoldemService.getGame(gameId);
        if (game == null) {
            return ApiResponse.error(404, "游戏不存在");
        }
        WinRateResult result = winRateCalculator.calculateWinRateFromGame(gameId, playerId, game);
        return ApiResponse.ok(result);
    }

    @PostMapping("/winrate/calculate")
    public ApiResponse<WinRateResult> calculateWinRateDirect(@RequestBody Map<String, Object> params) {
        try {
            List<Card> holeCards = parseCards((List<String>) params.get("holeCards"));
            List<Card> communityCards = parseCards((List<String>) params.get("communityCards"));
            int numOpponents = params.containsKey("numOpponents") ? ((Number) params.get("numOpponents")).intValue() : 1;
            WinRateResult result = winRateCalculator.calculateWinRate(holeCards, communityCards, numOpponents);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            return ApiResponse.error(400, "参数错误: " + e.getMessage());
        }
    }

    private List<Card> parseCards(List<String> cardCodes) {
        if (cardCodes == null) {
            return List.of();
        }
        return cardCodes.stream()
                .map(Card::fromCode)
                .filter(Objects::nonNull)
                .toList();
    }
}