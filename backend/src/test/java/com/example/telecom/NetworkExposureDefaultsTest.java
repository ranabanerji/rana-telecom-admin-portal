package com.example.telecom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Loads the real src/main/resources configuration, which the test
 * application.yaml otherwise shadows on the classpath.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=file:src/main/resources/", "spring.datasource.url=jdbc:sqlite::memory:"})
class NetworkExposureDefaultsTest {

    private static final int NOT_FOUND = 404;

    @Autowired
    private Environment environment;

    @Test
    void serverAddressDefaultsToLoopback() throws UnknownHostException {
        InetAddress ipv4Loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        assertEquals(ipv4Loopback, InetAddress.getByName(environment.getProperty("server.address")),
                "server.address default");
    }

    @Test
    void serverRefusesConnectionsOnNonLoopbackAddress() throws SocketException {
        Optional<InetAddress> externalAddress = NetworkInterface.networkInterfaces()
                .flatMap(NetworkInterface::inetAddresses).filter(address -> address instanceof Inet4Address
                        && !address.isLoopbackAddress() && !address.isLinkLocalAddress())
                .findFirst();
        assumeTrue(externalAddress.isPresent(), "no non-loopback IPv4 address on this machine");

        InetSocketAddress target = new InetSocketAddress(externalAddress.get(), port());
        assertThrows(IOException.class, () -> {
            try (Socket socket = new Socket()) {
                socket.connect(target, 2000);
            }
        }, "backend accepted a connection on " + target);
    }

    @Test
    void apiDocsAreDisabledWithoutDevProfile() throws IOException, InterruptedException {
        assertEquals(NOT_FOUND, statusOf("/v3/api-docs"), "/v3/api-docs status");
    }

    @Test
    void swaggerUiIsDisabledWithoutDevProfile() throws IOException, InterruptedException {
        assertEquals(NOT_FOUND, statusOf("/swagger-ui.html"), "/swagger-ui.html status");
    }

    private int port() {
        return environment.getRequiredProperty("local.server.port", Integer.class);
    }

    private int statusOf(String path) throws IOException, InterruptedException {
        return NetworkExposureDevProfileTest.statusOf(port(), path);
    }
}
