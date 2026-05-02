package com.ibm.demo.util;

// 定義一個通用的軟刪除接口，適用於所有需要軟刪除功能的實體。
public interface SoftDeleteRepository<ID> {
    int softDeleteById(ID id, Integer version);
}
