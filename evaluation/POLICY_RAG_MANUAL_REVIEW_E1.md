# Policy RAG Manual Grounding Review

Generated from real MySQL + Ollama/BGE-M3 + Qdrant + DeepSeek execution. All policy text is `EVALUATION_SYNTHETIC`.

Review date: 2026-09-07  
Business date: 2026-08-23  
Cases: 10

## fin-001 — PURE_FINANCIAL

**Question**

1. LN-10001 当前应该还多少钱？

**Execution**

- requestId: `e6dbe389-8901-4b1e-98a5-8afd326db091`
- conversationId: `1cd692ed-6f8c-4b48-8827-4c84378ca7eb`
- Agent Audit: `SUCCESS`
- Policy decision/status: `NOT_REQUIRED / NOT_RUN`
- Conversation roles: `USER,ASSISTANT`

**Tool Evidence**

- getCurrentRepayment(LN-10001)=SUCCESS

**Policy Evidence**

- None.

**Final Answer**

> 根据查询结果，贷款 LN-10001 的当前应还情况如下：
> 
> - **当前期次**：第 1 期
> - **应还本金**：8,000.00 元
> - **应还利息**：500.00 元
> - **本期应还总额**：**8,500.00 元**
> - **已还金额**：0 元
> - **剩余未还金额**：8,500.00 元
> - **还款日**：2026-08-30
> - **是否逾期**：否（逾期 0 天）
> 
> 因此，LN-10001 当前应还 **8,500.00 元**，尚未还款，且未逾期。

**Citation mapping**

- No citation mapping recorded.

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

## policy-001 — PURE_POLICY

**Question**

1. 个人贷款发生逾期以后，按规定应当如何处置？

**Execution**

- requestId: `7c92f90a-7219-4623-9397-f11c0e7d2910`
- conversationId: `0843f556-bfde-4f58-9e6a-d9800f090fcb`
- Agent Audit: `SUCCESS`
- Policy decision/status: `REQUIRED / MATCHED`
- Conversation roles: `USER,ASSISTANT`

**Tool Evidence**

- None.

**Policy Evidence**

- [P1] EVAL-POST-2026 第四十四条; score=0.8256; match=SEMANTIC; cited=true
  第四十四条
贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置。
- [P2] EVAL-POST-2026 第四十八条; score=0.6668; match=SEMANTIC; cited=false
  第四十八条
对借款人提出的贷后服务投诉，贷款人应及时登记、调查并反馈处理结果。
- [P3] EVAL-OPS-2026 第三十一条; score=0.6625; match=SEMANTIC; cited=false
  第三十一条
贷款资金支付后，应按照约定方式核验资金流向并记录异常情况。
- [P4] EVAL-POST-2026 第四十二条; score=0.6538; match=SEMANTIC; cited=false
  第四十二条
借款人申请展期的，应当在到期前提交申请和偿债计划，贷款人完成风险审查后作出决定。
- [P5] EVAL-RISK-2026 第十二条; score=0.6484; match=SEMANTIC; cited=false
  第十二条
逾期天数是风险分类的重要参考，但不得仅以逾期天数替代对偿债能力的综合判断。

**Final Answer**

> 根据贷后管理评测规程（EVAL-POST-2026）第四十四条的规定：
> 
> 贷款发生逾期后，贷款人应当依法采取**催收、协议重组、债权转让或者核销**等方式进行处置。[P1]
> 
> 这是当前政策对逾期处置方式的明确规定。请问您是否还需要针对某笔具体贷款查询其逾期情况或处置状态？

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十四条; citedInAnswer=true
- [P2] → EVAL-POST-2026 第四十八条; citedInAnswer=false
- [P3] → EVAL-OPS-2026 第三十一条; citedInAnswer=false
- [P4] → EVAL-POST-2026 第四十二条; citedInAnswer=false
- [P5] → EVAL-RISK-2026 第十二条; citedInAnswer=false

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

## policy-002 — PURE_POLICY

**Question**

