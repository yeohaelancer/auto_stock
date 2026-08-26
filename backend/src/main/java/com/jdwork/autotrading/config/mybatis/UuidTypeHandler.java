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
 * java.util.UUID ↔ MySQL CHAR(36) 컬럼 매핑용 MyBatis 타입 핸들러.
 * MySQL에는 PostgreSQL과 달리 네이티브 UUID 타입이 없어 문자열(CHAR(36))로 저장한다.
 * MyBatis type handler mapping java.util.UUID to/from a MySQL CHAR(36) column.
 * Unlike PostgreSQL, MySQL has no native UUID type, so it's stored as a string (CHAR(36)).
 *
 * 실제 기동 테스트 중 발견: 이 핸들러 없이는 UUID 파라미터를 쓰는 매퍼 XML이 파싱 단계에서 실패한다
 * ("Type handler was null on parameter mapping ... javaType java.util.UUID : jdbcType null").
 * 이 클래스가 있는 패키지를 application.yml의 mybatis.type-handlers-package로 등록해 전역 적용한다.
 * Found while actually booting the app: without this, any mapper XML using a UUID parameter fails
 * to parse ("Type handler was null on parameter mapping ... javaType java.util.UUID : jdbcType null").
 * This package is registered via application.yml's mybatis.type-handlers-package for global use.
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(JdbcType.CHAR)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.toString());
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : UUID.fromString(value);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : UUID.fromString(value);
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : UUID.fromString(value);
    }
}
