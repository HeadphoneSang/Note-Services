package com.chcbcraft.dto;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记本/文件夹 - 用于对笔记进行分类组织
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("notebook")
public class Notebook {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private String description;

    private String color;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer userId;

    private String avatar;

    @TableField(exist = false)
    private int totalNoteCnt;
}
