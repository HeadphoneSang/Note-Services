package com.chcbcraft.dto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteTagDiffRequest {

    private Integer noteId;

    private List<Integer> addTagIds;

    private List<Integer> removeTagIds;
}
