package com.sgcc.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("chain_contract_registry")
public class ChainContractRegistryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String chainName;
    private String contractName;
    private String contractAddress;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
