CREATE TABLE plans (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name VARCHAR(255) NOT NULL,
            monthly_price NUMERIC(10,2) NOT NULL,
            data_limit_gb INTEGER,
            minutes_limit INTEGER,
            sms_limit INTEGER,
            is_active BOOLEAN NOT NULL DEFAULT 1
        );

CREATE TABLE customers (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            first_name VARCHAR(255) NOT NULL,
            last_name VARCHAR(255) NOT NULL,
            email VARCHAR(255) NOT NULL UNIQUE,
            phone VARCHAR(255) NOT NULL,
            plan_id BIGINT NOT NULL,
            status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
            balance NUMERIC(10,2) NOT NULL DEFAULT 0,
            activated_at BIGINT,
            created_at BIGINT NOT NULL,
            FOREIGN KEY (plan_id) REFERENCES plans(id)
        );

CREATE TABLE devices (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            imei VARCHAR(255) NOT NULL UNIQUE,
            model VARCHAR(255) NOT NULL,
            sim_number VARCHAR(255) NOT NULL UNIQUE,
            customer_id BIGINT,
            status VARCHAR(255) NOT NULL DEFAULT 'AVAILABLE',
            assigned_at BIGINT,
            created_at BIGINT NOT NULL,
            FOREIGN KEY (customer_id) REFERENCES customers(id)
        );

CREATE TABLE usage_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id BIGINT NOT NULL,
            type VARCHAR(255) NOT NULL,
            quantity NUMERIC(10,2) NOT NULL,
            cost NUMERIC(10,2) NOT NULL,
            recorded_at BIGINT NOT NULL,
            FOREIGN KEY (customer_id) REFERENCES customers(id)
        );

CREATE TABLE support_tickets (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id BIGINT NOT NULL,
            subject VARCHAR(255) NOT NULL,
            description TEXT,
            priority VARCHAR(255) NOT NULL DEFAULT 'MEDIUM',
            status VARCHAR(255) NOT NULL DEFAULT 'OPEN',
            created_at BIGINT NOT NULL,
            resolved_at BIGINT,
            FOREIGN KEY (customer_id) REFERENCES customers(id)
        );

CREATE INDEX idx_usage_customer_recorded ON usage_records(customer_id, recorded_at);
CREATE INDEX idx_usage_recorded ON usage_records(recorded_at);
CREATE INDEX idx_tickets_created ON support_tickets(created_at);
CREATE INDEX idx_tickets_customer ON support_tickets(customer_id);
CREATE INDEX idx_devices_customer ON devices(customer_id);
CREATE INDEX idx_customers_plan_status ON customers(plan_id, status);
