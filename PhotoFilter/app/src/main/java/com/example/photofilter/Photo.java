package com.example.photofilter;

public class Photo {
    long id;
    String uri;
    String name;
    String date;
    int size;
    long bucketId;
    String bucketName;

    public Photo(long id, String uri, String name, String date, int size, long bucketId, String bucketName) {
        this.id = id;
        this.uri = uri;
        this.name = name;
        this.date = date;
        this.size = size;
        this.bucketId = bucketId;
        this.bucketName = bucketName;
    }

    public long getId() {
        return id;
    }

    public String getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public String getDate() {
        return date;
    }

    public int getSize() {
        return size;
    }

    public long getBucketId() {
        return bucketId;
    }

    public String getBucketName() {
        return bucketName;
    }
}
