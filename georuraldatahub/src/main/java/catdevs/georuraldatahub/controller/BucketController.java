package catdevs.georuraldatahub.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import catdevs.georuraldatahub.service.BucketService;

@RestController 
@RequestMapping ("/bucket")
public class BucketController {

    private final BucketService bucketService;

    public BucketController(BucketService bucketService) {
        this.bucketService = bucketService;
    }

    @GetMapping 
    public List<String> listObjects() {
        return bucketService.listarObjetos();
    }
}