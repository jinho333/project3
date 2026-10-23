package com.commit.project3.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    // localhost:8080 접속 시 메인 페이지(templates/pages/main.html)를 보여줌
    @GetMapping("/")
    public String main() {
        return "pages/main";
    }
}
