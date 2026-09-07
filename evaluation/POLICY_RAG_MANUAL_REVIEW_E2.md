# Policy RAG Manual Grounding Review

Generated from real MySQL + Ollama/BGE-M3 + Qdrant + DeepSeek execution. All policy text is `EVALUATION_SYNTHETIC`.

Review date: 2026-09-07  
Business date: 2026-08-23  
Cases: 10

## fin-001 — PURE_FINANCIAL

**Question**

1. LN-10001 当前应该还多少钱？

**Execution**

- requestId: `d0c6a123-658f-4b5c-a961-f9e59dc0f104`
- conversationId: `876685aa-0404-4ace-9944-89ad6638c2fc`
- Agent Audit: `SUCCESS`
- Policy decision/status: `NOT_REQUIRED / NOT_RUN`
- Conversation roles: `USER,ASSISTANT`

**Tool Evidence**

- getCurrentRepayment(LN-10001)=SUCCESS

**Policy Evidence**

- None.

**Final Answer**

> 根据系统查询结果，贷款 **LN-10001** 当前应还信息如下：
> 
> - **当前期次**：第 1 期
> - **还款日**：2026-08-30
> - **应还本金**：8,000.00 元
> - **应还利息**：500.00 元
> - **本期应还总额**：**8,500.00 元**
> - **已还金额**：0 元
> - **剩余应还金额**：**8,500.00 元**
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

- requestId: `8cbe9f9f-f01f-48f1-9d21-26e43f4acae6`
- conversationId: `ddc4e008-3b11-461c-9a5f-9f2d838ff4b6`
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
- [P5] EVAL-RISK-2026 第十二条; score=0.6484; match=SEMANTIC; cited=true
  第十二条
逾期天数是风险分类的重要参考，但不得仅以逾期天数替代对偿债能力的综合判断。

**Final Answer**

> 根据本次提供的政策证据，我为您说明贷款逾期后的处置规定。
> 
> 依据 [P1]（《贷后管理评测规程》第四十四条），贷款发生逾期后，贷款人应当依法采取以下方式之一进行处置：
> - **催收**
> - **协议重组**
> - **债权转让**
> - **核销**
> 
> 此外，[P5]（《金融资产风险分类评测规程》第十二条）提示，逾期天数是风险分类的重要参考，但**不得仅以逾期天数替代对偿债能力的综合判断**。
> 
> 需要说明的是，本 Agent 为只读诊断服务，上述为政策层面的处置方式说明，不涉及对具体贷款执行任何操作。如需查询某笔具体贷款的逾期情况，请提供贷款编号，我可以为您调用诊断工具核实。

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十四条; citedInAnswer=true
- [P2] → EVAL-POST-2026 第四十八条; citedInAnswer=false
- [P3] → EVAL-OPS-2026 第三十一条; citedInAnswer=false
- [P4] → EVAL-POST-2026 第四十二条; citedInAnswer=false
- [P5] → EVAL-RISK-2026 第十二条; citedInAnswer=true

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

- requestId: `0a463ed4-443b-43c1-932b-5189951ad8c6`
- conversationId: `eb43f52a-6e01-4684-a473-112337bb94e0`
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

> 我需要根据提供的政策证据来回答这个问题。根据 [P1] 的规定，我可以给出政策性结论。
> 
> 让我查看相关政策证据。
> 
> 根据提供的 POLICY_CONTEXT 中的 [P1]（贷后管理评测规程 EVAL-POST-2026 第六章 贷后管理/第四十五条）：
> 
> > 贷款全部清偿后，贷款人应当及时出具结清证明，办理相关解押手续，并妥善保存还款凭证。
> 
> 因此，贷款全部清偿后按规定需要办理的手续包括：
> 
> 1. **出具结清证明**：贷款人应当及时出具结清证明；
> 2. **办理相关解押手续**：办理抵押解除等相关手续；
> 3. **妥善保存还款凭证**：妥善保存还款凭证。
> 
> 以上结论依据 [P1] 得出。
> 
> 另外，如果您需要查询某笔具体贷款的结清状态（如是否已结清、还剩多少未结清），我可以使用工具为您查询。请问您是否需要查询某笔具体贷款的情况？

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

- requestId: `9ec35d60-7301-49b1-bccc-5cbecd3f3c0b`
- conversationId: `32ed3c06-bca1-4aca-afca-68fd3f65b9c2`
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

