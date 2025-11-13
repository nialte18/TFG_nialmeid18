package com.nicole.tfg;

import com.mongodb.client.*;
import org.bson.Document;

public class Mongo7 implements DatabaseClient {

    private MongoClient client; // cliente java de MongoDB,maneja la conexión con el serv Mongo
    private MongoCollection<Document> collection; // la "tabla" donde se guardarán los datos

    @Override
    public void open() throws Exception {
        String uri = "mongodb://localhost:27017";
        client = MongoClients.create(uri);
        MongoDatabase database = client.getDatabase("tfg_mongo7");
        collection = database.getCollection("mensajes");
    }

    @Override
    public void write(String key, String value) throws Exception {
        Document doc = new Document("_id", key).append("mensaje", value);
        collection.insertOne(doc);
    }

    @Override
    public String read(String key) throws Exception {
         Document res = collection.find(new Document("_id", key)).first();
        return (res == null) ? null : res.getString("mensaje");
    }

    @Override
    public void close() throws Exception {
          if (client != null) client.close();
    }

}