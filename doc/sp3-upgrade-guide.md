# http-raw-log SP3 升级指南

> 基于 Spring Boot 3.x 和 Java 17 的重大版本升级

## 版本说明

| 版本 | Spring Boot | Java | 分支 | 状态 |
|------|------------|------|------|------|
| 0.9.x | 2.x | 8+ | master | 维护模式 |
| 1.0.0+ | 3.x | 17+ | sp3 | **新特性开发** |

> 两个版本将**并行维护一段时间**，旧版本仅修复安全问题，新版本将获得所有新功能。

---

## 升级到 1.0.0

### 最低要求

- **Java 17** 或更高版本
- **Spring Boot 3.0** 或更高版本

### 迁移步骤

#### 1. 更新依赖坐标

```xml
<!-- 旧版本 -->
<dependency>
    <groupId>com.skycong</groupId>
    <artifactId>http-raw-log</artifactId>
    <version>0.9.16</version>
</dependency>

<!-- 新版本 -->
<dependency>
    <groupId>com.skycong</groupId>
    <artifactId>http-raw-log</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 2. Servlet 命名空间迁移

Spring Boot 3 基于 Jakarta EE 9+，所有 `javax.servlet` 已迁移为 `jakarta.servlet`。

如果你的项目中有自定义过滤器或直接引用了 servlet API，需要批量替换：

```bash
# IDE 全局替换
javax.servlet → jakarta.servlet
```

**主要影响的包：**

- `javax.servlet.http.HttpServletRequest` → `jakarta.servlet.http.HttpServletRequest`
- `javax.servlet.http.HttpServletResponse` → `jakarta.servlet.http.HttpServletResponse`
- `javax.servlet.Filter` → `jakarta.servlet.Filter`
- `javax.servlet.ReadListener` → `jakarta.servlet.ReadListener`
- `javax.servlet.WriteListener` → `jakarta.servlet.WriteListener`
- `javax.servlet.ServletInputStream` → `jakarta.servlet.ServletInputStream`

#### 3. 自动配置迁移

Spring Boot 3 移除了 `spring.factories` 自动配置，改用 `AutoConfiguration.imports`。

**如果你通过 `spring.autoconfigure.exclude` 排除了本库的自动配置，需要检查是否生效。**

#### 4. 验证接入

启动服务后，控制台无异常即为迁移成功：

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
```

---

## v1.0.0 变更日志

### 🚀 新特性

- **全面升级到 Spring Boot 3.2.5**
- **Java 版本要求提升至 17**
- 迁移到 Jakarta EE 9+（`javax.servlet` → `jakarta.servlet`）
- 更新自动配置机制以适配 Spring Boot 3

### 🔧 改进

- 更新 Gradle Wrapper 到 8.5 版本
- 简化 FormRequestWrapper 中的表单请求处理逻辑
- 移除反射代码，提升性能

### 📦 构建配置

- 核心模块版本号更新至 `1.0.0`
- 移除对 Java 8 的兼容性约束

---

## 与旧版本的区别

| 功能 | 0.9.16 | 1.0.0 |
|------|--------|-------|
| Spring Boot 版本 | 2.x | 3.2.5 |
| Java 版本 | 8+ | 17+ |
| Servlet API | javax | jakarta |
| 自动配置 | spring.factories | AutoConfiguration.imports |
| 请求体截断 | ✅ | ✅ |
| 历史记录查询 | ✅ | ✅ |
| 统计功能 | ✅ | ✅ |

---

## 常见问题

### Q: 我还在用 Spring Boot 2.x，可以升级吗？

**A:** 不可以。1.0.0 版本要求 Spring Boot 3.0+ 和 Java 17+。如果暂时无法升级，请继续使用 0.9.x 版本。

### Q: 两个版本会同时维护多久？

**A:** 0.9.x 版本将进入维护模式，仅修复安全漏洞。建议尽快迁移到 1.0.0 以获得新功能和性能优化。

### Q: 升级后配置参数有变化吗？

**A:** 配置参数保持不变，现有的 `application.properties` 配置无需修改。

### Q: 历史记录接口 `/httpRawLog/history` 还能用吗？

**A:** 可以。接口地址和参数完全兼容，无需修改调用代码。

---

## 回滚方案

如果升级后遇到问题，可以快速回滚：

1. 将依赖版本改回 `0.9.16`
2. 执行 `mvn clean install` 或 `gradle clean build`
3. 重启应用

---

## 参考链接

- [Spring Boot 3.0 迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)
- [Jakarta EE 9 迁移](https://jakarta.ee/docs/migration/9.0/)
- [Java 17 新特性](https://openjdk.org/projects/jdk/17/)
