/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class ConnectClient extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(ConnectClient.class);
    private final String host;
    private final int port;
    private final Set<String> senderAddresses;
    private final Set<String> receiverAddresses;
    private final AtomicLong reconnectDelay = new AtomicLong(1);
    private final Map<String, AtomicLong> reattachDelay = new HashMap<>();

    public ConnectClient(String host, int port, Set<String> senderAddresses, Set<String> receiverAddresses) {
        this.host = host;
        this.port = port;
        this.senderAddresses = senderAddresses;
        this.receiverAddresses = receiverAddresses;
    }

    @Override
    public void start(Future<Void> startPromise) {
        ProtonClient client = ProtonClient.create(vertx);
        connectAndAttach(client, startPromise);
    }

    private static final long maxRetryDelay = 5000;

    private void connectAndAttach(ProtonClient client, Future<Void> startPromise) {
        ProtonClientOptions protonClientOptions = new ProtonClientOptions()
                .setSsl(true)
                .setTrustAll(true)
                .setHostnameVerificationAlgorithm("");

        Runnable reconnectFn = () -> {
            // System.out.println("Reconnecting in " + reconnectDelay.get() + " ms");
            Context context = vertx.getOrCreateContext();
            vertx.setTimer(reconnectDelay.get(), id -> {
                context.runOnContext(c -> {
                    reconnectDelay.set(Math.min(maxRetryDelay, reconnectDelay.get() * 2));
                    connectAndAttach(client, null);
                });
            });
        };

        // System.out.println("Connecting to " + host + ":" + port);
        client.connect(protonClientOptions, host, port, connectResult -> {
            if (connectResult.succeeded()) {
                ProtonConnection connection = connectResult.result();
                connection.openHandler(openResult -> {
                    if (openResult.succeeded()) {
                        if (startPromise != null) {
                            startPromise.complete();
                        }

                        for (String address : senderAddresses) {
                            attachLink(connection, address, LinkType.sender);
                        }

                        for (String address : receiverAddresses) {
                            attachLink(connection, address, LinkType.receiver);
                        }
                    } else {
                        if (startPromise != null) {
                            startPromise.fail(connectResult.cause());
                        } else {
                            connection.disconnect();
                        }
                    }
                });

                connection.disconnectHandler(conn -> {
                    conn.close();
                    reconnectFn.run();
                });

                connection.closeHandler(closeResult -> {
                    long now = System.nanoTime();
                    reconnectFn.run();
                });

                connection.open();

            } else {
                if (startPromise != null) {
                    startPromise.fail(connectResult.cause());
                } else {
                    reconnectFn.run();
                }
            }
        });
    }

    private static double toSeconds(long nanos) {
        return (double)nanos / 1_000_000_000.0;
    }

    private void attachLink(ProtonConnection connection, String address, LinkType linkType) {
        Runnable reattachFn = () -> {
            Context context = vertx.getOrCreateContext();
            vertx.setTimer(reattachDelay.get(address).get(), handler -> {
                context.runOnContext(c -> {
                    reattachDelay.get(address).set(Math.min(maxRetryDelay, reattachDelay.get(address).get() * 2));
                    attachLink(connection, address, linkType);
                });
            });
        };

        if (linkType.equals(LinkType.receiver)) {
            ProtonReceiver receiver = connection.createReceiver(address);
            receiver.openHandler(receiverAttachResult -> {
                if (!receiverAttachResult.succeeded()) {
                    reattachFn.run();
                }
            });
            receiver.detachHandler(detachResult -> {
                reattachFn.run();
            });
            receiver.closeHandler(closeResult -> {
                reattachFn.run();
            });
            receiver.handler((protonDelivery, message) -> {
                //System.out.println("Received message from " + address);
                // numReceived.labels(addressType.name()).inc();
            });
            receiver.open();
        } else {
            ProtonSender sender = connection.createSender(address);
            sender.openHandler(senderAttachResult -> {
                if (senderAttachResult.succeeded()) {
                    // sendMessage(address, sender);
                } else {
                    reattachFn.run();
                }
            });
            sender.detachHandler(detachResult -> {
                reattachFn.run();
            });
            sender.closeHandler(closeResult -> {
                reattachFn.run();
            });
            sender.open();
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        if (args.length < 5) {
            System.err.println("Usage: java -jar connect-client.jar <messaging host> <messaging port> <num addresses> <address prefix> <num connections>");
            System.exit(1);
        }
        String messagingHost = args[0];
        int messagingPort = Integer.parseInt(args[1]);
        int numAddresses = Integer.parseInt(args[2]);
        String addressPrefix = args[3];
        int numConnections = Integer.parseInt(args[4]);

        Vertx vertx = Vertx.vertx();

        int addressesPerConnection = numAddresses / numConnections;
        for (int i = 0; i < numConnections; i++) {
            Set<String> senders = new HashSet<>();
            Set<String> receivers = new HashSet<>();
            for (int j = 0; j < addressesPerConnection; j++) {
                if (j % 2 == 0) {
                    senders.add(String.format("%s%d", addressPrefix, i * j));
                } else {
                    receivers.add(String.format("%s%d", addressPrefix, i * j));
                }
            }
            vertx.deployVerticle(new ConnectClient(messagingHost, messagingPort, senders, receivers), result -> {
                if (result.succeeded()) {
                    log.info("Started client for addresses {} and {}", senders, receivers);
                } else {
                    log.info("Failed starting client for addresses {} and {}", senders, receivers);
                }
            });
        }
        while (true) {
            Thread.sleep(600_000);
        }
    }
}
