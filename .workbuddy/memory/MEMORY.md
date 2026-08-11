# http-raw-log 项目记忆

## 项目定位
- Spring Boot Starter jar lib（com.skycong:http-raw-log），打印 HTTP 请求原始报文日志。
- 模块：core（核心 Filter/API/Wrapper）、log-record（AOP 业务日志，独立模块）、redis-web-admin（Web 化 Redis 管理，高危需独立评估，不在默认审查范围）。
- **约定**：本项目作为 jar lib，鉴权/授权由使用方负责（如 Spring Security）。安全审查时，端点「未授权访问」不列为本项目漏洞（如 /httpRawLog/history、/httpRawLog/statistics 的鉴权归使用方）。审查聚焦非鉴权类真实代码缺陷。

## 安全审查结论（core，2026-08-10，已剔除鉴权项）
- 真实缺陷：① `RequestWrapper` 缓存请求体到内存无大小限制（对比 `ResponseWrapper` 有 1MB 限制），大 body 可 OOM/DoS；② `FileAppender.readNewContent` 每新行全量遍历 `HISTORIES`（嵌套 O(n*m)）+ 单线程扫大日志文件，资源滥用面；③ `StatisticsApi` 静态 `map` 按 URL 无上限缓慢增长。
- 设计建议：默认记录 body/header 未脱敏（cookie/token/password 明文），建议库提供脱敏钩子；`FormRequestWrapper` 反射 Tomcat 内部 `ApplicationPart.fileItem` 强耦合且循环内 `clearCache()`。
- 亮点：v0.9.15 已修复竞态（volatile+compute）、ResponseWrapper 1MB OOM 限制、WeakReference 误用等。
