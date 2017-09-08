package com.intuit.karate.demo.util;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 *
 * @author pthomas3
 */
public class DbUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(DbUtils.class); 
    
    private final JdbcTemplate jdbc;

    public DbUtils(Map<String, Object> config) {
        String url = (String) config.get("url");
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        String driver = (String) config.get("driverClassName");
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driver);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        jdbc = new JdbcTemplate(dataSource);
        logger.info("init jdbc template: {}", url);
    }
    
    public Object readValue(String query) {
        return jdbc.queryForObject(query, Object.class);
    }    
    
    public Map<String, Object> readRow(String query) {
        return jdbc.queryForMap(query);
    }
    
    public List<Map<String, Object>> readRows(String query) {
        return jdbc.queryForList(query);
    }     
    
}
