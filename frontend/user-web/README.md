# user-web

当前前端是一个面向 MVP 演示的 `Vue 3 + TypeScript + Vite + Element Plus + ECharts` 页面。

它已经直接接上了当前全链路实现：

1. 浏览器端 AES-GCM 加密上传
2. 资源列表展示
3. 授权访问请求
4. 解密后负荷曲线图展示

## 当前前端加密模式说明

这里必须说清楚：

前端里的“浏览器端先 AES 加密”当前按更保守、更稳定的方式处理：

1. `Browser Web Crypto AES-GCM`
   浏览器原生 `crypto.subtle` 可用时，优先走这个
2. `Server Test Mode`
   如果当前浏览器环境不支持 `crypto.subtle.importKey / encrypt`，页面会自动切换为后端测试直传
3. 手动关闭开关
   你也可以主动关闭“浏览器端先 AES 加密”，直接走后端测试直传

也就是说：

`当前版本不再依赖前端 JS AES 兜底分支，以避免再次出现 importKey / 依赖解析 / 运行态不一致导致的上传报错。`

## Run

```bash
cd /home/ubuntu/sgcc-trust-data-space/frontend/user-web
npm install
npm run dev -- --host 0.0.0.0 --port 5173
```

更安全、也更推荐的测试/试用方式：

1. 本地开发时，只在你自己的电脑上运行 `vite`
2. 服务器演示时，不运行 `vite`
3. 而是先本地构建前端，再把静态产物同步进 `platform-api`

对应脚本：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sync_frontend_dist_to_backend.ps1
```

如果要直接一键部署到当前服务器：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\deploy_frontend_to_server.ps1
```

这样打出来的前端页面会被 `platform-api` 直接托管，访问入口统一变成：

```text
http://服务器IP:8088/
```

优点是：

1. 服务器上没有 `vite` watcher
2. 没有 `esbuild` 常驻子进程
3. 页面和 API 同源，不再依赖额外前端端口
4. 非 24 小时演示场景下更稳

服务器上当前不要这样常驻运行：

1. 不要把 `npm run dev`
2. 不要把 `vite --host 0.0.0.0 --port 5173`
3. 不要把任何前端 watcher

写成 `systemd` 或 `systemd --user` 的 `Restart=always` 服务。

默认后端地址优先级：

1. 如果设置了 `VITE_API_BASE`，优先使用它
2. 否则自动使用当前访问页面所在主机的 `8088` 端口

也就是说，如果你从自己电脑访问服务器前端页面，前端会自动请求：

- `http://<当前服务器IP>:8088`

如需修改：

```bash
VITE_API_BASE=http://127.0.0.1:8088 npm run dev -- --host 0.0.0.0 --port 5173
```

本地当前已验证：

1. `npm run build` 可以成功
2. 浏览器支持原生 Web Crypto 时，可以走浏览器 AES-GCM 上传
3. 浏览器不支持时，页面会自动切到后端测试直传，避免前端直接崩溃

服务器注意事项：

1. `5173` 是否真正可访问，除了前端代码本身，还受服务器当前前端启动方式影响
2. 如果服务器 `sshd` / 前端进程异常，会出现“代码没问题，但页面打不开或不是最新版本”的情况
3. `2026-06-22` 的事故已经证明：服务器端 `Vite + esbuild` 开发态进程如果被 service 自动反复拉起，会导致进程数和内存持续失控
4. 当前更推荐：
   本地 `npm run build` 验证前端代码
   用 `scripts/sync_frontend_dist_to_backend.ps1` 同步静态产物
   服务器通过 `platform-api:8088` 提供页面
