# Nginx Config

这里用于存放反向代理与静态资源发布配置。

## 当前推荐示例

### `sgcc-direct-vite.conf`

用途：

1. 让浏览器直接访问服务器主 URL
2. `/` 反代到前端：
   - 开发态默认 `127.0.0.1:5173`
   - 预览态可手动改成 `127.0.0.1:4173`
3. `/api/` 反代到：
   `127.0.0.1:8088/api/`
4. 保留 WebSocket upgrade，确保 Vite HMR 可用

如果你当前走 preview 模式，请把配置里的：

```nginx
proxy_pass http://127.0.0.1:5173;
```

改成：

```nginx
proxy_pass http://127.0.0.1:4173;
```
