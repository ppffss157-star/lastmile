package com.example.logistics.lastmile.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.logistics.lastmile.entity.TransactionDemoEntity;
import com.example.logistics.lastmile.repository.TransactionDemoRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * <h1>事务传播行为演示</h1>
 *
 * <h2>核心问题</h2>
 * 方法 A 有事务，调了方法 B（也有事务）——B 是加入 A 的事务，还是自己开一个？
 * 答案由 {@code propagation} 参数决定。
 *
 * <h2>这 4 个场景覆盖了 90% 的面试题</h2>
 *
 * <table>
 *   <tr><th>场景</th><th>外层</th><th>内层</th><th>内层结果</th><th>外层结果</th></tr>
 *   <tr><td>1</td><td>REQUIRED</td><td>REQUIRED</td><td>失败→回滚</td><td style="color:red">跟着回滚</td></tr>
 *   <tr><td>2</td><td>REQUIRED</td><td>REQUIRED</td><td>失败但外层 try-catch</td><td style="color:red">UnexpectedRollbackException</td></tr>
 *   <tr><td>3</td><td>REQUIRED</td><td>REQUIRES_NEW</td><td>失败→回滚</td><td style="color:green">独立提交</td></tr>
 *   <tr><td>4</td><td>REQUIRED</td><td>REQUIRES_NEW</td><td>成功→提交</td><td style="color:red">外层失败，内层不受影响</td></tr>
 * </table>
 *
 * <h2>为什么用 {@code @Lazy @Autowired self}</h2>
 * 同一个类里方法互调不走 Spring AOP 代理，{@code @Transactional} 注解会失效。
 * 注入自己的代理（self），通过 {@code self.xxx()} 调用才能触发事务拦截器。
 * OrderService 里也是这个模式。
 */
@Slf4j
@Service
public class TransactionDemoService {

    private final TransactionDemoRepository repository;

    @Lazy
    @Autowired
    private TransactionDemoService self;

    public TransactionDemoService(TransactionDemoRepository repository) {
        this.repository = repository;
    }

    // ========================================================================
    // 场景 1：REQUIRED + REQUIRED，内层失败 → 全部回滚
    // ========================================================================

