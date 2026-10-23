package com.springcommerce.backend.exception;

public class DuplicateSkuException extends RuntimeException{

    public DuplicateSkuException(String sku){
        super("Product with sku already exists: " + sku);
    }
}
