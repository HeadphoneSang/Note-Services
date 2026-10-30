package com.chcbcraft.dto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteDiffResult {

    private List<String> failUpdateBlocks = new ArrayList<>();
    private List<String> failDeletedBlocks = new ArrayList<>();
    private List<String> failCreatedBlocks = new ArrayList<>();

    public boolean hasConflict() {
        return !failUpdateBlocks.isEmpty() || !failDeletedBlocks.isEmpty() || !failCreatedBlocks.isEmpty();
    }

    @Override
    public String toString() {
        return "NoteDiffResult{" +
                "failUpdateBlocks=" + failUpdateBlocks +
                ", failDeletedBlocks=" + failDeletedBlocks +
                ", failCreatedBlocks=" + failCreatedBlocks +
                '}';
    }

}
