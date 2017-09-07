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

import com.intuit.karate.demo.domain.Dog;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author pthomas3
 */
@RestController
@RequestMapping("/dogs")
public class DogsController {

    @Autowired(required = true)
    private JdbcTemplate jdbc;

    private final AtomicInteger counter = new AtomicInteger();
    
    private static final RowMapper<Dog> ROW_MAPPER = (rs, rowNum) -> new Dog(rs.getInt("ID"), rs.getString("NAME"));

    @PostMapping
    public Dog create(@RequestBody Dog dog) {
        int id = counter.incrementAndGet();
        dog.setId(id);
        jdbc.update("INSERT INTO DOGS(ID, NAME) values(?, ?)", dog.getId(), dog.getName());
        return dog;
    }

    @GetMapping
    public Collection<Dog> list() {
        return jdbc.query("SELECT * FROM DOGS", ROW_MAPPER);
    }
    
    @GetMapping("/{id:.+}")
    public Dog get(@PathVariable int id) {
        return jdbc.queryForObject("SELECT * FROM DOGS D WHERE D.ID = ?", ROW_MAPPER, id);
    }

}
