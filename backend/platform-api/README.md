# platform-api

`platform-api` 是当前 MVP 的平台主后端。

它负责：

1. 接收前端上传与访问请求
2. 调用 `privacy-service`
3. 写入 MySQL / Redis / PostgreSQL / IPFS
4. 调用 WeBASE / FISCO BCOS 完成链上锚定与审计
5. 对外提供统一 REST API

## 当前 MVP 的关键说明

- 前端支持浏览器端先做 AES-GCM 加密再上传
- 后端保留了一个“明文直传测试入口”，仅用于自动化联调与测试
- `CT_ABE` 在当前可运行版本中使用 `MVP_POLICY_WRAPPED_DEK` 占位
- `Verkle` 在当前可运行版本中使用了 `Verkle-compatible demo commitment`
- 已处理 WeBASE 服务私钥重复导入的幂等问题，重复重启不会因为 `user name already exists` 直接失败

## 主要接口

- `GET /api/demo/health`
- `GET /api/demo/system-status`
- `POST /api/demo/upload`
- `GET /api/demo/resources`
- `GET /api/demo/resources/{dataId}`
- `GET /api/demo/resources/{dataId}/verkle`
- `GET /api/demo/resources/{dataId}/verkle-audit`
- `POST /api/demo/access`

补充说明：

`/api/demo/system-status`

是这次新增的排查接口，用来让前端或人工检查当前：

1. `platform-api` 是否在线
2. `privacy-service` 是否在线
3. `IPFS gateway` 是否在线
4. `IPFS API / MySQL / Redis / PostgreSQL` 是否在线
5. `qingdao / weifang / relay` 三条链是否可读
6. 当前三条链登记到的合约地址与登记条数
7. 是否检测到跨链合约地址复用现象

`/api/demo/resources/{dataId}/verkle-audit`

用于对单条资源执行 Verkle 相关一致性审计。它会重新读取 IPFS 包、重建当前区域的 demo commitment，并对比：

1. MySQL 中的 `HD_i/packageHash/policyHash/root/relayRoot`
2. Redis 中的 `verkle-proof:{HD_i}`
3. 区域链中的 `root`
4. relay 链中的 `root`
5. proof 分别针对 MySQL root、区域链 root、relay root 的校验结果

返回里的 `overallPassed=true` 才表示这条资源当前在 4 库与链上锚定视角下是一致的。

补充：

- Redis 中当前保存的是 proof envelope，而不再只是“裸 proof JSON”。
- envelope 外层带有 `scheme`、`engineVersion`、`proofType`、`leafKey`、`valueDigest`、`root` 与 `proofPayload`。
- 这样后续如果把 `Verkle-compatible demo commitment` 替换成正式密码学实现，平台主后端仍可以复用同一套 Redis 键、审计接口和验证调用边界。
- 当前 `platform-api` 内部又额外补了一层：
  `VerkleEngineGateway`
  也就是说 `DemoResourceService` 现在依赖的是“证明引擎抽象接口”，而不是直接依赖 demo 版 proof 细节。
- 当前默认实现是：
  `DemoVerkleEngineGateway`
  后续如果切到正式密码学版 Verkle，优先替换这里，而不是让业务服务整体返工。

补充说明：

- `SchemaUpgradeService` 已改为直接复用 Spring DataSource，不再在代码中单独硬编码 MySQL 连接串和口令。
- `system-status` 现在不只是“接口活着”，而是会显式给出存储层、链层和合约登记状态。

## Run

```bash
cd /home/ubuntu/sgcc-trust-data-space/backend/platform-api
[ -f /home/ubuntu/sgcc-trust-data-space/.server.env ] && set -a && source /home/ubuntu/sgcc-trust-data-space/.server.env && set +a
mvn clean package -DskipTests
java -Xms256m -Xmx1024m -jar target/platform-api-0.1.0.jar
```

服务器后台运行方式示例：

```bash
cd /home/ubuntu/sgcc-trust-data-space/backend/platform-api
[ -f /home/ubuntu/sgcc-trust-data-space/.server.env ] && set -a && source /home/ubuntu/sgcc-trust-data-space/.server.env && set +a
setsid nohup bash -lc 'cd /home/ubuntu/sgcc-trust-data-space/backend/platform-api && [ -f /home/ubuntu/sgcc-trust-data-space/.server.env ] && set -a && source /home/ubuntu/sgcc-trust-data-space/.server.env && set +a; exec java -Xms256m -Xmx1024m -jar target/platform-api-0.1.0.jar >> /tmp/sgcc-platform-live.log 2>&1' < /dev/null > /dev/null 2>&1 &
```

需要的环境变量见 `src/main/resources/application.yml` 与项目根目录 `.server.env`。

如果服务器上缺少 `.server.env`，可以先用当前代码目录下的 `.server.env.example` 复制出一份再启动服务。

当前运行注意事项：

1. `platform-api` 启动阶段会主动访问 `WeBASE-Front`，所以 `5100/5101/5102` 不可用时，应用会在 Spring 初始化阶段直接退出，而不是“先启动后部分接口报错”。
2. 当前服务器先不要把 `platform-api` 写成开机自启 service，尤其不要直接复用旧路径 `/home/ubuntu/sgcc-trust-data-space/...` 下的历史 service 文件。
3. 如需临时验证，优先使用上面的手动命令短时启动，验证完立即停止。
4. 当前推荐把前端构建产物同步到 `src/main/resources/static/`，由 `platform-api` 直接托管页面，避免服务器常驻 `vite`。
5. 如果通过 SSH 脚本远端重启 `platform-api`，建议使用 `setsid + nohup`，避免进程在部署会话退出时被一并带停。
