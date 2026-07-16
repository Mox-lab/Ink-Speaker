package ink.realm.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库连接失败分析器。
 * <p>
 * 当目标数据库不存在时(PostgreSQL 返回 SQLState=3D000,即 invalid_catalog_name),
 * 拦截默认的 JDBC 原始报错,改为输出清晰可读的中文提示与建库指引。
 * 原始报错中的中文在 GBK 控制台下会变成乱码(如 {@code ��������}),此处统一给出友好提示。
 * </p>
 * <p>
 * 注册方式:通过 {@code META-INF/spring.factories} 声明,Spring Boot 在启动失败时自动调用。
 * </p>
 */
public class DataSourceFailureAnalyzer extends AbstractFailureAnalyzer<CannotGetJdbcConnectionException> {

    /**
     * SQLState 3D000:invalid_catalog_name,表示连接的数据库不存在。
     */
    private static final String INVALID_CATALOG_NAME = "3D000";

    /**
     * 从异常消息中提取被引用(不存在)的数据库名,兼容中英文报错。
     */
    private static final Pattern DB_NAME_PATTERN =
            Pattern.compile("(?:数据库|database)\\s+\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, CannotGetJdbcConnectionException cause) {
        SQLException sqlException = findSqlException(cause);
        if (sqlException == null || !INVALID_CATALOG_NAME.equals(sqlException.getSQLState())) {
            // 仅处理"数据库不存在"这一类,其余连接失败交回默认分析器
            return null;
        }

        String dbName = extractDbName(cause);
        String description = String.format(
                "数据库 \"%s\" 不存在,应用无法启动。请先创建该数据库,或使用环境变量 DB_NAME 指向已存在的库。",
                dbName);
        String action = String.format(
                "以 UTF8 编码创建数据库(需先连入 PostgreSQL):%n"
                        + "  CREATE DATABASE \"%s\" WITH ENCODING 'UTF8' LC_COLLATE 'zh_CN.UTF-8' LC_CTYPE 'zh_CN.UTF-8' TEMPLATE template0;%n"
                        + "或在本机 docker-compose 中设置 DB_NAME=%s 后重启容器。",
                dbName, dbName);

        return new FailureAnalysis(description, action, cause);
    }

    /**
     * 沿异常链向上查找第一个 {@link SQLException}。
     *
     * @param throwable 起始异常
     * @return 找到的 SQLException,未找到返回 null
     */
    private SQLException findSqlException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException) {
                return (SQLException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 从异常消息(如 '数据库 "ink_realm" 不存在')中解析数据库名。
     *
     * @param throwable 连接异常
     * @return 数据库名,解析失败则返回默认值 ink_realm
     */
    private String extractDbName(Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null) {
            Matcher matcher = DB_NAME_PATTERN.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "ink_realm";
    }
}
