package com.dezhou.springai.service;

import com.dezhou.springai.config.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CardRecognitionService {

    private final ChatModel visionChatModel;
    private final AiConfig aiConfig;

    /**
     * 必须把 @Qualifier 写在构造器参数上。
     * Lombok @RequiredArgsConstructor 不会把字段上的 @Qualifier 拷到构造器，
     * 否则会注入 @Primary 的 chat 模型（如 deepseek-coder），导致看图报 multimodal 错误。
     */
    public CardRecognitionService(@Qualifier("visionChatModel") ChatModel visionChatModel,
                                  AiConfig aiConfig) {
        this.visionChatModel = visionChatModel;
        this.aiConfig = aiConfig;
    }

    private static final String PROMPT_TEMPLATE = """
            请仔细识别图片中的每一张扑克牌。
            请按照从左到右、从上到下的顺序识别。
            花色只能是：梅花、方块、红桃、黑桃。
            点数只能是：A、2、3、4、5、6、7、8、9、10、J、Q、K。
            
            请直接返回 JSON 数组，不要包含 markdown 代码块标记（如 ```json）。
            
            返回格式示例：
            [
              {"index": 1, "suit": "红桃", "rank": "A", "description": "红桃A", "position": "左上角"},
              {"index": 2, "suit": "黑桃", "rank": "K", "description": "黑桃K", "position": "中间"}
            ]
            
            请用你识别到的实际扑克牌信息替换示例中的值，不要复制示例内容。
            如果图片中没有扑克牌，请返回：{"error": "无法识别"}
            """.strip();

    public Map<String, Object> recognizeCards(MultipartFile imageFile) throws IOException {
        log.info("[recognizeCards] provider={}, modelClass={}, filename={}, size={}",
                aiConfig.getVisionProvider(),
                visionChatModel.getClass().getSimpleName(),
                imageFile.getOriginalFilename(),
                imageFile.getSize());
        if (visionChatModel.getDefaultOptions() != null) {
            log.info("[recognizeCards] modelOptions={}", visionChatModel.getDefaultOptions());
        }

        byte[] imageBytes = imageFile.getBytes();
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes);
        
        String contentType = imageFile.getContentType();
        MimeType mimeType = MimeType.valueOf(contentType != null ? contentType : "image/jpeg");
        
        ChatClient chatClient = ChatClient.builder(visionChatModel).build();
        
        String result = chatClient.prompt()
                .user(u -> u
                        .text(PROMPT_TEMPLATE)
                        .media(new Media(mimeType, imageResource)))
                .call()
                .content();
        
        log.info("[recognizeCards] raw result: {}", result);
        
        return parseResult(result);
    }

    private Map<String, Object> parseResult(String raw) {
        Map<String, Object> result = new HashMap<>();
        try {
            String jsonStr = extractJson(raw);
            if (jsonStr == null) {
                result.put("error", "模型返回格式不正确");
                result.put("raw", raw);
                return result;
            }
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            
            if (jsonStr.startsWith("[")) {
                List<Map<String, Object>> cards = mapper.readValue(jsonStr, 
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                
                List<Map<String, Object>> normalizedCards = new ArrayList<>();
                int index = 1;
                for (Map<String, Object> card : cards) {
                    Map<String, Object> normalized = normalizeCard(card);
                    if (!normalized.containsKey("error")) {
                        normalized.put("index", index++);
                        normalizedCards.add(normalized);
                    }
                }
                
                result.put("count", normalizedCards.size());
                result.put("cards", normalizedCards);
                result.put("totalCards", normalizedCards.size());
            } else if (jsonStr.startsWith("{")) {
                Map<String, Object> json = mapper.readValue(jsonStr, Map.class);
                
                if (json.containsKey("error")) {
                    return json;
                }
                
                Map<String, Object> normalized = normalizeCard(json);
                normalized.put("index", 1);
                
                result.put("count", 1);
                result.put("cards", Collections.singletonList(normalized));
                result.put("totalCards", 1);
            } else {
                result.put("error", "无法解析返回结果");
                result.put("raw", raw);
            }
            
            return result;
        } catch (Exception e) {
            log.error("[parseResult] error parsing result: {}", e.getMessage());
            result.put("error", "解析结果失败");
            result.put("raw", raw);
            return result;
        }
    }

    private Map<String, Object> normalizeCard(Map<String, Object> card) {
        Map<String, Object> result = new HashMap<>();
        
        String suit = card.containsKey("suit") ? String.valueOf(card.get("suit")) : "未知";
        String rank = card.containsKey("rank") ? String.valueOf(card.get("rank")) : "未知";
        String description = card.containsKey("description") ? String.valueOf(card.get("description")) : "";
        String position = card.containsKey("position") ? String.valueOf(card.get("position")) : "";
        
        if (description.isEmpty()) {
            description = suit + rank;
        }
        
        result.put("suit", normalizeSuit(suit));
        result.put("rank", normalizeRank(rank));
        result.put("description", description);
        result.put("position", position);
        result.put("suitCode", getSuitCode(suit));
        result.put("rankCode", getRankCode(rank));
        
        return result;
    }

    private String extractJson(String text) {
        String cleanText = text.trim();
        
        if (cleanText.startsWith("```")) {
            int start = cleanText.indexOf('\n');
            int end = cleanText.lastIndexOf("```");
            if (start > 0 && end > start) {
                cleanText = cleanText.substring(start + 1, end).trim();
            }
        }
        
        Pattern arrayPattern = Pattern.compile("\\[[\\s\\S]*\\]");
        Matcher arrayMatcher = arrayPattern.matcher(cleanText);
        if (arrayMatcher.find()) {
            return arrayMatcher.group();
        }
        
        Pattern objectPattern = Pattern.compile("\\{[\\s\\S]*\\}");
        Matcher objectMatcher = objectPattern.matcher(cleanText);
        if (objectMatcher.find()) {
            return objectMatcher.group();
        }
        
        return null;
    }

    private String normalizeSuit(String suit) {
        if (suit == null) return "未知";
        String s = suit.trim();
        if (s.contains("梅") || s.contains("club") || s.contains("梅花")) return "梅花";
        if (s.contains("方") || s.contains("diamond") || s.contains("方块")) return "方块";
        if (s.contains("红") || s.contains("heart") || s.contains("红桃")) return "红桃";
        if (s.contains("黑") || s.contains("spade") || s.contains("黑桃")) return "黑桃";
        return s;
    }

    private String normalizeRank(String rank) {
        if (rank == null) return "未知";
        String r = rank.trim().toUpperCase();
        if (r.equals("A") || r.contains("ACE")) return "A";
        if (r.equals("J") || r.contains("JACK")) return "J";
        if (r.equals("Q") || r.contains("QUEEN")) return "Q";
        if (r.equals("K") || r.contains("KING")) return "K";
        if (r.equals("10") || r.contains("TEN")) return "10";
        return r;
    }

    private String getSuitCode(String suit) {
        if (suit.contains("梅")) return "C";
        if (suit.contains("方")) return "D";
        if (suit.contains("红")) return "H";
        if (suit.contains("黑")) return "S";
        return "?";
    }

    private Integer getRankCode(String rank) {
        if (rank == null) return 0;
        String r = rank.trim().toUpperCase();
        if (r.equals("A")) return 1;
        if (r.equals("J")) return 11;
        if (r.equals("Q")) return 12;
        if (r.equals("K")) return 13;
        try {
            return Integer.parseInt(r);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}