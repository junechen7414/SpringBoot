package com.ibm.demo.contract;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;

import com.ibm.demo.GlobalExceptionHandler;
import com.ibm.demo.config.SecurityConfig;

/**
 * 契約測試專用的最小 Spring 設定。
 *
 * <p>刻意<b>不</b>用 {@code @ComponentScan}：{@code @SpringBootTest} 家族會從測試類別往上找最近的
 * {@code @SpringBootConfiguration}，因此本類別讓 {@code com.ibm.demo.contract} 底下的測試接管
 * context 組成，只匯入契約真正涉及的三個角色（探針 controller、例外處理、Security），不會連帶
 * 拉進 {@code RestClientConfig}、JPA、springdoc 等與 wire format 無關的基礎設施。
 *
 * <p>換句話說：這個 context 起不來就一定是錯誤契約壞了，不會是別的東西壞了。
 */
@SpringBootConfiguration
@Import({ ContractProbeController.class, GlobalExceptionHandler.class, SecurityConfig.class })
public class ContractTestApplication {
}
