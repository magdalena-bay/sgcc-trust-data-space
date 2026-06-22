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
   `/home/ubuntu/sgcc-trust-data-space-sync/sgcc-trust-data-space`
4. 之前遗留的若干 `systemd` service 仍指向旧目录 `/home/ubuntu/sgcc-trust-data-space/...`，它们现在只能视为历史残留，不能直接当成可用部署入口。
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

## 关键文档

- 项目代码与模块说明：
  [docs/planning/PROJECT_STRUCTURE_AND_TEAM_MAPPING.md](/d:/BIT/张川老师课题组/260511-竞赛-挑战杯-山东电网/sgcc-trust-data-space/docs/planning/PROJECT_STRUCTURE_AND_TEAM_MAPPING.md)
- 本轮实现与测试结果：
  [12-全链路MVP实现与测试结果.md](/d:/BIT/张川老师课题组/260511-竞赛-挑战杯-山东电网/12-全链路MVP实现与测试结果.md)
- Verkle 底层与手工测试：
  [13-Verkle树底层实现说明与手工测试指南.md](/d:/BIT/张川老师课题组/260511-竞赛-挑战杯-山东电网/13-Verkle树底层实现说明与手工测试指南.md)
