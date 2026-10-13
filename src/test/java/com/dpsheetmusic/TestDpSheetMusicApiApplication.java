package com.dpsheetmusic;

import org.springframework.boot.SpringApplication;

public class TestDpSheetMusicApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(DpSheetMusicApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
