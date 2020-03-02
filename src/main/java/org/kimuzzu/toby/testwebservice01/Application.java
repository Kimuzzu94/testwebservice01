package org.kimuzzu.toby.testwebservice01;

import jdk.internal.jline.internal.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;

@Slf4j
@SpringBootApplication
@EnableAsync
public class Application {

    @RestController
    public static class MyController {
        @GetMapping("/async")
        public Callable<String> async() throws InterruptedException {
            //Log.info("callable");
            return () ->  {
                //Log.info("async");
                Thread.sleep(2000);
                return "hello";
            };

        }

    }


    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
