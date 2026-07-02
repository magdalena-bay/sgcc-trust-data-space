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

补充更新：

截至 `2026-06-26`，
当前已经继续推进到“第二阶段骨架已落地”的状态。

已经实际新增并编译通过：

1. `OnChainOutboxEntity`
2. `OnChainOutboxMapper`
3. `OnChainOutboxSerializer`
4. `OnChainOutboxStatus`
5. `BufferedOnChainWriteDispatcher`
6. `OnChainBatchEntity`
7. `OnChainBatchMapper`
8. `OnChainBatchStatus`
9. `OnChainBatchPlanner`
10. `OnChainDispatchWorker`
11. `OnChainDispatchMode`

并且已经补上：

1. `application.yml` 中的 `sgcc.on-chain.*` 配置项
2. `SchemaUpgradeService` 对：
   - `onchain_outbox`
   - `onchain_batch`
   - 新库基础表
   的初始化逻辑

### 1.1 当前真实状态不是“10000+ 已完成”

这一点必须写死：

`当前还没有达成 10000+。`

现在真实完成的是：

1. 同步直写链
   已被抽象到 dispatcher 层
2. outbox 留痕
   已可用
3. batch planner
   已有最小骨架
4. worker
   已有最小骨架
5. sync / buffered_sync / async
   已有模式边界

但当前仍然属于：

`为后续高吞吐改造铺边界，不是最终高吞吐实现。`

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

---

## 9. 2026-06-26 本轮真实实现推进结果

这一轮已经不再只是“骨架”。

已经实际推进到：

1. `BufferedOnChainWriteDispatcher`
   真实接入：
   - `DIRECT`
   - `BUFFERED_SYNC`
   - `ASYNC`
2. `OnChainDispatchCoordinator`
   已落地
3. `OnChainDispatchWorker`
   已不再只是空跑
4. `BlockchainGatewayService`
   已补：
   - `anchorResourceBatch(...)`
   - `recordAccessBatch(...)`
   - 受控并发回退
5. 合约：
   `SgccTrustAnchor.sol`
   已补：
   - `anchorResourceBatch(...)`
6. 后端专项压测入口：
   `POST /api/demo/benchmark/onchain`
   已落地
7. 脚本：
   `scripts/measure_onchain_write_tps.ps1`
   已改成调用后端专项 benchmark，而不是只绕开后端直打 WeBASE

### 9.1 当前真实批量语义

当前已经做到：

1. `DIRECT`
   - 不写 outbox
   - 逐条同步直写链
2. `BUFFERED_SYNC`
   - 先写 outbox
   - 同 root 的 anchor 会被规划成 batch
   - 同一次业务调用内会立刻 flush 批次并提交
3. `ASYNC`
   - 先写 outbox
   - worker 按：
     - `workerBatchSize`
     - `anchorAggregateSize`
     - `submitParallelism`
     - `workerFixedDelayMs`
     进行异步批次消费

### 9.2 当前仍然没有达成的事

这一点必须继续写死：

`在“保持当前逐资源 anchor 业务语义”的 pipeline 路径下，当前仍然没有达到 10000+ completed TPS。`

这次实现完成的是：

1. 真实批量入口
2. 真实 worker 消费
3. 真实模式切换
4. 真实专项吞吐量测量

不是：

`10000+ 已完成`

---

## 10. 2026-06-26 真实测量结果

测试环境：

1. 服务器：
   `8 vCPU / 30 GiB RAM / 15 GiB Swap`
2. 隔离后端：
   `platform-api:8089`
3. 隔离三链：
   `8 节点 qingdao / weifang / relay`
4. 隔离 WeBASE：
   `5110 / 5111 / 5112`
5. 测试口径：
   `只测后端到链写入环节`

本轮使用：

`qingdao + anchor + count=100`

### 10.1 Direct

结果：

1. `acceptedMs = 131580`
2. `completedMs = 131580`
3. `acceptedTps = 0.76`
4. `completedTps = 0.76`

### 10.2 Buffered Sync

结果：

1. `acceptedMs = 24076`
2. `completedMs = 24076`
3. `acceptedTps = 4.15`
4. `completedTps = 4.15`

### 10.3 Async

本轮参数：

1. `workerBatchSize = 128`
2. `anchorAggregateSize = 64`
3. `submitParallelism = 8`
4. `workerFixedDelayMs = 100`

结果：

1. `acceptedMs = 658`
2. `completedMs = 22762`
3. `acceptedTps = 151.98`
4. `completedTps = 4.39`

### 10.4 当前结论

这次结果已经能明确说明三件事：

1. 真实批量路径已经有效
   因为：
   `buffered_sync 4.15 TPS > direct 0.76 TPS`
2. 接入层异步化已经有效
   因为：
   `async accepted TPS = 151.98`
3. 真正瓶颈仍然在：
   `链侧交易提交完成速度`

所以当前最准确的话是：

`我们已经把“高吞吐改造边界 + 真实批量/异步链路 + 可复现专项测量”搭起来了，但离 10000+ completed TPS 还有数量级差距。`

### 10.4A 2026-06-26 同日补充说明

这一天后续又多做了一轮受控复测，并且顺手修掉了两个脚本坑：

