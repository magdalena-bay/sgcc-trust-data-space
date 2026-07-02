# Project Structure And Team Mapping

对应中文主文档：

- `../..\\..\\10-项目代码目录骨架设计与团队分工对照表.md`

本文件用于仓库内快速说明两件事：

1. 推荐代码目录骨架
2. 推荐模块负责分工

补充规划文档：

1. `./ONCHAIN_THROUGHPUT_10000_PLUS_PLAN.md`
   只讨论“只测区块链上链环节时间”如何规划到 `10000+`
2. `./SINGLE_CHAIN_8_NODE_PLAN.md`
   只讨论单机单链 `8` 节点的部署与资源方案

补充说明：

截至 `2026-06-26`，项目正式主路径已经切到：

`batch_digest / root checkpoint`

所以后续凡是涉及：

1. 吞吐量推进
2. 链写 worker
3. 批次聚合
4. 区块链模块排期

都必须默认围绕这条正式主路径来设计，不再以：

`逐资源同步 anchorResource`

作为 `10000+` 目标路线。

## Recommended Structure

```text
frontend/
  user-web/
  admin-web/
  agent-web/

backend/
  api-gateway/
  auth-service/
  identity-service/
  data-service/
  policy-service/
  crypto-service/
  blockchain-service/
  storage-service/
  audit-service/
  agent-service/
  fl-service/
  scheduler-service/

contracts/
  common/
  qingdao/
  weifang/
  relay/

chain-config/
  qingdao/
  weifang/
  relay/
  wecross/
  webase/

infra/
  mysql/
  redis/
  postgres/
  ipfs/
  nginx/
  docker/
```

## Recommended Team Mapping

| Group | Suggested Scope |
|---|---|
| Frontend | `frontend/user-web`, `frontend/admin-web`, `frontend/agent-web` |
| Backend Core | `backend/api-gateway`, `backend/data-service`, `backend/policy-service`, `backend/audit-service` |
| Identity And Crypto | `backend/identity-service`, `backend/crypto-service` |
| Blockchain | `backend/blockchain-service`, `contracts`, `chain-config` |
| Storage And Infra | `backend/storage-service`, `infra`, `data` |
| Federated Learning | `backend/fl-service`, `fl` |
| AI Agent | `backend/agent-service`, `frontend/agent-web` |

## Notes

- Keep module boundaries stable before writing large amounts of code.
- Prefer adding module-level README files before implementing full business logic.
- Use one owner per module, even if multiple people collaborate on it.

## 2026-06-22 Next-Step Work Arrangement

下面这部分按“当前 Verkle-compatible demo commitment 已经跑通”的前提来安排下一步。

### Current Baseline

当前已经可以视为稳定底座的部分：

1. `frontend/user-web`
   已能完成上传、访问、Verkle 视图、Verkle 审计展示
2. `backend/platform-api`
   已能串起 MySQL / Redis / PostgreSQL / IPFS / WeBASE / FISCO BCOS
3. `services/privacy-service`
   已能提供：
   - AES-GCM 打包
   - demo commitment 生成
   - demo proof 校验
4. `scripts/run_verkle_backend_smoke.ps1`
   已能脚本化跑通 `qingdao / weifang` 两轮后端联调

这意味着：

`接下来不需要为了等待正式密码学版 Verkle 而暂停主线开发。`

### ABE Work Arrangement

建议把 ABE 部分拆成两阶段：

1. 第一阶段：
   保持当前 `MVP_POLICY_WRAPPED_DEK` 外部接口不变，只补强密钥流转、策略语义、错误处理和审计字段
2. 第二阶段：
   在不改上传/访问主流程 DTO 的前提下，把内部 `wrappedDek` 逻辑替换到更接近正式 `MA-CP-ABE`

建议交付顺序：

1. 明确 `policyExpr` 的正式语法
2. 明确 authority / attribute / grantStatus 的字段模型
3. 在 `privacy-service` 内为 `ctAbeMode` 预留多实现分支
4. 在 `platform-api` 中增加更细的策略/密钥错误码
5. 再考虑真正引入正式 ABE 算法依赖

