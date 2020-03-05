package org.kimuzzu.toby.testwebservice01;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

@Slf4j
public class CFuture {

    public static void main(String[] args) throws InterruptedException {
        //CompletableFuture<Integer> f = CompletableFuture.completedFuture(1);
        //log.info("Hello World");
        ExecutorService es = Executors.newFixedThreadPool(10);
        CompletableFuture.supplyAsync( () -> {
            System.out.println("supplyAsync");
            return 1;
        }, es)
        .thenCompose( s -> {
            System.out.println("thenCompose " + s);
            return CompletableFuture.supplyAsync( () -> s + 1, es);
        })
        .thenApplyAsync( s -> {
            System.out.println("thenApply " + s);
            return s + 1;
        }, es)
        . thenApplyAsync( s -> {
            System.out.println("thenAooly " + s);
            return s * 3;
        }, es)
        .exceptionally( e -> -10)
        .thenAcceptAsync(s -> System.out.println("thendAccept " + s), es);

        ForkJoinPool.commonPool().shutdown();
        ForkJoinPool.commonPool().awaitTermination(10, TimeUnit.SECONDS);

        es.shutdown();
        es.awaitTermination(10, TimeUnit.SECONDS);
    }
}
