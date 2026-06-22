<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import * as echarts from "echarts";
import { API_BASE, accessResource, fetchResourceDetail, fetchResourceVerkle, fetchResourceVerkleAudit, fetchResources, fetchSystemStatus, uploadResource } from "./api";
import { browserCryptoStatus, encryptForUpload } from "./crypto";
import type {
  AccessRequest,
  AccessResponse,
  ResourceDetail,
  ResourceSummary,
  ResourceVerkleAudit,
  SystemStatus,
  ResourceVerkle,
  UploadRequest
} from "./types";

const resources = ref<ResourceSummary[]>([]);
const selectedResource = ref<ResourceDetail | null>(null);
const selectedVerkle = ref<ResourceVerkle | null>(null);
const selectedVerkleAudit = ref<ResourceVerkleAudit | null>(null);
const accessResult = ref<AccessResponse | null>(null);
const chartRef = ref<HTMLDivElement | null>(null);
const browserEncryptionStatus = ref("未检测");
const lastUploadMode = ref("未上传");
const systemStatus = ref<SystemStatus | null>(null);

const uploadForm = reactive<UploadRequest>({
  region: "qingdao",
  ownerDid: "did:weid:qingdao:1001",
  dataType: "load_curve",
  policyOrg: "SGCC_dispatch_center",
  policyRole: "load_analyst",
  policyGrantStatus: "valid",
  plaintext: "time,load\n08:00,1180\n08:15,1215\n08:30,1240"
});

const accessForm = reactive<AccessRequest>({
  dataId: "",
  requesterOrg: "SGCC_dispatch_center",
  requesterRole: "load_analyst",
  requesterGrantStatus: "valid"
});

const useBrowserEncryption = ref(true);

async function loadResources() {
  resources.value = await fetchResources();
  if (resources.value.length > 0 && !selectedResource.value) {
    await openDetail(resources.value[0].dataId);
  }
}

async function loadSystemStatus() {
  systemStatus.value = await fetchSystemStatus();
}

async function openDetail(dataId: string) {
  selectedResource.value = await fetchResourceDetail(dataId);
  selectedVerkle.value = await fetchResourceVerkle(dataId);
  selectedVerkleAudit.value = await fetchResourceVerkleAudit(dataId);
  accessForm.dataId = dataId;
}

function refreshBrowserEncryptionStatus() {
  const status = browserCryptoStatus();
  if (status.hasWebCrypto) {
    browserEncryptionStatus.value = status.isSecureContext
      ? "浏览器 Web Crypto 可用（安全上下文）"
      : "浏览器 Web Crypto 可用（当前页面不是安全上下文）";
    return;
  }

  browserEncryptionStatus.value = status.isSecureContext
    ? "浏览器原生 Web Crypto 不可用，已切换为后端测试直传"
    : "当前页面不是安全上下文，已切换为后端测试直传";
  useBrowserEncryption.value = false;
}

async function submitUpload() {
  try {
    const payload: UploadRequest = { ...uploadForm };

    if (useBrowserEncryption.value && uploadForm.plaintext) {
      const encrypted = await encryptForUpload(uploadForm.plaintext);
      payload.plaintext = undefined;
      payload.encDataBase64 = encrypted.encDataBase64;
      payload.ivBase64 = encrypted.ivBase64;
      payload.dekBase64 = encrypted.dekBase64;
      payload.dataHash = encrypted.dataHash;
      lastUploadMode.value = "浏览器 Web Crypto AES-GCM";
      if (encrypted.encryptionMode === "webcrypto") {
        ElMessage.success("本次上传已使用浏览器端 Web Crypto AES-GCM。");
      }
    } else {
      lastUploadMode.value = "后端测试直传模式";
    }

    await uploadResource(payload);
    ElMessage.success("上传并锚定成功");
    await loadResources();
    if (payload.dataId) {
      await openDetail(payload.dataId);
    }
  } catch (error) {
    ElMessage.error(String(error));
  }
}

async function submitAccess() {
  try {
    accessResult.value = await accessResource({ ...accessForm });
    if (!accessResult.value.granted) {
      ElMessage.warning(accessResult.value.message);
      return;
    }
    ElMessage.success("访问成功");
    await nextTick();
    renderChart(accessResult.value.plaintext || "");
  } catch (error) {
    ElMessage.error(String(error));
  }
}

function renderChart(plaintext: string) {
  if (!chartRef.value) {
    return;
  }
  const lines = plaintext.split("\n").slice(1).filter(Boolean);
  const xAxis = lines.map((line) => line.split(",")[0]);
  const values = lines.map((line) => Number(line.split(",")[1] || 0));

  const chart = echarts.init(chartRef.value);
  chart.setOption({
    backgroundColor: "transparent",
    tooltip: { trigger: "axis" },
    xAxis: { type: "category", data: xAxis },
    yAxis: { type: "value", name: "load" },
    series: [
      {
        type: "line",
        smooth: true,
        data: values,
        areaStyle: {},
        lineStyle: { width: 3 }
      }
    ]
  });
}

