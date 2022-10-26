package ch.niceideas.eskimo.proxy;

import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.ServicesDefinition;
import org.apache.http.NoHttpResponseException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles establishment and tracking of next 'hop', and
 * copies data from the current session to the next hop.
 */
public interface WebSocketProxyServer extends WebSocketHandler {

    @Override
    void handleMessage(WebSocketSession webSocketServerSession, WebSocketMessage<?> webSocketMessage) throws Exception;

    @Override
    void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception;

    void removeForwardersForService(String serviceId);
}