package com.company.acts.parts;

import com.company.acts.common.web.CommonWebConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(CommonWebConfiguration.class)
public class PartsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PartsApplication.class, args);
    }
}
