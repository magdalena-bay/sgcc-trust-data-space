package com.sgcc.platform.controller;

import com.sgcc.platform.dto.AccessRequest;
import com.sgcc.platform.dto.AccessResponse;
import com.sgcc.platform.dto.ResourceDetailResponse;
import com.sgcc.platform.dto.ResourceSummaryResponse;
import com.sgcc.platform.dto.ResourceVerkleResponse;
import com.sgcc.platform.dto.ResourceVerkleAuditResponse;
import com.sgcc.platform.dto.SystemStatusResponse;
import com.sgcc.platform.dto.UploadRequest;
import com.sgcc.platform.dto.UploadResponse;
import com.sgcc.platform.service.DemoResourceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoDataController {

    private final DemoResourceService demoResourceService;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return demoResourceService.health();
    }

    @GetMapping("/system-status")
    public SystemStatusResponse systemStatus() {
        return demoResourceService.systemStatus();
    }

    @PostMapping("/upload")
    public UploadResponse upload(@Valid @RequestBody UploadRequest request) {
        return demoResourceService.upload(request);
    }

    @GetMapping("/resources")
    public List<ResourceSummaryResponse> listResources() {
        return demoResourceService.listResources();
    }

    @GetMapping("/resources/{dataId}")
    public ResourceDetailResponse getResource(@PathVariable String dataId) {
        return demoResourceService.getResource(dataId);
    }

    @GetMapping("/resources/{dataId}/verkle")
    public ResourceVerkleResponse getVerkle(@PathVariable String dataId) {
        return demoResourceService.getVerkle(dataId);
    }

    @GetMapping("/resources/{dataId}/verkle-audit")
    public ResourceVerkleAuditResponse getVerkleAudit(@PathVariable String dataId) {
        return demoResourceService.getVerkleAudit(dataId);
    }

    @PostMapping("/access")
    public AccessResponse access(@Valid @RequestBody AccessRequest request) {
        return demoResourceService.access(request);
    }
}