1. 贷款全部清偿后按规定要办理哪些手续？

**Execution**

- requestId: `2750f4c2-1b24-4351-b311-dd7eb22da9f2`
- conversationId: `5471c06b-0299-4a76-bccc-9d077b91ae5e`
- Agent Audit: `SUCCESS`
- Policy decision/status: `REQUIRED / MATCHED`
- Conversation roles: `USER,ASSISTANT`

**Tool Evidence**

- None.

**Policy Evidence**

- [P1] EVAL-POST-2026 第四十五条; score=0.7963; match=SEMANTIC; cited=true
  第四十五条
贷款全部清偿后，贷款人应当及时出具结清证明，办理相关解押手续，并妥善保存还款凭证。
- [P2] EVAL-OPS-2026 第三十一条; score=0.6906; match=SEMANTIC; cited=false
  第三十一条
贷款资金支付后，应按照约定方式核验资金流向并记录异常情况。
- [P3] EVAL-POST-2026 第四十四条; score=0.6787; match=SEMANTIC; cited=false
  第四十四条
贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置。
- [P4] EVAL-RIGHTS-2026 第二十三条; score=0.6732; match=SEMANTIC; cited=false
  第二十三条
借款人提交提前还款申请的，贷款人应按照合同约定告知办理条件和所需材料。
- [P5] EVAL-POST-2026 第四十八条; score=0.6583; match=SEMANTIC; cited=false
  第四十八条
对借款人提出的贷后服务投诉，贷款人应及时登记、调查并反馈处理结果。

**Final Answer**

> 我需要根据提供的政策证据来回答这个问题。让我查看相关的政策内容。
> 
> 根据 POLICY_CONTEXT 中提供的证据，[P1]（EVAL-POST-2026 第四十五条）明确规定了贷款全部清偿后的相关手续要求。
> 
> 让我为您解答：
> 
> 根据 **贷后管理评测规程（EVAL-POST-2026）第四十五条** [P1] 的规定：
> 
> 贷款全部清偿后，贷款人应当及时办理以下手续：
> 1. **出具结清证明**
> 2. **办理相关解押手续**
> 3. **妥善保存还款凭证**
> 
> ---
> 
> 需要说明的是，本 Agent 为只读诊断服务，无法执行上述任何操作（如出具证明、办理解押等）。以上仅为政策层面的规定说明，如需实际办理相关手续，请联系贷款经办机构。

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十五条; citedInAnswer=true
- [P2] → EVAL-OPS-2026 第三十一条; citedInAnswer=false
- [P3] → EVAL-POST-2026 第四十四条; citedInAnswer=false
- [P4] → EVAL-RIGHTS-2026 第二十三条; citedInAnswer=false
- [P5] → EVAL-POST-2026 第四十八条; citedInAnswer=false

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

## mixed-001 — MIXED

**Question**

1. LN-10002 已经逾期了，按照规定现在应该怎么处理？

**Execution**

- requestId: `f77b78f0-308e-4339-8c59-349708a2d072`
- conversationId: `e51fc7ae-f889-4c8c-8d7d-e11749cb43f1`
- Agent Audit: `SUCCESS`
- Policy decision/status: `SUPPLEMENTAL / MATCHED`
- Conversation roles: `USER,ASSISTANT`

**Tool Evidence**

- getOverdueDiagnosis(LN-10002)=SUCCESS

**Policy Evidence**

- [P1] EVAL-POST-2026 第四十四条; score=0.7027; match=SEMANTIC; cited=true
  第四十四条
贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置。
- [P2] EVAL-RISK-2026 第十二条; score=0.6279; match=SEMANTIC; cited=true
  第十二条
逾期天数是风险分类的重要参考，但不得仅以逾期天数替代对偿债能力的综合判断。
- [P3] EVAL-OPS-2026 第三十一条; score=0.6027; match=SEMANTIC; cited=false
  第三十一条
贷款资金支付后，应按照约定方式核验资金流向并记录异常情况。

**Final Answer**

