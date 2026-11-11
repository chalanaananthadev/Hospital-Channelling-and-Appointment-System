package com.medichannel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class MediChannelApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediChannelApplication.class, args);
    }
}
