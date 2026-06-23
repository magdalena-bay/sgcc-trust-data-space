# Project Structure And Team Mapping

对应中文主文档：

- `../..\\..\\10-项目代码目录骨架设计与团队分工对照表.md`

本文件用于仓库内快速说明两件事：

1. 推荐代码目录骨架
2. 推荐模块负责分工

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
