package com.example.telecom;

import com.example.telecom.controller.CustomerController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class TelecomDemoApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertNotNull(context.getBean(CustomerController.class), "CustomerController bean");
    }

}
