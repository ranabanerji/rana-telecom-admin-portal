package com.example.telecom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertTrue;

// No web server: the point is to check the resolved setting without opening a socket on every interface.
@SpringBootTest(webEnvironment = WebEnvironment.NONE, properties = {"spring.config.location=file:src/main/resources/",
        "spring.datasource.url=jdbc:sqlite::memory:", "SERVER_ADDRESS=0.0.0.0"})
class NetworkExposureOverrideTest {

    @Autowired
    private Environment environment;

    @Test
    void serverAddressCanBeOverridden() throws UnknownHostException {
        assertTrue(InetAddress.getByName(environment.getProperty("server.address")).isAnyLocalAddress(),
                "server.address should be the wildcard address when SERVER_ADDRESS=0.0.0.0");
    }
}
