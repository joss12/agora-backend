package com.springcommerce.backend.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public String hell(){
        return "Spring Commerce is running!";
    }
}
