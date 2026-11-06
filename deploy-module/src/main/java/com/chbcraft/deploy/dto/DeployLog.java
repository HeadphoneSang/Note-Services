package com.chbcraft.deploy.dto;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("deploy_log")
public class DeployLog {

    private Long id;

    private String taskId;

    private String content;

    private String currentAt;

    private String action;

    private String status;
}
