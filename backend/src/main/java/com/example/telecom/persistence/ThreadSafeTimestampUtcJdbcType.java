package com.example.telecom.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.TimestampUtcAsJdbcTimestampJdbcType;

/**
 * Hibernate's {@code TIMESTAMP_UTC} type (used for every {@link Instant}
 * column) with a new UTC calendar per call.
 *
 * <p>
 * Before Hibernate 7.4, {@link TimestampUtcAsJdbcTimestampJdbcType} passes one
 * static calendar to the driver. sqlite-jdbc's
 * {@code getTimestamp(int, Calendar)} calls {@code setTimeInMillis} on that
 * calendar for INTEGER columns, so concurrent reads corrupt each other and fail
 * with {@code ArrayIndexOutOfBoundsException}. This is the upstream fix for
 * HHH-20355; remove this class and its
 * {@link ThreadSafeTimestampTypeContributor} once the project uses Hibernate
 * 7.4 or later.
 */
public class ThreadSafeTimestampUtcJdbcType extends TimestampUtcAsJdbcTimestampJdbcType {

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return new BasicBinder<>(javaType, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options)
                    throws SQLException {
                Instant instant = javaType.unwrap(value, Instant.class, options);
                st.setTimestamp(index, Timestamp.from(instant), utcCalendar());
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                    throws SQLException {
                Instant instant = javaType.unwrap(value, Instant.class, options);
                st.setTimestamp(name, Timestamp.from(instant), utcCalendar());
            }
        };
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return new BasicExtractor<>(javaType, this) {
            @Override
            protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
                return wrap(rs.getTimestamp(paramIndex, utcCalendar()), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return wrap(statement.getTimestamp(index, utcCalendar()), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options)
                    throws SQLException {
                return wrap(statement.getTimestamp(name, utcCalendar()), options);
            }

            private X wrap(Timestamp timestamp, WrapperOptions options) {
                return javaType.wrap(timestamp == null ? null : timestamp.toInstant(), options);
            }
        };
    }
}
