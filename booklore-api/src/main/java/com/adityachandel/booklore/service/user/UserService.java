package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.request.ChangePasswordRequest;
import com.adityachandel.booklore.model.dto.request.ChangeUserPasswordRequest;
import com.adityachandel.booklore.model.dto.request.UpdateUserSettingRequest;
import com.adityachandel.booklore.model.dto.request.UserUpdateRequest;
import com.adityachandel.booklore.model.dto.settings.UserSettingKey;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.UserSettingEntity;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;
    private final AuthenticationService authenticationService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final BookLoreUserTransformer bookLoreUserTransformer;

    public List<BookLoreUser> getBookLoreUsers() {
        return userRepository.findAll()
                .stream()
                .map(bookLoreUserTransformer::toDTO)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "userAuthCache", key = "#id")
    public BookLoreUser updateUser(Long id, UserUpdateRequest updateRequest) {
        BookLoreUserEntity user = userRepository.findById(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));
        user.setName(updateRequest.getName());
        user.setEmail(updateRequest.getEmail());

        if (updateRequest.getPermissions() != null && getMyself().getPermissions().isAdmin()) {
            user.getPermissions().setPermissionAdmin(updateRequest.getPermissions().isAdmin());
            user.getPermissions().setPermissionUpload(updateRequest.getPermissions().isCanUpload());
            user.getPermissions().setPermissionDownload(updateRequest.getPermissions().isCanDownload());
            user.getPermissions().setPermissionEditMetadata(updateRequest.getPermissions().isCanEditMetadata());
            user.getPermissions().setPermissionManipulateLibrary(updateRequest.getPermissions().isCanManipulateLibrary());
            user.getPermissions().setPermissionEmailBook(updateRequest.getPermissions().isCanEmailBook());
            user.getPermissions().setPermissionDeleteBook(updateRequest.getPermissions().isCanDeleteBook());
            user.getPermissions().setPermissionAccessOpds(updateRequest.getPermissions().isCanAccessOpds());
            user.getPermissions().setPermissionSyncKoreader(updateRequest.getPermissions().isCanSyncKoReader());
            user.getPermissions().setPermissionSyncKobo(updateRequest.getPermissions().isCanSyncKobo());
        }

        if (updateRequest.getAssignedLibraries() != null && getMyself().getPermissions().isAdmin()) {
            List<Long> libraryIds = updateRequest.getAssignedLibraries();
            List<LibraryEntity> updatedLibraries = libraryRepository.findAllById(libraryIds);
            user.setLibraries(updatedLibraries);
        }

        userRepository.save(user);
        return bookLoreUserTransformer.toDTO(user);
    }

    @CacheEvict(value = "userAuthCache", key = "#id")
    public void deleteUser(Long id) {
        BookLoreUserEntity userToDelete = userRepository.findById(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));
        BookLoreUser currentUser = authenticationService.getAuthenticatedUser();
        boolean isAdmin = currentUser.getPermissions().isAdmin();
        if (!isAdmin) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("You do not have permission to delete this User");
        }
        if (currentUser.getId().equals(userToDelete.getId())) {
            throw ApiError.SELF_DELETION_NOT_ALLOWED.createException();
        }
        userRepository.delete(userToDelete);
    }

    public BookLoreUser getBookLoreUser(Long id) {
        BookLoreUserEntity user = userRepository.findById(id).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(id));
        return bookLoreUserTransformer.toDTO(user);
    }

    public BookLoreUser getMyself() {
        return authenticationService.getAuthenticatedUser();
    }

    public void changePassword(ChangePasswordRequest changePasswordRequest) {
        BookLoreUser bookLoreUser = authenticationService.getAuthenticatedUser();
        BookLoreUserEntity bookLoreUserEntity = userRepository.findById(bookLoreUser.getId())
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(bookLoreUser.getId()));

        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), bookLoreUserEntity.getPasswordHash())) {
            throw ApiError.PASSWORD_INCORRECT.createException();
        }

        if (passwordEncoder.matches(changePasswordRequest.getNewPassword(), bookLoreUserEntity.getPasswordHash())) {
            throw ApiError.PASSWORD_SAME_AS_CURRENT.createException();
        }

        if (!meetsMinimumPasswordRequirements(changePasswordRequest.getNewPassword())) {
            throw ApiError.PASSWORD_TOO_SHORT.createException();
        }

        bookLoreUserEntity.setDefaultPassword(false);
        bookLoreUserEntity.setPasswordHash(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        userRepository.save(bookLoreUserEntity);
        evictUserCache(bookLoreUser.getId());
    }

    @CacheEvict(value = "userAuthCache", key = "#request.userId")
    public void changeUserPassword(ChangeUserPasswordRequest request) {
        BookLoreUserEntity userEntity = userRepository.findById(request.getUserId()).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(request.getUserId()));
        if (!meetsMinimumPasswordRequirements(request.getNewPassword())) {
            throw ApiError.PASSWORD_TOO_SHORT.createException();
        }
        userEntity.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(userEntity);
    }

    @CacheEvict(value = "userAuthCache", key = "#userId")
    private void evictUserCache(Long userId) {
        // Evict cache entry for this user
    }

    public void updateUserSetting(Long userId, UpdateUserSettingRequest request) {
        BookLoreUserEntity user = userRepository.findById(userId).orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        String key = request.getKey();
        Object value = request.getValue();

        if (key == null || key.isBlank()) {
            throw ApiError.INVALID_INPUT.createException("Setting key cannot be null or blank.");
        }

        UserSettingKey settingKey;
        try {
            settingKey = UserSettingKey.fromDbKey(key);
        } catch (IllegalArgumentException e) {
            throw ApiError.INVALID_INPUT.createException("Unknown setting key: " + key);
        }

        UserSettingEntity setting = user.getSettings().stream()
                .filter(s -> s.getSettingKey().equals(key))
                .findFirst()
                .orElseGet(() -> {
                    UserSettingEntity newSetting = new UserSettingEntity();
                    newSetting.setUser(user);
                    newSetting.setSettingKey(key);
                    user.getSettings().add(newSetting);
                    return newSetting;
                });

        try {
            String serializedValue;
            if (settingKey.isJson()) {
                serializedValue = objectMapper.writeValueAsString(value);
            } else {
                serializedValue = value.toString();
            }
            setting.setSettingValue(serializedValue);
        } catch (Exception e) {
            throw ApiError.INVALID_INPUT.createException("Could not serialize setting value.");
        }

        userRepository.save(user);
    }

    private boolean meetsMinimumPasswordRequirements(String password) {
        return password != null && password.length() >= 6;
    }

    /**
     * Finds a user by ID for authentication purposes.
     * Uses lightweight query that EXCLUDES eager-loaded libraries and settings.
     * Uses read-only transaction to minimize connection hold time.
     * Cached to avoid repeated DB hits during authentication filter chain.
     *
     * CRITICAL: This method uses findByIdForAuthentication() which skips EAGER relationships.
     * Regular findById() loads libraries + settings which is expensive for concurrent auth requests.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "userAuthCache", key = "#userId", unless = "#result == null")
    public BookLoreUserEntity findUserForAuthentication(Long userId) {
        return userRepository.findByIdForAuthentication(userId).orElse(null);
    }

    /**
     * Finds a user by username for OIDC authentication.
     * Uses lightweight query that EXCLUDES eager-loaded libraries and settings.
     * Uses read-only transaction to minimize connection hold time.
     */
    @Transactional(readOnly = true)
    public BookLoreUserEntity findUserByUsernameForAuthentication(String username) {
        return userRepository.findByUsernameForAuthentication(username).orElse(null);
    }
}