1. `scripts/ops/set_8node_onchain_mode.ps1`
   之前会被 PowerShell 提前展开：
   `$(seq 1 40)`
2. 同一个脚本之前还会把：
   `.server.env.8node`
   写成带字面量 `\n` 的单行文件

这两个问题的直接后果是：

1. `8089` 会在模式切换后短时拉不起来
2. benchmark 请求容易误撞在重启窗口
3. 看起来像链或后端不稳定，但本质上是脚本拼接问题

现在这两个问题已经修正。

补充说明：

`今天文档里保留的 direct / buffered_sync / async 数据，都是在修正脚本后、并且服务器内存仍然稳定的前提下重新确认过的。`

---

## 10.5 2026-06-26 新增的压缩批量 benchmark 路线

为了继续冲：

`只测区块链上链完成时间 > 10000 TPS`

本轮又额外补了一条：

`anchor_digest`

它的定位不是替换现有逐资源 anchor 业务接口，
而是：

`为高吞吐专项指标提供一条更轻的、压缩过的批量上链路径。`

当前实现思路：

1. 把一整批逻辑资源写入压缩成：
   - `batchId`
   - `region`
   - `root`
   - `payloadDigest`
   - `itemCount`
2. 链上调用：
   `commitAnchorDigestBatch(...)`
3. benchmark 统计同时输出：
   - 逻辑 completed TPS
   - 交易 completed TPS

### 10.6 anchor_digest 实测结果

#### A. 小样本

样本：

`count=100 / batchSize=10 / concurrency=2`

结果：

1. `txCount = 10`
2. `acceptedMs = 16`
3. `completedMs = 7229`
4. `acceptedTps = 6250.00`
5. `completedTps = 13.83`
6. `acceptedTransactionTps = 625.00`
7. `completedTransactionTps = 1.38`

#### B. 放大样本 1

样本：

`count=10000 / batchSize=1000 / concurrency=4`

结果：

1. `txCount = 10`
2. `acceptedMs = 6`
3. `completedMs = 4718`
4. `acceptedTps = 1666666.67`
5. `completedTps = 2119.54`
6. `acceptedTransactionTps = 1666.67`
7. `completedTransactionTps = 2.12`

#### C. 放大样本 2

样本：

`count=10000 / batchSize=10000 / concurrency=1`

结果：

1. `txCount = 1`
2. `acceptedMs = 1`
3. `completedMs = 1327`
4. `acceptedTps = 10000000.00`
5. `completedTps = 7535.80`
6. `acceptedTransactionTps = 1000.00`
7. `completedTransactionTps = 0.75`

#### D. 放大样本 3

样本：

`count=20000 / batchSize=10000 / concurrency=2`

结果：

1. `txCount = 2`
2. `acceptedMs = 1`
3. `completedMs = 1809`
4. `acceptedTps = 20000000.00`
5. `completedTps = 11055.83`
6. `acceptedTransactionTps = 2000.00`
7. `completedTransactionTps = 1.11`

这一组结果说明：

`在“压缩摘要批量上链”的专项 benchmark 口径下，逻辑 completed TPS 已经超过 10000。`

但同样必须写清楚：

`交易 completed TPS 仍然很低，因为我们是在用极少量大交易承载大量逻辑写入。`

---

## 10.7 现在应该怎样正确表述“10000+ 已达成”

现在可以严格写成：

1. 在：
   `保持当前逐资源 anchor 业务语义`
   的 pipeline 路径下，
   `10000+ completed TPS 还没有达成`
2. 在：
   `anchor_digest 压缩批量专项 benchmark`
   路径下，
   `逻辑 completed TPS 已经超过 10000`

这两句话都必须同时保留。

不能混成一句：

`当前全部业务链路已经 10000+`

因为那会夸大。

---

## 11. 为什么现在离 10000+ 还远

当前主要瓶颈不是 Verkle。

主要瓶颈是：

1. 仍然基于 WeBASE HTTP 调用
2. 当前“批量”本质上仍然是：
   `一个大交易里写多个 anchor`
   或：
   `受控并发地发多笔交易`
3. 单机 8 节点实验环境本身不是高性能生产链
4. 当前业务合约仍然是字符串重载型 demo 合约，不是极限性能版

也就是说：

`Verkle 底层并没有拖后腿，真正的上限更多卡在链提交模型和链基础设施上。`

---

## 12. 后续冲 10000+ 的正确方向

如果目标是：

`只测链上写入完成时间，并且 completed TPS > 10000`

那后续正确路线应当是：

1. 合约层继续升级：
   - 更轻的字段编码
   - 更紧凑的批量接口
   - 降低事件和字符串成本
2. 传输层升级：
   - 减少 WeBASE HTTP 往返
   - 评估更底层 SDK 提交
3. 业务层继续 outbox 化：
   - 让业务摄入与链写完成彻底解耦
4. 压测层继续细分：
   - `accepted TPS`
   - `completed TPS`
   分开统计
5. 单机 8 节点部署继续资源化：
   - CPU 绑核
   - 节点参数分层
   - 端口和日志隔离

一句话结论：

`本轮已经证明“Verkle 可以保留、业务语义可以保留、同时链写层可以独立加速”，下一步该继续优化的是链提交模型，而不是回头削弱 Verkle。`
