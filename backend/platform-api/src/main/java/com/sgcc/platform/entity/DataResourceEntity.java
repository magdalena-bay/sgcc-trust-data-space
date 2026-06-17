package com.sgcc.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("data_resource")
public class DataResourceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dataId;
    private String region;
    private String ownerDid;
    private String dataType;
    private String policyExpr;
    private String policyOrg;
    private String policyRole;
    private String policyGrantStatus;
    private String cid;
    private String hdValue;
    private String packageHash;
    private String policyHash;
    private String dataHash;
    private String root;
    private String relayRoot;
    private String redisProofKey;
    private String status;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