> 根据查询结果，LN-10002 确实已逾期。以下是诊断结果和处理建议：
> 
> ## 逾期诊断结果（LN-10002）
> 
> | 项目 | 数值 |
> |------|------|
> | 应还日 | 2026-08-20 |
> | 业务日期 | 2026-08-23 |
> | 应还金额 | 8,500.00 元 |
> | 已还金额 | 5,000.00 元 |
> | 逾期未还金额 | 3,500.00 元 |
> | 是否逾期 | 是 |
> | 逾期天数 | 3 天 |
> 
> 该贷款尚未结清，当前逾期未还金额为 **3,500.00 元**，已逾期 **3 天**。
> 
> ## 处理建议（依据政策）
> 
> 根据贷后管理评测规程，贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置 [P1]。
> 
> 同时，根据金融资产风险分类评测规程，逾期天数是风险分类的重要参考，但**不得仅以逾期天数替代对偿债能力的综合判断** [P2]。也就是说，虽然当前逾期仅 3 天，但应结合借款人的整体偿债能力进行综合评估，而不能仅凭逾期天数就简单定性。
> 
> ---
> 
> **说明**：本 Agent 为只读诊断服务，无法执行催收、重组、核销等实际操作。如需执行上述处置动作，请由具备相应权限的系统或人员处理。

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十四条; citedInAnswer=true
- [P2] → EVAL-RISK-2026 第十二条; citedInAnswer=true
- [P3] → EVAL-OPS-2026 第三十一条; citedInAnswer=false

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

## mixed-002 — MIXED

**Question**

1. LN-10003 已经结清，按规定还需要办理什么手续？

**Execution**

- requestId: `7d7da552-70cc-4dd7-bb85-ed00e03fc24a`
- conversationId: `6cb7d4c3-d817-4c9b-bf7c-f3b72ae001f2`
- Agent Audit: `SUCCESS`
- Policy decision/status: `SUPPLEMENTAL / MATCHED`
- Conversation roles: `USER,ASSISTANT`

**Tool Evidence**

- getSettlementStatus(LN-10003)=SUCCESS

**Policy Evidence**

- [P1] EVAL-POST-2026 第四十五条; score=0.6509; match=SEMANTIC; cited=true
  第四十五条
贷款全部清偿后，贷款人应当及时出具结清证明，办理相关解押手续，并妥善保存还款凭证。
- [P2] EVAL-OPS-2026 第三十一条; score=0.6117; match=SEMANTIC; cited=false
  第三十一条
贷款资金支付后，应按照约定方式核验资金流向并记录异常情况。
- [P3] EVAL-OPS-2026 第三十条; score=0.6034; match=SEMANTIC; cited=false
  第三十条
贷款发放前应核对合同生效条件、放款指令和收款账户信息。
- [P4] EVAL-RIGHTS-2026 第二十三条; score=0.6025; match=SEMANTIC; cited=false
  第二十三条
借款人提交提前还款申请的，贷款人应按照合同约定告知办理条件和所需材料。

**Final Answer**

> 根据查询结果，LN-10003 已确认结清（settled=true，剩余未结清金额为 0.00 元）。
> 
> 关于结清后需要办理的手续，依据本次提供的政策证据 [P1]（《贷后管理评测规程》第四十五条）：
> 
> > 贷款全部清偿后，贷款人应当及时出具结清证明，办理相关解押手续，并妥善保存还款凭证。
> 
> 因此，LN-10003 结清后按规定应办理以下手续：
> 1. **出具结清证明**——贷款人应及时出具结清证明；
> 2. **办理相关解押手续**——如涉及抵押/质押，需办理解押；
> 3. **妥善保存还款凭证**——相关凭证需妥善留存。
> 
> 需要说明的是，本 Agent 为只读诊断服务，无法代为办理上述手续，具体办理请通过贷款机构相应渠道进行。

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十五条; citedInAnswer=true
- [P2] → EVAL-OPS-2026 第三十一条; citedInAnswer=false
- [P3] → EVAL-OPS-2026 第三十条; citedInAnswer=false
- [P4] → EVAL-RIGHTS-2026 第二十三条; citedInAnswer=false

