# MoYu 项目记忆

## 项目信息

- **项目名**: MoYu (moyu)
- **技术栈**: Spring Boot 4.1.0 + MyBatis-Plus + JPA (Hibernate) + PostgreSQL + Flyway
- **包名**: ink.realm
- **开发者**: songshan.li

## 技术决策

### MyBatis-Plus 版本
- 使用 `mybatis-plus-spring-boot4-starter` 3.5.16（因为 Spring Boot 4.x）
- 注意：不可使用 `mybatis-plus-spring-boot3-starter`，会导致 SqlSessionFactory 无法创建
- mybatis-plus 配置前缀为顶层 `mybatis-plus:`，不可嵌套在 `spring:` 下

### Flyway 迁移
- 2026-07-16：将所有迁移脚本合并为单一 `V1__init_schema.sql`
- 包含 12 个实体对应的表 + 1 个关联表 (user_roles)
- 所有实体继承 BaseEntity，统一需要 `created_at` / `updated_at` 列

### 数据库
- PostgreSQL + BIGSERIAL 主键
- JPA ddl-auto=validate 模式，DDL 完全由 Flyway 管理
- Hibernate 实体列必须与数据库表列严格匹配
