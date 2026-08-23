DROP TABLE IF EXISTS payment_record;
DROP TABLE IF EXISTS repayment_plan;
DROP TABLE IF EXISTS loan_contract;

CREATE TABLE loan_contract (
    id BIGINT PRIMARY KEY,
    loan_no VARCHAR(64) NOT NULL UNIQUE,
    borrower_name VARCHAR(128) NOT NULL,
    principal DECIMAL(18, 2) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL
);

CREATE TABLE repayment_plan (
    id BIGINT PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    installment_no INT NOT NULL,
    due_date DATE NOT NULL,
    principal_due DECIMAL(18, 2) NOT NULL,
    interest_due DECIMAL(18, 2) NOT NULL,
    CONSTRAINT uq_repayment_plan_loan_installment UNIQUE (loan_id, installment_no),
    CONSTRAINT uq_repayment_plan_loan_id_id UNIQUE (loan_id, id),
    CONSTRAINT fk_repayment_plan_loan FOREIGN KEY (loan_id) REFERENCES loan_contract(id)
);

CREATE TABLE payment_record (
    id BIGINT PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    repayment_plan_id BIGINT NOT NULL,
    payment_date DATE NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    CONSTRAINT fk_payment_loan FOREIGN KEY (loan_id) REFERENCES loan_contract(id),
    CONSTRAINT fk_payment_plan_loan FOREIGN KEY (loan_id, repayment_plan_id)
        REFERENCES repayment_plan(loan_id, id)
);
