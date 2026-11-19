package com.linkscale.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {

    @GetMapping({"/", "/login", "/register", "/dashboard"})
    public String frontend() {
        return "forward:/index.html";
    }
}
