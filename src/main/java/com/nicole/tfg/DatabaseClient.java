package com.nicole.tfg;

public interface DatabaseClient {
    void open(String uri) throws Exception;
    void write(String key, String value) throws Exception;
    String read(String key) throws Exception; 
    void close() throws Exception;
}
