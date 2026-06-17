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
4. 当前后端正在使用哪些依赖地址

`/api/demo/resources/{dataId}/verkle-audit`

用于对单条资源执行 Verkle 相关一致性审计。它会重新读取 IPFS 包、重建当前区域的 demo commitment，并对比：

1. MySQL 中的 `HD_i/packageHash/policyHash/root/relayRoot`
2. Redis 中的 `verkle-proof:{HD_i}`
3. 区域链中的 `root`
4. relay 链中的 `root`
5. proof 分别针对 MySQL root、区域链 root、relay root 的校验结果

返回里的 `overallPassed=true` 才表示这条资源当前在 4 库与链上锚定视角下是一致的。

## Run

```bash
cd ~/sgcc-trust-data-space/backend/platform-api
set -a
source /home/ubuntu/sgcc-trust-data-space/.server.env
set +a
mvn clean package -DskipTests
java -jar target/platform-api-0.1.0.jar
```

服务器后台运行方式示例：

```bash
cd ~/sgcc-trust-data-space/backend/platform-api
set -a
source /home/ubuntu/sgcc-trust-data-space/.server.env
set +a
nohup java -jar target/platform-api-0.1.0.jar > platform-api.log 2>&1 < /dev/null &
```

需要的环境变量见 `src/main/resources/application.yml` 与项目根目录 `.server.env`。