    /**
     * <h3>场景 1：默认传播——同生共死</h3>
     * 外层和内层在<b>同一个事务</b>里，内层抛异常 = 事务标记为 rollback-only，
     * 外层即使 try-catch 也救不回来（UnexpectedRollbackException）。
     *
     * <pre>
     * 外层 REQUIRED ──开始事务──────────────────┬── 想提交？不行！
     *                     │                      │
     * 内层 REQUIRED ──────┴──抛异常！────────────┘
     *                                          rollback-only 标记
     * </pre>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String scenario1_requiredRequired_innerFail() {
        repository.save(new TransactionDemoEntity("场景1-外层-应回滚"));
        log.info("场景1: 外层保存成功，准备调内层...");

        try {
            self.scenario1_innerRequired_fail();
        } catch (RuntimeException e) {
            log.warn("场景1: 外层 catch 了内层异常，但事务已标记 rollback-only: {}", e.getMessage());
        }

        // 这里不会真的提交——Spring 检测到 rollback-only 标记，抛 UnexpectedRollbackException
        return "外层记录应回滚（实际上会抛 UnexpectedRollbackException）";
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void scenario1_innerRequired_fail() {
        repository.save(new TransactionDemoEntity("场景1-内层-应回滚"));
        throw new RuntimeException("场景1: 内层故意抛异常");
    }

    // ========================================================================
    // 场景 2：REQUIRED + REQUIRES_NEW，内层失败 → 外层不受影响
    // ========================================================================

    /**
     * <h3>场景 2：内层挂起外层事务，自己开新的——内层死了外层还活着</h3>
     * REQUIRES_NEW 做两件事：
     * <ol>
     *   <li><b>挂起</b>当前事务（暂时放一边）</li>
     *   <li><b>新建</b>一个独立事务</li>
     * </ol>
     * 内层事务独立提交/回滚，和外层互不影响。
     *
     * <pre>
     * 外层 REQUIRED ──开始事务────────────────────提交 ✅
     *                     │
     * 内层 REQUIRES_NEW ──┴──独立事务──抛异常──回滚 ❌
     * </pre>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String scenario2_requiredRequiresNew_innerFail() {
        repository.save(new TransactionDemoEntity("场景2-外层-应提交"));
        log.info("场景2: 外层保存成功，准备调内层...");

        try {
            self.scenario2_innerRequiresNew_fail();
        } catch (RuntimeException e) {
            log.warn("场景2: 外层 catch 了内层异常，但这次外层事务是干净的: {}", e.getMessage());
        }
        // 外层正常提交！
        return "场景2: 外层提交成功 ✅，内层回滚 ❌";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scenario2_innerRequiresNew_fail() {
        repository.save(new TransactionDemoEntity("场景2-内层-应回滚"));
        throw new RuntimeException("场景2: 内层故意抛异常");
    }

    // ========================================================================
    // 场景 3：REQUIRES_NEW 内层成功，外层失败 → 内层不受影响
    // ========================================================================

    /**
     * <h3>场景 3：内层先独立提交，外层后来崩了——内层的数据还在</h3>
     * 这是 REQUIRES_NEW 最常用的场景：记账/日志等"无论如何都要落盘"的操作。
     *
     * <pre>
     * 外层 REQUIRED ──开始事务──调内层──────抛异常──回滚 ❌
     *                     │
     * 内层 REQUIRES_NEW ──┴──独立事务──提交 ✅（已经落盘，外层回滚不影响）
     * </pre>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String scenario3_outerFails_innerCommitted() {
        repository.save(new TransactionDemoEntity("场景3-外层-应回滚"));
        log.info("场景3: 外层保存成功，准备调内层...");

        // 内层独立提交
        self.scenario3_innerRequiresNew_commit();

        log.info("场景3: 内层已提交，现在外层故意抛异常...");
        throw new RuntimeException("场景3: 外层故意抛异常，但内层已经提交了");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scenario3_innerRequiresNew_commit() {
        repository.save(new TransactionDemoEntity("场景3-内层-应保留"));
        log.info("场景3: 内层保存成功（独立事务，即将提交）");
    }

    // ========================================================================
    // 场景 4：内层 REQUIRES_NEW 抛异常，外层 try-catch，外层正常提交
    // ========================================================================
    // 这个和场景 2 行为一样，这里不再重复。
    // 关键点：REQUIRES_NEW 内层抛异常不会污染外层事务的 rollback-only 标记。

    // ========================================================================
    // 场景 4（补充）：演示 rollback-only 标记的坑
    // ========================================================================

    /**
     * <h3>场景 4（补充）：rollback-only 标记——try-catch 也救不回来</h3>
     * Spring 的默认行为：事务里任何 RuntimeException 都会把事务标记为 rollback-only。
     * 外层 catch 了异常以为没事，提交时才发现已经标记回滚了 → UnexpectedRollbackException。
     *
     * <p><b>这是最常见的线上 bug：</b>日志里看到 "UnexpectedRollbackException"，</p>
     * 就是因为内层事务抛异常标记了 rollback-only，外层 try-catch 后还继续操作。
     *
     * <p><b>解决方案：</b></p>
     * <ul>
     *   <li>内层用 REQUIRES_NEW → 独立事务，不影响外层</li>
     *   <li>外层 @Transactional(noRollbackFor = ...) → 不让特定异常触发回滚</li>
     *   <li>内层用 NESTED → 回滚到 savepoint，外层继续</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String scenario4_rollbackOnlyTrap() {
        repository.save(new TransactionDemoEntity("场景4-外层-想提交但不行"));
        log.info("场景4: 外层保存成功，准备调内层...");

        try {
            self.scenario4_innerRequired_fail();
        } catch (RuntimeException e) {
            // 以为 catch 了就没事？太天真了 😅
            log.warn("场景4: 外层 catch 了异常，但 rollback-only 标记还在！提交时会炸");
            // 此时事务已被标记为 rollback-only
            // 等这个方法返回时，Spring 尝试提交 → UnexpectedRollbackException
        }

        return "场景4: 你以为会提交？实际上会抛 UnexpectedRollbackException！";
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void scenario4_innerRequired_fail() {
        repository.save(new TransactionDemoEntity("场景4-内层-会回滚"));
        throw new RuntimeException("场景4: 内层故意抛异常");
    }

    // ========================================================================
    // 辅助方法：查询和清理
    // ========================================================================

    @Transactional(readOnly = true)
    public long countAll() {
        return repository.count();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteAll() {
        repository.deleteAll();
        log.info("🗑 演示数据已清空");
    }
}