> 根据查询结果，我确认了 LN-10002 的逾期情况，并结合政策依据给出处理建议。
> 
> ## LN-10002 逾期诊断结果
> 
> **逾期事实确认：**
> - 应还日期：2026-08-20
> - 业务日期：2026-08-23
> - 本期应还金额：8,500.00 元
> - 已还金额：5,000.00 元
> - 剩余未还金额：3,500.00 元
> - **逾期状态：是**
> - **逾期天数：3 天**
> 
> 该贷款确实已逾期，尚有 3,500.00 元未还清。
> 
> ## 处理建议（依据政策）
> 
> 根据现行贷后管理规程，贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置 [P1]。
> 
> 同时需要说明的是，逾期天数是风险分类的重要参考，但**不得仅以逾期天数替代对偿债能力的综合判断** [P2]。因此，虽然当前逾期仅 3 天，仍应结合借款人的实际偿债能力进行综合评估后再确定具体处置方式。
> 
> ---
> 
> **说明：** 本 Agent 为只读诊断服务，无法执行催收、重组、核销等任何处置操作。以上仅为基于逾期事实与政策依据的处置方向说明，具体处置需由具备相应权限的运营人员依法实施。

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

- requestId: `6457dfd6-0400-43ca-a5ad-c0e6c0371347`
- conversationId: `3884405d-2a18-489e-a85b-3f83e302c353`
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
> **结论：**
> 
> LN-10003 已全部结清。按照 [P1] 规定，贷款人应当：
> 1. **及时出具结清证明**；
> 2. **办理相关解押手续**；
> 3. **妥善保存还款凭证**。
> 
> 以上为政策层面的规定。如需了解具体办理流程或所需材料，建议咨询贷款经办机构。

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十五条; citedInAnswer=true
- [P2] → EVAL-OPS-2026 第三十一条; citedInAnswer=false
- [P3] → EVAL-OPS-2026 第三十条; citedInAnswer=false
- [P4] → EVAL-RIGHTS-2026 第二十三条; citedInAnswer=false

**Review finding**

本次 E2 run 未复现 Phase 7.4 E1 中 unsupported“质押”的 generation expansion；Phase 7.5 未修改 generation constraint，因此不将该历史问题记为已修复。

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

## exact-001 — EXACT_REFERENCE

**Question**

1. EVAL-POST-2026 第四十四条规定什么？

**Execution**

- requestId: `0070583d-9cf6-4302-a76a-32b6a0400cfd`
- conversationId: `2bf03053-a1d0-4ba1-ae29-23ebf63833f7`
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

> 根据提供的政策证据 [P1]（EVAL-POST-2026 第四十四条），该条规定：
> 
> **贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置。**
> 
> 引用依据：[P1]

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

- requestId: `f6783663-1cf6-4a3a-acd6-de6ebe19e352`
- conversationId: `b6e57d36-9bbf-47c8-899d-84400dd9ebbe`
- Agent Audit: `SUCCESS`
- Policy decision/status: `REQUIRED / MATCHED`
- Conversation roles: `USER,ASSISTANT`

**Tool Evidence**

- None.

**Policy Evidence**

- [P1] EVAL-POST-2026 第四十五条; score=0.6440; match=SEMANTIC; cited=true
  第四十五条
贷款全部清偿后，贷款人应当及时出具结清证明，办理相关解押手续，并妥善保存还款凭证。
- [P2] EVAL-POST-2026 第四十四条; score=0.6380; match=SEMANTIC; cited=false
  第四十四条
贷款发生逾期后，贷款人应当依法采取催收、协议重组、债权转让或者核销等方式进行处置。
- [P3] EVAL-POST-2026 第四十三条; score=0.6269; match=SEMANTIC; cited=false
  第四十三条
对出现风险预警信号的贷款，贷款人应及时核实原因、评估影响并制定跟踪措施。
- [P4] EVAL-POST-2026 第四十八条; score=0.6169; match=SEMANTIC; cited=false
  第四十八条
对借款人提出的贷后服务投诉，贷款人应及时登记、调查并反馈处理结果。
- [P5] EVAL-OPS-2026 第三十一条; score=0.6167; match=SEMANTIC; cited=false
  第三十一条
贷款资金支付后，应按照约定方式核验资金流向并记录异常情况。

**Final Answer**

> 我需要先确认这笔贷款的状态。请问您能提供贷款编号吗？
> 
> 同时，根据您描述的情况，我需要查询贷款的结清状态来确认事实。
> 
> 请提供贷款编号（如 LN-10001），我将为您查询结清状态。
> 
> 关于您提到的"贷款已全部付清但抵押登记还挂着"的情况，根据政策依据：
> 
> 根据 [P1]（EVAL-POST-2026 第六章 贷后管理/第四十五条）：贷款全部清偿后，贷款人应当及时出具结清证明，办理相关解押手续，并妥善保存还款凭证。
> 
> 因此，机构应当及时出具结清证明并办理相关解押手续。
> 
> 不过，我需要先核实该贷款的实际结清状态。请提供贷款编号，我将为您查询确认。

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十五条; citedInAnswer=true
- [P2] → EVAL-POST-2026 第四十四条; citedInAnswer=false
- [P3] → EVAL-POST-2026 第四十三条; citedInAnswer=false
- [P4] → EVAL-POST-2026 第四十八条; citedInAnswer=false
- [P5] → EVAL-OPS-2026 第三十一条; citedInAnswer=false

