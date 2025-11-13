package com.nicole.tfg;

import com.mongodb.client.*;
import org.bson.Document;


public class Mongo8  implements DatabaseClient{
  private MongoClient client;
    private MongoCollection<Document> collection;

    @Override
    public void open() throws Exception {
        client = MongoClients.create("mongodb://localhost:27018");
        MongoDatabase db = client.getDatabase("tfg_mongo8");
        collection = db.getCollection("mensajes");
    }

    @Override
    public void write(String key, String value) throws Exception {
        Document doc = new Document("_id", key)
                .append("mensaje", value);

        collection.replaceOne(
                new Document("_id", key),
                doc,
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
        );
    }

    @Override
    public String read(String key) throws Exception {
        Document doc = collection.find(new Document("_id", key)).first();
        return (doc == null) ? null : doc.getString("mensaje");
    }

    @Override
    public void close() throws Exception {
        if (client != null) client.close();
    }
    
    
}

