package com.jdwork.autotrading.config.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * java.util.UUID ↔ PostgreSQL UUID(JDBC OTHER) 컬럼 매핑용 MyBatis 타입 핸들러.
 * MyBatis type handler mapping java.util.UUID to/from PostgreSQL's UUID column (JDBC type OTHER).
 *
 * 실제 기동 테스트 중 발견: 이 핸들러 없이는 UUID 파라미터를 쓰는 매퍼 XML이 파싱 단계에서 실패한다
 * ("Type handler was null on parameter mapping ... javaType java.util.UUID : jdbcType null").
 * 이 클래스가 있는 패키지를 application.yml의 mybatis.type-handlers-package로 등록해 전역 적용한다.
 * Found while actually booting the app: without this, any mapper XML using a UUID parameter fails
 * to parse ("Type handler was null on parameter mapping ... javaType java.util.UUID : jdbcType null").
 * This package is registered via application.yml's mybatis.type-handlers-package for global use.
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.OTHER)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter, java.sql.Types.OTHER);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return value == null ? null : UUID.fromString(value.toString());
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object value = rs.getObject(columnIndex);
        return value == null ? null : UUID.fromString(value.toString());
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object value = cs.getObject(columnIndex);
        return value == null ? null : UUID.fromString(value.toString());
    }
}