**Review finding**

Router 已由 E1 的 `NOT_REQUIRED / NOT_RUN` 变为 `REQUIRED / MATCHED`；第四十五条作为 [P1] 进入 Policy Context，DeepSeek 答案使用了有效 [P1] 引用并回答了解押手续。

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

## temporal-003 — TEMPORAL_VERSION

**Question**

1. 贷款发生逾期以后应当采取哪些处置方式？

**Execution**

- requestId: `3a5e6f4a-4777-414f-af3a-c115bc48fde7`
- conversationId: `450174df-21d6-4a92-9d1d-468eb271d596`
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
- [P3] EVAL-POST-2026 第四十三条; score=0.6587; match=SEMANTIC; cited=true
  第四十三条
对出现风险预警信号的贷款，贷款人应及时核实原因、评估影响并制定跟踪措施。
- [P4] EVAL-RISK-2026 第十二条; score=0.6553; match=SEMANTIC; cited=true
  第十二条
逾期天数是风险分类的重要参考，但不得仅以逾期天数替代对偿债能力的综合判断。
- [P5] EVAL-POST-2026 第四十二条; score=0.6551; match=SEMANTIC; cited=false
  第四十二条
借款人申请展期的，应当在到期前提交申请和偿债计划，贷款人完成风险审查后作出决定。

**Final Answer**

> 我需要根据提供的政策证据来回答这个问题。根据 [P1] 中的规定，我可以直接回答。
> 
> 根据 **EVAL-POST-2026《贷后管理评测规程》第六章第四十四条** [P1] 的规定：
> 
> 贷款发生逾期后，贷款人应当依法采取以下处置方式：
> 1. **催收**
> 2. **协议重组**
> 3. **债权转让**
> 4. **核销**
> 
> 贷款人应当依法选择上述方式之一或组合进行处置。
> 
> 此外，结合相关配套规定，若贷款出现风险预警信号，贷款人还应当及时核实原因、评估影响并制定跟踪措施 [P3]；同时，逾期天数仅是风险分类的重要参考，不得仅以逾期天数替代对偿债能力的综合判断 [P4]。
> 
> ---
> 
> 如果您需要查询某笔具体贷款的逾期情况，请提供贷款编号，我可以为您调用诊断工具获取确定性结果。

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十四条; citedInAnswer=true
- [P2] → EVAL-POST-2026 第四十八条; citedInAnswer=false
- [P3] → EVAL-POST-2026 第四十三条; citedInAnswer=true
- [P4] → EVAL-RISK-2026 第十二条; citedInAnswer=true
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

- requestId: `61fb445a-9deb-4a80-9bef-0cb21237d159`
- conversationId: `b8f23b7e-d286-41c3-b6a0-8a8a697c9009`
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

- requestId: `b0365e13-7b98-40f6-8006-2af860efe77a`
- conversationId: `176dcd0f-3f9a-4909-b994-fce46ae015c8`
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

> 根据提供的政策证据，针对贷款逾期后的处置方式如下：
> 
> **依据 [P1]**（《贷后管理评测规程》EVAL-POST-2026 第四十四条）：贷款发生逾期后，贷款人应当依法采取**催收、协议重组、债权转让或者核销**等方式进行处置。
> 
> **依据 [P2]**（《金融资产风险分类评测规程》EVAL-RISK-2026 第十二条）：逾期天数是风险分类的重要参考，但**不得仅以逾期天数替代对偿债能力的综合判断**。
> 
> **结合本笔贷款情况**：LN-10002 目前逾期 3 天，逾期金额 3,500.00 元。按照上述规程，贷款人应依法启动催收等处置程序，同时应结合借款人整体偿债能力进行综合判断，而不能仅凭逾期天数就作出风险分类结论。
> 
> ---
> 
> 需要说明的是，本 Agent 为**只读诊断服务**，无法执行催收、协议重组、债权转让或核销等任何实际操作。如需执行上述处置动作，请联系具备相应权限的经办人员处理。

**Citation mapping**

- [P1] → EVAL-POST-2026 第四十四条; citedInAnswer=true
- [P2] → EVAL-RISK-2026 第十二条; citedInAnswer=true

**Human checklist**

- [x] 当前金融事实与 Tool Evidence 一致或不适用。
- [x] 政策结论可在 Policy Evidence 中找到直接依据或已正确 abstain。
- [x] Citation mapping 正确或不适用。
- [x] 未出现证据之外的额外政策结论。
- [x] 已完整回答用户主要问题，或明确记录 Router/知识库限制。

