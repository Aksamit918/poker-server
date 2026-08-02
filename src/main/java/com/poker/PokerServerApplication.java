package com.poker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PokerServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PokerServerApplication.class, args);
	}

}
