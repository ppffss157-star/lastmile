package com.example.logistics.lastmile.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.logistics.lastmile.common.Result;
import com.example.logistics.lastmile.service.TransactionDemoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 事务传播行为演示——4 个场景覆盖 90% 面试题。
 * <p>
 * 所有接口都是 GET，方便浏览器直接点。
 */
@RestController
@RequestMapping("/tx-demo")
@Tag(name = "事务传播演示", description = "演示 REQUIRED / REQUIRES_NEW / rollback-only 四种场景")
public class TransactionDemoController {

    private final TransactionDemoService service;

    public TransactionDemoController(TransactionDemoService service) {
        this.service = service;
    }

    @Operation(summary = "场景1：同生共死", description = "外层 REQUIRED + 内层 REQUIRED，内层抛异常 → 全部回滚，外层 catch 也救不回来（UnexpectedRollbackException）")
    @GetMapping("/scenario1")
    public Result<String> scenario1() {
        try {
            String msg = service.scenario1_requiredRequired_innerFail();
            return Result.success(msg);
        } catch (Exception e) {
            return Result.success("场景1 ✅ 嵌套验证通过：外层也被迫回滚 → " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Operation(summary = "场景2：内层死外层活", description = "外层 REQUIRED + 内层 REQUIRES_NEW，内层抛异常 → 内层回滚，外层正常提交")
    @GetMapping("/scenario2")
    public Result<String> scenario2() {
        String msg = service.scenario2_requiredRequiresNew_innerFail();
        return Result.success(msg);
    }

    @Operation(summary = "场景3：内层活外层死", description = "内层 REQUIRES_NEW 先独立提交，外层后来抛异常 → 内层数据保留，外层回滚")
    @GetMapping("/scenario3")
    public Result<String> scenario3() {
        try {
            service.scenario3_outerFails_innerCommitted();
        } catch (RuntimeException e) {
            return Result.success("场景3 ✅: 外层回滚 ❌，但内层数据已落盘 ✅ → " + e.getMessage());
        }
        return Result.success("场景3");
    }

    @Operation(summary = "场景4：rollback-only 陷阱", description = "内层抛异常标记 rollback-only，外层 catch 以为没事，提交时炸 UnexpectedRollbackException")
    @GetMapping("/scenario4")
    public Result<String> scenario4() {
        try {
            service.scenario4_rollbackOnlyTrap();
            return Result.success("场景4: 外层竟然提交成功了？！（不应该走到这里）");
        } catch (Exception e) {
            return Result.success("场景4 ✅: rollback-only 陷阱生效 → " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Operation(summary = "查看演示数据", description = "查看 tx_demo 表当前有多少条记录")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(service.countAll());
    }

    @Operation(summary = "清空演示数据", description = "删除 tx_demo 表全部记录，方便下一轮演示")
    @DeleteMapping("/all")
    public Result<String> deleteAll() {
        service.deleteAll();
        return Result.success("演示数据已清空");
    }
}
