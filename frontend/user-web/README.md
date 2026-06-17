# user-web

当前前端是一个面向 MVP 演示的 `Vue 3 + TypeScript + Vite + Element Plus + ECharts` 页面。

它已经直接接上了当前全链路实现：

1. 浏览器端 AES-GCM 加密上传
2. 资源列表展示
3. 授权访问请求
4. 解密后负荷曲线图展示

## 当前前端加密模式说明

这里必须说清楚：

前端里的“浏览器端先 AES 加密”不是一句空话，它现在有 3 种实际运行模式：

1. `Browser Web Crypto AES-GCM`
   浏览器原生 `crypto.subtle` 可用时，优先走这个
2. `Browser JS AES-GCM Fallback`
   如果你是通过 `http://服务器IP:5173` 访问，浏览器往往不给安全上下文，这时会改用前端内置的 JS AES-GCM 兜底
3. `Server Test Mode`
   只有你手动关闭“浏览器端先 AES 加密”开关时，才会走后端测试直传

也就是说：

`现在默认不会再因为 Web Crypto 不可用，就自动退回后端明文直传。`

## Run

```bash
cd ~/sgcc-trust-data-space/frontend/user-web
npm install
npm run dev -- --host 0.0.0.0 --port 5173
```

默认后端地址优先级：

1. 如果设置了 `VITE_API_BASE`，优先使用它
2. 否则自动使用当前访问页面所在主机的 `8088` 端口

也就是说，如果你从自己电脑访问服务器前端页面，前端会自动请求：

- `http://<当前服务器IP>:8088`

如需修改：

```bash
VITE_API_BASE=http://127.0.0.1:8088 npm run dev -- --host 0.0.0.0 --port 5173
```

服务器上当前已验证：

1. `npm run build` 可以成功
2. `npm run dev -- --host 0.0.0.0 --port 5173` 可以正常启动
3. 页面访问 `http://152.136.167.239:5173` 时，仍然会真实调用服务器后端 `http://152.136.167.239:8088`
