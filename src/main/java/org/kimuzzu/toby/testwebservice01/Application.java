package org.kimuzzu.toby.testwebservice01;

import io.netty.channel.nio.NioEventLoopGroup;
import jdk.internal.jline.internal.Log;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.compiler.lir.LIRInstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.Netty4ClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("deprecation")
@Slf4j
@SpringBootApplication
@EnableAsync
public class Application {

    @RestController
    public static class MyController {

        @Autowired MyService myService;

        @GetMapping("/callable")
        public Callable<String> callable() throws InterruptedException {
            //Log.info("callable");
            return () ->  {
                //Log.info("async");
                Thread.sleep(2000);
                return "hello";
            };
        }

        Queue<DeferredResult<String>> results = new ConcurrentLinkedQueue<>();
        @GetMapping("/dr")
        public DeferredResult<String> deferred() throws InterruptedException {
            //Log.info("deferred");
            DeferredResult<String> dr = new DeferredResult<>(600000L);
            results.add(dr);
            return dr;
        }

        @GetMapping("/dr/count")
        public String drcount() {
            return String.valueOf(results.size());
        }

        @GetMapping("/dr/event")
        public String drevent(String name) {
            for (DeferredResult<String> dr : results) {
                dr.setResult("Hello " + name);
                results.remove(dr);
            }
            return "OK";
        }