建议人数：

1. `1` 人主做密码接口与策略模型
2. `1` 人配合做后端 DTO / 审计 / 联调

### AI Agent Work Arrangement

AI Agent 不建议现在就直接深度嵌入所有主业务内部。

当前更稳的做法是：

1. 把 Agent 当成：
   `受控编排层`
2. 让它只调用已经稳定的主框架能力：
   - 查询资源
   - 查询审计
   - 发起受控访问申请
   - 查看系统状态
3. 不允许 Agent 直接绕过：
   - 策略控制
   - proof 验证
   - 链上审计

建议第一阶段能力：

1. 自然语言转查询条件
2. 解释 `verkle-audit` 结果
3. 解释访问被拒绝的原因
4. 生成演示用业务摘要

建议第二阶段能力：

1. 审批辅助
2. 风险提示
3. 调度建议
4. 结合联邦学习结果做解释型问答

建议人数：

1. `1` 人负责 `agent-service`
2. `1` 人负责 `frontend/agent-web` 或把 Agent 面板嵌进现有前端

### Federated Learning Work Arrangement

联邦学习和主框架不是“完全无关”，但它们可以：

`相对独立开发，后期再通过稳定接口挂接。`

当前最合适的边界是：

1. 联邦学习自己先独立完成：
   - 数据预处理
   - 训练任务
   - 模型聚合
   - 指标评估
2. 与主框架的交汇点只先收敛在：
   - 数据集元信息
   - 训练任务登记
   - 结果摘要上链或入库
   - Agent 查询接口

所以判断是：

1. `AI Agent`
   不是完全独立，因为它要调用主框架接口和权限边界
2. `联邦学习`
   相对更独立，可以较长时间并行开发，只在任务登记、结果接入和审计层与主框架汇合

换句话说：

1. `AI Agent`
   是“上层编排能力”，天然依赖主框架
2. `联邦学习`
   是“并行能力模块”，可以先独立跑，再接回主框架

### Coupling Judgment

当前模块独立性可以这样理解：

1. `区块链 / 存储 / 后端主流程 / Verkle`
   属于主干，不可拆开
2. `ABE`
   与主干强相关，但可以分阶段替换内部实现
3. `AI Agent`
   与主框架不是独立关系，它依赖主框架稳定接口
4. `联邦学习`
   与主框架是弱耦合关系，可以先独立推进

### Recommended Order

建议下一阶段按这个顺序推进：

1. 继续保持 Verkle / upload / access / audit 脚本化可验
2. 先补强 ABE 的正式策略模型与接口边界
3. 再推进 DID / VC 与授权撤销
4. 并行推进联邦学习最小可运行流
5. 在主框架接口稳定后接入 AI Agent
6. 最后单独排期升级正式密码学版 Verkle

### Minimal Staffing Recommendation

如果当前只按“下一阶段能推进并可交付”来配人，建议最少：

1. `2` 人：
   后端主流程 + 存储/链联调
2. `1` 人：
   密码学 / ABE / Verkle 引擎边界
3. `1` 人：
   前端与演示交互
4. `1` 人：
   联邦学习
5. `1` 人：
   AI Agent / 文档 / 演示编排

如果人数不足，优先顺序是：

1. 主干链路
2. ABE / DID
3. 联邦学习
4. AI Agent

## 2026-06-23 Updated Team Plan Based On Latest PDF

下面这部分直接按你们当前真实人员结构来安排，不再按“理想团队”空谈。

当前已知约束是：

1. 你本人是：
   `统筹安排人 + 主要开发工程师`
2. `1` 人现在立刻开始：
   `联邦学习`
3. `1` 人现在立刻开始：
   `AI Agent`
4. 还有 `2` 位新人暂未分配

同时，最新 `国网挑战杯.pdf` 已经把最小可运行闭环和比赛交付物说得更明确：

1. 必须先完成：
   `身份闭环 + 数据闭环 + 访问闭环 + 三链闭环`
