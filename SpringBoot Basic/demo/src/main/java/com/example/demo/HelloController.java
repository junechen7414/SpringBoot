package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // 標示為 REST 控制器
public class HelloController {
    // IoC 透過依賴注入管理 HelloService
    @Autowired
    private HelloService helloService;

    @GetMapping("/api/hello")
    public String hello() {
        return helloService.sayHello();
    }
}