        @GetMapping("/emitter")
        public ResponseBodyEmitter emitter() throws InterruptedException {
            //Log.info("emitter");
            ResponseBodyEmitter emitter = new ResponseBodyEmitter();
            Executors.newSingleThreadExecutor().submit(() -> {
                for (int i = 0; i <= 50; i++) {
                    try {
                        emitter.send("<p>Stream " + i + "</p>");
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            return emitter;
        }

        RestTemplate rt = new RestTemplate();
        AsyncRestTemplate art = new AsyncRestTemplate();
        AsyncRestTemplate rtex = new AsyncRestTemplate(new Netty4ClientHttpRequestFactory(
                new NioEventLoopGroup(1)
        ));

        //RestTemplate을 이용한 싱크 처리
        @GetMapping("/rest")
        public String rest(int idx) {
            String url = "http://localhost:8081/service?req={req}";
            String ret = rt.getForObject(url, String.class, "hello" + idx);
            return "rest " + ret + idx;
        }

        //AsyncRestTemplate을 이용한 어싱크 처
        @GetMapping("/restasync")
        public ListenableFuture<ResponseEntity<String>> restasync(int idx) {
            String url = "http://localhost:8081/service?req={req}";
            ListenableFuture<ResponseEntity<String>> ret = art.getForEntity(url, String.class, "hello" + idx);
            return ret;
        }

        //netty를 이용한 어싱크 처리
        @GetMapping("/restasyncEX")
        public ListenableFuture<ResponseEntity<String>> restasyncEX(int idx) {
            String url = "http://localhost:8081/service?req={req}";
            ListenableFuture<ResponseEntity<String>> ret = rtex.getForEntity(url, String.class, "hello" + idx);
            return ret;
        }

        //netty + DeferredResult를 이용한 콜백 방식 처리
        @GetMapping("/restasyncEX2")
        public DeferredResult<String> restasyncEX2(int idx) {
            DeferredResult<String> dr = new DeferredResult();

            String url = "http://localhost:8081/service?req={req}";
            ListenableFuture<ResponseEntity<String>> ret = rtex.getForEntity(url, String.class, "hello" + idx);

            ret.addCallback(s-> {
                dr.setResult(s.getBody() + "/work");
            }, e -> {
                dr.setErrorResult(e.getMessage());
            });

            return dr;
        }

        static final String url = "http://localhost:8081/service?req={req}";
        static final String url2 = "http://localhost:8081/service2?req={req}";
        //일종의 트랜잭션 처리
        @GetMapping("/restasyncEX3")
        public DeferredResult<String> restasyncEX3(int idx) {
            DeferredResult<String> dr = new DeferredResult();

            ListenableFuture<ResponseEntity<String>> ret = rtex.getForEntity(url, String.class, "hello" + idx);

            ret.addCallback(s-> {
                ListenableFuture<ResponseEntity<String>> ret2 = rtex.getForEntity(url2, String.class, s.getBody());
                ret2.addCallback(
                        s2 -> {
                            //dr.setResult(s2.getBody());
                            ListenableFuture<String> lf  = myService.work(s2.getBody());
                            lf.addCallback(
                                    s3 -> {
                                        dr.setResult(s3);
                                    }, e -> {
                                        dr.setErrorResult(e.getMessage());
                                    });
                        }, e -> {
                            dr.setErrorResult(e.getMessage());
                        });

            }, e -> {
                dr.setErrorResult(e.getMessage());
            });

            return dr;
        }

        //clean code(콜백을 이용한 명령형 스타일의 코드를 함수형 스타일의 코드로 정리)
        @GetMapping("/restasyncEX4")
        public DeferredResult<String> restasyncEX4(int idx) {
            DeferredResult<String> dr = new DeferredResult();

            Completion
                    .from(rtex.getForEntity(url, String.class, "hello" + idx))
                    .andApply(s -> rtex.getForEntity(url2, String.class, s.getBody()))
                    .andApply(s->myService.work(s.getBody()))
                    .andError(e -> dr.setErrorResult(e.toString()))
                    .andAccept( s -> dr.setResult(s));

            return dr;
        }
    }

    public static class AcceptCompletion<S> extends Completion<S, Void> {
        public Consumer<S> con;

        public AcceptCompletion(Consumer<S> con) {
            this.con = con;
        }

        @Override
        void run(S value) {
            con.accept(value);
        }
    }

    public static  class ApplyCompletion<S, T> extends  Completion<S, T> {
        public Function<S, ListenableFuture<T>> func;

        public ApplyCompletion(Function<S, ListenableFuture<T>> func) {
            this.func = func;
        }

        @Override
        void run(S value) {
            ListenableFuture<T> lf = func.apply(value);
            lf.addCallback(s -> complete(s), e -> error(e));
        }
    }

    public static class ErrorCompletion<T> extends Completion<T, T> {
        public Consumer<Throwable> econ;

        public ErrorCompletion(Consumer<Throwable> econ) {
            this.econ = econ;
        }

        @Override
        void run(T value) {
            if(next != null) next.run(value);
        }

        @Override
        void error(Throwable e) {
            econ.accept(e);
        }
    }

    public static class Completion<S, T> {
        Completion next;

        public void andAccept(Consumer<T> con) {
            Completion<T, Void> c = new AcceptCompletion<>(con);
            this.next = c;
        }

        public Completion<T, T> andError(Consumer<Throwable> econ) {
            Completion<T, T> c = new ErrorCompletion<>(econ);
            this.next = c;
            return c;
        }

        public <V>  Completion<T, V> andApply(Function<T, ListenableFuture<V>> func) {

            Completion<T, V> c = new ApplyCompletion<>(func);
            this.next = c;
            return c;
        }

        public static  <S, T> Completion<S, T> from(ListenableFuture<T> lf) {
            Completion<S, T> c = new Completion<>();
            lf.addCallback(s -> {
                c.complete(s);
            }, e -> {
                c.error(e);
            });
            return c;
        }

        void error(Throwable e) {
            if (next != null) next.error(e);
        }

        void complete(T s) {
            if (next != null) next.run(s);  //다음 컴플리션에 이전 컴플리션 결과값을 전달
        }

        void run(S value) {
        }
    }

    //비지니스 로직을 담고 있는 Bean
    @Service
    public static class MyService {
        @Async
        public ListenableFuture<String> work(String req) {
            return new AsyncResult<>(req + "/MyService");
        }
    }

    //Beand은 개발자가 작성한 메서드를 통해 반환되는 객체를 Bean으로 만드는
    @Bean
    ThreadPoolTaskExecutor myThreadPool() {
        ThreadPoolTaskExecutor te = new ThreadPoolTaskExecutor();
        te.setCorePoolSize(1);
        te.setMaxPoolSize(1);
        te.initialize();
        return te;
    }

    public static void main(String[] args) {
        System.setProperty("server.tomcat.max-threads", "1");
        SpringApplication.run(Application.class, args);
    }
}
