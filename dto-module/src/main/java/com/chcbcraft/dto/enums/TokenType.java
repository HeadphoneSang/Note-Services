package com.chcbcraft.dto.enums;

import lombok.Getter;

@Getter
public enum TokenType {
    ACCESS_TOKEN("access"),
    REFRESH_TOKEN("refresh");
    private final String type;
    TokenType(String type) {
        this.type = type;
    }

}
