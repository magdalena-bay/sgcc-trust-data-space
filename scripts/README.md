# Utility Scripts

这里用于存放开发辅助脚本和本地工具脚本。

## 当前可直接使用的脚本

### `sync_frontend_dist_to_backend.ps1`

用途：

1. 在本地构建 `frontend/user-web`
2. 把构建产物同步到：
   `backend/platform-api/src/main/resources/static/`
3. 供 `platform-api:8088` 直接托管静态前端

适用场景：

1. 本地继续写前端代码
2. 服务器禁止常驻 `vite`
3. 需要把一份可演示页面打进后端 jar

执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync_frontend_dist_to_backend.ps1
```

### `deploy_frontend_to_server.ps1`

用途：

1. 先执行 `sync_frontend_dist_to_backend.ps1`
2. 打包当前仓库源码
3. 上传到远端：
   `/home/ubuntu/sgcc-trust-data-space`
4. 远端重建 `platform-api`
5. 用“受控后台”方式重启 `8088`
6. 自动执行健康检查和首页检查

执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\deploy_frontend_to_server.ps1
```

注意事项：

1. 这个脚本的目标是：
   `让 8088 同时提供 API 和静态前端`
2. 它不会去启动服务器端 `vite`
3. 它会把本地生成的 `static/` 一并同步到远端，但这不代表应该把 `static/` 提交到 Gitee
4. 当前仓库已经把：
   `backend/platform-api/src/main/resources/static`
   放进 `.gitignore`
5. 如果脚本执行后失败，优先查看远端日志：
   `/tmp/sgcc-platform-live.log`

### `run_verkle_backend_smoke.ps1`

用途：

1. 通过 `SSH -> 服务器本机 127.0.0.1:8088` 做一次完整后端联调
2. 自动执行：
   `health -> system-status -> upload -> detail -> verkle -> verkle-audit -> allowed access -> denied access`
3. 全程不启动服务器 `vite`

执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run_verkle_backend_smoke.ps1
```

可选参数：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run_verkle_backend_smoke.ps1 -Region weifang
```

说明：

1. 当前脚本默认 SSH 到：
   `152.136.167.239`
2. 默认远端代码目录是：
   `/home/ubuntu/sgcc-trust-data-space`
3. 默认 API 基址使用服务器本机：
   `http://127.0.0.1:8088`
4. 这样做是为了避开公网 `8088` 额外网关层干扰，更准确反映真实后端链路状态

### `scripts/ops/start_frontend_server.sh`

用途：

1. 通过 `systemd-run` 启动受控前端 transient unit
2. 支持：
   - `dev`
   - `preview`
3. 默认限制 Node 堆与 systemd 内存上限
4. 默认仅监听：
   `127.0.0.1`
5. 适合配合 Nginx 直接映射到服务器主 URL

执行：

```bash
cd /home/ubuntu/sgcc-trust-data-space
./scripts/ops/start_frontend_server.sh --mode dev
./scripts/ops/start_frontend_server.sh --mode preview
```

说明：

1. `dev`
   适合短时远程联调，有 HMR
2. `preview`
   更适合“从 SSH 启动前端后直接给别人访问”的稳态试看
3. 当前脚本显式避免：
   - `Restart=always`
   - `vite --force`
   - 公网直接暴露 `5173`

### `scripts/ops/stop_frontend_server.sh`

用途：

1. 停止上面两类 transient unit
2. 快速结束服务器端前端试运行

执行：

```bash
cd /home/ubuntu/sgcc-trust-data-space
./scripts/ops/stop_frontend_server.sh all
```

### `measure_onchain_write_tps.ps1`

用途：

1. 只测：
   `区块链上链写交易本身`
2. 当前改为走：
   `platform-api /api/demo/benchmark/onchain`
3. 不包含：
   - IPFS
   - MySQL
   - Redis
   - Verkle proof 重建
   - 解密
   - 完整上传编排

适用场景：

1. 单独评估：
   `anchor`
   路径的链上耗时与吞吐量
2. 单独评估：
   `recordAccess`
   的链上耗时与吞吐量
3. 单独评估：
   `anchor_digest`
   压缩批量专项口径
4. 单独评估：
   `upload_checkpoint`
   更接近真实业务的：
   `资源锚点 + region root checkpoint + relay root checkpoint`
   口径
5. 单独评估：
   `upload_legacy`
   旧的：
   `每次上传都按整区域全量重锚定`
   基线口径
4. 避免把“整条业务链路耗时”误写成“区块链上链吞吐量”

执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\measure_onchain_write_tps.ps1
```

可选参数：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\measure_onchain_write_tps.ps1 -ApiBase 'http://127.0.0.1:8089' -ChainName qingdao -EventType anchor_digest -Count 10000 -BatchSize 10000 -Concurrency 1

powershell -ExecutionPolicy Bypass -File .\scripts\measure_onchain_write_tps.ps1 -ApiBase 'http://127.0.0.1:8089' -ChainName qingdao -EventType upload_checkpoint -Count 5 -BatchSize 5 -Concurrency 8

powershell -ExecutionPolicy Bypass -File .\scripts\measure_onchain_write_tps.ps1 -ApiBase 'http://127.0.0.1:8089' -ChainName qingdao -EventType upload_legacy -Count 3 -BatchSize 3 -Concurrency 1
```

