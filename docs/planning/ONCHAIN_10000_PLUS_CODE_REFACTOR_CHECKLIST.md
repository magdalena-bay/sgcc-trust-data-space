# On-Chain 10000+ Code Refactor Checklist

更新时间：`2026-06-26`

这份清单只回答一个问题：

`为了后续把链上写入方案升级到 10000+ 路线，代码层面应该先新增哪些类，哪些类现在不要乱动。`

注意：

`这里的目标不是今天就把当前同步单笔上链改炸，而是先把可演进边界抽出来。`

---

## 1. 当前已经先落地的第一步

本轮已经先完成一层最小抽象：

1. `com.sgcc.platform.onchain.OnChainWriteDispatcher`
2. `com.sgcc.platform.onchain.DirectOnChainWriteDispatcher`
3. `com.sgcc.platform.onchain.AnchorResourceWriteCommand`
4. `com.sgcc.platform.onchain.RecordAccessWriteCommand`

当前行为仍然是：

`同步直写链`

但这一步已经把：

`DemoResourceService -> BlockchainGatewayService`

之间的直接耦合先拆薄了一层。

---

## 2. 现在优先新增的类

这些类建议优先新增。

### 2.1 onchain 事件与调度层

建议新增：

1. `com.sgcc.platform.onchain.OnChainOutboxEntity`
2. `com.sgcc.platform.onchain.OnChainBatchEntity`
3. `com.sgcc.platform.onchain.OnChainOutboxMapper`
4. `com.sgcc.platform.onchain.OnChainBatchMapper`
5. `com.sgcc.platform.onchain.BufferedOnChainWriteDispatcher`
6. `com.sgcc.platform.onchain.OnChainBatchPlanner`
7. `com.sgcc.platform.onchain.OnChainDispatchWorker`
8. `com.sgcc.platform.onchain.OnChainDispatchProperties`

职责建议：

1. `OnChainOutboxEntity`
   保存待上链事件
2. `OnChainBatchEntity`
   保存批次元数据与状态
3. `BufferedOnChainWriteDispatcher`
   把业务写入先投递到 outbox，而不是直接打链
4. `OnChainBatchPlanner`
   负责按条数窗、时间窗、链名、事件类型聚合
5. `OnChainDispatchWorker`
   负责真正把批次提交到链

### 2.2 onchain DTO 与状态模型

建议新增：

1. `OnChainWriteStatus`
2. `OnChainBatchStatus`
3. `OnChainWritePayload`
4. `OnChainBatchSummary`

目的：

1. 不要让业务层自己拼 JSON
2. 不要把批次逻辑散在多个 service 里

### 2.3 合约与注册抽象层

建议新增：

1. `com.sgcc.platform.service.BlockchainContractResolver`
2. `com.sgcc.platform.service.BlockchainTransportClient`
3. `com.sgcc.platform.service.BlockchainBatchGatewayService`

目的：

1. 让 `BlockchainGatewayService`
   不再同时承担：
   - 合约准备
   - HTTP 传输
   - 单笔直写
   - 后续批次扩展
2. 为后续切换：
   `WeBASE HTTP -> 更底层 SDK / async transport`
   留入口

---

## 3. 现在优先保留不动的类

下面这些类当前不要大动。

### 3.1 Verkle 边界层

先不要大动：

1. `VerkleEngineGateway`
2. `DemoVerkleEngineGateway`
3. `CommitmentResult`
4. `StoredProofEnvelope`
5. `VerkleProofEnvelopeCodec`

原因：

1. 这是当前 Verkle-compatible 的稳定工程边界
2. 未来升级正式密码学版 Verkle，也主要应该在这里替换内部实现
3. 不应该为了吞吐量重构，把这层一起搅乱

### 3.2 隐私与存储客户端

先不要大动：

1. `PrivacyClient`
2. `IpfsClient`
3. `PostgresShadowService`

原因：

1. 当前瓶颈不在这里
2. 它们已经稳定参与全链路联调

### 3.3 Controller 与对外 DTO

先不要大动：

1. `DemoDataController`
2. `UploadRequest`
3. `UploadResponse`
4. `AccessRequest`
5. `AccessResponse`
6. `ResourceVerkleResponse`
7. `ResourceVerkleAuditResponse`

原因：

1. 这是对页面、脚本、演示最敏感的一层
2. 当前需要的是内部调度升级，不是对外接口返工

---

## 4. 当前允许小步改造的类

### 4.1 `DemoResourceService`

允许改，但只建议做：

1. 抽离“写入调度”
2. 抽离“批次锚定规划”
3. 抽离“写入后审计状态记录”

不要做：

1. 把上传主流程拆得面目全非
2. 把 Verkle 重建逻辑和业务语义一起打散

### 4.2 `BlockchainGatewayService`

允许改，但只建议做：

1. 拆成：
   - 合约解析
   - 传输客户端
   - 单笔直写实现
   - 批次写实现
2. 保留当前方法签名兼容期

不要做：

1. 现在就把所有旧方法一次性删掉

---

## 5. 推荐分阶段实施顺序

## 5.1 第一阶段

只做边界抽象，不改业务语义。

1. 引入：
   `OnChainWriteDispatcher`
2. 新增 outbox 表与 entity
3. 增加 worker 骨架，但先不开启常驻调度

## 5.2 第二阶段

开始双写模式验证。

1. 业务仍然同步直写
2. 同时写一份 outbox
3. 只做链下批次规划演练，不正式切换

## 5.3 第三阶段

切到可控批次。

1. 某些场景改为：
   `先 outbox，再 worker 批次上链`
2. 保留 feature flag

## 5.4 第四阶段

才开始追逐高吞吐指标。

1. 并发发送
2. 批次聚合
3. 交易提交 TPS
4. 业务事件摄入 TPS

---

## 6. 需要新增的表

建议至少新增两张表：

### 6.1 `onchain_outbox`

字段建议：

1. `id`
2. `event_type`
3. `chain_name`
4. `business_key`
5. `payload_json`
6. `status`
7. `retry_count`
8. `last_error`
9. `created_at`
10. `updated_at`

### 6.2 `onchain_batch`

字段建议：

1. `id`
2. `chain_name`
3. `batch_type`
4. `event_count`
5. `batch_root`
6. `tx_hash`
7. `status`
8. `created_at`
9. `updated_at`

---

## 7. 现在绝对不要做的事

1. 不要为了追吞吐量，把 Verkle 整层删掉
2. 不要把 `DemoResourceService` 一次性拆成十几个 service 再失去主流程可读性
3. 不要现在就把同步直写链完全关闭
4. 不要引入任何需要服务器常驻的开发态 watcher
5. 不要把 worker 先做成 `Restart=always` 的自启服务

---

## 8. 当前建议结论

代码级上，当前最正确的做法不是“大改所有类”，而是：

1. 先新增：
   `onchain 调度层`
2. 先保留：
   `Verkle 边界层`
3. 先稳定：
   `DemoResourceService` 主业务语义
4. 后续通过：
   `outbox -> batch -> worker`
   逐步演进到高吞吐方案

一句话结论：

`现阶段应该优先新增“链写入调度与批次层”，而不是去动 Verkle、DTO、Controller 和对外业务语义。`
