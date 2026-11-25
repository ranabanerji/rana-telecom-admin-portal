package com.example.telecom.persistence;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.service.ServiceRegistry;

/**
 * Replaces the dialect's {@code TIMESTAMP_UTC} type with
 * {@link ThreadSafeTimestampUtcJdbcType}. Hibernate loads it through
 * {@code META-INF/services} after the dialect has contributed its types.
 */
public class ThreadSafeTimestampTypeContributor implements TypeContributor {

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        typeContributions.contributeJdbcType(new ThreadSafeTimestampUtcJdbcType());
    }
}
