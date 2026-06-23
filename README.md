# sgcc-trust-data-space

面向能源可信数据空间的全链路 MVP 工程。

当前这版已经不只是“目录骨架”，而是已经打通了下面这条最小可运行主线：

`前端上传 -> 浏览器端 AES 加密 -> Spring Boot 主后端编排 -> Python 隐私服务 -> MySQL / Redis / PostgreSQL / IPFS -> FISCO BCOS 链上锚定 -> 授权访问 -> 完整性验证 -> 解密展示`

## 当前技术栈

- `frontend/user-web`
  `Vue 3 + TypeScript + Vite + Element Plus + ECharts`
- `backend/platform-api`
  `Java 17 + Spring Boot 3 + MyBatis Plus`
- `services/privacy-service`
  `Python 3.10/3.11 + FastAPI`
- `blockchain`
  `FISCO BCOS + WeBASE-Front + WeCross`

## 当前已经实现的能力

1. 前端支持浏览器端先做 `AES-GCM` 加密，再上传密文正文。
2. 后端会把密文包写入 `IPFS`，把业务索引写入 `MySQL`。
3. 后端会把 `HD_i -> ProofD_i` 的证明映射写入 `Redis`。
4. 后端会把上传与访问审计影子日志写入 `PostgreSQL`。
5. 后端会通过 `WeBASE-Front` 调用三条 `FISCO BCOS` 链上的锚定合约。
6. 访问时会先做策略判断，再做完整性验证，最后才执行解密。
7. 前端可直接展示解密后的曲线数据。

## 四类存储和链上职责

- `MySQL`
  负责 `data_resource`、`access_audit`、`chain_contract_registry`
- `Redis`
  负责保存 `verkle-proof:{HD_i}` 这样的证明键值
- `IPFS`
  负责保存完整密文包 `Package`
- `PostgreSQL`
  负责保存 `shadow_audit_log`
- `FISCO BCOS`
  负责保存资源锚点、根值和访问审计

## 当前实现中的“可运行占位”

这点必须明确说明，避免后面团队误以为已经是最终密码学版本。

1. `CT_ABE` 当前是 `MVP_POLICY_WRAPPED_DEK`
   用 AES-GCM 包装 `DEK`，并把策略表达式作为 AAD 绑定。
2. `Verkle` 当前是 `Verkle-compatible demo commitment`
   保留了 `data_id -> HD_i -> ProofD_i -> Vroot` 的工程落点和校验流程，但还不是正式多项式承诺实现。
3. Redis 中的 proof 现在保存为 `proof envelope`
   外层统一包含 `scheme / engineVersion / proofType / proofPayload / root`，避免业务代码直接绑定 demo proof 的内部字段。

也就是说：

`工程链路已经真实跑通，但密码学内核后面仍可以继续替换升级。`

## 本地目录结构

```text
sgcc-trust-data-space/
├─ frontend/user-web
├─ backend/platform-api
├─ services/privacy-service
├─ contracts/common
├─ deploy
├─ docs
├─ scripts
├─ tests
└─ data
```

## 当前阅读这份 README 时你必须先知道

这套系统是“活环境”，不是静态样板。

所以你以后不要默认：

1. 某条测试数据的 root 永远不变
2. 某个 proof JSON 永远不变
3. 某篇测试文档里写过的哈希值今天还一定一样

你必须先做健康检查，再做手工测试。

目前最重要的两个检查是：

1. `curl http://127.0.0.1:8088/api/demo/health`
2. `curl http://127.0.0.1:8010/health`
3. `curl http://127.0.0.1:8088/api/demo/system-status`

如果 `8010` 不通，那么上传与访问都可能返回 `500`。

`system-status` 现在还会补充：

1. `MySQL / Redis / PostgreSQL / IPFS API / IPFS Gateway` 是否在线
2. `qingdao / weifang / relay` 三条链是否可读
3. 三条链当前登记到的合约地址和登记条数
4. 是否检测到跨链合约地址复用

## Verkle 升级兼容性结论

当前版本已经满足“先继续开发其他模块，后续再把 Verkle-compatible demo commitment 升级成正式密码学版本”的工程前提，原因是：

1. 前端、MySQL、IPFS、区块链锚定、访问控制依赖的是稳定语义：
   `dataId / HD_i / root / proof 是否可验证`
2. 后端与隐私服务之间，已经补上了：
   `scheme + engineVersion + proof envelope`
3. Redis 现在保存的是“证明封装对象”，而不是把 demo proof 的内部结构直接散落在业务层。

这意味着后续真正替换时，主要收敛在：

1. `privacy-service` 内部的 commitment / verify 算法实现
2. 证明序列化规则
3. 正式密码学版本所需的额外依赖或可信参数

而不会再强迫前端、4库落点、链上锚定流程一起重写。