2. 然后再扩展：
   `隐私统计 + 联邦学习 + AI Agent`

这意味着当前最合理的真实分工不是“平均分人”，而是：

`先保证主干闭环由最熟悉代码的人继续控盘，再把联邦学习、AI Agent 和可快速上手的配套模块交给其他人。`

### Role Assignment

建议现在就固定成下面 `5` 个责任位。

#### R1 你本人：主干架构与集成负责人

职责范围：

1. 继续负责：
   `Verkle / upload / access / audit / 三链联调 / 后端主流程`
2. 统一接口边界：
   - DTO
   - 审计字段
   - region / root / relayRoot / policyExpr 等核心字段
3. 把正式密码学 Verkle、正式 ABE、DID/VC 的升级边界继续收口
4. 负责最终联调、收敛、验收脚本和答辩主线

本阶段直接交付：

1. 稳定的一键联调脚本
2. 稳定的后端闭环
3. 稳定的三链与四库存证口径
4. 统一的接口文档与演示口径

#### R2 联邦学习负责人

职责范围：

1. 立刻启动：
   `FRLDRC + PFL-EPM`
   这条联邦智能分析主线
2. 先不要依赖前端完整页面
3. 优先产出：
   - 可运行训练脚本
   - 固定输入输出格式
   - 指标计算脚本
   - 小样本演示结果

本阶段直接交付：

1. `FRLDRC`
   - 负荷预测最小可运行流
   - `MAE / RMSE / MAPE`
2. `PFL-EPM`
   - 运行监测 / 告警最小可运行流
   - `准确率 / 召回率 / F1`
3. 供平台调用的：
   - 任务输入 JSON
   - 结果输出 JSON

对接边界：

1. 输入只先依赖：
   `脱敏 CSV / 预处理后的样例数据`
2. 输出先统一成：
   `任务摘要 + 指标摘要 + 结果文件路径`
3. 暂时不要直接改主后端数据库结构

#### R3 AI Agent 负责人

职责范围：

1. 立刻启动：
   `Agent 受控交互层`
2. 第一阶段只允许调用：
   - 资源查询
   - 审计查询
   - system-status
   - Verkle audit 解释
3. 不允许第一阶段就做：
   - 绕过策略直接访问明文
   - 自主发链上写操作
   - 直接碰联邦训练底层脚本

本阶段直接交付：

1. 一份工具清单：
   `tool schema`
2. 一份受控提示词模板：
   - 查询型
   - 解释型
   - 报告型
3. 一个最小可运行服务：
   能返回查询摘要与解释文本

对接边界：

1. 先通过只读 API 对接主框架
2. 第二阶段再接入：
   `受控访问申请`
3. 最后再考虑：
   `联邦结果解释 + 自动报告`

#### R4 新人 A：DID / VC / 撤销与审计协助

这是最适合新人立即进入、又能快速产出价值的方向。

职责范围：

1. 跟着现有接口梳理：
   - DID
   - VC
   - authority
   - revocation
2. 先做文档化、接口化和测试化
3. 再补：
   - 正反样例
   - 审计字段对齐
   - 状态查询页或接口页

本阶段直接交付：

1. DID / VC / 撤销测试用例清单
2. 状态流转说明
3. 与比赛功能验证表一一对应的验证脚本或接口说明

#### R5 新人 B：前端静态演示与测试数据协助

这位新人不要先碰服务器常驻进程和高风险部署。

优先职责：

1. 协助整理：
   - 前端展示字段
   - 演示数据
   - 页面文案
   - 图表字段
2. 优先做：
   `静态构建 + 受控部署`
   这条路线
3. 不要在服务器上常驻跑：
   `vite`

本阶段直接交付：

1. 演示页面字段核对表
2. 比赛截图与录屏素材清单
3. 前端静态构建发布说明

### Four-Week Immediate Schedule

这里不再写空泛的“长期规划”，而是按你们现在立刻要动起来的 `4` 周安排。

#### Week 1

目标：

