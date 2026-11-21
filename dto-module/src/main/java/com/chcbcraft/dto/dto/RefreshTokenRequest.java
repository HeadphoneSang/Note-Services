package com.chcbcraft.dto.dto;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private Long userId;
    private String sessionId;
    private String account;
    private String refreshToken;
}
