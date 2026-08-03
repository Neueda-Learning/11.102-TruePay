package org.example.truepay.api;

import jakarta.validation.Valid;
import org.example.truepay.model.UserProfile;
import org.example.truepay.service.ProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping
    public ProfileResponse createProfile(@Valid @RequestBody ProfileRequest request) {
        UserProfile created = profileService.createProfile(request);
        return new ProfileResponse(created.getId(), created.getFullName(), created.getEmail(), created.getMobile());
    }

    @GetMapping("/{userId}")
    public ProfileResponse getProfile(@PathVariable Long userId) {
        UserProfile user = profileService.getUserOrThrow(userId);
        return new ProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getMobile());
    }
}