## 服务器当前运行端口

- 前端开发服务器：`5173`
- 平台主后端：`8088`
- 隐私服务：`8010`
- WeBASE-Front：
  - `5100` `qingdao`
  - `5101` `weifang`
  - `5102` `relay`
- IPFS：
  - API `5001`
  - Gateway `8080`
- Redis：`6379`
- PostgreSQL：`5432`
- MySQL：`3306`

## 启动顺序建议

1. 先确认链、WeBASE、Redis、PostgreSQL、IPFS、MySQL 已运行。
2. 启动 `privacy-service`。
3. 启动 `platform-api`。
4. 如需页面演示，优先使用 `platform-api` 托管的静态前端页面。
5. 只有本地开发调试前端时，才在自己电脑上启动 `frontend/user-web`。

补充说明：

1. `2026-06-22` 之后，服务器端先不要把 `frontend/user-web`、`platform-api`、`privacy-service` 设置成任何形式的开机自启。
2. 特别不要把 `vite`、`npm run dev` 或其他开发态 watcher 写进 `systemd`，更不要配 `Restart=always`。
3. 当前服务器上的有效代码目录是：
   `/home/ubuntu/sgcc-trust-data-space`
4. 之前排障阶段还出现过旧目录与历史 service 文件混用
   现在统一以：
   `/home/ubuntu/sgcc-trust-data-space`
   为准
5. 建议优先使用“手动启动 -> 健康检查 -> 立即停掉”的短时验证方式，确认链路和资源都稳定后，再单独设计正式托管方案。

如果服务器缺少项目根目录 `.server.env`，可以先参考当前代码目录下的 `.server.env.example` 补出一份，再启动 `platform-api`。

## 当前服务器基线状态

截至 `2026-06-22`，服务器已经确认的安全基线是：

1. `ssh 22` 已恢复稳定。
2. `Swap 16 GiB` 已添加。
3. 会自动拉起 `Vite/esbuild` 的用户级 `systemd` 服务已经禁用。
4. Docker 容器 `sgcc-postgres`、`sgcc-redis`、`sgcc-ipfs` 当前默认不运行。
5. `WeBASE-Front` 三个 system service 文件仍存在，但当前是 `disabled`，且不应在未复核资源占用前直接改回自启。

这意味着：

1. 当前代码可以继续开发、构建、做短时测试。
2. 当前并不是“整套线上服务常驻运行”的状态。
3. 如果你要恢复全链路运行，需要按依赖顺序手动拉起并逐项检查内存，而不是一次性全开。

## 2026-06-22 手动恢复结果

在“不恢复任何开机自启”的前提下，已经验证下面这组手动运行组合可以稳定拉起：

1. 三套 FISCO BCOS 节点：
   - `qingdao` RPC `20200/20201`
   - `weifang` RPC `20400/20401`
   - `relay` RPC `20600/20601`
2. 三套 `WeBASE-Front`：
   - `5100`
   - `5101`
   - `5102`
3. Docker 容器：
   - `sgcc-postgres`
   - `sgcc-redis`
   - `sgcc-ipfs`
4. `privacy-service`
   - `127.0.0.1:8010`
5. `platform-api`
   - `127.0.0.1:8088`

恢复完成后的资源状态实测约为：

1. `Mem used` 约 `3.1 GiB`
2. `Mem available` 约 `27 GiB`
3. `Swap used` 约 `80 MiB`

这说明当前真正安全的策略是：

1. 允许手动持续运行必要后端链路
2. 继续禁止前端 `vite` / `npm run dev` 常驻
3. 前端试用和演示优先走 `platform-api:8088` 托管静态页面
4. 继续禁止任何未经审核的 `Restart=always` 自启

## 当前 Verkle 联调状态

截至 `2026-06-22`，当前代码已经额外完成两类验证：

1. 页面侧：
   - `platform-api:8088` 已直接托管最新静态前端
   - 页面可查看 `verkle` 视图与 `verkle-audit`
   - 页面可直接填充 `qingdao / weifang` 联调样例
2. 脚本侧：
   - `scripts/run_verkle_backend_smoke.ps1`
   - 已按“SSH 到服务器本机 API”方式跑通 `qingdao` 与 `weifang` 两轮联调

本轮实际跑通的核心结果是：

1. `overallPassed = true`
2. 正确权限访问：
   `granted = true, verified = true`
3. 错误权限访问：
   `granted = false, verified = true`

这说明当前 Verkle-compatible demo commitment 已经不只是“代码存在”，而是已经可以被页面、接口和脚本三种方式重复验证。

## 能否继续开发其他模块

可以。

当前结论已经足够明确：

`现在可以在 Verkle-compatible demo commitment 的基础上，继续推进区块链、前后端、密码学、隐私保护、AI Agent 等其余模块开发。`

