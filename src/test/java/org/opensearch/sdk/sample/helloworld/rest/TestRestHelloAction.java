/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk.sample.helloworld.rest;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.identity.ExtensionTokenProcessor;
import org.opensearch.identity.PrincipalIdentifierToken;
import org.opensearch.rest.RestHandler.Route;
import org.opensearch.rest.RestRequest.Method;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.extensions.rest.ExtensionRestRequest;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestResponse;
import org.opensearch.rest.RestStatus;
import org.opensearch.sdk.ExtensionRestHandler;
import org.opensearch.test.OpenSearchTestCase;

public class TestRestHelloAction extends OpenSearchTestCase {

    private ExtensionRestHandler restHelloAction;
    private static final String EXTENSION_NAME = "hello-world";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        restHelloAction = new RestHelloAction();
    }

    @Test
    public void testRoutes() {
        List<Route> routes = restHelloAction.routes();
        assertEquals(4, routes.size());
        assertEquals(Method.GET, routes.get(0).getMethod());
        assertEquals("/hello", routes.get(0).getPath());
        assertEquals(Method.POST, routes.get(1).getMethod());
        assertEquals("/hello", routes.get(1).getPath());
        assertEquals(Method.DELETE, routes.get(2).getMethod());
        assertEquals("/hello", routes.get(2).getPath());
        assertEquals(Method.PUT, routes.get(3).getMethod());
        assertEquals("/hello/{name}", routes.get(3).getPath());
    }

    @Test
    public void testHandleRequest() {
        Principal userPrincipal = () -> "user1";
        ExtensionTokenProcessor extensionTokenProcessor = new ExtensionTokenProcessor(EXTENSION_NAME);
        PrincipalIdentifierToken token = extensionTokenProcessor.generateToken(userPrincipal);
        Map<String, String> params = Collections.emptyMap();

        ExtensionRestRequest getRequest = new ExtensionRestRequest(Method.GET, "/hello", params, null, new BytesArray(""), token);
        ExtensionRestRequest putRequest = new ExtensionRestRequest(
            Method.PUT,
            "/hello/Passing+Test",
            Map.of("name", "Passing+Test"),
            null,
            new BytesArray(""),
            token
        );
        ExtensionRestRequest postRequest = new ExtensionRestRequest(
            Method.POST,
            "/hello",
            params,
            XContentType.JSON,
            new BytesArray("{\"adjective\":\"testable\"}"),
            token
        );
        ExtensionRestRequest deleteRequest = new ExtensionRestRequest(
            Method.DELETE,
            "/hello",
            params,
            null,
            new BytesArray("testable"),
            token
        );
        ExtensionRestRequest badRequest = new ExtensionRestRequest(
            Method.PUT,
            "/hello/Bad%Request",
            Map.of("name", "Bad%Request"),
            null,
            new BytesArray(""),
            token
        );
        ExtensionRestRequest unhandledRequest = new ExtensionRestRequest(Method.HEAD, "/goodbye", params, null, new BytesArray(""), token);

        // Initial default response
        RestResponse response = restHelloAction.handleRequest(getRequest);
        assertEquals(RestStatus.OK, response.status());
        assertEquals(BytesRestResponse.TEXT_CONTENT_TYPE, response.contentType());
        String responseStr = new String(BytesReference.toBytes(response.content()), StandardCharsets.UTF_8);
        assertEquals("Hello, World!", responseStr);

        // Change world's name
        response = restHelloAction.handleRequest(putRequest);
        assertEquals(RestStatus.OK, response.status());
        assertEquals(BytesRestResponse.TEXT_CONTENT_TYPE, response.contentType());
        responseStr = new String(BytesReference.toBytes(response.content()), StandardCharsets.UTF_8);
        assertEquals("Updated the world's name to Passing Test", responseStr);

        response = restHelloAction.handleRequest(getRequest);
        assertEquals(RestStatus.OK, response.status());
        assertEquals(BytesRestResponse.TEXT_CONTENT_TYPE, response.contentType());
        responseStr = new String(BytesReference.toBytes(response.content()), StandardCharsets.UTF_8);
        assertEquals("Hello, Passing Test!", responseStr);

        // Add an adjective
        response = restHelloAction.handleRequest(postRequest);
        assertEquals(RestStatus.OK, response.status());
        assertEquals(BytesRestResponse.TEXT_CONTENT_TYPE, response.contentType());
        responseStr = new String(BytesReference.toBytes(response.content()), StandardCharsets.UTF_8);
        assertTrue(responseStr.contains("testable"));

        response = restHelloAction.handleRequest(getRequest);
        assertEquals(RestStatus.OK, response.status());
        assertEquals(BytesRestResponse.TEXT_CONTENT_TYPE, response.contentType());
        responseStr = new String(BytesReference.toBytes(response.content()), StandardCharsets.UTF_8);
        assertEquals("Hello, testable Passing Test!", responseStr);

        // Remove the adjective
        response = restHelloAction.handleRequest(deleteRequest);
        assertEquals(RestStatus.OK, response.status());
        assertEquals(BytesRestResponse.TEXT_CONTENT_TYPE, response.contentType());
        responseStr = new String(BytesReference.toBytes(response.content()), StandardCharsets.UTF_8);
        assertTrue(responseStr.contains("testable"));

        response = restHelloAction.handleRequest(getRequest);
        assertEquals(RestStatus.OK, response.status());
        assertEquals(BytesRestResponse.TEXT_CONTENT_TYPE, response.contentType());
        responseStr = new String(BytesReference.toBytes(response.content()), StandardCharsets.UTF_8);
        assertEquals("Hello, Passing Test!", responseStr);

        // Try to remove nonexistent adjective
        response = restHelloAction.handleRequest(deleteRequest);
        assertEquals(RestStatus.NOT_MODIFIED, response.status());

        // Unparseable
        response = restHelloAction.handleRequest(badRequest);
        assertEquals(RestStatus.BAD_REQUEST, response.status());
        assertEquals(BytesRestResponse.TEXT_CONTENT_TYPE, response.contentType());
        responseStr = new String(BytesReference.toBytes(response.content()), StandardCharsets.UTF_8);
        assertTrue(responseStr.contains("Illegal hex characters in escape (%) pattern"));

        // Not registered
        response = restHelloAction.handleRequest(unhandledRequest);
        assertEquals(RestStatus.NOT_FOUND, response.status());
        assertEquals(BytesRestResponse.TEXT_CONTENT_TYPE, response.contentType());
        responseStr = new String(BytesReference.toBytes(response.content()), StandardCharsets.UTF_8);
        assertTrue(responseStr.contains("/goodbye"));
    }
}