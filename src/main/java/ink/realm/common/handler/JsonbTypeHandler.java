package ink.realm.common.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL jsonb 列的 MyBatis TypeHandler。
 * <p>MyBatis-Plus 默认把 String 参数以 {@code varchar} 形式绑定到 PreparedStatement,
 * 而 PostgreSQL 的 jsonb 列不接受 character varying,会抛出
 * "字段类型为 jsonb, 但表达式的类型为 character varying"。</p>
 * <p>本 Handler 在写入时将字符串包装为类型为 {@code jsonb} 的 {@link PGobject},
 * 读取时直接返回原始 JSON 字符串,交由上层自行解析。</p>
 *
 * <p>使用方式:在实体上标注 {@code @TableName(autoResultMap = true)},
 * 并在对应字段上标注 {@code @TableField(typeHandler = JsonbTypeHandler.class)}。</p>
 *
 * @author songshan.li
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbTypeHandler extends BaseTypeHandler<String> {

    /** jsonb 列在 PostgreSQL 中的类型名。 */
    private static final String JSONB_TYPE = "jsonb";

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType(JSONB_TYPE);
        pgObject.setValue(parameter);
        ps.setObject(i, pgObject);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
