package com.orderautomation.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

@Configuration
public class DataSourceConfiguration {
  @Bean
  SQLiteDataSource dataSource(
      @Value("${order-automation.data-dir:${user.home}/OrderAutomation/data}") String dataDir)
      throws IOException {
    Path databaseDirectory = Path.of(dataDir).toAbsolutePath().normalize();
    Files.createDirectories(databaseDirectory);

    SQLiteConfig config = new SQLiteConfig();
    config.enforceForeignKeys(true);
    SQLiteDataSource dataSource = new SQLiteDataSource(config);
    dataSource.setUrl("jdbc:sqlite:" + databaseDirectory.resolve("app.db"));
    return dataSource;
  }
}
