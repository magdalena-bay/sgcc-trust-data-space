# 正式 Verkle 引擎接入方案

更新时间：`2026-06-26`

这份文档只回答一个问题：

`当前已经跑通的 Java + Python + Verkle-compatible demo commitment 工程，后续怎样接入正式密码学版 Verkle 引擎，并且尽量不破坏现有平台与业务逻辑。`

---

## 1. 先给最终结论

当前最合理的路线不是：

`把整个平台迁到 Go 或 Rust。`

而是：

`继续保留 Java + Python 主工程，把正式 Verkle 引擎作为独立 proof service 接入。`

也就是：

1. `Spring Boot`
   继续承接平台 API、数据库、链写编排、审计与业务流程
2. `Python`
   继续承接隐私计算、算法实验、联邦学习、Agent 等能力
3. `正式 Verkle 引擎`
   以独立服务形式接入
4. 平台主后端只继续依赖：
   `VerkleEngineGateway`
   这一层抽象边界

---

## 2. 为什么现在不应该整体迁语言

当前项目已经真实跑通并联调过的主干包括：

1. `Spring Boot`
2. `MySQL / Redis / PostgreSQL / IPFS`
3. `FISCO BCOS / WeBASE`
4. `Vue 前端静态构建 -> platform-api`
5. `Verkle-compatible demo commitment`
6. `链写 outbox / batch / worker`

如果现在整体迁到 Go 或 Rust，会同时引入：

1. 平台 API 重写
2. DTO / Mapper / 数据库访问层重写
3. 区块链 HTTP 接入层重写
4. 脚本、部署、联调说明重写
5. 已有文档、演示和测试链路重写

这会直接拖慢比赛主线，而且对：

1. `单链 8 节点`
2. `只测上链 completed TPS > 10000`

这两个硬指标没有立刻的决定性帮助。

---

## 3. 当前已经具备的“可替换边界”

现在主工程已经把 Verkle 证明能力收口在：

1. `VerkleEngineGateway`
2. `DemoVerkleEngineGateway`
3. `CommitmentResult`
4. `StoredProofEnvelope`

当前 Redis 里保存的也不是裸 proof，而是带有：

1. `scheme`
2. `engineVersion`
3. `proofType`
4. `leafKey`
5. `valueDigest`
6. `root`
7. `proofPayload`

的 proof envelope。

这意味着后续正式引擎替换时：

`前端、MySQL、IPFS、链锚定、审计接口都不需要整体推翻。`

---

## 4. 语言生态怎么判断

### 4.1 Go

Go 生态的优势通常在：

1. 以太坊客户端相关实现接近生产
2. 性能与部署都比较友好
3. 独立服务化非常自然

如果后续正式 Verkle 证明实现最终主要依赖 Go，那么最合理的接入方式是：

`Go proof service + HTTP/gRPC RPC`

而不是：

`整个平台迁 Go`

### 4.2 Rust

Rust 生态的优势通常在：

1. 密码学实现和底层库较强
2. 性能高
3. 类型与内存安全好

如果后续选择 Rust，同样推荐：

`Rust proof service + HTTP/gRPC RPC`

### 4.3 Python

Python 生态的优势通常在：

1. 研究代码接入快
2. 算法实验快
3. 你们当前团队已经在使用

但 Python 更适合作为：

1. 研究原型
2. 过渡实现
3. 包装外部引擎

不一定适合作为最终高性能正式 Verkle 引擎本体。

---

## 5. 推荐的最终接入形态

推荐采用：

`平台主工程 + 正式 Verkle 引擎服务`

### 5.1 服务边界

主后端只通过 RPC 调用以下能力：

1. `commitments(items)`
2. `buildProofEnvelope(leafKey, valueDigest, items)`
3. `verifyProofEnvelope(leafKey, valueDigest, envelope, root)`
4. `health()`
5. `metadata()`

### 5.2 Java 侧继续保留的职责

1. 资源上传编排
2. MySQL / Redis / IPFS / 区块链写入
3. proof envelope 落 Redis
4. `/verkle`
5. `/verkle-audit`
6. `/access`

### 5.3 正式引擎服务负责的职责

1. 正式 Verkle commitment 计算
2. 正式 proof 生成
3. 正式 proof 验证
4. scheme / engineVersion 管理

---

## 6. 推荐的 RPC 协议

当前阶段优先推荐：

`HTTP + JSON`

原因：

1. 当前 Java 主后端接入最简单
2. 便于抓包和排障
3. 对比赛阶段更友好

如果后续性能和稳定性要求更高，再升级：

`gRPC`

### 6.1 最小接口草案

#### `POST /verkle/commitments`

请求：

```json
{
  "items": [
    {"key": "D001", "value": "hash1"},
    {"key": "D002", "value": "hash2"}
  ]
}
```

返回：

```json
{
  "scheme": "verkle-formal",
  "engineVersion": "v1",
  "root": "0x...",
  "treeMeta": {}
}
```

#### `POST /verkle/proof/build`

请求：

```json
{
  "items": [
    {"key": "D001", "value": "hash1"},
    {"key": "D002", "value": "hash2"}
  ],
  "leafKey": "D001",
  "valueDigest": "hash1"
}
```

返回：

```json
{
  "scheme": "verkle-formal",
  "engineVersion": "v1",
  "proofType": "membership",
  "leafKey": "D001",
  "valueDigest": "hash1",
  "root": "0x...",
  "proofPayload": {}
}
```

#### `POST /verkle/proof/verify`

请求：

```json
{
  "leafKey": "D001",
  "valueDigest": "hash1",
  "root": "0x...",
  "proofEnvelope": {}
}
```

返回：

```json
{
  "valid": true
}
```

---

## 7. 推荐的替换步骤

### 第一步

继续保留当前：

`DemoVerkleEngineGateway`

作为默认实现

### 第二步

新增：

`RpcVerkleEngineGateway`

让它实现同一接口：

`VerkleEngineGateway`

### 第三步

通过配置切换：

1. `demo`
2. `rpc`

### 第四步

先让正式引擎只参与：

1. `commitments`
2. `buildEnvelope`
3. `verify`

不要一开始就改动：

1. 数据库存储格式
2. 前端响应结构
3. 区块链锚点结构

---

## 8. 当前最推荐的工程策略

如果比赛周期内要兼顾：

1. 业务逻辑继续开发
2. 8 节点
3. 10000+ 专项
4. 正式 Verkle 可升级性

那么推荐顺序是：

1. 继续以当前 `Verkle-compatible demo` 为底层稳定边界推进业务开发
2. 把正式引擎接入方案先按 RPC 方式抽象好
3. 后续如果选定 Go 或 Rust 具体实现，再独立落 proof service
4. 不要为了正式 Verkle 预研去整体重写平台

---

## 9. 一句话结论

当前最稳、最符合比赛主线的方案是：

`平台主工程继续保持 Java + Python，正式 Verkle 引擎后续以 Go 或 Rust 独立 proof service 的方式接入，通过 VerkleEngineGateway / proof envelope / RPC 边界平滑替换，而不是整体迁语言。`
