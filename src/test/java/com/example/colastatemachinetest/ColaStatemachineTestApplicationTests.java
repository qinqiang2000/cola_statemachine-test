package com.example.colastatemachinetest;

import com.alibaba.cola.statemachine.Action;
import com.alibaba.cola.statemachine.Condition;
import com.alibaba.cola.statemachine.StateMachine;
import com.alibaba.cola.statemachine.builder.AlertFailCallback;
import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.PostConstruct;

import static com.example.colastatemachinetest.ColaStatemachineTestApplicationTests.States.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.example.colastatemachinetest.ColaStatemachineTestApplicationTests.Events.*;

@SpringBootTest
class ColaStatemachineTestApplicationTests {
    static String MACHINE_ID = "RPA_OPEN_FSM";

    static enum States {
        // 初始状态
        INIT,
        // 待入库建单
        WAIT_CREATE,
        // 已入库，待入队列
        WAIT_ENQUEUE,
        // 已入队列，待被消费
        WAIT_DEQUEUE,
        // 已消费，待开票结果返回
        WAIT_RESULT,
        // 开票成功
        OPEN_SUCCESS,
        // 开票失败
        OPEN_FAIL
    }

    static enum Events {
        EVENT_RECV_DATA,
        EVENT_CREATE,
        EVENT_ENQUEUE,
        EVENT_DEQUEUE,
        EVENT_RESULT_SUCCESS,
        EVENT_RESULT_FAIL
    }

    // 模拟预开票单对象
    static class Context {
        String serialNo = "123456789";
        String sellerName = "票据云";
    }

    private StateMachine<States, Events, Context> stateMachine = null;

    public void init(){
        StateMachineBuilder<States, Events, Context> builder = StateMachineBuilderFactory.create();
        // 初始化--->待建单
        builder.externalTransition()
            .from(INIT).to(WAIT_CREATE).on(EVENT_RECV_DATA).perform(doVeriryAction());
        // 待建单--->待入队列
        builder.externalTransition()
            .from(WAIT_CREATE).to(WAIT_ENQUEUE).on(EVENT_CREATE).perform(doCreateAction());
        // 待入队列--->待消费
        builder.externalTransition()
            .from(WAIT_ENQUEUE).to(WAIT_DEQUEUE).on(EVENT_ENQUEUE).perform(doEnqueueAction());
        // 待消费--->已消费，待返回结果
        builder.externalTransition()
            .from(WAIT_DEQUEUE).to(WAIT_RESULT).on(EVENT_DEQUEUE).perform(doRequerstAction());
        // 已消费，待返回结果--->开票成功
        builder.externalTransition()
            .from(WAIT_RESULT).to(OPEN_SUCCESS).on(EVENT_RESULT_SUCCESS).perform(doOpenSuccessAction());
        // 已消费，待返回结果--->开票失败
        builder.externalTransition()
            .from(WAIT_RESULT).to(OPEN_FAIL).on(EVENT_RESULT_FAIL).perform(doRequerstAction());

        // 状态转移失败，抛出异常
        builder.setFailCallback(new AlertFailCallback<>());

        this.stateMachine = builder.build("async_open_machine");
        // 生成PlantUML并保存到文件
        try {
            String plantUML = this.stateMachine.generatePlantUML();
            Files.write(Paths.get("state.puml"), plantUML.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testNormal() {
        States target;
        Context ctx = new Context();

        // 收到数据，进行校验
        target = stateMachine.fireEvent(INIT, EVENT_RECV_DATA, ctx);
        Assertions.assertEquals(target, WAIT_CREATE);

        // 入库，准备入队列
        target = stateMachine.fireEvent(WAIT_CREATE, EVENT_CREATE, ctx);
        Assertions.assertEquals(target, WAIT_ENQUEUE);

        // 入队列，准备消费
        target = stateMachine.fireEvent(WAIT_ENQUEUE, EVENT_ENQUEUE, ctx);
        Assertions.assertEquals(target, WAIT_DEQUEUE);

        // 消费，发送请求，并等待返回结果
        target = stateMachine.fireEvent(WAIT_DEQUEUE, EVENT_DEQUEUE, ctx);
        Assertions.assertEquals(target, WAIT_RESULT);

        // 返回成功结果，开票成功
        target = stateMachine.fireEvent(WAIT_RESULT, EVENT_RESULT_SUCCESS, ctx);
        Assertions.assertEquals(target, OPEN_SUCCESS);
    }

    @Test
    void testFail() {
        States target = stateMachine.fireEvent(WAIT_ENQUEUE, EVENT_RECV_DATA, new Context());
        Assertions.assertEquals(target, WAIT_CREATE);
    }

    @BeforeEach
    void setUp() {
        init();
    }

    private Condition<Context> checkCondition() {
        return (ctx) -> true;
    }

    //  开票成功处理
    private Action<States, Events, Context> doOpenSuccessAction() {
        return (from, to, event, ctx) -> {
            System.out.println(ctx.serialNo + " state:" + " from:" + from + " to:" + to + " on:" + event);
        };
    }
    //  开票失败处理
    private Action<States, Events, Context> doOpenFailAction() {
        return (from, to, event, ctx) -> {
            System.out.println(ctx.serialNo + " state:" + " from:" + from + " to:" + to + " on:" + event);
        };
    }

    //  请求RPA客户端
    private Action<States, Events, Context> doRequerstAction() {
        return (from, to, event, ctx) -> {
            System.out.println(ctx.serialNo + " state:" + " from:" + from + " to:" + to + " on:" + event);
        };
    }

    //  入队列并更新状态
    private Action<States, Events, Context> doEnqueueAction() {
        return (from, to, event, ctx) -> {
            System.out.println(ctx.serialNo + " state:" + " from:" + from + " to:" + to + " on:" + event);
        };
    }

    // 创单，存入数据库
    private Action<States, Events, Context> doCreateAction() {
        return (from, to, event, ctx) -> {
            System.out.println("保存入库:" + ctx.serialNo + " " + ctx.sellerName);
            System.out.println(ctx.serialNo + " state:" + " from:" + from + " to:" + to + " on:" + event);
        };
    }

    // 校验收到请求
    private Action<States, Events, Context> doVeriryAction() {
        return (from, to, event, ctx) -> {
            System.out.println("校验收到请求:" + ctx.serialNo);
            System.out.println(ctx.serialNo + " state:" + " from:" + from + " to:" + to + " on:" + event);
        };
    }
}
