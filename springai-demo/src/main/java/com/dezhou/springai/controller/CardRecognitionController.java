package com.dezhou.springai.controller;

import com.dezhou.springai.config.AiConfig;
import com.dezhou.springai.dto.ApiResponse;
import com.dezhou.springai.service.CardRecognitionService;
import com.dezhou.springai.texas.model.ActionAdvice;
import com.dezhou.springai.texas.model.ActionSpot;
import com.dezhou.springai.texas.model.Card;
import com.dezhou.springai.texas.model.Position;
import com.dezhou.springai.texas.model.Rank;
import com.dezhou.springai.texas.model.Suit;
import com.dezhou.springai.texas.model.WinRateResult;
import com.dezhou.springai.texas.service.ActionAdvisor;
import com.dezhou.springai.texas.service.WinRateCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/card")
@RequiredArgsConstructor
public class CardRecognitionController {

    private final CardRecognitionService cardRecognitionService;
    private final AiConfig aiConfig;
    private final WinRateCalculator winRateCalculator;
    private final ActionAdvisor actionAdvisor;

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> recognize(
            @RequestParam("image") MultipartFile image) {
        try {
            if (image.isEmpty()) {
                return ApiResponse.error(400, "图片文件不能为空");
            }

            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ApiResponse.error(400, "文件必须为图片");
            }

            Map<String, Object> result = cardRecognitionService.recognizeCards(image);
            return ApiResponse.ok(result, aiConfig.getVisionProvider());
        } catch (IOException e) {
            return ApiResponse.error(500, "读取图片文件失败: " + e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(500, "识别失败: " + e.getMessage());
        }
    }

    /**
     * 德州扑克胜率 + 实战建议：上传手牌/公牌图片，识别后计算胜率，
     * 若提供 pot/spot 等场面参数则额外返回行动建议。
     */
    @PostMapping(value = "/texas-winrate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> texasWinrate(
            @RequestParam("handImage") MultipartFile handImage,
            @RequestParam(value = "communityImage", required = false) MultipartFile communityImage,
            @RequestParam(value = "numOpponents", defaultValue = "1") int numOpponents,
            @RequestParam(value = "pot", required = false) Double pot,
            @RequestParam(value = "toCall", required = false) Double toCall,
            @RequestParam(value = "position", required = false) String position,
            @RequestParam(value = "spot", required = false) String spot,
            @RequestParam(value = "bigBlind", required = false) Double bigBlind) {
        try {
            log.info("[texasWinrate] handImage={}, communityImage={}, numOpponents={}, pot={}, toCall={}, position={}, spot={}",
                    handImage.getOriginalFilename(),
                    communityImage != null ? communityImage.getOriginalFilename() : "null",
                    numOpponents, pot, toCall, position, spot);

            // 1. 识别手牌
            if (handImage.isEmpty()) {
                return ApiResponse.error(400, "手牌图片不能为空");
            }
            Map<String, Object> handResult = cardRecognitionService.recognizeCards(handImage);
            List<Card> holeCards = extractCards(handResult);

            if (holeCards.size() != 2) {
                return ApiResponse.error(400, "手牌识别失败或数量不对，需要恰好2张手牌，识别到" + holeCards.size() + "张");
            }

            // 2. 识别公牌（可选）
            Map<String, Object> communityResult = new HashMap<>();
            communityResult.put("count", 0);
            communityResult.put("cards", Collections.emptyList());
            List<Card> communityCards = new ArrayList<>();

            if (communityImage != null && !communityImage.isEmpty()) {
                communityResult = cardRecognitionService.recognizeCards(communityImage);
                communityCards = extractCards(communityResult);
            }

            // 3. 计算胜率
            WinRateResult winRate = winRateCalculator.calculateWinRate(holeCards, communityCards, numOpponents);

            // 4. 组装结果
            Map<String, Object> result = new HashMap<>();
            result.put("holeCards", handResult);
            result.put("communityCards", communityResult);
            result.put("winRate", winRate);
            result.put("holeCardCodes", holeCards.stream().map(Card::toCode).toList());
            result.put("communityCardCodes", communityCards.stream().map(Card::toCode).toList());
            result.put("numOpponents", numOpponents);

            // 5. 可选：实战行动建议（需至少提供 spot + pot）
            ActionSpot actionSpot = ActionSpot.fromString(spot);
            if (actionSpot != null && pot != null) {
                Position pos = Position.fromString(position);
                double callAmount = toCall != null ? toCall : 0.0;
                ActionAdvice advice = actionAdvisor.advise(
                        winRate.getWinRate(),
                        pot,
                        callAmount,
                        pos,
                        actionSpot,
                        bigBlind
                );
                result.put("actionAdvice", advice);
                result.put("scene", Map.of(
                        "pot", pot,
                        "toCall", callAmount,
                        "position", pos != null ? pos.name() : Position.MP.name(),
                        "spot", actionSpot.name(),
                        "bigBlind", bigBlind != null ? bigBlind : 0
                ));
                log.info("[texasWinrate] advice={} reason={}", advice.getAction(), advice.getReason());
            }

            log.info("[texasWinrate] hole={}, community={}, winRate={}%",
                    holeCards, communityCards, winRate.getWinRateFormatted());

            return ApiResponse.ok(result, aiConfig.getVisionProvider());
        } catch (IOException e) {
            return ApiResponse.error(500, "读取图片文件失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[texasWinrate] error", e);
            return ApiResponse.error(500, "计算失败: " + e.getMessage());
        }
    }

    /**
     * 从识别结果中提取 Card 对象列表
     */
    @SuppressWarnings("unchecked")
    private List<Card> extractCards(Map<String, Object> recognitionResult) {
        List<Card> cards = new ArrayList<>();
        if (recognitionResult == null || !recognitionResult.containsKey("cards")) {
            return cards;
        }

        List<Map<String, Object>> cardList = (List<Map<String, Object>>) recognitionResult.get("cards");
        for (Map<String, Object> card : cardList) {
            String suitStr = String.valueOf(card.get("suit"));
            String rankStr = String.valueOf(card.get("rank"));
            Suit suit = Suit.fromChinese(suitStr);
            Rank rank = Rank.fromChinese(rankStr);
            if (suit != null && rank != null) {
                cards.add(new Card(suit, rank));
            }
        }
        return cards;
    }
}
