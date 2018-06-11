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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.karate.demo.domain.FileInfo;
import com.intuit.karate.demo.domain.Message;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

/**
 *
 * @author pthomas3
 */
@Controller
@RequestMapping("/files")
public class UploadController {

    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    private static final String FILES_BASE = "target/demofiles/";
    
    private final ObjectMapper mapper = new ObjectMapper();

    public UploadController() throws Exception {
        File file = new File(FILES_BASE);
        FileUtils.forceMkdir(file);
        logger.info("created directory: {}", file);
    }

    @PostMapping
    public @ResponseBody FileInfo upload(@RequestParam("myFile") MultipartFile file,
            @RequestParam("message") String message) throws Exception {
        return getFileInfo(file, message);
    }

    @PostMapping("/multiple")
    public @ResponseBody List<FileInfo> upload(@RequestParam("myFile1") MultipartFile file1,
                          @RequestParam("myFile2") MultipartFile file2, @RequestParam("message") String message) throws Exception {
        List<FileInfo> fileInfoList = new ArrayList<>();
        fileInfoList.add(getFileInfo(file1, message));
        fileInfoList.add(getFileInfo(file2, message));
        return fileInfoList;
    }
    
    @PostMapping("/fields")
    public @ResponseBody Map<String, Object> fields(
            @RequestParam("message") String message, @RequestParam("json") String json) throws Exception {
        Map<String, Object> map = new HashMap();
        map.put("message", message);
        map.put("json", mapper.readValue(json, HashMap.class));
        return map;
    }    

    private FileInfo getFileInfo(MultipartFile file, String message) throws Exception {

        String uuid = UUID.randomUUID().toString();
        String filePath = FILES_BASE + uuid;

        FileUtils.copyToFile(file.getInputStream(), new File(filePath));
        String filename1 = file.getOriginalFilename();
        String contentType1 = file.getContentType();

        FileInfo fileInfo = new FileInfo(uuid, filename1, message, contentType1);
        String json = mapper.writeValueAsString(fileInfo);
        FileUtils.writeStringToFile(new File(filePath + "_meta.txt"), json, "utf-8");

        return fileInfo;

    }
    
    @PostMapping("/mixed")
    public @ResponseBody FileInfo uploadMixed(@RequestPart("myJson") String json, 
            @RequestPart("myFile") MultipartFile file) throws Exception {
        Message message = mapper.readValue(json, Message.class);
        String text = message.getText();
        return upload(file, text);
    }

    @GetMapping("/{id:.+}")
    public ResponseEntity<Resource> download(@PathVariable String id) throws Exception {
        String filePath = FILES_BASE + id;
        File file = new File(filePath);
        File meta = new File(filePath + "_meta.txt");
        String json = FileUtils.readFileToString(meta, "utf-8");
        FileInfo fileInfo = mapper.readValue(json, FileInfo.class);
        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileInfo.getFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, fileInfo.getContentType())
                .body(new FileSystemResource(file));
    }
    
    @PostMapping("/binary")
    public @ResponseBody FileInfo uploadBinary(@RequestParam String name, @RequestBody byte[] bytes) throws Exception {
        String uuid = UUID.randomUUID().toString();
        String filePath = FILES_BASE + uuid;
        File file = new File(filePath);
        FileUtils.writeByteArrayToFile(file, bytes);
        FileInfo fileInfo = new FileInfo(uuid, name, "hello world", "application/octet-stream");
        String json = mapper.writeValueAsString(fileInfo);
        FileUtils.writeStringToFile(new File(filePath + "_meta.txt"), json, "utf-8");
        return fileInfo;
    }    

}
