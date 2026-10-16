package com.energy.translator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> list;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;
    
    public static <T> PageResponse<T> of(List<T> list, long total, int page, int pageSize) {
        return PageResponse.<T>builder()
                .list(list)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .totalPages((int) Math.ceil((double) total / pageSize))
                .build();
    }
}