说明：

1. 如果从本机公网直接访问：
   `http://152.136.167.239:8089`
   偶发出现：
   `502`
   不一定代表服务器或链坏了
2. 更推荐通过服务器本机：
   `127.0.0.1:8089`
   做精确测量
3. `anchor_digest`
   是专项指标口径，不等于当前逐资源 anchor 的正式业务语义
4. `upload_checkpoint`
   是当前更接近真实业务的对比口径
5. `upload_legacy`
   用来作为“历史全量重锚定”的对照基线

### `scripts/ops/set_8node_onchain_mode.ps1`

用途：

1. 受控切换隔离 `8` 节点后端：
   - `direct`
   - `buffered_sync`
   - `async`
2. 自动改：
   `.server.env.8node`
3. 自动重启：
   `platform-api:8089`
4. 自动做健康检查

执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\ops\set_8node_onchain_mode.ps1 -Mode async -WorkerBatchSize 128 -AnchorAggregateSize 64 -SubmitParallelism 8 -WorkerFixedDelayMs 100
```

注意：

1. 这个脚本只操作：
   `8089`
   隔离环境
2. 不会改主环境：
   `8088`
3. 不会启 GUI
4. 不会启服务器端 `vite`

### `scripts/ops/measure_onchain_write_tps_remote.ps1`

用途：

1. 通过 SSH 到服务器本机
2. 直接调用：
   `http://127.0.0.1:8089/api/demo/benchmark/onchain`
3. 避开公网网关和本机 `Invoke-RestMethod` 额外噪音

执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\ops\measure_onchain_write_tps_remote.ps1 -EventType anchor_digest -ChainName qingdao -Count 20000 -BatchSize 10000 -Concurrency 2

powershell -ExecutionPolicy Bypass -File .\scripts\ops\measure_onchain_write_tps_remote.ps1 -EventType upload_checkpoint -ChainName qingdao -Count 5 -BatchSize 5 -Concurrency 8

powershell -ExecutionPolicy Bypass -File .\scripts\ops\measure_onchain_write_tps_remote.ps1 -EventType upload_legacy -ChainName qingdao -Count 3 -BatchSize 3 -Concurrency 1
```

### `scripts/ops/query_chain_root_remote.ps1`

用途：

1. 从本机 PowerShell 通过 `SSH` 到服务器
2. 查询当前 `8` 节点正式隔离环境的链上根检查点
3. 输出适合演示和排查的 JSON

执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\ops\query_chain_root_remote.ps1 -ChainName qingdao -RegionRoot qingdao

powershell -ExecutionPolicy Bypass -File .\scripts\ops\query_chain_root_remote.ps1 -ChainName weifang -RegionRoot weifang

powershell -ExecutionPolicy Bypass -File .\scripts\ops\query_chain_root_remote.ps1 -ChainName relay -RegionRoot qingdao
```

说明：

1. `ChainName`
   是你要查询的链：
   `qingdao / weifang / relay`
2. `RegionRoot`
   是你要查询的区域根名称：
   最终会自动拼成：
   `__VERKLE_ROOT__:<region>`
3. 脚本内部会自动使用当前服务器正式隔离环境的：
   - `5110 / 5111 / 5112`
   - 注册合约地址
   - 服务账户地址
4. 一般不需要你手工改：
   `base`
   `contract_address`
   `user`

注意：

1. 这条命令必须在项目根目录执行，例如：
   `D:\BIT\张川老师课题组\260511-竞赛-挑战杯-山东电网`
2. 如果你在：
   `C:\Users\21006`
   这种目录下直接执行相对路径，就会报：
   `-File 形式参数的实际参数不存在`

### `scripts/ops/query_chain_root.sh`

用途：

1. 直接在服务器本机查询链上根检查点
2. 不依赖本机 PowerShell
3. 适合录屏时在服务器终端直接展示

执行：

```bash
cd /home/ubuntu/sgcc-trust-data-space/scripts/ops
./query_chain_root.sh qingdao
./query_chain_root.sh weifang
./query_chain_root.sh relay qingdao
```

说明：

1. `./query_chain_root.sh qingdao`
   等价于查：
   `__VERKLE_ROOT__:qingdao`
2. `./query_chain_root.sh relay qingdao`
   表示查：
   `relay` 链里记录的 `qingdao` 区域根

### `scripts/ops/query_batch_digest_anchor.sh`

用途：

1. 直接在服务器本机查询链上的批次摘要锚点
2. 用来证明当前业务记录属于哪个 `batchId`
3. 能补上“不能只看链高和 ABI”的问题

执行：

```bash
cd /home/ubuntu/sgcc-trust-data-space/scripts/ops
./query_batch_digest_anchor.sh qingdao 'BATCH:qingdao:xxxx'
./query_batch_digest_anchor.sh relay 'RELAY:qingdao:BATCH:qingdao:xxxx'
```

