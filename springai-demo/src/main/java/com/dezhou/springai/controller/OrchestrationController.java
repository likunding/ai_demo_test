package com.dezhou.springai.controller;

import com.dezhou.springai.config.AiConfig;
import com.dezhou.springai.dto.ApiResponse;
import com.dezhou.springai.service.PlannerService;
import com.dezhou.springai.service.WorkflowService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orchestration")
@RequiredArgsConstructor
public class OrchestrationController {

    private final WorkflowService workflowService;
    private final PlannerService plannerService;
    private final AiConfig aiConfig;

    @PostMapping("/workflow/code-fix")
    public ApiResponse<WorkflowService.WorkflowResult> codeFix(@RequestBody CodeFixRequest req) {
        return ApiResponse.ok(
                workflowService.runCodeFixWorkflow(req.getRequirement(), req.getLanguage()),
                aiConfig.getProvider()
        );
    }

    @PostMapping("/planner/split")
    public ApiResponse<PlannerService.PlanResult> splitPlan(@RequestBody PlannerRequest req) {
        return ApiResponse.ok(
                plannerService.splitPlan(req.getTask(), req.getLanguage()),
                aiConfig.getProvider()
        );
    }

    @PostMapping("/planner/run")
    public ApiResponse<PlannerService.PlannerExecutionResult> runPlanner(@RequestBody PlannerRequest req) {
        return ApiResponse.ok(
                plannerService.runPlanner(req.getTask(), req.getLanguage()),
                aiConfig.getProvider()
        );
    }

    @Data
    public static class CodeFixRequest {
        @NotBlank
        private String requirement = "实现用户分页查询接口";
        private String language = "Python";
    }

    @Data
    public static class PlannerRequest {
        @NotBlank
        private String task = """
                实现一个 Redis 分布式锁工具类；
                之后为这份代码生成单元测试；
                最后生成接口使用文档。
                """;
        private String language = "Python";
    }

}
