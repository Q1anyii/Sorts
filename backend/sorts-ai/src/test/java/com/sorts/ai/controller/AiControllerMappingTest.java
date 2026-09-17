package com.sorts.ai.controller;

import com.sorts.ai.dto.AIPlanRequest;
import com.sorts.ai.dto.AIPlanResponse;
import com.sorts.ai.dto.AIReportInfo;
import com.sorts.ai.dto.AISummaryRequest;
import com.sorts.ai.dto.ChatRequest;
import com.sorts.ai.dto.ChatResponse;
import com.sorts.ai.service.ChatService;
import com.sorts.ai.service.ConversationService;
import com.sorts.ai.service.PlanService;
import com.sorts.ai.service.ReportService;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.constant.AuthConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 控制器「JSON / SSE 双通道」的映射回归测试。
 *
 * <p>历史缺陷：SSE 方法原先写 {@code params = "!stream=false"}，在 {@code stream} 参数
 * **完全缺省**时匹配不上，直接抛 {@code UnsatisfiedServletRequestParameterException}（500）。
 * 该场景在单测里很难被发现（MockMvc 直接调方法会绕过映射判定），因此这里用 standaloneSetup
 * 真正走一遍 {@code RequestMappingHandlerMapping} 的分派逻辑。</p>
 *
 * @author sorts
 */
class AiControllerMappingTest {

    private final ChatService chatService = mock(ChatService.class);
    private final PlanService planService = mock(PlanService.class);
    private final ReportService reportService = mock(ReportService.class);
    private final ConversationService conversationService = mock(ConversationService.class);
    private final ToolJsonCodec jsonCodec = new ToolJsonCodec();

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // 同步执行器：让 SSE 的异步任务在断言前跑完，避免用例偶发
        Executor sameThread = Runnable::run;
        AiController controller =
                new AiController(chatService, planService, reportService, conversationService, jsonCodec, sameThread);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(chatService.chat(any(), any(), any())).thenReturn(mock(ChatResponse.class));
        when(planService.generate(any(), any(), anyBoolean(), any())).thenReturn(mock(AIPlanResponse.class));
        when(reportService.generateDaily(any(), any(), any())).thenReturn(mock(AIReportInfo.class));
    }

    @Test
    @DisplayName("/chat 不带 stream 参数：走 SSE")
    void chatWithoutParamShouldBeSse() throws Exception {
        mvc.perform(post("/api/v1/ai/chat")
                        .header(AuthConstants.HEADER_USER_ID, "1")
                        .contentType("application/json")
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("/chat?stream=false：走 JSON")
    void chatWithStreamFalseShouldBeJson() throws Exception {
        mvc.perform(post("/api/v1/ai/chat")
                        .param("stream", "false")
                        .header(AuthConstants.HEADER_USER_ID, "1")
                        .contentType("application/json")
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncNotStarted())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("/chat?stream=true：仍走 SSE")
    void chatWithStreamTrueShouldBeSse() throws Exception {
        mvc.perform(post("/api/v1/ai/chat")
                        .param("stream", "true")
                        .header(AuthConstants.HEADER_USER_ID, "1")
                        .contentType("application/json")
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("/plan 不带 stream 参数：走 JSON（与 /chat 默认相反）")
    void planWithoutParamShouldBeJson() throws Exception {
        mvc.perform(post("/api/v1/ai/plan")
                        .header(AuthConstants.HEADER_USER_ID, "1")
                        .contentType("application/json")
                        .content("{\"userPrompt\":\"明天上午写周报\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncNotStarted())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("/plan?stream=true：走 SSE")
    void planWithStreamTrueShouldBeSse() throws Exception {
        mvc.perform(post("/api/v1/ai/plan")
                        .param("stream", "true")
                        .header(AuthConstants.HEADER_USER_ID, "1")
                        .contentType("application/json")
                        .content("{\"userPrompt\":\"明天上午写周报\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("/summary/daily 不带 stream 参数：走 SSE")
    void dailySummaryWithoutParamShouldBeSse() throws Exception {
        mvc.perform(post("/api/v1/ai/summary/daily")
                        .header(AuthConstants.HEADER_USER_ID, "1")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    @DisplayName("/summary/daily?stream=false：走 JSON")
    void dailySummaryWithStreamFalseShouldBeJson() throws Exception {
        mvc.perform(post("/api/v1/ai/summary/daily")
                        .param("stream", "false")
                        .header(AuthConstants.HEADER_USER_ID, "1")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncNotStarted())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("规划生成默认走非流式：stream 标志为 false")
    void planDefaultPassesStreamFalse() throws Exception {
        mvc.perform(post("/api/v1/ai/plan")
                        .header(AuthConstants.HEADER_USER_ID, "1")
                        .contentType("application/json")
                        .content("{\"userPrompt\":\"安排三天复习计划\"}"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(planService).generate(any(), any(AIPlanRequest.class), eq(false), any());
    }
}
