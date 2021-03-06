package com.whymock.mapping;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.common.JsonException;
import com.github.tomakehurst.wiremock.common.SafeNames;
import com.github.tomakehurst.wiremock.common.TextFile;
import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.AbsentPattern;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.standalone.MappingFileException;
import com.github.tomakehurst.wiremock.standalone.MappingsSource;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.stubbing.StubMappingCollection;
import com.github.tomakehurst.wiremock.stubbing.StubMappings;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.common.AbstractFileSource.byFileExtension;
import static com.github.tomakehurst.wiremock.common.Json.writePrivate;

@Slf4j
public class CustomMappingSource implements MappingsSource {

    private static final String FILE_NAME = "file_name";

    private static final String STATUS = "status";

    private static final String CONTEXT = "context";

    private final FileSource mappingsFileSource;

    private final Map<UUID, String> fileNameMap;

    private StubMappings stubMappings;

    private HttpHeader[] additionalHeaders = new HttpHeader[]{
            new HttpHeader("Access-Control-Allow-Origin", "*"),
            new HttpHeader("Access-Control-Allow-Methods", "*"),
            new HttpHeader("Access-Control-Allow-Headers",
                    "*")};

    public CustomMappingSource(FileSource mappingsFileSource) {
        this.mappingsFileSource = mappingsFileSource;
        fileNameMap = new HashMap<>();
    }

    @Override
    public void save(List<StubMapping> stubMappings) {
        stubMappings.stream().filter(Objects::nonNull).filter(StubMapping::isDirty).forEach(this::save);
    }

