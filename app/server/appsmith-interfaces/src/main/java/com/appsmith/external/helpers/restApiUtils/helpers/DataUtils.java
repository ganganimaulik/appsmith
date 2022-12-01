package com.appsmith.external.helpers.restApiUtils.helpers;

import com.appsmith.external.dtos.MultipartFormDataDTO;
import com.appsmith.external.exceptions.pluginExceptions.AppsmithPluginError;
import com.appsmith.external.exceptions.pluginExceptions.AppsmithPluginException;
import com.appsmith.external.helpers.PluginUtils;
import com.appsmith.external.models.ActionConfiguration;
import com.appsmith.external.models.ApiContentType;
import com.appsmith.external.models.Property;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonSyntaxException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class DataUtils {

    public static String FIELD_API_CONTENT_TYPE = "apiContentType";

    private final ObjectMapper objectMapper;

    public enum MultipartFormDataType {
        TEXT,
        FILE,
        ARRAY,
    }

    public DataUtils() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public BodyInserter<?, ?> buildBodyInserter(Object body, String contentType, Boolean encodeParamsToggle) {
        if (body == null) {
            return BodyInserters.fromValue(new byte[0]);
        }
        switch (contentType) {
            case MediaType.APPLICATION_JSON_VALUE:
                final Object bodyObject = parseJsonBody(body);
                return BodyInserters.fromValue(bodyObject);
            case MediaType.APPLICATION_FORM_URLENCODED_VALUE:
                final String formData = parseFormData((List<Property>) body, encodeParamsToggle);
                if ("".equals(formData)) {
                    return BodyInserters.fromValue(new byte[0]);
                }
                return BodyInserters.fromValue(formData);
            case MediaType.MULTIPART_FORM_DATA_VALUE:
                return parseMultipartFileData((List<Property>) body);
            default:
                return BodyInserters.fromValue(((String) body).getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    public Object parseJsonBody(Object body) {
        try {
            if (body instanceof String) {
                // Setting the requestBody to an empty byte array here
                // since the an empty string causes issues with a signed request.
                // If the content of the SignableRequest is null, the query string parameters
                // will be encoded and used as the contentSha256 segment of the canonical request string.
                // This causes a SignatureMatch Error for signed urls like those generated by AWS S3.
                // More detail here - https://github.com/aws/aws-sdk-java/issues/2205
                if ("" == body) {
                    return new byte[0];
                }
                Object objectFromJson = objectFromJson((String) body);
                if (objectFromJson != null) {
                    body = objectFromJson;
                }
            }
        } catch (JsonSyntaxException | ParseException e) {
            throw new AppsmithPluginException(
                    AppsmithPluginError.PLUGIN_JSON_PARSE_ERROR,
                    body,
                    "Malformed JSON: " + e.getMessage()
            );
        }
        return body;
    }

    public String parseFormData(List<Property> bodyFormData, Boolean encodeParamsToggle) {
        if (bodyFormData == null || bodyFormData.isEmpty()) {
            return "";
        }

        return bodyFormData
                // Disregard keys that are null
                .stream()
                .filter(property -> property.getKey() != null)
                .map(property -> {
                    String key = property.getKey();
                    String value = (String) property.getValue();

                    if (encodeParamsToggle == true) {
                        try {
                            value = URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
                        } catch (UnsupportedEncodingException e) {
                            throw new UnsupportedOperationException(e);
                        }
                    }

                    return key + "=" + value;
                })
                .collect(Collectors.joining("&"));

    }

    public BodyInserter<?, ?> parseMultipartFileData(List<Property> bodyFormData) {
        if (bodyFormData == null || bodyFormData.isEmpty()) {
            return BodyInserters.fromValue(new byte[0]);
        }

        return (BodyInserter<?, ClientHttpRequest>) (outputMessage, context) ->
                Mono.defer(() -> {
                    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

                    for (Property property : bodyFormData) {
                        final String key = property.getKey();

                        if (property.getKey() == null) {
                            continue;
                        }

                        // This condition is for the current scenario, while we wait for client changes to come in
                        // before the migration can be introduced
                        if (property.getType() == null) {
                            bodyBuilder.part(key, property.getValue());
                            continue;
                        }

                        final MultipartFormDataType multipartFormDataType =
                                MultipartFormDataType.valueOf(property.getType().toUpperCase(Locale.ROOT));

                        switch (multipartFormDataType) {
                            case TEXT:
                                byte[] valueBytesArray = new byte[0];
                                if (StringUtils.hasLength(String.valueOf(property.getValue()))) {
                                    valueBytesArray = String.valueOf(property.getValue()).getBytes(StandardCharsets.ISO_8859_1);
                                }
                                bodyBuilder.part(key, valueBytesArray, MediaType.TEXT_PLAIN);
                                break;
                            case FILE:
                                try {
                                    populateFileTypeBodyBuilder(bodyBuilder, property, outputMessage);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    throw new AppsmithPluginException(
                                            AppsmithPluginError.PLUGIN_DATASOURCE_ARGUMENT_ERROR,
                                            "Unable to parse content. Expected to receive an array or object of multipart data"
                                    );
                                }
                                break;
                            case ARRAY:
                                if (property.getValue() instanceof String) {
                                    final String value = (String) property.getValue();
                                    try {
                                        final JsonNode jsonNode = objectMapper.readTree(value);
                                        if (jsonNode.isArray()) {
                                            for (JsonNode node : jsonNode) {
                                                if (node.isTextual()) bodyBuilder.part(key, node.asText());
                                                else bodyBuilder.part(key, node);
                                            }
                                        } else {
                                            bodyBuilder.part(key, value);
                                        }
                                    } catch (JsonProcessingException e) {
                                        bodyBuilder.part(key, value);
                                    }
                                } else {
                                    bodyBuilder.part(key, property.getValue());
                                }
                                break;
                        }
                    }

                    final BodyInserters.MultipartInserter multipartInserter =
                            BodyInserters
                                    .fromMultipartData(bodyBuilder.build());
                    return multipartInserter
                            .insert(outputMessage, context);
                });
    }

    private void populateFileTypeBodyBuilder(MultipartBodyBuilder bodyBuilder, Property property, ClientHttpRequest outputMessage)
            throws IOException {
        final String fileValue = (String) property.getValue();
        final String key = property.getKey();
        List<MultipartFormDataDTO> multipartFormDataDTOs = new ArrayList<>();


        if (fileValue.startsWith("{")) {
            // Check whether the JSON string is an object
            final MultipartFormDataDTO multipartFormDataDTO = objectMapper.readValue(
                    fileValue,
                    MultipartFormDataDTO.class);
            multipartFormDataDTOs.add(multipartFormDataDTO);
        } else if (fileValue.startsWith("[")) {
            // Check whether the JSON string is an array
            multipartFormDataDTOs = Arrays.asList(
                    objectMapper.readValue(
                            fileValue,
                            MultipartFormDataDTO[].class));
        } else {
            throw new AppsmithPluginException(AppsmithPluginError.PLUGIN_DATASOURCE_ARGUMENT_ERROR,
                    "Unable to parse content. Expected to receive an array or object of multipart data");
        }

        multipartFormDataDTOs.forEach(multipartFormDataDTO -> {
            final MultipartFormDataDTO finalMultipartFormDataDTO = multipartFormDataDTO;
            Flux<DataBuffer> data = DataBufferUtils.readInputStream(
                    () -> new ByteArrayInputStream(String.valueOf(finalMultipartFormDataDTO.getData())
                            .getBytes(StandardCharsets.ISO_8859_1)),
                    outputMessage.bufferFactory(),
                    4096);

            bodyBuilder.asyncPart(key, data, DataBuffer.class)
                    .filename(multipartFormDataDTO.getName())
                    .contentType(MediaType.valueOf(multipartFormDataDTO.getType()));
        });

    }

    /**
     * Given a JSON string, we infer the top-level type of the object it represents and then parse it into that
     * type. However, only `Map` and `List` top-levels are supported. Note that the map or list may contain
     * anything, like booleans or number or even more maps or lists. It's only that the top-level type should be a
     * map / list.
     *
     * @param jsonString A string that confirms to JSON syntax. Shouldn't be null.
     * @return An object of type `Map`, `List`, if applicable, or `null`.
     */
    private static Object objectFromJson(String jsonString) throws ParseException {
        Class<?> type;
        String trimmed = jsonString.trim();

        if (trimmed.startsWith("{")) {
            type = Map.class;
        } else if (trimmed.startsWith("[")) {
            type = List.class;
        } else {
            return null;
        }

        JSONParser jsonParser = new JSONParser(JSONParser.MODE_PERMISSIVE);
        Object parsedJson = null;

        if (type.equals(List.class)) {
            parsedJson = (JSONArray) jsonParser.parse(jsonString);
        } else {
            parsedJson = (JSONObject) jsonParser.parse(jsonString);
        }

        return parsedJson;

    }

    public Object getRequestBodyObject(ActionConfiguration actionConfiguration, String reqContentType,
                                       boolean encodeParamsToggle, HttpMethod httpMethod) {
        // We initialize this object to an empty string because body can never be empty
        // Based on the content-type, this Object may be of type MultiValueMap or String
        Object requestBodyObj = "";

        // We will read the request body for all HTTP calls where the apiContentType is NOT "none".
        // This is irrespective of the content-type header or the HTTP method
        String apiContentTypeStr = (String) PluginUtils.getValueSafelyFromFormData(
                actionConfiguration.getFormData(),
                FIELD_API_CONTENT_TYPE
        );
        ApiContentType apiContentType = ApiContentType.getValueFromString(apiContentTypeStr);

        if (!httpMethod.equals(HttpMethod.GET)) {
            // Read the body normally as this is a non-GET request
            requestBodyObj = (actionConfiguration.getBody() == null) ? "" : actionConfiguration.getBody();
        } else if (apiContentType != null && apiContentType != ApiContentType.NONE) {
            // This is a GET request
            // For all existing GET APIs, the apiContentType will be null. Hence we don't read the body
            // Also, any new APIs which have apiContentType set to NONE shouldn't read the body.
            // All other API content types should read the body
            requestBodyObj = (actionConfiguration.getBody() == null) ? "" : actionConfiguration.getBody();
        }

        if (MediaType.APPLICATION_FORM_URLENCODED_VALUE.equals(reqContentType)
                || MediaType.MULTIPART_FORM_DATA_VALUE.equals(reqContentType)) {
            requestBodyObj = actionConfiguration.getBodyFormData();
        }

        requestBodyObj = this.buildBodyInserter(requestBodyObj, reqContentType, encodeParamsToggle);

        return requestBodyObj;
    }
}