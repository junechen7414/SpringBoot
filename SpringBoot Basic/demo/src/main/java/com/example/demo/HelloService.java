package com.example.demo;

import org.springframework.stereotype.Service;

@Service // 標示為 Service，交由 Spring IoC 管理
public class HelloService {
    public String sayHello() {
        return "Hello from Service";
    }
}
