# CLAUDE.md — 项目级

## 环境速查

| 事项 | 详情 |
|------|------|
| JDK | `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot` |
| MySQL | Windows 服务 `MySQL80`，`root/123456`，库 `logistics_db`，端口 3306 |
| Redis | WSL Ubuntu 里，启动：`wsl -d Ubuntu -- bash -c "redis-server --daemonize yes"`，端口 6379 |
| Maven | `./mvnw` |

### MySQL 启动

先检查再启动：
```bash
sc query MySQL80 | grep STATE   # RUNNING = 已启动
```

提权启动：
```bash
/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -Command "Start-Process -Verb RunAs -FilePath 'sc.exe' -ArgumentList 'start','MySQL80'"
```

## 技术栈

Spring Boot 4.0.5 + JPA + MySQL + Redis + JWT + WebSocket + AOP + Docker

## 行为准则

### 决定 ≠ 执行
用户确认选项、做出选择、说"这三个"等，只表示结论已定，**不代表授权动手**。必须等用户明确说"搞""搬""改""可以执行"之类指令后再操作。禁止替用户跳过这一步。

### 改代码和验证是两件事，用不同脑子

改造类任务天然分两个阶段，搞混了就会在原地打转：

- **改代码阶段**：目标是让代码正确。加依赖、改配置、编译通过。这阶段动的是文件。
- **验证阶段**：目标是让系统跑起来。代码已经对了，别再回头改它。这阶段动的是进程。

验证阶段最容易犯的错——服务起不来就回头改代码："再加个依赖试试""换个版本号看看"。这不是修 bug，是在增加变量。正确做法是盯日志第一行报错，定位到具体组件、修完继续往下走，不跳步不绕路。

验证阶段的另一个坏习惯是**并行操作**——同时起多个服务、来回换启动方式、一把全杀重来——每次都引入多个新变量，排查无从下手。串行启动、每次只动一个东西、确认结果再下一步，反而更快。
