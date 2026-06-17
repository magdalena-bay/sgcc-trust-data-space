package com.sgcc.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("access_audit")
public class AccessAuditEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dataId;
    private String requesterOrg;
    private String requesterRole;
    private String requesterGrantStatus;
    private Integer verified;
    private Integer granted;
    private String reason;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
