package com.ibm.demo.util;

import java.util.List;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 統一分頁回應格式。
 * 將 Spring Data 的 Page<T>; 轉換為乾淨的 API 回應結構。
 *
 * @param <T> 資料內容的型別
 */
@Schema(description = "分頁回應")
public record PageResponse<T>(
        @Schema(description = "資料內容") List<T> content,

        @Schema(description = "當前頁碼（從 0 開始）", example = "0") int page,

        @Schema(description = "每頁筆數", example = "20") int size,

        @Schema(description = "總筆數", example = "100") long totalElements,

        @Schema(description = "總頁數", example = "5") int totalPages) {
    /**
     * 從 Spring Data Page 轉換為 PageResponse。
     *
     * @param springPage Spring Data 的 Page 物件
     * @param <T>        資料型別
     * @return PageResponse 實例
     */
    public static <T> PageResponse<T> from(Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages());
    }
}
