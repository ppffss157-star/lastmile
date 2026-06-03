package com.example.logistics.lastmile.aspect;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 自动记录 @LogExecution 标记的方法的入参、返回值和执行耗时。
 *
 * <h3>核心概念（面试高频！）</h3>
 * <table>
 *   <tr><td><b>Aspect（切面）</b></td>      <td>= 本类，横切关注点的模块化单元</td></tr>
 *   <tr><td><b>Advice（通知）</b></td>      <td>= @Around 标注的方法，定义"何时"和"如何"执行横切逻辑</td></tr>
 *   <tr><td><b>Pointcut（切点）</b></td>    <td>= @Pointcut 标注的方法，定义"何处"应用通知</td></tr>
 *   <tr><td><b>JoinPoint（连接点）</b></td> <td>= ProceedingJoinPoint，代表正在执行的被拦截方法</td></tr>
 *   <tr><td><b>Weaving（织入）</b></td>    <td>= Spring 运行时通过动态代理将切面逻辑插入目标对象</td></tr>
 * </table>
 *
 * <h3>代理陷阱（常见坑）</h3>
 * 同类内部调用不触发 AOP！因为 this.method() 直接调用实例方法，不走 Spring 代理。
 * <pre>{@code
 * // ❌ 不会触发 AOP
 * public void methodA() {
 *     this.methodB();  // this 是原始对象，不是代理
 * }
 * }</pre>
 */
@Aspect
@Component
public class LoggingAspect {

    /**
     * Pointcut：匹配所有标注了 @LogExecution 的方法。
     *
     * <p>两种常见写法：
     * <ul>
     *   <li>{@code @annotation(...)} — 按注解匹配（本类用的方式）</li>
     *   <li>{@code execution(* com.example..controller..*.*(..))} — 按包路径匹配，不需要注解</li>
     * </ul>
     */
    @Pointcut("@annotation(com.example.logistics.lastmile.aspect.LogExecution)")
    public void loggableMethods() {
        // 方法体留空，只用于承载 @Pointcut 注解
    }

    /**
     * @Around 通知：在目标方法执行前后都运行。
     *
     * <ul>
     *   <li>pjp.proceed() 之前 = 相当于 @Before</li>
     *   <li>pjp.proceed() 之后 = 相当于 @AfterReturning</li>
     *   <li>整个包围起来     = @Around（最灵活，能做计时）</li>
     * </ul>
     *
     * @param pjp 被拦截方法的运行时信息（类名、方法名、参数等）
     * @return 原方法的返回值，原样透传
     * @throws Throwable 原方法抛出的异常，原样抛出
     */
    @Around("loggableMethods()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        // 获取目标类的 Logger，这样日志输出时会显示正确的类名
        Logger log = LoggerFactory.getLogger(pjp.getTarget().getClass());

        String className = pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();
        Object[] args = pjp.getArgs();

        // === 方法进入时 ===
        log.info("==> {}.{}() 被调用，参数: {}", className, methodName, Arrays.toString(args));

        long start = System.currentTimeMillis();

        // 执行目标方法（没有这行，原方法就不会运行！）
        Object result = pjp.proceed();

        // === 方法返回后 ===
        long elapsed = System.currentTimeMillis() - start;
        log.info("<== {}.{}() 返回: {}, 耗时: {}ms", className, methodName, result, elapsed);

        // 原样返回，不修改返回值
        return result;
    }
}
