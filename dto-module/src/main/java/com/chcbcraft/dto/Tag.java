package com.chcbcraft.dto;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标签 - 用于对笔记进行灵活标记和分类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tag")
public class Tag {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private String color;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private Integer userId;
}
