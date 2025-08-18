package org.example.deboardv2.user.controller;

import lombok.RequiredArgsConstructor;
import org.example.deboardv2.user.dto.UpdateRequest;
import org.example.deboardv2.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<?> read(@PathVariable Long userId){

        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<?> update(@RequestBody UpdateRequest dto,@PathVariable Long userId){
        userService.update(userId, dto);
        return ResponseEntity.ok("ok");
    }
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> delete(@PathVariable Long userId){
        userService.delete(userId);
        return ResponseEntity.ok("ok");
    }

}
