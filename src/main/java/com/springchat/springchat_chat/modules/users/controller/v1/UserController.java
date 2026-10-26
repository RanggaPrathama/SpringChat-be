package com.springchat.springchat_chat.modules.users.controller.v1;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.springchat.springchat_chat.common.response.ApiResponse;
import com.springchat.springchat_chat.common.response.PaginationMeta;
import com.springchat.springchat_chat.modules.users.dto.ProfileUpdateRequest;
import com.springchat.springchat_chat.modules.users.dto.UserResponse;
import com.springchat.springchat_chat.modules.users.service.UserService;
import com.springchat.springchat_chat.utils.Sanitizer;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private static final int MAX_PAGE_SIZE = 100;

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable boundedPageable = PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), pageable.getSort());
        Page<UserResponse> result = userService.searchUsers(Sanitizer.trim(search),
                UUID.fromString(jwt.getSubject()), boundedPageable);
        return ResponseEntity.ok(ApiResponse.paginated("Users retrieved successfully",
                result.getContent(), PaginationMeta.from(result)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable UUID id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.ok("User retrieved successfully", user));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {
        UserResponse profile = userService.getOwnProfile(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok(ApiResponse.ok("Profile retrieved successfully", profile));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileUpdateRequest request) {
        UserResponse profile = userService.updateOwnProfile(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.ok(ApiResponse.ok("Profile updated successfully", profile));
    }


}
