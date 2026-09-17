package com.sorts.ai.controller;

import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.config.AsyncConfig;
import com.sorts.ai.dto.AIPlanRequest;
import com.sorts.ai.dto.AIPlanResponse;
import com.sorts.ai.dto.AIReportInfo;
import com.sorts.ai.dto.AISummaryRequest;
import com.sorts.ai.dto.AdoptPlanRequest;
import com.sorts.ai.dto.AsyncReportResponse;
import com.sorts.ai.dto.BatchDeleteRequest;
import com.sorts.ai.dto.ChatRequest;
import com.sorts.ai.dto.ChatResponse;
import com.sorts.ai.dto.ConversationSaveRequest;
import com.sorts.ai.dto.ConversationVO;
import com.sorts.ai.dto.PeriodSummaryRequest;
import com.sorts.ai.service.ChatService;
import com.sorts.ai.service.ConversationService;
import com.sorts.ai.service.PlanService;
import com.sorts.ai.service.ReportService;
import com.sorts.ai.support.SseStream;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.constant.AuthConstants;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.PageData;
import com.sorts.common.result.Result;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * AI 助手接口（api-spec.json 的 AI助手 分组）。
 *
 * <h3>关于「同一路径同时支持 JSON 与 SSE」</h3>
 * <p>api-spec 中 /ai/chat、/ai/plan、/ai/summary/daily 都有 {@code stream} 开关，
 * 流式返回 {@code text/event-stream}、非流式返回 JSON。但 Spring MVC 是按
 * <b>方法声明的返回类型</b>挑选返回值处理器的：若把返回类型写成 {@code Object}，
 * {@code SseEmitter} 就不会被 SSE 处理器接管，而是被当成普通对象交给 Jackson 序列化
 * （表现为前端拿到一堆字段却收不到事件流）。</p>
 *
 * <p>因此这里用 {@code params} 条件把两种响应拆成两个处理方法，且默认走流式：</p>
 * <ul>
 *   <li>{@code params = "!stream=false"} —— 未传 stream 或 stream≠false → SSE（符合 api-spec 默认 true）</li>
 *   <li>{@code params = "stream=false"} —— 显式关闭 → JSON</li>
 * </ul>
 * <p>注意：<b>传输方式由 query 参数决定</b>；请求体里的 stream 字段为契约兼容保留，不参与路由。</p>
 *
 * @author sorts
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    /** SSE 连接超时：给足模型生成时间，同时避免连接被无限占用 */
    private static final long SSE_TIMEOUT_MS = 180_000L;

    private final ChatService chatService;

    private final PlanService planService;

    private final ReportService reportService;

    private final ConversationService conversationService;

    private final ToolJsonCodec jsonCodec;

    private final Executor aiTaskExecutor;

    public AiController(ChatService chatService,
                        PlanService planService,
                        ReportService reportService,
                        ConversationService conversationService,
                        ToolJsonCodec jsonCodec,
                        @Qualifier(AsyncConfig.AI_EXECUTOR) Executor aiTaskExecutor) {
        this.chatService = chatService;
        this.planService = planService;
        this.reportService = reportService;
        this.conversationService = conversationService;
        this.jsonCodec = jsonCodec;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    // ==================== 对话助手 ====================

    /**
     * 流式对话（默认）。
     *
     * <p>刻意**不写** {@code params}：早期这里写的是 {@code !stream=false}，实测在
     * {@code stream} 参数「完全缺省」时匹配不上（Spring 的参数否定表达式对缺省值不成立），
     * 不带参调用会直接 500（UnsatisfiedServletRequestParameterException）。
     * 现在改成「SSE 无 params 条件 = 兜底；JSON 带 {@code stream=false} = 更具体」，
     * 两者同时匹配时 Spring 按「params 表达式更多者更优先」选中 JSON，语义稳定。</p>
     */
    @PostMapping("/chat")
    public SseEmitter chatStream(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                 @Valid @RequestBody ChatRequest request) {
        return stream(out -> out.done(chatService.chat(userId, request, out::delta)));
    }

    /** 非流式对话 */
    @PostMapping(value = "/chat", params = "stream=false")
    public Result<ChatResponse> chat(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                     @Valid @RequestBody ChatRequest request) {
        return Result.success(chatService.chat(userId, request, null));
    }

    // ==================== 日程规划 ====================

    /**
     * 非流式生成规划（默认）。
     *
     * <p>与 /chat 相反：api-spec 中 {@code stream} 默认值是 false，因此「不传 stream」走 JSON。
     * 同样不使用 {@code !stream=true}，理由见 {@link #chatStream} 的说明。</p>
     */
    @PostMapping("/plan")
    public Result<AIPlanResponse> plan(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                       @Valid @RequestBody AIPlanRequest request) {
        return Result.success(planService.generate(userId, request, false, null));
    }

    /** 流式生成规划 */
    @PostMapping(value = "/plan", params = "stream=true")
    public SseEmitter planStream(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                 @Valid @RequestBody AIPlanRequest request) {
        return stream(out -> out.done(planService.generate(userId, request, true, out::delta)));
    }

    /** 采纳规划为真实日程 */
    @PostMapping("/plan/{planId}/adopt")
    public Result<List<ScheduleDto>> adopt(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                           @PathVariable("planId") String planId,
                                           @Valid @RequestBody AdoptPlanRequest request) {
        // 路径变量为准：避免路径与请求体不一致时出现「采纳了另一个规划」这种事故
        request.setPlanId(planId);
        return Result.success(planService.adopt(userId, request));
    }

    // ==================== 周期总结 ====================

    /** 流式生成每日总结（默认）。同样不使用 {@code !stream=false}，理由见 {@link #chatStream} */
    @PostMapping("/summary/daily")
    public SseEmitter dailyStream(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                  @Valid @RequestBody(required = false) AISummaryRequest request) {
        AISummaryRequest payload = request == null ? new AISummaryRequest() : request;
        return stream(out -> out.done(reportService.generateDaily(userId, payload, out::delta)));
    }

    /** 非流式生成每日总结 */
    @PostMapping(value = "/summary/daily", params = "stream=false")
    public Result<AIReportInfo> daily(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                      @Valid @RequestBody(required = false) AISummaryRequest request) {
        return Result.success(reportService.generateDaily(userId, request == null ? new AISummaryRequest() : request, null));
    }

    /** 异步生成月度总结：立即返回 reportId，前端轮询 /reports/{id} */
    @PostMapping("/summary/monthly")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Result<AsyncReportResponse> monthly(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                               @RequestBody(required = false) PeriodSummaryRequest request) {
        return Result.success(reportService.generateMonthly(userId, request));
    }

    /** 异步生成年度总结 */
    @PostMapping("/summary/yearly")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Result<AsyncReportResponse> yearly(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                              @RequestBody(required = false) PeriodSummaryRequest request) {
        return Result.success(reportService.generateYearly(userId, request));
    }

    // ==================== 报告 ====================

    @GetMapping("/reports")
    public Result<PageData<AIReportInfo>> reports(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                                  @RequestParam(value = "type", required = false) String type,
                                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                                  @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return Result.success(reportService.list(userId, type, page, pageSize));
    }

    @GetMapping("/reports/{id}")
    public Result<AIReportInfo> report(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                       @PathVariable("id") Long id) {
        return Result.success(reportService.get(userId, id));
    }

    /** 删除指定织史（软删除 + 审计时间） */
    @DeleteMapping("/reports/{id}")
    public Result<Void> deleteReport(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                     @PathVariable("id") Long id) {
        reportService.delete(userId, id);
        return Result.success();
    }

    /** 批量删除织史：事务内全部成功或全部失败 */
    @PostMapping("/reports/batch-delete")
    public Result<Void> deleteReportsBatch(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                           @Valid @RequestBody BatchDeleteRequest request) {
        reportService.deleteBatch(userId, request.getIds());
        return Result.success();
    }

    // ==================== 会话持久化 ====================

    @GetMapping("/conversations")
    public Result<PageData<ConversationVO>> conversations(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                                          @RequestParam(value = "page", defaultValue = "1") int page,
                                                          @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return Result.success(conversationService.list(userId, page, pageSize));
    }

    @PostMapping("/conversations")
    public Result<ConversationVO> createConversation(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                                     @Valid @RequestBody(required = false) ConversationSaveRequest request) {
        return Result.success(conversationService.create(userId, request == null ? null : request.getTitle()));
    }

    @GetMapping("/conversations/{id}")
    public Result<ConversationVO> conversation(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                               @PathVariable("id") Long id) {
        return Result.success(conversationService.get(userId, id));
    }

    /** 全量快照保存：标题 / 消息 / 草稿 / 勾选项 / 最近生成区间 */
    @PutMapping("/conversations/{id}")
    public Result<ConversationVO> saveConversation(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                                   @PathVariable("id") Long id,
                                                   @Valid @RequestBody ConversationSaveRequest request) {
        return Result.success(conversationService.update(userId, id, request));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                           @PathVariable("id") Long id) {
        conversationService.delete(userId, id);
        return Result.success();
    }

    /** 批量删除会话 */
    @PostMapping("/conversations/batch-delete")
    public Result<Void> deleteConversationsBatch(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                                 @Valid @RequestBody BatchDeleteRequest request) {
        conversationService.deleteBatch(userId, request.getIds());
        return Result.success();
    }

    /**
     * 在独立线程里跑阻塞式生成，并把结果推成 SSE 事件。
     *
     * <p>必须切换线程：SSE 要求请求线程尽快返回 emitter，后续由 Spring 异步派发；
     * 若在请求线程里同步调用模型，就等于把连接超时时间当成了模型超时时间。</p>
     */
    private SseEmitter stream(Consumer<SseStream> work) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        SseStream out = new SseStream(emitter, jsonCodec);
        aiTaskExecutor.execute(() -> {
            try {
                work.accept(out);
            } catch (BizException e) {
                log.warn("AI 流式生成业务失败：{}", e.getMessage());
                out.error(e.getCode(), e.getMessage());
            } catch (Exception e) {
                log.error("AI 流式生成异常", e);
                out.error(500, "生成过程中出现异常，请稍后重试");
            }
        });
        return emitter;
    }
}