**Review finding**

核心手续与 [P1] 一致，但“如涉及抵押/质押”中的“质押”不在本次证据中，记为轻微 GENERATION 扩展。

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [ ] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

## exact-001 — EXACT_REFERENCE

**Question**

1. EVAL-POST-2026 第四十四条规定什么？

**Execution**

- requestId: `76eeb297-bb61-49ed-86ce-f1fc2bb6ef98`
- conversationId: `4b4d743e-35f5-4668-9e66-9aaeca803ce1`
- Agent Audit: `SUCCESS`
- Policy decision/status: `REQUIRED / MATCHED`
- Conversation roles: `USER,ASSISTANT`

**Tool Evidence**

- None.

**Policy Evidence**

- [P1] EVAL-POST-2026 第四十四条; score=1.0000; match=EXACT; cited=true
  第四十四条
贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置。

**Final Answer**

> 根据提供的政策证据 [P1]：
> 
> **EVAL-POST-2026《贷后管理评测规程》第四十四条**规定：
> 
> > 贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置。
> 
> 该条款属于贷后管理章节，明确了贷款逾期后的处置方式要求。

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十四条; citedInAnswer=true

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

## semantic-002 — SEMANTIC_PARAPHRASE

**Question**

1. 贷款已经全部付清但抵押登记还挂着，机构应该做什么？

**Execution**

- requestId: `508fb6c1-a91a-498f-84d7-37b8abbf93d6`
- conversationId: `d05e7263-b345-4249-a2ec-4d2f237b613d`
- Agent Audit: `SUCCESS`
- Policy decision/status: `NOT_REQUIRED / NOT_RUN`
- Conversation roles: `USER,ASSISTANT`

**Tool Evidence**

- None.

**Policy Evidence**

- None.

**Final Answer**

> 我需要先了解您指的是哪笔贷款。请问您能提供贷款编号吗？这样我才能查询该贷款的结清状态和相关信息。
> 
> 另外，关于"抵押登记还挂着"的处理流程，这涉及政策和流程方面的问题。我需要先确认贷款的具体情况，再结合可用的政策依据来回答。
> 
> 请您提供贷款编号，我将为您查询相关事实信息。

**Citation mapping**

- No citation mapping recorded.

**Review finding**

安全地没有编造政策，但 Router 将该规范性问法判为 NOT_REQUIRED，未检索已能 rank 1 命中的第四十五条，因此答案不完整。

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [ ] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

## temporal-003 — TEMPORAL_VERSION

**Question**

1. 贷款发生逾期以后应当采取哪些处置方式？

**Execution**

- requestId: `325e76b8-abe2-455e-a329-38ab4ec70b75`
- conversationId: `dfa63c8f-00eb-46eb-b4d6-598073f85171`
- Agent Audit: `SUCCESS`
- Policy decision/status: `REQUIRED / MATCHED`
- Conversation roles: `USER,ASSISTANT`

**Tool Evidence**

- None.

**Policy Evidence**

- [P1] EVAL-POST-2026 第四十四条; score=0.8302; match=SEMANTIC; cited=true
  第四十四条
贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置。
- [P2] EVAL-POST-2026 第四十八条; score=0.6610; match=SEMANTIC; cited=false
  第四十八条
对借款人提出的贷后服务投诉，贷款人应及时登记、调查并反馈处理结果。
- [P3] EVAL-POST-2026 第四十三条; score=0.6587; match=SEMANTIC; cited=false
  第四十三条
对出现风险预警信号的贷款，贷款人应及时核实原因、评估影响并制定跟踪措施。
- [P4] EVAL-RISK-2026 第十二条; score=0.6553; match=SEMANTIC; cited=false
  第十二条
