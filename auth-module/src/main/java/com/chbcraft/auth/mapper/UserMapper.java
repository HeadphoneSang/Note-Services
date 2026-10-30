package com.chbcraft.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chcbcraft.dto.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问层（auth 域：注册/登录/改密码）
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
