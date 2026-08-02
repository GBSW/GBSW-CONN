package kr.hs.gbsw.communication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SchoolCommunicationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolCommunicationApplication.class, args);
    }
}
