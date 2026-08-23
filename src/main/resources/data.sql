INSERT INTO loan_contract (id, loan_no, borrower_name, principal, start_date, end_date) VALUES
(1, 'LN-10001', 'Demo Borrower A', 8000.00, '2026-01-01', '2027-01-01'),
(2, 'LN-10002', 'Demo Borrower B', 8000.00, '2026-01-01', '2027-01-01'),
(3, 'LN-10003', 'Demo Borrower C', 16000.00, '2026-01-01', '2027-01-01');

INSERT INTO repayment_plan (id, loan_id, installment_no, due_date, principal_due, interest_due) VALUES
(11, 1, 1, '2026-08-30', 8000.00, 500.00),
(21, 2, 1, '2026-08-20', 8000.00, 500.00),
(31, 3, 1, '2026-07-20', 8000.00, 500.00),
(32, 3, 2, '2026-08-20', 8000.00, 500.00);

INSERT INTO payment_record (id, loan_id, repayment_plan_id, payment_date, amount) VALUES
(201, 2, 21, '2026-08-19', 5000.00),
(301, 3, 31, '2026-07-20', 8500.00),
(302, 3, 32, '2026-08-20', 8500.00);