package com.example.campusrepair.mq;

import com.example.campusrepair.service.DispatchService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 工单消息队列（进程内轻量级 MQ，生产者-消费者模型）
 *
 * @Description 用 JDK BlockingQueue 实现异步解耦与削峰：学生提交工单后，
 *              生产者只往队列发一条消息就立即返回（用户零等待），
 *              后台消费线程从队列取消息，慢慢调用调度 Agent 完成自动派单。
 *              AI 识别模块按同样模式接入：发 ORDER_AI_RECOGNIZE 消息即可，
 *              与派单消费者互不干扰。
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Slf4j
@Component
public class OrderMessageQueue {

    /** 队列容量：有界队列，超出时拒绝生产者，防止内存被压垮（削峰保护） */
    private static final int CAPACITY = 1024;
    /** 派单失败最多重试次数，超过则落日志人工介入，避免无限循环 */
    private static final int MAX_RETRY = 3;

    private final BlockingQueue<Task> queue = new ArrayBlockingQueue<>(CAPACITY);
    private final DispatchService dispatchService;

    /** 消费线程轮询超时（秒）：take() 阻塞，interrupt 触发优雅停机 */
    @Value("${mq.consumer-poll-seconds:600}")
    private long pollSeconds;

    public OrderMessageQueue(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    private volatile boolean running = true;
    private Thread consumer;

    // ------------------------------------------------------------------
    // 生产者 API：任何业务方（工单提交 / AI 识别完成等）调用后立即返回
    // ------------------------------------------------------------------

    /**
     * 发送消息。队列满时抛出拒绝异常（有界队列背压）。
     *
     * @param message 工单领域消息
     * @return 是否投递成功
     */
    public boolean send(OrderMessage message) {
        Task task = new Task(message, 0);
        boolean offered = queue.offer(task);
        if (offered) {
            log.info("[MQ] 生产者投递消息 type={} orderId={}", message.type(), message.orderId());
        } else {
            log.warn("[MQ] 队列已满，消息被拒绝 type={} orderId={}", message.type(), message.orderId());
        }
        return offered;
    }

    /** 当前积压消息数（可用于监控/答辩演示） */
    public int backlog() {
        return queue.size();
    }

    // ------------------------------------------------------------------
    // 消费者：后台单线程慢慢处理，业务高峰时队列起到削峰作用
    // ------------------------------------------------------------------

    @PostConstruct
    public void start() {
        consumer = new Thread(this::consume, "order-mq-consumer");
        consumer.setDaemon(true);
        consumer.start();
        log.info("[MQ] 消息队列已启动（容量={}，消费者线程={}）", CAPACITY, consumer.getName());
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (consumer != null) {
            consumer.interrupt();
        }
        log.info("[MQ] 消息队列已停机");
    }

    private void consume() {
        while (running) {
            try {
                Task task = queue.poll(pollSeconds, TimeUnit.SECONDS);
                if (task == null) {
                    continue;
                }
                handle(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 消费者线程绝不能被单条消息拖死
                log.error("[MQ] 消费者处理异常", e);
            }
        }
    }

    /** 分发消息到对应业务处理器，失败按次数重试 */
    private void handle(Task task) {
        OrderMessage msg = task.message;
        try {
            switch (msg.type()) {
                case OrderMessage.ORDER_SUBMITTED -> dispatchService.autoAssign(msg.orderId());
                default -> log.warn("[MQ] 未知消息类型 {}", msg.type());
            }
            log.info("[MQ] 消息处理完成 type={} orderId={}", msg.type(), msg.orderId());
        } catch (Exception e) {
            log.warn("[MQ] 消息处理失败(第{}次) type={} orderId={} reason={}",
                    task.retry + 1, msg.type(), msg.orderId(), e.getMessage());
            if (task.retry + 1 < MAX_RETRY) {
                queue.offer(new Task(msg, task.retry + 1));
            } else {
                log.error("[MQ] 消息重试耗尽，转人工处理 type={} orderId={}", msg.type(), msg.orderId());
            }
        }
    }

    /** 带重试计数的内部任务 */
    private record Task(OrderMessage message, int retry) {
    }
}
