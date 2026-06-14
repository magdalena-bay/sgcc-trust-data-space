# Project Structure And Team Mapping

对应中文主文档：

- `../..\\..\\10-项目代码目录骨架设计与团队分工对照表.md`

本文件用于仓库内快速说明两件事：

1. 推荐代码目录骨架
2. 推荐模块负责分工

## Recommended Structure

```text
frontend/
  user-web/
  admin-web/
  agent-web/

backend/
  api-gateway/
  auth-service/
  identity-service/
  data-service/
  policy-service/
  crypto-service/
  blockchain-service/
  storage-service/
  audit-service/
  agent-service/
  fl-service/
  scheduler-service/

contracts/
  common/
  qingdao/
  weifang/
  relay/

chain-config/
  qingdao/
  weifang/
  relay/
  wecross/
  webase/

infra/
  mysql/
  redis/
  postgres/
  ipfs/
  nginx/
  docker/
```

## Recommended Team Mapping

| Group | Suggested Scope |
|---|---|
| Frontend | `frontend/user-web`, `frontend/admin-web`, `frontend/agent-web` |
| Backend Core | `backend/api-gateway`, `backend/data-service`, `backend/policy-service`, `backend/audit-service` |
| Identity And Crypto | `backend/identity-service`, `backend/crypto-service` |
| Blockchain | `backend/blockchain-service`, `contracts`, `chain-config` |
| Storage And Infra | `backend/storage-service`, `infra`, `data` |
| Federated Learning | `backend/fl-service`, `fl` |
| AI Agent | `backend/agent-service`, `frontend/agent-web` |

## Notes

- Keep module boundaries stable before writing large amounts of code.
- Prefer adding module-level README files before implementing full business logic.
- Use one owner per module, even if multiple people collaborate on it.
