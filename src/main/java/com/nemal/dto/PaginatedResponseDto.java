package com.nemal.dto;

import java.util.List;

public record PaginatedResponseDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {}

