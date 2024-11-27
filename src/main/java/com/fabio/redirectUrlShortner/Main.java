package com.fabio.redirectUrlShortner;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
        String pathParameters = input.get("rawPath").toString();
        String shortUrlCode = pathParameters.replace("/", "");

        if(shortUrlCode.isEmpty()){
            throw new IllegalArgumentException("Invalid input: 'shortUrlCode' is required.");
        }

        GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName("url-shortener-table")
                .key(Map.of("id", AttributeValue.builder().s(shortUrlCode).build()))
                .build();

        Map<String, AttributeValue> item;
        try{
            item = dynamoDbClient.getItem(getItemRequest).item();
        } catch (Exception e){
            throw new RuntimeException("Error fetching URL data from DynamoDB: " + e.getMessage(), e);
        }

        if (item == null || item.isEmpty()) {
            throw new RuntimeException("URL not found in DynamoDB.");
        }

        UrlData urlData;
        try{
            // Deserializar os dados do DynamoDB para o objeto UrlData
            String originalUrl = item.get("originalUrl").s();
            long expirationTime = Long.parseLong(item.get("expirationTime").n());
            urlData = new UrlData(originalUrl, expirationTime);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing URL data: " + e.getMessage(), e);
        }

        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        Map<String, Object> response = new HashMap<>();

        if (currentTimeSeconds < urlData.getExpirationTime()){
            response.put("statusCode", 302);
            Map<String, String> headers = new HashMap<>();
            headers.put("Location", urlData.getOriginalUrl());
            response.put("headers", headers);
        } else{
            response.put("statusCode", 410);
            response.put("body", "This URL has expired.");
        }

        return response;
    }
}