onMounted(() => {
  refreshBrowserEncryptionStatus();
  loadSystemStatus();
  loadResources();
});
</script>

<template>
  <div class="page-shell">
    <section class="hero-card">
      <div>
        <p class="eyebrow">SGCC TRUST DATA SPACE</p>
        <h1>全链路 MVP 演示台</h1>
        <p class="hero-copy">
          当前页面已经接通前端浏览器加密、Spring Boot 主后端、Python 隐私服务、
          MySQL / Redis / PostgreSQL / IPFS 与 FISCO BCOS 链上锚定。
        </p>
      </div>
      <div class="hero-metrics">
        <div class="metric-box">
          <span>资源数</span>
          <strong>{{ resources.length }}</strong>
        </div>
        <div class="metric-box">
          <span>当前上传策略</span>
          <strong>{{ useBrowserEncryption ? "优先浏览器加密" : "后端测试直传" }}</strong>
        </div>
        <div class="metric-box">
          <span>浏览器加密状态</span>
          <strong>{{ browserEncryptionStatus }}</strong>
        </div>
        <div class="metric-box">
          <span>最近一次上传模式</span>
          <strong>{{ lastUploadMode }}</strong>
        </div>
      </div>
    </section>

    <section class="status-card" v-if="systemStatus">
      <div class="panel-title">0. 当前页面实际连接状态</div>
      <div class="detail-grid">
        <div><span>前端请求 API 地址</span><strong>{{ API_BASE }}</strong></div>
        <div><span>platform-api</span><strong>{{ systemStatus.platformApi }}</strong></div>
        <div><span>privacy-service 地址</span><strong>{{ systemStatus.privacyServiceBaseUrl }}</strong></div>
        <div><span>IPFS Gateway 地址</span><strong>{{ systemStatus.ipfsGatewayUrl }}</strong></div>
        <div><span>platform-api 是否在线</span><strong>{{ systemStatus.components.platformApi }}</strong></div>
        <div><span>privacy-service 是否在线</span><strong>{{ systemStatus.components.privacyService }}</strong></div>
        <div><span>IPFS 是否在线</span><strong>{{ systemStatus.components.ipfsGateway }}</strong></div>
        <div><span>qingdao WeBASE</span><strong>{{ systemStatus.qingdaoWebaseUrl }}</strong></div>
        <div><span>weifang WeBASE</span><strong>{{ systemStatus.weifangWebaseUrl }}</strong></div>
        <div><span>relay WeBASE</span><strong>{{ systemStatus.relayWebaseUrl }}</strong></div>
      </div>
    </section>

    <section class="grid-layout">
      <el-card class="panel-card" shadow="hover">
        <template #header>
          <div class="panel-title">1. 上传数据</div>
        </template>
        <el-form label-position="top">
          <el-form-item label="区域链">
            <el-select v-model="uploadForm.region">
              <el-option label="qingdao" value="qingdao" />
              <el-option label="weifang" value="weifang" />
            </el-select>
          </el-form-item>
          <el-form-item label="拥有者 DID">
            <el-input v-model="uploadForm.ownerDid" />
          </el-form-item>
          <el-form-item label="数据类型">
            <el-input v-model="uploadForm.dataType" />
          </el-form-item>
          <el-form-item label="策略组织">
            <el-input v-model="uploadForm.policyOrg" />
          </el-form-item>
          <el-form-item label="策略角色">
            <el-input v-model="uploadForm.policyRole" />
          </el-form-item>
          <el-form-item label="策略授权状态">
            <el-input v-model="uploadForm.policyGrantStatus" />
          </el-form-item>
          <el-form-item label="负荷数据明文">
            <el-input v-model="uploadForm.plaintext" type="textarea" :rows="8" />
          </el-form-item>
          <el-form-item>
            <el-switch
              v-model="useBrowserEncryption"
              active-text="浏览器端先 AES 加密"
              inactive-text="后端测试直传"
            />
          </el-form-item>
          <div class="mode-hint">
            当前说明：开启时会优先使用浏览器原生 Web Crypto 做 AES-GCM 加密；
            若当前浏览器环境不支持，则页面会自动切换为后端测试直传，避免再出现上传时报错。
          </div>
          <el-button type="primary" @click="submitUpload">上传并锚定</el-button>
        </el-form>
      </el-card>

      <el-card class="panel-card" shadow="hover">
        <template #header>
          <div class="panel-title">2. 数据资源列表</div>
        </template>
        <el-table :data="resources" height="420" @row-click="(row: ResourceSummary) => openDetail(row.dataId)">
          <el-table-column prop="dataId" label="dataId" min-width="180" />
          <el-table-column prop="region" label="region" width="100" />
          <el-table-column prop="dataType" label="type" width="120" />
          <el-table-column prop="status" label="status" width="100" />
        </el-table>
      </el-card>
    </section>

    <section class="grid-layout">
      <el-card class="panel-card" shadow="hover">
        <template #header>
          <div class="panel-title">3. 资源详情</div>
        </template>
        <div v-if="selectedResource" class="detail-grid">
          <div><span>dataId</span><strong>{{ selectedResource.dataId }}</strong></div>
          <div><span>region</span><strong>{{ selectedResource.region }}</strong></div>
          <div><span>cid</span><strong>{{ selectedResource.cid }}</strong></div>
          <div><span>HD_i</span><strong>{{ selectedResource.hdValue }}</strong></div>
          <div><span>packageHash</span><strong>{{ selectedResource.packageHash }}</strong></div>
          <div><span>root</span><strong>{{ selectedResource.root }}</strong></div>
          <div><span>relayRoot</span><strong>{{ selectedResource.relayRoot }}</strong></div>
          <div class="wide"><span>policyExpr</span><strong>{{ selectedResource.policyExpr }}</strong></div>
        </div>
      </el-card>

      <el-card class="panel-card" shadow="hover">
        <template #header>
          <div class="panel-title">4. 授权访问</div>
        </template>
        <el-form label-position="top">
          <el-form-item label="dataId">
            <el-input v-model="accessForm.dataId" />
          </el-form-item>
          <el-form-item label="请求组织">
            <el-input v-model="accessForm.requesterOrg" />
          </el-form-item>
          <el-form-item label="请求角色">
            <el-input v-model="accessForm.requesterRole" />
          </el-form-item>
          <el-form-item label="请求授权状态">
            <el-input v-model="accessForm.requesterGrantStatus" />
          </el-form-item>
          <el-button type="success" @click="submitAccess">验证并解密</el-button>
        </el-form>
        <div v-if="accessResult" class="access-result">
          <p><strong>granted:</strong> {{ accessResult.granted }}</p>
          <p><strong>verified:</strong> {{ accessResult.verified }}</p>
          <p><strong>message:</strong> {{ accessResult.message }}</p>
          <p><strong>HD_i:</strong> {{ accessResult.hdValue }}</p>
          <p><strong>root:</strong> {{ accessResult.root }}</p>
          <el-input
            v-if="accessResult.plaintext"
            :model-value="accessResult.plaintext"
            type="textarea"
            :rows="8"
            readonly
          />
        </div>
      </el-card>
    </section>

    <section class="chart-card">
      <div class="panel-title">5. 解密后负荷曲线</div>
      <div ref="chartRef" class="chart-box"></div>
    </section>

    <section class="chart-card" v-if="selectedVerkle">
      <div class="panel-title">6. Verkle 证明视图</div>
      <div class="detail-grid">
        <div><span>HD_i</span><strong>{{ selectedVerkle.hdValue }}</strong></div>
        <div><span>Proof Key</span><strong>{{ selectedVerkle.proofKey }}</strong></div>
        <div><span>Region Root</span><strong>{{ selectedVerkle.regionRoot }}</strong></div>
        <div><span>Relay Root</span><strong>{{ selectedVerkle.relayRoot }}</strong></div>
        <div><span>Chain Root</span><strong>{{ selectedVerkle.chainRoot }}</strong></div>
        <div><span>Chain Anchor Exists</span><strong>{{ selectedVerkle.chainAnchorExists }}</strong></div>
        <div class="wide">
          <span>ProofD_i(JSON)</span>
          <strong>{{ selectedVerkle.proofJson }}</strong>
        </div>
      </div>
    </section>

    <section class="chart-card" v-if="selectedVerkleAudit">
      <div class="panel-title">7. Verkle 一致性审计</div>
      <div class="detail-grid audit-grid">
        <div><span>overallPassed</span><strong>{{ selectedVerkleAudit.overallPassed }}</strong></div>
        <div><span>redisProofExists</span><strong>{{ selectedVerkleAudit.redisProofExists }}</strong></div>
        <div><span>redisProofMatchesRebuilt</span><strong>{{ selectedVerkleAudit.redisProofMatchesRebuilt }}</strong></div>
        <div><span>rebuiltRootMatchesMysqlRoot</span><strong>{{ selectedVerkleAudit.rebuiltRootMatchesMysqlRoot }}</strong></div>
        <div><span>rebuiltRootMatchesRegionChainRoot</span><strong>{{ selectedVerkleAudit.rebuiltRootMatchesRegionChainRoot }}</strong></div>
        <div><span>mysqlRelayRootMatchesRelayChainRoot</span><strong>{{ selectedVerkleAudit.mysqlRelayRootMatchesRelayChainRoot }}</strong></div>
        <div><span>proofVerifiesAgainstMysqlRoot</span><strong>{{ selectedVerkleAudit.proofVerifiesAgainstMysqlRoot }}</strong></div>
        <div><span>proofVerifiesAgainstRegionChainRoot</span><strong>{{ selectedVerkleAudit.proofVerifiesAgainstRegionChainRoot }}</strong></div>
        <div><span>proofVerifiesAgainstRelayChainRoot</span><strong>{{ selectedVerkleAudit.proofVerifiesAgainstRelayChainRoot }}</strong></div>
        <div><span>regionChainAnchorExists</span><strong>{{ selectedVerkleAudit.regionChainAnchorExists }}</strong></div>
        <div><span>relayChainAnchorExists</span><strong>{{ selectedVerkleAudit.relayChainAnchorExists }}</strong></div>
        <div><span>mysqlPackageHashMatchesIpfsHash</span><strong>{{ selectedVerkleAudit.mysqlPackageHashMatchesIpfsHash }}</strong></div>
        <div><span>mysqlPolicyHashMatchesIpfsPolicyHash</span><strong>{{ selectedVerkleAudit.mysqlPolicyHashMatchesIpfsPolicyHash }}</strong></div>
        <div><span>mysqlRoot</span><strong>{{ selectedVerkleAudit.mysqlRoot }}</strong></div>
        <div><span>rebuiltRoot</span><strong>{{ selectedVerkleAudit.rebuiltRoot }}</strong></div>
        <div><span>regionChainRoot</span><strong>{{ selectedVerkleAudit.regionChainRoot }}</strong></div>
        <div><span>relayChainRoot</span><strong>{{ selectedVerkleAudit.relayChainRoot }}</strong></div>
        <div class="wide"><span>redisProofJson</span><strong>{{ selectedVerkleAudit.redisProofJson }}</strong></div>
        <div class="wide"><span>rebuiltProofJson</span><strong>{{ selectedVerkleAudit.rebuiltProofJson }}</strong></div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page-shell {
  padding: 28px;
}

