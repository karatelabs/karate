/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.demo.controller;

import com.intuit.karate.demo.domain.Binary;
import com.intuit.karate.demo.domain.Message;
import com.intuit.karate.demo.domain.SignIn;
import java.util.Arrays;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author pthomas3
 */
@RestController
@RequestMapping("/echo")
public class EchoController {
        
    @PostMapping
    public String echo(@RequestBody String request) {
        return request;
    }  
    
    @PostMapping
    @RequestMapping("/message")
    public String echo(@ModelAttribute Message message) {
        return message.getText();
    }     
    
    @GetMapping
    public Map<String, String[]> search(HttpServletRequest request) {
        return request.getParameterMap();
    }
    
    @PostMapping("/jwt")
    public ResponseEntity jwtPost(@RequestBody SignIn signin) {
        if ("john".equals(signin.getUsername()) && "secret".equals(signin.getPassword())) {
            return ResponseEntity.ok("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoidGVzdEBleGFtcGxlLmNvbSIsInJvbGUiOiJlZGl0b3IiLCJleHAiOjk5OTk5OTk5OSwiaXNzIjoia2xpbmdtYW4ifQ._D2tcNJN6mawerckbNotuINRm_8bRaXVi18hgsuOk9Y");
        } else {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/jwt/resource")
    public String jwtResource() {
        return "success";
    }  
    
    @PostMapping("/binary")
    public Binary create(@RequestBody Binary bin) {
        if (!bin.getMessage().equals("hello")) {
            throw new RuntimeException("expected message 'hello' but was: " + bin.getMessage());
        }
        if (!Arrays.equals("hello".getBytes(), bin.getData())) {
            throw new RuntimeException("expected data 'hello' but was: " + new String(bin.getData()));
        }        
        bin = new Binary();
        bin.setMessage("world");
        bin.setData("world".getBytes());
        return bin;
    }     
    
}
