package org.kimuzzu.toby.testwebservice01;

//request 100개를 쏘는 코

import org.springframework.util.StopWatch;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadTest {
    static AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException, BrokenBarrierException {
        ExecutorService es = Executors.newFixedThreadPool(100);

        RestTemplate rt = new RestTemplate();
        final String url = "http://localhost:8080/restasyncEX5?idx={idx}";

        CyclicBarrier barrier = new CyclicBarrier(101);

        for (int i = 0; i < 100; i++) {
            es.submit( () -> {
                int idx = counter.addAndGet(1);

                barrier.await();    //람다식 내에서 exception을 던지는 함수를 쓰게 되는 경우에는 람다식 자체를 runnable이 아닌 callable을 사용하

                System.out.println("Thread " + idx);

                StopWatch sw = new StopWatch();
                sw.start();
                String res = rt.getForObject(url, String.class, idx);

                sw.stop();
                System.out.println("Elapsed: " + idx + "->" + sw.getTotalTimeSeconds() + "/" + res);
                return null;
            });

        }

        barrier.await();
        StopWatch main = new StopWatch();
        main.start();

        es.shutdown();
        es.awaitTermination(100, TimeUnit.SECONDS);

        main.stop();

        System.out.println("Total: " + main.getTotalTimeSeconds());
    }
}
