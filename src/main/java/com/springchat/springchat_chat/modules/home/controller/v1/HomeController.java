package com.springchat.springchat_chat.modules.home.controller.v1;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.springchat.springchat_chat.common.response.ApiResponse;

@RestController
public class HomeController {


    @GetMapping("/")
    public ResponseEntity<ApiResponse<String>> home() {
        return ResponseEntity.ok(ApiResponse.<String>ok("Welcome to SpringChat API!", null));
    }


}