说明：

1. 这条脚本查的是合约里的：
   `getBatchDigestAnchor(batchId)`
2. 如果某条链上部署的还是旧版合约，可能返回：
   `Call contract return error: call contract error of status:16`
3. 这时说明：
   `当前这条链更适合用 root checkpoint 作为正式演示主路径`

### `scripts/ops/fisco_console_clean.sh`

用途：

1. 包装官方 `FISCO Console`
2. 自动过滤当前环境下的：
   `libproviders.so: cannot open shared object file`
   这条噪音
3. 适合录屏时使用

执行：

```bash
cd /home/ubuntu/sgcc-trust-data-space/scripts/ops
./fisco_console_clean.sh getBlockNumber
./fisco_console_clean.sh listAbi SgccTrustAnchor
```

说明：

1. 当前这套官方 `FISCO Console 3.8.0` 已稳定验证：
   - `getBlockNumber`
   - `listAbi SgccTrustAnchor`
   - `getCode <contractAddress>`
2. 但在这台服务器的当前配置下，直接用 Console 对
   `SgccTrustAnchor` 执行 `call getAnchor(...)`
   仍会出现：
   `Abi is empty, please check contract abi exists.`
3. 所以当前“业务锚定值查询”以：
   - `query_chain_root.sh`
   - `query_batch_digest_anchor.sh`
   - `WeBASE-Front` 读调用
   为正式可复现路径
4. Console 仍然保留为：
   - 官方节点查询与管理工具
   - 链高 / 合约 ABI / 合约代码存在性证明入口

### `scripts/ops/open_fisco_console.sh`

用途：

1. 直接进入官方 `FISCO BCOS Console` 交互界面
2. 形态就是：
   `Welcome to FISCO BCOS console(3.8.0)!`
   和
   `[group0]: /apps>`
3. 更适合录屏时展示“官方命令行交互控制台”的样子

执行：

```bash
cd /home/ubuntu/sgcc-trust-data-space/scripts/ops
./open_fisco_console.sh qingdao
```

进入交互台后可执行：

```text
getBlockNumber
listAbi SgccTrustAnchor
getCode 0xafcca7e2ca495ca06a4715ef4ce6457958c42a6e
help
quit
```

当前已确认：

1. `getBlockNumber` 可直接返回链高
2. `listAbi SgccTrustAnchor` 可直接列出业务合约 ABI
3. `getCode <contractAddress>` 可直接查询链上合约代码

当前限制：

1. 这台服务器上的官方 Console 交互环境里，直接执行：
   `call SgccTrustAnchor <contractAddress> getAnchor __VERKLE_ROOT__:qingdao`
   仍会出现：
   `Abi is empty, please check contract abi exists.`
2. 所以当前“root 业务值查询”仍推荐使用：
   - `query_chain_root.sh`
   - `query_chain_root_remote.ps1`
   - `WeBASE-Front` 读调用
3. 但如果你要录制“官方命令行交互控制台：节点查询与管理工具”的样式，
   这条脚本已经可以稳定实现

### `scripts/ops/import_ipfs_cid_to_local_desktop.ps1`

用途：

1. 从服务器的 IPFS 网关拉取指定 `CID`
2. 导入到你本机 `IPFS Desktop` 自带的本地节点
3. 让这条业务数据能在本机 `IPFS Desktop` 里真正可见

为什么需要这条脚本：

1. 你本机 `IPFS Desktop` 默认跑的是：
   `C:\Users\21006\.ipfs`
   这套本地节点
2. 它不是服务器上的那台 IPFS 节点
3. 所以服务器有的 `CID`，本机 Desktop 不会自动就有
4. 这也是之前你在 Desktop 里输入：
   `QmZNonw5SeyFojpqfAQ8UpfdUBfgRh1S6qzX3A6pKAW5yt`
   却显示为空的根本原因

执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\ops\import_ipfs_cid_to_local_desktop.ps1 -Cid QmZNonw5SeyFojpqfAQ8UpfdUBfgRh1S6qzX3A6pKAW5yt
```

验证：

```powershell
& 'D:\evermore\IPFS\IPFS Desktop\resources\app.asar.unpacked\node_modules\kubo\kubo\ipfs.exe' pin ls --type=recursive
& 'D:\evermore\IPFS\IPFS Desktop\resources\app.asar.unpacked\node_modules\kubo\kubo\ipfs.exe' cat QmZNonw5SeyFojpqfAQ8UpfdUBfgRh1S6qzX3A6pKAW5yt
```

补充说明：

1. 我已经实际验证过：
   这条脚本导入后，本机节点重新 `add` 出来的仍然是同一个 CID：
   `QmZNonw5SeyFojpqfAQ8UpfdUBfgRh1S6qzX3A6pKAW5yt`
2. 这说明当前这条业务数据内容和 CID 是一致的
3. 也说明 IPFS Desktop 之前“看不到”的问题不是 CID 错，而是本机节点里原本没有这份内容
