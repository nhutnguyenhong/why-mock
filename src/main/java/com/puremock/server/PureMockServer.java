package com.puremock.server;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.puremock.mapping.CustomMappingSource;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.core.WireMockApp.MAPPINGS_ROOT;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@Slf4j
public class PureMockServer {

    static WireMockServer wireMockServer;

    public static void startServer() {
        WireMockConfiguration option = options();
        option.port(5868);
        option.usingFilesUnderDirectory("./wiremock");
        option.mappingSource(new CustomMappingSource(option.filesRoot().child(MAPPINGS_ROOT)));
        wireMockServer = new WireMockServer(option);
        wireMockServer.start();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Start mocking server at port 5868");
        startServer();
    }
}
