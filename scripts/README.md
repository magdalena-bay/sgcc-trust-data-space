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
