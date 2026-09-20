CREATE TABLE loan_contract (
    id BIGINT PRIMARY KEY,  
    -- 数据库内部主键，用于表之间关联，例如 id = 2

    loan_no VARCHAR(64) NOT NULL UNIQUE,  
    -- 业务上的贷款编号，例如 LN-10002

    borrower_name VARCHAR(128) NOT NULL,  
    -- 借款人姓名，如“张三”

    principal DECIMAL(18, 2) NOT NULL,  
    -- 整笔贷款的本金，即最初借出的金额

    start_date DATE NOT NULL, 
    -- 贷款合同开始日期

    end_date DATE NOT NULL  
    -- 贷款合同结束日期
);


CREATE TABLE repayment_plan (
    id BIGINT PRIMARY KEY,  
    -- 一条还款计划记录的数据库内部主键

    loan_id BIGINT NOT NULL,  
    -- 该还款计划属于哪笔贷款，关联 loan_contract.id
    -- 注意：这里不是 LN-10001 这种业务贷款编号

    installment_no INT NOT NULL,  
    -- 这是该笔贷款的第几期，例如 1 表示第 1 期

    due_date DATE NOT NULL,  
    -- 本期还款的到期日

    principal_due DECIMAL(18, 2) NOT NULL,  
    -- 本期应该偿还的本金

    interest_due DECIMAL(18, 2) NOT NULL,  
    -- 本期应该支付的利息

    CONSTRAINT uq_repayment_plan_loan_installment 
        UNIQUE (loan_id, installment_no),
    -- 同一笔贷款不能出现两个“第 1 期”或两个“第 2 期”

    CONSTRAINT uq_repayment_plan_loan_id_id 
        UNIQUE (loan_id, id),
    -- 保证 (loan_id, id) 这个组合唯一，
    -- 主要用于后面的复合外键校验

    CONSTRAINT fk_repayment_plan_loan 
        FOREIGN KEY (loan_id) REFERENCES loan_contract(id)
    -- 保证 repayment_plan.loan_id 必须对应真实存在的贷款
);


CREATE TABLE payment_record (
    id BIGINT PRIMARY KEY,  
    -- 一条实际还款记录的数据库内部主键

    loan_id BIGINT NOT NULL,  
    -- 这笔实际还款属于哪一笔贷款，关联 loan_contract.id

    repayment_plan_id BIGINT NOT NULL,  
    -- 这笔钱是在偿还哪一条还款计划，关联 repayment_plan.id
    -- 注意：它不是“第几期”的数字，第几期由 installment_no 表示

    payment_date DATE NOT NULL,  
    -- 实际发生还款的日期

    amount DECIMAL(18, 2) NOT NULL,  
    -- 本次实际还了多少钱

    CONSTRAINT fk_payment_loan 
        FOREIGN KEY (loan_id) REFERENCES loan_contract(id),
    -- 保证这笔还款对应的贷款真实存在

    CONSTRAINT fk_payment_plan_loan 
        FOREIGN KEY (loan_id, repayment_plan_id)
        REFERENCES repayment_plan(loan_id, id)
    -- 不仅要求 repayment_plan_id 存在，
    -- 还要求这条还款计划确实属于同一个 loan_id，
    -- 防止把 A 贷款的还款记录错误挂到 B 贷款的还款计划上
);