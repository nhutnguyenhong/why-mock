package com.whymock.server;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.whymock.mapping.CustomMappingSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.core.WireMockApp.MAPPINGS_ROOT;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@Slf4j
public class MockServer {

    static WireMockServer wireMockServer;

    public static void startServer(String port) {
        WireMockConfiguration option = options();
        option.port(Integer.parseInt(port));
        option.usingFilesUnderDirectory("./wiremock");
        option.mappingSource(new CustomMappingSource(option.filesRoot().child(MAPPINGS_ROOT)));
        wireMockServer = new WireMockServer(option);
        wireMockServer.start();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String port = StringUtils.defaultIfEmpty(System.getProperty("server.port"),"5868") ;
        System.out.println("Start mocking server at port " + port);
        startServer(port);
    }
}