.hero-card {
  display: grid;
  grid-template-columns: 1.8fr 1fr;
  gap: 18px;
  padding: 28px;
  border-radius: 24px;
  background: linear-gradient(135deg, #123f67 0%, #1f5e8f 46%, #2d8f92 100%);
  color: #fff;
  box-shadow: 0 24px 60px rgba(17, 53, 88, 0.22);
}

.eyebrow {
  margin: 0 0 12px;
  letter-spacing: 0.22em;
  font-size: 12px;
  opacity: 0.82;
}

h1 {
  margin: 0;
  font-size: 34px;
}

.hero-copy {
  max-width: 720px;
  margin-top: 16px;
  line-height: 1.7;
  opacity: 0.92;
}

.hero-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  align-self: end;
}

.metric-box {
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(10px);
}

.metric-box span {
  display: block;
  opacity: 0.85;
  font-size: 12px;
}

.metric-box strong {
  display: block;
  margin-top: 10px;
  font-size: 22px;
}

.grid-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-top: 22px;
}

.panel-card,
.chart-card,
.status-card {
  border-radius: 22px;
  border: 1px solid rgba(18, 57, 90, 0.08);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 16px 40px rgba(31, 66, 103, 0.1);
}

.panel-title {
  font-size: 18px;
  font-weight: 700;
  color: #174162;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.detail-grid div,
.access-result,
.mode-hint {
  padding: 12px 14px;
  border-radius: 14px;
  background: #f5f9fd;
}

.mode-hint {
  margin-bottom: 14px;
  color: #425f78;
  line-height: 1.6;
}

.detail-grid span {
  display: block;
  margin-bottom: 8px;
  color: #5e768d;
  font-size: 12px;
}

.detail-grid strong {
  word-break: break-all;
}

.wide {
  grid-column: 1 / -1;
}

.chart-card {
  margin-top: 22px;
  padding: 22px;
}

.status-card {
  margin-top: 22px;
  padding: 22px;
}

.chart-box {
  width: 100%;
  height: 360px;
}

.audit-grid strong {
  font-family: "Consolas", "Menlo", monospace;
  font-size: 12px;
}

@media (max-width: 1100px) {
  .hero-card,
  .grid-layout {
    grid-template-columns: 1fr;
  }
}
</style>
