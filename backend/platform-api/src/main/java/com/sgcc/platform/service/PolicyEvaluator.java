package com.sgcc.platform.service;

import com.sgcc.platform.dto.AccessRequest;
import com.sgcc.platform.entity.DataResourceEntity;
import org.springframework.stereotype.Component;

@Component
public class PolicyEvaluator {

    public boolean isGranted(DataResourceEntity resource, AccessRequest request) {
        return resource.getPolicyOrg().equals(request.getRequesterOrg())
                && resource.getPolicyRole().equals(request.getRequesterRole())
                && resource.getPolicyGrantStatus().equals(request.getRequesterGrantStatus());
    }
}
