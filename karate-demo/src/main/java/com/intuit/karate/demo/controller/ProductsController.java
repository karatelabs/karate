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

import com.intuit.karate.demo.domain.Product;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author pthomas3
 */
@RestController
@RequestMapping("/products")
public class ProductsController {
    
    private final AtomicInteger counter = new AtomicInteger();
    private final Map<Integer, Product> productMap = new ConcurrentHashMap<>();
    
    @PostMapping
    public Product create(@RequestBody Product product) {
        int id = counter.incrementAndGet();
        product.setId(id);
        productMap.put(id, product);
        return product;
    }
    
    @GetMapping
    public Collection<Product> list() {
        return productMap.values();
    }
    
    @GetMapping("/{id:.+}")
    public Product get(@PathVariable int id) {
        return productMap.get(id);
    }
    
    @PutMapping("/{id:.+}")
    public Product put(@PathVariable int id, @RequestBody Product product) {
        productMap.put(id, product);
        return product;        
    }    
    
    @DeleteMapping("/{id:.+}")
    public void delete(@PathVariable int id) {
        Product product = productMap.remove(id);
        if (product == null) {
            throw new RuntimeException("product not found, id: " + id);
        }
    }

    @DeleteMapping
    public void deleteWithBody(@RequestBody Product product) {
        float id = product.getId();
        delete((int) id);
    }    
    
}