逾期天数是风险分类的重要参考，但不得仅以逾期天数替代对偿债能力的综合判断。
- [P5] EVAL-POST-2026 第四十二条; score=0.6551; match=SEMANTIC; cited=false
  第四十二条
借款人申请展期的，应当在到期前提交申请和偿债计划，贷款人完成风险审查后作出决定。

**Final Answer**

> 根据提供的政策证据，我来回答这个问题。
> 
> 根据 [P1]（《贷后管理评测规程》EVAL-POST-2026，第六章 贷后管理/第四十四条）：
> 
> 贷款发生逾期后，贷款人应当依法采取以下处置方式：
> 1. **催收**
> 2. **协议重组**
> 3. **债权转让**
> 4. **核销**
> 
> 以上处置方式均需依法进行。
> 
> ---
> 
> 如果您需要查询某笔具体贷款的逾期情况或还款信息，请提供贷款编号，我可以为您查询。

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十四条; citedInAnswer=true
- [P2] → EVAL-POST-2026 第四十八条; citedInAnswer=false
- [P3] → EVAL-POST-2026 第四十三条; citedInAnswer=false
- [P4] → EVAL-RISK-2026 第十二条; citedInAnswer=false
- [P5] → EVAL-POST-2026 第四十二条; citedInAnswer=false

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

## nomatch-001 — NO_MATCH

**Question**

1. 员工年假审批有哪些规定？

**Execution**

- requestId: `a730625e-64bc-4815-87b0-416cdefb4725`
- conversationId: `d4def91a-028a-40c4-80ac-921ef7006ca4`
- Agent Audit: `SUCCESS`
- Policy decision/status: `REQUIRED / NO_MATCH`
- Conversation roles: `USER,ASSISTANT`

**Tool Evidence**

- None.

**Policy Evidence**

- None.

**Final Answer**

> 当前政策知识库没有找到足够适用依据，因此无法确认政策部分。

**Citation mapping**

- No citation mapping recorded.

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

## multi-001 — MULTI_TURN

**Question**

1. LN-10002 为什么逾期？
2. 那按规定应该怎么办？

**Execution**

- requestId: `b86eaa17-6697-49c8-adc1-f7461f5e7b39`
- conversationId: `ce38f2ca-ff8d-43e7-bd54-446b2161ac5e`
- Agent Audit: `SUCCESS`
- Policy decision/status: `SUPPLEMENTAL / MATCHED`
- Conversation roles: `USER,ASSISTANT,USER,ASSISTANT`

**Tool Evidence**

- getOverdueDiagnosis(LN-10002)=SUCCESS

**Policy Evidence**

- [P1] EVAL-POST-2026 第四十四条; score=0.6342; match=SEMANTIC; cited=true
  第四十四条
贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置。
- [P2] EVAL-RISK-2026 第十二条; score=0.6274; match=SEMANTIC; cited=true
  第十二条
逾期天数是风险分类的重要参考，但不得仅以逾期天数替代对偿债能力的综合判断。

**Final Answer**

> 根据提供的政策证据，针对贷款 LN-10002 逾期后的处置方式如下：
> 
> **处置方式（依据政策）**
> 依据 [P1]《贷后管理评测规程》第四十四条，贷款发生逾期后，贷款人应当依法采取**催收、协议重组、债权转让或者核销**等方式进行处置。
> 
> **风险分类提示（依据政策）**
> 依据 [P2]《金融资产风险分类评测规程》第十二条，逾期天数是风险分类的重要参考，但**不得仅以逾期天数替代对偿债能力的综合判断**。因此，虽然 LN-10002 已逾期 3 天，但风险分类仍需结合借款人偿债能力等综合因素判断，不能仅凭逾期天数下结论。
> 
> ---
> 
> 需要说明的是，本服务为**只读诊断服务**，无法执行催收、重组、核销等任何处置操作。以上仅为政策层面的处置方向说明，具体执行请由具备相应权限的经办人员按流程办理。

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十四条; citedInAnswer=true
- [P2] → EVAL-RISK-2026 第十二条; citedInAnswer=true

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