原因不是“demo 版已经等于正式版”，而是：

1. 前后端、MySQL、Redis、IPFS、链上锚定依赖的是稳定语义边界：
   `dataId / HD_i / proof envelope / root / verify`
2. 当前 Redis 中保存的不是裸 proof，而是：
   `scheme + engineVersion + proofType + proofPayload + root`
3. `platform-api` 并没有把 demo proof 的内部 JSON 结构散落到所有业务模块，而是通过：
   - `CommitmentResult`
   - `StoredProofEnvelope`
   - `VerkleProofEnvelopeCodec`
   - `VerkleEngineGateway`
   - `DemoVerkleEngineGateway`
   这几层收口

也就是说，当前版本已经满足：

`先继续开发其他模块，后续再把 Verkle 证明引擎升级成正式密码学版本。`

## 后续升级正式 Verkle 是否会冲击其他模块

当前代码已经满足“后续可平滑升级”的核心要求。

更准确地说：

1. 需要优先改动的会是：
   - `services/privacy-service` 内部 commitment / verify 算法
   - proof 序列化规则
   - 正式可信参数、承诺依赖或证明类型
2. 不需要大面积重写的会是：
   - 前端上传与访问页面
   - MySQL 主数据落点
   - Redis proof 键结构
   - IPFS 包结构
   - 区域链 / 中继链锚定主流程

因此当前更推荐的策略不是“停下一切先做正式版”，而是：

1. 先继续开发其余框架和业务模块
2. 同时保持 Verkle 证明边界稳定
3. 在后续单独一轮里，把 `scheme=verkle-compatible-demo` 升级到正式密码学实现

## 当前是否还需要为了正式 Verkle 暂停其他开发

不建议。

除非你们当前阶段的核心目标改成：

`优先交付正式多项式承诺版 Verkle。`

否则按现在的比赛目标和工程状态，更优先的是：

1. 保持当前全链路持续可验
2. 继续推进：
   - DID / VC
   - MA-CP-ABE
   - 区块链业务锚定与审计
   - 前端演示交互
   - AI Agent 与联邦学习展示链路
3. 把正式 Verkle 作为“证明引擎升级任务”独立排期

## 当前推荐开发方向

基于当前环境，下一阶段建议按下面顺序推进：

1. 固化当前 Verkle 页面联调、接口验证和脚本化验收
2. 继续补强 DID / VC 与策略控制链路
3. 推进 `MVP_POLICY_WRAPPED_DEK -> 更接近正式 MA-CP-ABE` 的接口边界
4. 推进 AI Agent / 联邦学习模块时，继续复用当前：
   `dataId / hdValue / root / audit`
   这些稳定字段
5. 正式密码学版 Verkle 作为单独子任务收敛在证明引擎层

## 当前 IPFS 到底怎么实现

当前 IPFS 不是公网现成第三方实例，也不是“直接把数据发到网上某个共享节点”。

当前实现方式是：

1. 服务器本机 Docker 容器：
   `sgcc-ipfs`
2. 镜像：
   `ipfs/kubo:release`
3. 端口映射：
   - API `127.0.0.1:5001`
   - Gateway `127.0.0.1:8080`
4. 数据目录是服务器本机绑定挂载：
   - `/home/ubuntu/platform-infra/data/ipfs-data -> /data/ipfs`
   - `/home/ubuntu/platform-infra/data/ipfs-staging -> /export`

所以当前 IPFS 的本质是：

`你们自己的服务器本机上跑着一个 Kubo 容器实例。`

## 当前文档化开发结论

现在最准确的结论是：

`当前 Verkle-compatible demo commitment 已经完成页面联调、接口验证和脚本化验收，可以作为后续区块链、前后端、密码学、隐私保护、AI Agent 等模块继续开发的稳定工程底座；后续若升级正式密码学版 Verkle，改动将主要收敛在证明引擎层，而不会要求其他业务模块整体返工。`

## 关键文档

- 项目代码与模块说明：
  [docs/planning/PROJECT_STRUCTURE_AND_TEAM_MAPPING.md](/d:/BIT/张川老师课题组/260511-竞赛-挑战杯-山东电网/sgcc-trust-data-space/docs/planning/PROJECT_STRUCTURE_AND_TEAM_MAPPING.md)
- 本轮实现与测试结果：
  [12-全链路MVP实现与测试结果.md](/d:/BIT/张川老师课题组/260511-竞赛-挑战杯-山东电网/12-全链路MVP实现与测试结果.md)
- Verkle 底层与手工测试：
  [13-Verkle树底层实现说明与手工测试指南.md](/d:/BIT/张川老师课题组/260511-竞赛-挑战杯-山东电网/13-Verkle树底层实现说明与手工测试指南.md)
