package org.template.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.template.mapper.UserMapper;
import org.template.model.User;
import org.template.model.dto.UpdateUserRequest;

import java.util.Optional;

/**
 * 用户业务层（note 域）— 仅保留资料接口
 * <p>
 * 注册/登录/改密码等认证逻辑已迁往 auth-module。
 */
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    // ==================== 获取用户信息 ====================

    /**
     * 根据用户 ID 获取用户信息
     *
     * @param userId 用户 ID
     * @return 用户实体（密码字段会被置空，避免泄露）
     */
    public User getUserInfo(Integer userId) {
        User user = Optional.ofNullable(userMapper.selectById(userId))
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        // 密码不返回
        user.setPassword(null);
        return user;
    }

    // ==================== 更新用户信息 ====================

    /**
     * 更新用户基本信息（昵称、手机号、邮箱、头像）
     */
    @Transactional
    public User updateUserInfo(Integer userId, UpdateUserRequest req) {
        User user = Optional.ofNullable(userMapper.selectById(userId))
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getPhoneNumber() != null) {
            user.setPhoneNumber(req.getPhoneNumber());
        }
        if (req.getEmail() != null) {
            user.setEmail(req.getEmail());
        }
        if (req.getAvatar() != null) {
            user.setAvatar(req.getAvatar());
        }

        userMapper.updateById(user);
        user.setPassword(null);
        return user;
    }
}
