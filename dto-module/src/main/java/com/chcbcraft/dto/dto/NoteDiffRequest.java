package com.chcbcraft.dto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.chcbcraft.dto.NoteBlock;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteDiffRequest {

    private List<NoteBlock> updatedBlocks;
    private List<NoteBlock> deletedBlocks;
    private List<NoteBlock> insertedBlocks;
}