1. 你本人继续稳住：
   `Verkle + upload + access + audit`
2. 联邦学习负责人跑通：
   `最小训练脚本 + 指标输出`
3. AI Agent 负责人跑通：
   `只读查询 + 审计解释`
4. 新人 A 整理：
   `DID / VC / 撤销 功能验证清单`
5. 新人 B 整理：
   `前端静态演示字段与测试数据`

验收结果：

1. 主干联调脚本可重复跑
2. 联邦学习能离线跑出第一版结果
3. Agent 能解释一条 Verkle 审计结果

#### Week 2

目标：

1. 你本人补强：
   `DID / VC / 撤销 与主后端对接`
2. 联邦学习负责人完成：
   - `FRLDRC` 一版
   - `PFL-EPM` 一版
3. AI Agent 负责人完成：
   - 系统状态查询
   - 资源查询
   - 审计解释报告
4. 新人 A 补：
   `正反样例测试`
5. 新人 B 补：
   `前端演示页字段、图表和文案对齐`

验收结果：

1. 能对照 PDF 第 `11.2` 节给出一版功能验证清单
2. 能拿出第一版联邦学习结果指标
3. 能拿出第一版 Agent 演示问答

#### Week 3

目标：

1. 你本人开始控盘：
   `Paillier / ABE / DID / Verkle / 审计`
   的系统集成边界
2. 联邦学习负责人把结果固化成：
   `接口或文件输出规范`
3. AI Agent 负责人接入：
   `联邦结果解释`
4. 新人 A 协助做：
   `撤销前后访问阻断验证`
5. 新人 B 协助做：
   `前端页面录屏与静态部署回归`

验收结果：

1. 主干链路和联邦结果开始汇合
2. Agent 开始能解释预测/告警结果
3. 撤销阻断形成可演示对比

#### Week 4

目标：

1. 你本人收口：
   `比赛闭环`
2. 联邦学习负责人收口：
   指标表与结果样例
3. AI Agent 负责人收口：
   对话演示与报告模板
4. 新人 A 收口：
   功能验证表与实验数据表
5. 新人 B 收口：
   截图、静态页面、演示素材

验收结果：

1. 对齐 PDF 的最小可运行闭环
2. 对齐 PDF 的功能验证清单
3. 对齐 PDF 的实验数据清单
4. 形成可录屏、可答辩、可回放的稳定版本

### Expected Deliverables

按最新 PDF，你们当前最该追的交付物不是“模块数量”，而是下面这些可验收结果：

1. `identity closure`
   - DID 注册
   - VC 签发
   - VC 验证
   - authority 查询
2. `data closure`
   - 上传
   - 密文落 IPFS
   - MySQL / Redis / PostgreSQL / 链上锚定一致
3. `access closure`
   - 合法访问成功
   - 非法访问被拒
   - 撤销后访问阻断
4. `three-chain closure`
   - qingdao
   - weifang
   - relay
5. `federated outputs`
   - 预测指标
   - 告警指标
6. `agent outputs`
   - 查询摘要
   - 审计解释
   - 报告草稿

### Integration Rules

为了避免后面再出现“各做各的，最后接不上”的情况，必须统一下面这些对接纪律：

1. 联邦学习负责人只承诺：
   `稳定输入 / 稳定输出`
   不直接侵入主后端代码
2. AI Agent 负责人只通过：
   `受控 API`
   调主框架，不直连数据库、不直写链
3. 两位新人先做：
   `低风险、高文档化、高可验证`
   的工作，不直接接管服务器长期运行
4. 所有模块的最终汇合点都由你本人控盘

### Risk Reminder

继续强调一遍当前最重要的工程纪律：

1. 服务器端不要恢复 `vite` 常驻开发模式
2. 不要给开发态服务配 `Restart=always`
3. 所有性能测试优先走：
   `SSH -> 127.0.0.1`
4. 所有新模块先做：
   `短时启动 -> 验证 -> 关闭`
5. 在比赛主线没有完全收口前，不要让新人直接改动主干数据库结构和链上字段语义
