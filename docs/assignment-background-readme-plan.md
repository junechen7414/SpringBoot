# 作業背景與 README 整理紀錄

更新：2026-11-26。兩個 repo 的根目錄 README 已完成；本次只整理文件，沒有改程式、設定或 CI。

## 已核對的背景

唯讀閱讀使用者提供的訂單管理作業規格、初階／進階驗收指標與培訓簡報，核對到以下學習目標：

- 訂單、商品、Account 三個平台的 CRUD、請求欄位驗證與 Oracle／JPA 操作。
- Controller／Service／Repository 分層，以及模組間的 HTTP 呼叫練習；簡報當時以 WebClient 為例。
- 自行規劃業務檢核、交易管理、全域例外處理與 Service 單元測試。

目前實作使用單一 Spring Boot 應用、三個 domain 分組與 loopback HTTP，另採 RestClient、Flyway、Resilience4j、契約／整合測試、容器、監控與上下游 CI。README 分清練習起點與後續演進，沒有把所有實作選擇都歸因於教材，也沒有宣稱逐字重現原作業規格。

Playwright 的背景依使用者說明：先從網路上的 SauceDemo 資源練習 UI 自動化，後來接上 SpringBoot API，練習後端建置、映像發布、跨 repo 觸發與測試的整段整合。分 repo 是這項整合練習的一部分，不是原 Java／Spring 教材要求。

原教材只在本機閱讀，未執行腳本、巨集或程式，也未搬入 repo。公開說明不附原教材、原文節錄、公司內部資訊、個人路徑或實際憑證。

## 完成內容

### SpringBoot

新增 [README.md](../README.md)，包含：

- 專案定位、作業起點、目前呼叫與交易分工，以及與下游測試的關係。
- 以 CONTEXT／目前程式為準的業務範圍，並區分 Account 與 API Credential。
- 實際建置版本、完整 Compose 與 Oracle＋IDE／bootRun 兩種本機路徑。
- `.env`／程序環境差異、認證、服務入口、測試與 OpenAPI 指令。
- 指標／追蹤鏈路、CI 發布與下游通知／Swagger 同步順序、教學環境限制及延伸文件。

### Playwright

新增同層 Playwright repo 的根目錄 README.md，包含：

- SauceDemo UI 到 SpringBoot API 的學習脈絡，以及分 repo 整合的目的。
- Page Objects、API requester、fixtures 與生成型別的分工。
- pnpm 安裝、UI 瀏覽器、API 環境／就緒／型別／測試順序。
- 測試環境重置的資料影響、Windows-only clean、並行資料隔離與報告位置。
- 兩條 CI、PR label gate、image tag 選擇、live spec 與版控快照的關係。

兩份 README 都以第一次接觸專案的開發者為讀者，只介紹目前專案；不記錄其他產品、搬遷／淘汰策略或對話中的澄清歷史。

## 驗證與範圍

- 對照 build／package 宣告、config、Compose、workflow、業務詞彙及相關程式核對 README。
- 靜態檢查兩份 Markdown 的標題連結、檔案路徑、程式碼區塊、檔尾與空白；60 個本地路徑／章節連結均有效，提及的 pnpm scripts 均存在。
- `git diff --check` 通過；未追蹤的新 README 另行檢查。既有 handout 與其他未提交檔案保持原狀。
- 沒有安裝、啟動容器、執行應用、重產型別或跑整合／UI／API／壓測；不能把本次靜態核對當成執行通過。
- 以上為文件整理與靜態核對紀錄；提交／推送狀態以 Git 紀錄為準。
