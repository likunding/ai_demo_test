package com.dezhou.springai.controller;

import com.dezhou.springai.config.AiConfig;
import com.dezhou.springai.dto.ApiResponse;
import com.dezhou.springai.service.CardRecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/card")
@RequiredArgsConstructor
public class CardRecognitionController {

    private final CardRecognitionService cardRecognitionService;
    private final AiConfig aiConfig;

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

}