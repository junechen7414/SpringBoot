# 第一階段：編譯 (使用 JDK 21)
FROM gradle:8.6-jdk21 AS build
# 設定工作目錄為 /app，後續的命令都會在這個目錄下執行
WORKDIR /app

COPY gradlew /app/
COPY gradle ./gradle/
# 先複製依賴定義文件到容器內的work路徑，利用 Docker Layer Cache 減少下載時間
COPY build.gradle settings.gradle ./

# 僅下載依賴
RUN ./gradlew dependencies --no-daemon

# 複製原始碼並編譯，跳過測試以加快構建速度
# 執行打包命令，生成可執行的 jar 文件在預設 build/libs/ 目錄下
# bootjar 是 Spring Boot Gradle Plugin 提供的任務。它不只會編譯程式碼，還會把所有相依的第三方 Library（如 Spring, Hibernate, Oracle Driver）全部塞進同一個 JAR 檔中（稱為 Fat Jar），讓它能獨立執行。
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# 第二階段：運行 (使用輕量化 JRE)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 從 build 階段複製編譯好的 jar
# Gradle 預設產出路徑在 build/libs/
COPY --from=build /app/build/libs/*.jar app.jar

# --no-verbose: 不輸出冗長訊息，保持日誌清晰。
# --spider: 只檢查 URL 是否可達，不下載內容。
# alpine 包含 wget，不包含curl
HEALTHCHECK --interval=30s --timeout=30s --start-period=30s --retries=3 CMD wget --no-verbose --tries=1 --spider http://localhost:8787/actuator/health || exit 1

EXPOSE 8787

# 執行jar檔
ENTRYPOINT ["java", "-jar", "app.jar"]