    @Override
    public void save(StubMapping stubMapping) {
        String path = fileNameMap.getOrDefault(
                stubMapping.getId(),
                SafeNames.makeSafeFileName(stubMapping));
        Optional<String> pathByContext = getContextPath(stubMapping);
        String absolutePath = path;
        if (stubMapping.getMetadata().containsKey(FILE_NAME)) {
            try {
                String directoryName = stubMapping.getMetadata().get(FILE_NAME).toString();
                String absoluteDirPath =
                        mappingsFileSource.getPath() +
                                (pathByContext.isPresent() ? File.separator + pathByContext.get() : "")
                                + File.separator + directoryName;
                FileUtils.forceMkdir(new File(absoluteDirPath));
                mappingsFileSource.deleteFile(path);
                path = (pathByContext.isPresent() ? pathByContext.get() + File.separator : "") + directoryName + File.separator + stubMapping.getName() + ".json";
                absolutePath = mappingsFileSource.getPath() + File.separator + path;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        mappingsFileSource.writeTextFile(path, writePrivate(stubMapping));

        fileNameMap.put(stubMapping.getId(), path);
        stubMapping.setDirty(false);
        allowLoad(stubMapping);
        addDefaultHeaders(stubMapping);
        addOptionMapping(new TextFile(new File(absolutePath).toURI()));
    }

    private void addOptionMapping(TextFile mappingFile) {
        StubMappingCollection stubCollectionOptions = Json.read(mappingFile.readContentsAsString(), StubMappingCollection.class);
        setOptionsStub(stubCollectionOptions);
        addDefaultHeaders(stubCollectionOptions);
        stubMappings.addMapping(stubCollectionOptions);
    }

    private Optional<String> getContextPath(StubMapping stubMapping) {
        if (stubMapping.getMetadata().containsKey(CONTEXT)) {
            String directoryName = stubMapping.getMetadata().get(CONTEXT).toString();
            try {
                File contextDir = new File(mappingsFileSource.getPath() + File.separator + CONTEXT);
                if (!contextDir.exists()) {
                    FileUtils.forceMkdir(contextDir);
                }
                FileUtils.forceMkdir(new File(mappingsFileSource.getPath() + File.separator + CONTEXT + File.separator + directoryName));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return Optional.of(CONTEXT + File.separator + directoryName);
        }
        return Optional.empty();
    }


    @Override
    public void remove(StubMapping stubMapping) {
        String path = fileNameMap.get(stubMapping.getId());

        if (stubMapping.getMetadata().containsKey(FILE_NAME)) {
            path =
                    (stubMapping.getMetadata().containsKey(CONTEXT) ?
                            CONTEXT + File.separator + stubMapping.getMetadata().get(CONTEXT) + File.separator
                            :
                            "") +
                            stubMapping.getMetadata().get(FILE_NAME)
                            + File.separator + stubMapping.getName() + ".json";
        }
        mappingsFileSource.deleteFile(path);
        fileNameMap.remove(stubMapping.getId());
    }

    @Override
    public void removeAll() {
        fileNameMap.values().forEach(path -> mappingsFileSource.deleteFile(path));
        fileNameMap.clear();
    }

    @Override
    public void loadMappingsInto(StubMappings stubMappings) {
        if (!mappingsFileSource.exists()) {
            return;
        }
        if (this.stubMappings == null) {
            this.stubMappings = stubMappings;
        }

        Iterable<TextFile> mappingJSONFiles = mappingsFileSource.listFilesRecursively().stream().filter(byFileExtension("json")::apply).collect(Collectors.toList());
        List<TextFile> allTextFile = new ArrayList<>(IteratorUtils.toList(mappingJSONFiles.iterator()));
        allTextFile.forEach(this::loadStubMapping);
    }

    private void allowLoad(StubMapping stubCollection) {
        if (stubCollection.getMetadata() != null &&
                stubCollection.getMetadata().containsKey(STATUS) &&
                stubCollection.getMetadata().getString(STATUS).equalsIgnoreCase("DISABLED")) {
            UrlPattern urlPattern = stubCollection.getRequest().getUrlMatcher();
            StringValuePattern pattern = urlPattern.getPattern();
            if (pattern instanceof RegexPattern) {
                RegexPattern pattern1 = (RegexPattern) pattern;
                StringValuePattern newStringValuePattern = new AbsentPattern(pattern1.getMatches());
                UrlPattern newUrlPattern = new UrlPattern(newStringValuePattern, urlPattern.isRegex());
                setFieldValue(stubCollection.getRequest(), "url", newUrlPattern);
            }
        }
    }

    private void loadStubMapping(TextFile mappingFile) {
        try {
            List<StubMappingCollection> a = new ArrayList<>();
            StubMappingCollection s = Json.read(mappingFile.readContentsAsString(), StubMappingCollection.class);
            allowLoad(s);
            addDefaultHeaders(s);
            a.add(s);
            StubMappingCollection stubCollectionOptions = Json.read(mappingFile.readContentsAsString(), StubMappingCollection.class);
            setOptionsStub(stubCollectionOptions);
            a.add(stubCollectionOptions);
            a.forEach(stubCollection -> {
                for (StubMapping mapping : stubCollection.getMappingOrMappings()) {
                    mapping.setDirty(false);
                    stubMappings.addMapping(mapping);
                    fileNameMap.put(mapping.getId(), mappingFile.getPath());
                }
            });
        } catch (JsonException e) {
            throw new MappingFileException(mappingFile.getPath(), e.getErrors().first().getDetail());
        }
    }

    private void addDefaultHeaders(StubMapping stub) {
        HttpHeader[] objects = Arrays.stream(additionalHeaders).filter(additionalHeader -> !stub.getResponse().getHeaders().getHeader(additionalHeader.key()).isPresent()
        ).toArray(HttpHeader[]::new);
        HttpHeaders headers = stub.getResponse().getHeaders().plus(objects);
        setFieldValue(stub.getResponse(), "headers", headers);
    }

    private void setOptionsStub(StubMappingCollection item) {
        item.setRequest(new RequestPattern(item.getRequest().getUrlMatcher(),
                RequestMethod.OPTIONS,
                item.getRequest().getHeaders(),
                item.getRequest().getQueryParameters(),
                item.getRequest().getCookies(),
                item.getRequest().getBasicAuthCredentials(),
                item.getRequest().getBodyPatterns(),
                item.getRequest().getCustomMatcher(),
                null,
                item.getRequest().getMultipartPatterns()));
        //remove body content
        setFieldAccessibleAndValue(item.getResponse(), "body", new Body(""));
        //set status 200 as default
        setFieldAccessibleAndValue(item.getResponse(), STATUS, 200);
        addDefaultHeaders(item);
        item.setId(UUID.randomUUID());
    }

    private void setFieldValue(Object obj, String fieldName, Object fieldValue) {
        try {
            Field headersField = obj.getClass().getDeclaredField(fieldName);
            headersField.setAccessible(true);
            headersField.set(obj, fieldValue);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void setFieldAccessibleAndValue(Object obj, String fieldName, Object fieldValue) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
            field.set(obj, fieldValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}