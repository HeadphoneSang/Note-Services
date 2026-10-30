package com.chcbcraft.dto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteTagDiffResult {

    private List<Integer> failAddedTags;

    private List<Integer> failRemovedTags;

    public boolean hasConflict() {
        return !failAddedTags.isEmpty() || !failRemovedTags.isEmpty();
    }
}
