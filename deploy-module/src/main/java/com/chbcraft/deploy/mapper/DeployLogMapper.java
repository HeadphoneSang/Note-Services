package com.chbcraft.deploy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chbcraft.deploy.dto.DeployLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeployLogMapper extends BaseMapper<DeployLog> {
}
