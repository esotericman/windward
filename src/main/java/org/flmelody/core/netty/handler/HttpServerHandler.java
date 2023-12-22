/*
 * Copyright (C) 2023 Flmelody.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flmelody.core.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.util.CharsetUtil;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.flmelody.core.ExceptionHandler;
import org.flmelody.core.Filter;
import org.flmelody.core.FunctionMetaInfo;
import org.flmelody.core.HttpStatus;
import org.flmelody.core.Windward;
import org.flmelody.core.WindwardRequest;
import org.flmelody.core.WindwardResponse;
import org.flmelody.core.context.EmptyWindwardContext;
import org.flmelody.core.context.EnhancedWindwardContext;
import org.flmelody.core.context.SimpleWindwardContext;
import org.flmelody.core.context.WindwardContext;
import org.flmelody.core.exception.HandlerNotFoundException;
import org.flmelody.core.netty.NettyResponseWriter;
import org.flmelody.core.ws.WebSocketEvent;
import org.flmelody.core.ws.WebSocketFireEvent;
import org.flmelody.core.ws.WebSocketWindwardContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author esotericman
 */
public class HttpServerHandler extends SimpleChannelInboundHandler<Object> {
  private static final Logger logger = LoggerFactory.getLogger(HttpServerHandler.class);
  private WindwardContext cachedWindwardContext;
  private FunctionMetaInfo<?> cachedFunctionMetaInfo;

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
    if (msg instanceof FullHttpRequest) {
      FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;
      String uri = fullHttpRequest.uri().split("\\?")[0];
      FunctionMetaInfo<?> functionMetaInfo =
          Windward.findRouter(uri, fullHttpRequest.method().name());
      WindwardContext windwardContext = cachedWindwardContext;
      if (windwardContext == null) {
        windwardContext = initContext(ctx, fullHttpRequest, functionMetaInfo);
        if (windwardContext.isCacheable()) {
          cachedWindwardContext = windwardContext;
          cachedFunctionMetaInfo = functionMetaInfo;
        }
      }
      if (isWebsocketUpgrade(fullHttpRequest.headers()) && cachedWindwardContext != null) {
        ctx.pipeline()
            .addBefore(
                ctx.name(),
                WebSocketServerCompressionHandler.class.getSimpleName(),
                new WebSocketServerCompressionHandler());
        ctx.pipeline()
            .addBefore(ctx.name(), WebSocketHandler.class.getSimpleName(), new WebSocketHandler());
        ctx.pipeline()
            .addAfter(
                ctx.name(),
                WebSocketServerProtocolHandler.class.getSimpleName(),
                new WebSocketServerProtocolHandler(fullHttpRequest.uri(), null, true));
        ctx.pipeline().addLast(new SocketTailHandler());
        ctx.fireChannelRead(fullHttpRequest.retain());
        return;
      }
      if (cachedWindwardContext != null
          && cachedWindwardContext instanceof WebSocketWindwardContext) {
        WebSocketWindwardContext websocketWindwardContext =
            (WebSocketWindwardContext) cachedWindwardContext;
        websocketWindwardContext.setHttpResponse(true);
      }
      handle(functionMetaInfo, windwardContext);
    }
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    // websocket context should always be cached
    if (evt instanceof WebSocketFireEvent
        && cachedWindwardContext instanceof WebSocketWindwardContext) {
      WebSocketFireEvent webSocketFireEvent = (WebSocketFireEvent) evt;
      WebSocketWindwardContext websocketWindwardContext =
          (WebSocketWindwardContext) cachedWindwardContext;
      websocketWindwardContext.setWebSocketEvent(webSocketFireEvent.getEvent());
      websocketWindwardContext.setWebSocketData(webSocketFireEvent.getData());
      handle(cachedFunctionMetaInfo, websocketWindwardContext);
    } else {
      super.userEventTriggered(ctx, evt);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ctx.close()
        .addListener(
            (ChannelFutureListener)
                future -> {
                  if (cachedWindwardContext instanceof WebSocketWindwardContext) {
                    WebSocketWindwardContext webSocketWindwardContext =
                        (WebSocketWindwardContext) cachedWindwardContext;
                    webSocketWindwardContext.setWebSocketEvent(WebSocketEvent.ON_CLOSE);
                    webSocketWindwardContext.setWebSocketData(null);
                    handle(cachedFunctionMetaInfo, webSocketWindwardContext);
                  }
                });
  }

  private Map<String, List<String>> prepareHeaders(HttpHeaders httpHeaders) {
    Iterator<Map.Entry<String, String>> entryIterator = httpHeaders.iteratorAsString();
    Map<String, List<String>> headers = new HashMap<>();
    while (entryIterator.hasNext()) {
      Map.Entry<String, String> next = entryIterator.next();
      if (headers.containsKey(next.getKey())) {
        headers.get(next.getKey()).add(next.getValue());
      } else {
        ArrayList<String> values = new ArrayList<>();
        values.add(next.getValue());
        headers.put(next.getKey(), values);
      }
    }
    return headers;
  }

