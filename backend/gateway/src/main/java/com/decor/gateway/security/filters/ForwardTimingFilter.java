package com.decor.gateway.security.filters;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ForwardTimingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        long start = System.currentTimeMillis();

        String path = exchange.getRequest()
                .getURI()
                .getPath();

        System.out.println("REQUEST START -> " + path);

        return chain.filter(exchange)

                .doOnSuccess(aVoid -> {

                    long total = System.currentTimeMillis() - start;

                    Integer status = exchange.getResponse()
                            .getStatusCode() != null
                            ? exchange.getResponse()
                            .getStatusCode()
                            .value()
                            : 0;

                    boolean forwarded =
                            exchange.getAttribute(
                                    ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR
                            ) != null;

                    if (status >= 500) {

                        if (forwarded) {

                            System.out.println(
                                    "MS ERROR -> status="
                                            + status
                                            + " total="
                                            + total
                                            + "ms path="
                                            + path
                            );

                        } else {

                            System.out.println(
                                    "GATEWAY ERROR -> status="
                                            + status
                                            + " total="
                                            + total
                                            + "ms path="
                                            + path
                            );
                        }

                    } else {

                        System.out.println(
                                "REQUEST FINISHED -> status="
                                        + status
                                        + " total="
                                        + total
                                        + "ms path="
                                        + path
                        );
                    }
                })

                .doOnError(ex -> {

                    long total = System.currentTimeMillis() - start;

                    boolean forwarded =
                            exchange.getAttribute(
                                    ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR
                            ) != null;

                    if (forwarded) {

                        System.out.println(
                                "MS EXCEPTION -> "
                                        + ex.getMessage()
                                        + " total="
                                        + total
                                        + "ms path="
                                        + path
                        );

                    } else {

                        System.out.println(
                                "GATEWAY EXCEPTION -> "
                                        + ex.getMessage()
                                        + " total="
                                        + total
                                        + "ms path="
                                        + path
                        );
                    }
                });
    }

    @Override
    public int getOrder() {
        return -1000;
    }
}