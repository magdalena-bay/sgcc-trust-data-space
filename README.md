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
  负责保存 `verkle-proof:{packageHash}` 这样的证明键值
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

如果 `8010` 不通，那么上传与访问都可能返回 `500`。

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
4. 启动 `frontend/user-web`。

补充说明：

`privacy-service` 目前在服务器上已经改为 `systemd --user` 托管，不建议再只靠临时终端前台运行。

## 关键文档

- 项目代码与模块说明：
  [docs/planning/PROJECT_STRUCTURE_AND_TEAM_MAPPING.md](/d:/BIT/张川老师课题组/260511-竞赛-挑战杯-山东电网/sgcc-trust-data-space/docs/planning/PROJECT_STRUCTURE_AND_TEAM_MAPPING.md)
- 本轮实现与测试结果：
  [12-全链路MVP实现与测试结果.md](/d:/BIT/张川老师课题组/260511-竞赛-挑战杯-山东电网/12-全链路MVP实现与测试结果.md)
- Verkle 底层与手工测试：
  [13-Verkle树底层实现说明与手工测试指南.md](/d:/BIT/张川老师课题组/260511-竞赛-挑战杯-山东电网/13-Verkle树底层实现说明与手工测试指南.md)