  private <I> WindwardContext initContext(
      ChannelHandlerContext ctx,
      FullHttpRequest fullHttpRequest,
      FunctionMetaInfo<I> functionMetaInfo) {
    String uri = fullHttpRequest.uri();
    ByteBuf content = fullHttpRequest.content();
    boolean keepAlive = HttpUtil.isKeepAlive(fullHttpRequest);
    QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
    Map<String, List<String>> params = queryStringDecoder.parameters();

    uri = uri.split("\\?")[0];
    WindwardRequest.WindwardRequestBuilder windwardRequestBuilder =
        WindwardRequest.newBuild()
            .headers(prepareHeaders(fullHttpRequest.headers()))
            .method(fullHttpRequest.method().name())
            .uri(uri)
            .keepAlive(keepAlive)
            .querystring(params);
    if (content.isReadable()) {
      String string = content.toString(CharsetUtil.UTF_8);
      windwardRequestBuilder.requestBody(string);
    }
    WindwardResponse.WindwardResponseBuild windwardResponseBuild =
        WindwardResponse.newBuilder().responseWriter(new NettyResponseWriter(ctx, keepAlive));
    if (functionMetaInfo == null) {
      return new SimpleWindwardContext(
          windwardRequestBuilder.build(), windwardResponseBuild.build());
    } else {
      try {
        Class<? extends WindwardContext> clazz = functionMetaInfo.getClazz();
        if (clazz.isAssignableFrom(SimpleWindwardContext.class)) {
          return new SimpleWindwardContext(
              windwardRequestBuilder.pathVariables(functionMetaInfo.getPathVariables()).build(),
              windwardResponseBuild.build());
        } else if (clazz.isAssignableFrom(EnhancedWindwardContext.class)) {
          return new EnhancedWindwardContext(
              windwardRequestBuilder.pathVariables(functionMetaInfo.getPathVariables()).build(),
              windwardResponseBuild.build());
        } else if (clazz.isAssignableFrom(WebSocketWindwardContext.class)) {
          return new WebSocketWindwardContext(
              windwardRequestBuilder.pathVariables(functionMetaInfo.getPathVariables()).build(),
              windwardResponseBuild.build());
        }
      } catch (Exception e) {
        logger.atError().log("Failed to construct context");
      }
    }
    return new EmptyWindwardContext();
  }

  private void handle(FunctionMetaInfo<?> functionMetaInfo, WindwardContext windwardContext) {
    if (windwardContext.isClosed()) {
      return;
    }
    for (Filter filter : Windward.filters()) {
      try {
        filter.filter(windwardContext);
      } catch (Exception e) {
        logger.atError().log("Handler error", e);
        windwardContext.writeString(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase());
        windwardContext.close();
        return;
      }
    }
    execute(functionMetaInfo, windwardContext);
  }

  private void execute(FunctionMetaInfo<?> functionMetaInfo, WindwardContext windwardContext) {
    try {
      if (functionMetaInfo == null) {
        throw new HandlerNotFoundException("No handler found!");
      }
      Object function = functionMetaInfo.getFunction();
      if (function instanceof Consumer) {
        @SuppressWarnings("unchecked")
        final Consumer<WindwardContext> contextConsumer = (Consumer<WindwardContext>) function;
        contextConsumer.accept(windwardContext);
      } else if (function instanceof Function) {
        @SuppressWarnings("unchecked")
        final Function<WindwardContext, ?> contextConsumer =
            (Function<WindwardContext, ?>) function;
        contextConsumer.apply(windwardContext);
      } else if (function instanceof Supplier) {
        Supplier<?> supplier = (Supplier<?>) function;
        Object object = supplier.get();
        if (object instanceof Serializable && !(object instanceof String)) {
          windwardContext.writeJson(object);
        } else {
          windwardContext.writeString(object.toString());
        }
      } else {
        throw new HandlerNotFoundException("No handler found!");
      }
    } catch (Exception e) {
      if (!handleException(windwardContext, e)) {
        logger.atError().log("Error occurred", e);
        windwardContext.writeString(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase());
      }
    }
  }

  private boolean handleException(WindwardContext windwardContext, Exception e) {
    List<ExceptionHandler> exceptionHandlers = Windward.exceptionHandlers();
    boolean alreadyDone = false;
    for (ExceptionHandler exceptionHandler : exceptionHandlers) {
      try {
        if (exceptionHandler.supported(e)) {
          exceptionHandler.handle(windwardContext);
          alreadyDone = true;
          break;
        }
      } catch (Exception exception) {
        logger.atError().log("Handle exception error", e);
      }
    }
    return alreadyDone;
  }

  private static boolean isWebsocketUpgrade(HttpHeaders headers) {
    return headers.contains(HttpHeaderNames.UPGRADE)
        && headers.containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)
        && headers.contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true);
  }
}
