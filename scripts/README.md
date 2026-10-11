# scripts —— 开发期一次性脚本

这些脚本**不是**应用的一部分：后端启动、测试、部署都不依赖它们。
它们当初散落在仓库根目录，和 `pom.xml` / `README.md` 混在一起，
看起来像项目入口，因此统一收到这里。

## 目录

| 脚本 | 用途 | 依赖 |
|---|---|---|
| `create_products.py` | 批量造演示商品（含本地图片目录 → base64 入库） | 后端已启动、`requests` |
| `auto_categorize_products.py` | 按标题关键词把老数据的分类补齐（对应 `ProductService.autoCategorize` 的映射表） | 后端已启动、`requests` |
| `update_product_images.py` | 批量替换商品图片 | 后端已启动、`requests` |
| `update_product_prices.py` | 按商品类型批量调整价格区间 | 后端已启动、`requests` |
| `fix_password.py` | 直接改库，把指定用户的密码重置为给定哈希 | `pymysql`、环境变量 |
| `test_login.py` / `test_login_new.py` | 手工打一下登录接口 | 后端已启动、`requests` |
| `test_register.py` / `test_user_register.py` | 手工打一下注册接口 | 后端已启动、`requests` |

## 运行前提

- 需要后端在 `http://localhost:8080` 运行（改库的脚本除外）。
- `fix_password.py` **不写死任何口令**，需要先设置环境变量：

  ```powershell
  $env:DB_PASSWORD = '<数据库口令>'
  $env:PASSWORD_HASH = '<bcrypt 哈希>'   # 可用 spring-security-crypto 的 BCryptPasswordEncoder 生成
  python scripts\fix_password.py
  ```

  它的注释里记着一段历史：此前把 `123456` 和一段 bcrypt 哈希直接写进了脚本，
  并且随公开仓库一起提交。**这类东西一旦进过 git 历史就只能靠换口令补救**，
  所以现在的写法是「口令与哈希都从环境变量读」。

## 注意

- `test_login.py` / `test_user_register.py` 里带的是**演示账号口令 `123456`**，
  仅用于本地开发库。它们不是密钥，但也不要在真实环境的账号上复用同样的口令。
- 这些脚本按当前实现直接调用接口或直接写库，字段/路径随重构会失效；
  失效时优先改脚本，不要为了迁就脚本去放宽接口（尤其是鉴权）。
