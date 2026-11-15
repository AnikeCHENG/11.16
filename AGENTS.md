# AGENTS.md — 校园二手交易平台

> 本文件是 AI 助手在本仓库工作时的**唯一事实来源**。若本文件与代码/README 冲突，**以代码为准**并顺手修正本文件。

## 1. 仓库位置（易踩坑）

- 本文件所在目录 `--main\--main\` 就是 Git 仓库根。外层目录是空壳，不是仓库。
- 后端：`afterend\campussecondhand\`（Maven，含 `mvnw.cmd`，**优先用 wrapper**）
- 前端：`frontend\campus-secondhand\`（Node `^20.19.0 || >=22.12.0`）
- **数据库名是 `campus_secondhand`**，不是 README 里写的 `waste_recycle_platform`。

## 2. 技术栈（实测）

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.1.2 / Java 17 / MyBatis-Plus 3.5.3.1 / Spring Security / jjwt 0.11.5 |
| 存储 | MySQL 8.0（`localhost:3306/campus_secondhand`）+ Redis（`localhost:6379`） |
| 前端 | Vue 3.5 Composition API / Vite 7 / Element Plus 2.3 / Pinia 3 / vue-router 4.6 / ECharts 6 |
| 测试 | 后端 JUnit 5（36 个测试类 / 316 条用例）/ 前端 Vitest 4 + Playwright 1.58 + ESLint 9 + Prettier |

## 3. 数据库迁移 —— 本项目最容易踩的坑

**项目没有 Flyway / Liquibase。** 迁移靠手工执行 SQL 文件。

- `src\main\resources\*.sql` 中有 **18 个 `migration-*.sql`**，需**手工**在数据库执行。
- `application.properties` 的 `spring.sql.init.schema-locations=classpath:plaza-schema.sql`
  只会在启动时**自动执行 `plaza-schema.sql` 一个文件**，其余 migration 一概不自动跑。
- ⇒ **改了实体字段却没跑对应 migration，就会直接抛 "Unknown column"。改 schema 必须先跑 SQL。**

已存在的迁移：`browse_history` / `cart` / `payment_record` / `plaza-posts` / `product-condition` / `report` / `status-semantics` / `student-review` / `user-email-unique` / `order-checkout`
/ `order-unique-active`（商品未取消订单的唯一约束；脚本第 1 步先查重，为空才继续） / `seller-balance`（`users.balance` + `account_flow`；**确认收货放款依赖它，不跑则确认收货必失败**） / `order-hide-flags`（买家/卖家各自隐藏，纯增量） / `student-screenshot`（**学生认证申请表 `student_verification`**，支付宝截图辅助认证的申请与人工审核留痕；不跑则该功能报 "Table doesn't exist"）
/ `product-review`（`products.review_status/review_reason/reviewed_by/reviewed_time`；**商品人工审核依赖它，不跑则商品列表报 "Unknown column"**）
/ `category-code`（`categories.code` 唯一标识，与 `products.category` 存的 slug 对齐；**不跑则后台分类管理、用户端分类下拉全部报错**）。

> `plaza-schema.sql` 里的 `ALTER TABLE orders ADD COLUMN seller_income` **不幂等**，
> 所以 `spring.sql.init.continue-on-error=true` 是必需的，不是随手打开的；
> 置为 `false` 会让第二次启动直接失败。

> 查真实表结构时优先用 `mysql` MCP 的 `describe_table`（只读模式），不要凭实体类推断。

### 关键约定

- **MyBatis-Plus 逻辑删除**：字段 `deleted`，已删 = `1`，未删 = `0`。查询自动过滤，但**原生 SQL / `@Select` 不生效**，需手写 `WHERE deleted = 0`。
- `id-type=auto`，主键由数据库自增。

## 4. 后端结构

`com.example.campussecondhand`

```
common/      ApiResponse（code/message/data）、PageParam、PageResult
config/      Spring Security + MyBatis-Plus 配置
controller/  16 个：Admin/Auth/BrowseHistory/Cart/Category/Dashboard/
             DevMigration/DevTest/Favorite/Message/Order/Post/Product/
             ProductInteraction/Report/User
dto/         DTO
enums/       ConditionLevel, OrderStatus, PostType, ProductStatus,
             ReportAction, ReportStatus, ReportTargetType
entity/      14 个实体
exception/   全局异常处理
repository/  MyBatis-Plus Mapper
service/     + service/impl/
task/        OrderTimeoutTask（订单超时扫描，间隔见 order.timeout-scan-interval-ms）
util/        JWT 工具等
```

### 统一响应

所有 Controller 返回 `ApiResponse<T>`：`{ code, message, data }`，用 `success(msg)` / `success(msg, data)` / `error(code, msg)` 构造。**不要绕过它直接返回实体。**

### 安全

- JWT 密钥来自环境变量 `JWT_SECRET`（HS512，**长度必须 ≥ 64**）。
- `DevMigrationController` 标了 `@Profile("dev")`，仅 dev profile 注册在 `/api/dev`。**不要在非 dev 环境暴露它。**

### 配置与密钥

- `application.properties` 只放占位符：`${DB_PASSWORD}`、`${JWT_SECRET}`、`${MAIL_PASSWORD}`。
- 真实值在 `application-local.properties`（**已 gitignore，勿提交**），模板见 `.example`。
- 本地启动：`$env:SPRING_PROFILES_ACTIVE = "dev,local"`
- `application-dev.properties` 会把 `repository` 包打到 DEBUG，用来确认分页 SQL 真的带 `LIMIT ?, ?`。**没看到 LIMIT 就说明分页拦截器没生效、分页是假的。**

## 5. 前端结构

`src\`: `api/`（auth/product/message/order/user 等）、`components/`、`router/`（含全局守卫）、`stores/`、`views/`（含 `Admin/`）、`images/`。

- axios 请求经 Vite 代理转发到 `http://localhost:8080`。
- 路由守卫依赖 Pinia 中的登录态与角色，**新增 admin 页面必须同步守卫与角色判断**。
- 提交前跑 `npm run lint`（ESLint `--fix`）与 `npm run test:unit`。

## 6. 验证命令（改完必跑）

```powershell
# 后端：编译 + 全部单测（项目自带 wrapper，勿依赖本机 mvn）
cd afterend\campussecondhand
.\mvnw.cmd -q test

# 前端
cd frontend\campus-secondhand
npm run lint
npm run test:unit
```

**不要在没跑通上述命令的情况下声称改动完成。** 起服务做接口冒烟时用 `verify-api` skill。

## 7. 提交规范

沿用仓库现有风格（见 `git log`）：

```
feat(scope): 中文描述
fix(scope): 中文描述
fix(security): 描述 (P0)     # 安全问题在描述后标注 P0
```

scope 用模块名（`auth` / `state` / `security` / `ui` / `db`）。

## 8. 已知文档漂移（README 不可信处）

1. README 写库名 `waste_recycle_platform`，实际是 `campus_secondhand`。
2. README 的包结构漏了 `enums/` 和 `task/`。
3. README 完全没提 plaza / cart / payment / report / review 这几个模块。
4. ~~`application.properties` 中 `spring.sql.init.mode=always`，但其上方注释写着"设为 never 跳过自动初始化"，**注释与实际相反**。~~ 已修正：注释改为如实说明 mode=always 只跑 `plaza-schema.sql`，其余 migration 需手工执行。
5. `application.properties` 里曾有 `logging.level.com.example.wastereuseplatform`，包名写错（真实为 `campussecondhand`），是一条永不生效的死配置，现已移除。