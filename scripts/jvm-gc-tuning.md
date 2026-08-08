# JVM GC 调参实战

## 1. 启动参数（加到 IDE Run Configuration 的 VM Options）

```
-Xms256m -Xmx256m
-Xlog:gc*:file=logs/gc.log::filecount=5,filesize=10m
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=logs/
```

| 参数 | 干什么 |
|------|--------|
| `-Xms256m -Xmx256m` | 堆大小固定 256MB（小堆让 GC 更频繁触发，方便观察） |
| `-Xlog:gc*` | 输出所有 GC 日志到文件 |
| `filecount=5,filesize=10m` | 最多 5 个日志文件，每个 10MB，满了轮转 |
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM 时自动 dump 堆快照 |

## 2. 压测 + 观察流程

```bash
# 终端 1：反复调看线程池变化
while true; do
  curl -s http://localhost:9090/actuator/thread-pools | python3 -m json.tool
  sleep 1
done

# 终端 2：打压测
curl "http://localhost:9090/actuator/stress/async?count=50"   # 轻量：queue 积压，poolSize 不变
curl "http://localhost:9090/actuator/stress/async?count=150"  # 重量：poolSize 涨到 8，触发扩容
curl "http://localhost:9090/actuator/stress/async?count=200"  # 极限：CallerRunsPolicy 触发

# 触发 GC 后分析日志
curl "http://localhost:9090/actuator/stress/gc?objects=5000000"
```

## 3. GC 日志分析命令（PowerShell / Git Bash）

```bash
# 看前 20 条 GC 记录
grep "GC(" logs/gc.log | head -20

# 统计 GC 次数
grep -c "Pause Young" logs/gc.log   # Young GC 次数
grep -c "Pause Full" logs/gc.log    # Full GC 次数

# 看 GC 暂停时间
grep "Pause" logs/gc.log | grep -oP '\d+\.\d+ms' | head -20

# 看堆回收情况（回收前 → 回收后）
grep "GC(" logs/gc.log | head -10
```

## 4. GC 日志解读（一条真实日志拆开看）

```
[2026-07-27T10:30:15.123+0800] GC(42) Pause Young (Allocation Failure) 120M->45M(256M) 8.234ms
                                   ^^^^  ^^^^^^^^^^^  ^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^ ^^^^^^^
                                  序号   暂停原因     回收前→回收后(总)   耗时
```

| 字段 | 含义 | 面试怎么说 |
|------|------|-----------|
| `GC(42)` | 第 42 次 GC | "我压测跑了 5 分钟，Young GC 触发了 42 次" |
| `Allocation Failure` | 新生代没空间放新对象了 | "Eden 区满了，触发 Minor GC" |
| `120M->45M` | 回收前 120MB→回收后 45MB | "这次 GC 回收了 75MB，效果不错" |
| `(256M)` | 堆总共 256MB | "堆设了 256MB，压测够用" |
| `8.234ms` | 暂停 8 毫秒 | "平均暂停 10ms 以内，对接口影响可忽略" |

## 5. 线程池参数怎么定（面试高频）

**不是背数字，是讲过程：**

```
1. 先看业务类型：
   - CPU 密集型（加密、计算）→ coreSize ≈ CPU 核数
   - IO 密集型（数据库、网络）→ coreSize ≈ CPU 核数 × 2

2. 本项目是 IO 密集型（查 DB、调 Redis、发 WebSocket），
   所以 coreSize 设 4（8 核 CPU 的一半）

3. maxSize = coreSize × 2 = 8，给峰值留余量

4. queueCapacity = 100：
   - 太小 → 频繁创建临时线程
   - 太大 → 排队太久，请求超时
   - 100 是压测后调的：50 并发时 queue 积压 46 个，刚好

5. CallerRunsPolicy：
   - 宁可让调用者自己跑（慢但不丢），也不抛异常
   - 这是"降级"不是"崩溃"

6. 压测验证：150 并发时 poolSize 涨到 8，queue 打满 100，
   平均响应 2s，无任务丢弃 → 参数合理
```

## 6. OOM 排查流程（面试高频）

```
1. 看日志：有没有 java.lang.OutOfMemoryError: Java heap space
2. 有 HeapDump → 用 jvisualvm / Eclipse MAT 打开 .hprof 文件
3. 看"Biggest Objects"：哪个类占了最多内存
4. 看 GC Root 路径：谁在引用这些对象，为什么没释放
5. 常见原因：
   - 集合类不停 add 不清空（List<Order> 装了 100w 条）
   - 线程池队列无限增长（没用有界队列）
   - 缓存没设 TTL（Redis 有 TTL，但本地 Map 缓存没设）
```
