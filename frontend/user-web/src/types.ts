export interface UploadRequest {
  dataId?: string;
  region: string;
  ownerDid: string;
  dataType: string;
  policyOrg: string;
  policyRole: string;
  policyGrantStatus: string;
  plaintext?: string;
  encDataBase64?: string;
  ivBase64?: string;
  dekBase64?: string;
  dataHash?: string;
}

export interface ResourceSummary {
  dataId: string;
  region: string;
  ownerDid: string;
  dataType: string;
  cid: string;
  hdValue: string;
  packageHash: string;
  root: string;
  status: string;
}

export interface ResourceDetail extends ResourceSummary {
  policyExpr: string;
  policyHash: string;
  relayRoot: string;
}

export interface ResourceVerkle {
  dataId: string;
  hdValue: string;
  proofKey: string;
  proofJson: string;
  regionRoot: string;
  relayRoot: string;
  chainRoot: string;
  chainAnchorExists: boolean;
}

export interface AccessRequest {
  dataId: string;
  requesterOrg: string;
  requesterRole: string;
  requesterGrantStatus: string;
}

export interface AccessResponse {
  granted: boolean;
  verified: boolean;
  message: string;
  plaintext?: string;
  hdValue: string;
  packageHash: string;
  cid: string;
  root: string;
}

export interface SystemStatus {
  platformApi: string;
  privacyServiceBaseUrl: string;
  ipfsGatewayUrl: string;
  qingdaoWebaseUrl: string;
  weifangWebaseUrl: string;
  relayWebaseUrl: string;
  components: Record<string, boolean>;
}
